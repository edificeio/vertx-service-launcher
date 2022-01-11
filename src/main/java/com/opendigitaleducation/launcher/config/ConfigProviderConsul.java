package com.opendigitaleducation.launcher.config;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collector;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.opendigitaleducation.launcher.utils.FileUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigProviderConsul implements ConfigProvider {
    static DateFormat format = new SimpleDateFormat("yyyyMMdd-HHmm");
    static String CONSUL_ENABLE = "consulEnabled";
    static String CONSUL_MODS_CONFIG = "consulMods";
    public static int countDeploy = 0;
    private static final Logger log = LoggerFactory.getLogger(ConfigProviderConsul.class);
    private Vertx vertx;
    private int pullEveryMs;
    private long periodic;
    private List<String> urlMods = new ArrayList<>();
    private String urlSync;
    private HttpClient client;
    private String nodeName;
    private JsonObject originalConfig;
    private Optional<String> consulToken = Optional.empty();
    private ConfigChangeEventConsul lastEvent = new ConfigChangeEventConsul();
    private ConfigBuilder configBuilder;
    private final List<Handler<ConfigChangeEvent>> handlers = new ArrayList<>();
    private final List<ConfigProviderListener> listeners = new ArrayList<>();

    public ConfigProviderConsul() {
    }

    @Override
    public ConfigProvider onConfigChange(Handler<ConfigChangeEvent> handler) {
        handlers.add(handler);
        if (lastEvent != null) {
            handler.handle(lastEvent);
        }
        return this;
    }

    public void pushEvent(ConfigChangeEvent event) {
        for (Handler<ConfigChangeEvent> h : handlers) {
            h.handle(event);
        }
        if(event instanceof ConfigChangeEventConsul) {
            lastEvent = (ConfigChangeEventConsul) event;
        }
        //do not save lastEvent if not of type Consul(it used for compare)
    }

    @Override
    public ConfigProvider triggerChange(ConfigChangeEvent event) {
        log.info(String.format("Node %s is triggering deployment (deploy=%s, undeploy=%s, restart=%s)", nodeName, event.getServicesToDeploy().size(), event.getServicesToUndeploy().size(), event.getServicesToRestart().size()));
        doPush(event);
        return this;
    }

    private void doPush(ConfigChangeEvent event){
        triggerBeforeChange(event);
        pushEvent(event.onEnd(onFinish -> {
            triggerAfterChange(event, true);
            initTimer();
            final HttpClientRequest req = client.putAbs(urlSync + nodeName, resPut -> {
                // do nothing
                log.info(String.format("Node %s is synced with consul (deployed=%s, undeployed=%s, restart=%s)", nodeName, event.getServicesToDeploy().size(), event.getServicesToUndeploy().size(), event.getServicesToRestart().size()));
            });
            if(consulToken.isPresent()){
                req.putHeader("X-Consul-Token", consulToken.get());
            }
            req.exceptionHandler(err -> {
                log.error("Fail to send state for node: " + nodeName, err);
            });
            final Date date = new Date();
            final String dateFormat = format.format(date);
            req.end(Buffer.buffer(dateFormat));
            final String dumpFolder = originalConfig.getString("assets-path", ".") + File.separator
                + "history";
            final String file = dumpFolder + File.separator + dateFormat + "-config.json";
            vertx.fileSystem().mkdir(dumpFolder, r -> {//mkdir if not exists
                vertx.fileSystem().writeFile(file, Buffer.buffer(event.getDump().encodePrettily()),
                    resW -> {
                        if (resW.failed()) {
                            log.error("Failed to dump config : ", resW.cause());
                        }
                    });
            });
        }).onEmpty(resEmpty -> {
            initTimer();
            triggerAfterChange(event, false);
        }));
    }

    private void reload() {
        final List<Future> futures = new ArrayList<>();
        for (final String url : urlMods) {
            final Future<JsonArray> future = Future.future();
            futures.add(future);
            final HttpClientRequest req = client.getAbs(url + "?recurse", res -> {
                res.bodyHandler(body -> {
                    final JsonArray services = new JsonArray(body);
                    future.complete(services);
                });
                res.exceptionHandler(exc -> {
                    log.error("Failed to load consul config (body) : " + url, exc);
                    future.fail(exc);
                });
            }).exceptionHandler(exc -> {
                log.error("Failed to load consul config (connection) : " + url, exc);
                future.fail(exc);
            });
            if(consulToken.isPresent()){
                req.putHeader("X-Consul-Token", consulToken.get());
            }
            req.end();
        }
        CompositeFuture.all(futures).compose(res->{
            return configBuilder.build(res.result().list());
        }).onComplete(res -> {
            if (res.succeeded()) {
                countDeploy++;
                final ConfigChangeEventConsul event = new ConfigChangeEventConsul(originalConfig, res.result(), lastEvent);
                doPush(event);
            } else {
                log.error("Failed to build config:", res.cause());
                initTimer();
            }
        });
    }

    private void initTimer() {
        periodic = vertx.setTimer(pullEveryMs, res -> {
            reload();
        });
    }

    private String cleanModUrl(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    private boolean initModsUrls(Object urlMods) {
        if (urlMods == null) {
            return false;
        } else if (urlMods instanceof String) {
            final String url = cleanModUrl((String) urlMods);
            this.urlMods.add(url);
            return url.trim().length() > 1;
        } else if (urlMods instanceof JsonArray) {
            for (final Object val : ((JsonArray) urlMods)) {
                if (val instanceof String) {
                    this.urlMods.add(cleanModUrl((String) val));
                }
            }
            return this.urlMods.size() > 0;
        } else {
            return false;
        }
    }

    @Override
    public ConfigProvider start(Vertx vertx, JsonObject config) {
        final String depPath = FileUtils.absolutePath(System.getProperty("vertx.deployments.path"));
        final String servicesPath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
        final String safeDepPath = depPath != null && !depPath.isEmpty()? depPath:servicesPath;
        this.vertx = vertx;
        originalConfig = config;
        pullEveryMs = config.getInteger("pullEverySeconds", 1) * 1000;
        configBuilder = config.getBoolean("use-template", true)?ConfigBuilder.fromTemplate(vertx, safeDepPath): ConfigBuilder.fromJsons();
        // url mods
        Object urlMods = config.getValue(CONSUL_MODS_CONFIG);
        if (!initModsUrls(urlMods)) {
            log.error("Invalid consul mods url: " + urlMods);
            return this;
        }
        // url sync
        urlSync = config.getString("consulSync");
        if (!urlSync.endsWith("/")) {
            urlSync += "/";
        }
        if (urlSync == null || urlSync.trim().isEmpty()) {
            log.error("Invalid consul sync url: " + urlSync);
            return this;
        }
        //
        nodeName = config.getString("node", config.getString("consuleNode", "unknown"));
        final HttpClientOptions options = new HttpClientOptions().setKeepAlive(config.getBoolean("consulKeepAlive", false));
        if(config.containsKey("consulToken")){
            consulToken = Optional.ofNullable(config.getString("consulToken"));
        }
        client = vertx.createHttpClient(options);
        reload();
        return this;
    }

    @Override
    public ConfigProvider stop(Vertx vertx) {
        vertx.cancelTimer(periodic);
        return this;
    }

    private static class ConfigChangeEventConsul extends ConfigChangeEvent {
        private List<ConfigBuilder.ServiceConfig> orderedServices = new ArrayList<>();
        private Map<String, ConfigBuilder.ServiceConfig> modelByKey = new HashMap<>();
        private List<JsonObject> toRestart = new ArrayList<>();
        private List<JsonObject> toDeploy = new ArrayList<>();
        private List<JsonObject> toUndeploy = new ArrayList<>();
        private final JsonObject originalConfig;

        ConfigChangeEventConsul() {
            originalConfig = new JsonObject();
        }

        ConfigChangeEventConsul(ConfigChangeEvent current, ConfigChangeEvent lastEvent) {
            originalConfig = new JsonObject();
            //init from last event
            this.toDeploy.addAll(lastEvent.getServicesToDeploy());
            this.toUndeploy.addAll(lastEvent.getServicesToUndeploy());
            this.toRestart.addAll(lastEvent.getServicesToRestart());
            //remove all services from current event and add it anew
            final List<JsonObject> all = current.getAll();
            this.toDeploy.removeAll(all);
            this.toUndeploy.removeAll(all);
            this.toRestart.removeAll(all);
            //add all services from current event
            this.toDeploy.addAll(current.getServicesToDeploy());
            this.toUndeploy.addAll(current.getServicesToUndeploy());
            this.toRestart.addAll(current.getServicesToRestart());
            for(Handler<Boolean> h : current.getEndHandlers()){
                this.onEnd(h);
            }
        }

        ConfigChangeEventConsul(JsonObject originalConfig, List<ConfigBuilder.ServiceConfig> servicesList, ConfigChangeEventConsul previous) {
            this.originalConfig = new JsonObject(originalConfig.getMap());
            this.orderedServices = servicesList;
            // compute changes
            for (final ConfigBuilder.ServiceConfig jsonService : servicesList) {
                modelByKey.put(jsonService.getKey(), jsonService);
                final JsonObject serviceConfig = jsonService.getConfig();
                serviceConfig.put("consulKey", jsonService.getKey());
                if (previous.keyExists(jsonService)) {
                    final ConfigBuilder.ServiceConfig previousService = previous.getService(jsonService);
                    if (previous.valueHasChanges(jsonService)) {
                        if (jsonService.getFullQualifiedName().equals(previousService.getFullQualifiedName())) {
                            // restart module
                            toRestart.add(serviceConfig);
                        } else {
                            // redploy module
                            toDeploy.add(serviceConfig);
                            toUndeploy.add(serviceConfig);
                        }
                    } else {
                        // DO NOTHING IF NOTHING HAS CHANGED
                    }
                } else {
                    toDeploy.add(serviceConfig);
                }
            }
            //get services to undeploy
            for (final String previousKey : previous.getSortedKeys()) {
                if (modelByKey.containsKey(previousKey)) {
                    // DO NOTHING
                } else {
                    // remove module
                    toUndeploy.add(previous.getService(previousKey).getConfig());
                }
            }
        }

        public List<String> getSortedKeys() {
            return orderedServices.stream().map(e->e.getKey()).collect(Collectors.toList());
        }

        public ConfigBuilder.ServiceConfig getService(final String key) {
            return modelByKey.get(key);
        }

        public ConfigBuilder.ServiceConfig getService(final ConfigBuilder.ServiceConfig key) {
            return modelByKey.get(key.getKey());
        }

        public boolean keyExists(final ConfigBuilder.ServiceConfig key) {
            return modelByKey.containsKey(key.getKey());
        }

        public boolean valueHasChanges(final ConfigBuilder.ServiceConfig other) {
            return modelByKey.get(other.getKey()).hasChanged(other);
        }

        @Override
        public List<JsonObject> getServicesToRestart() {
            return toRestart;
        }

        @Override
        public List<JsonObject> getServicesToUndeploy() {
            return toUndeploy;
        }

        @Override
        public List<JsonObject> getServicesToDeploy() {
            return toDeploy;
        }

        @Override
        public JsonObject getDump() {
            final JsonArray services = new JsonArray();
            for (final ConfigBuilder.ServiceConfig service : orderedServices) {
                services.add(service.getConfig());
            }
            return originalConfig.put("services", services);
        }

    }

    protected void triggerBeforeChange(ConfigChangeEvent event){
        for(final ConfigProviderListener l : listeners){
            l.beforeConfigChange(event);
        }
    }

    protected void triggerAfterChange(ConfigChangeEvent event, boolean success){
        for(final ConfigProviderListener l : listeners){
            l.afterConfigChange(event, success);
        }
    }

    @Override
    public ConfigProvider addListener(ConfigProviderListener listener) {
        listeners.add(listener);
        return this;
    }
}
