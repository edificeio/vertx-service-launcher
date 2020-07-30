package com.opendigitaleducation.launcher.config;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
    static String CONSUL_MODS_CONFIG = "consulMods";
    private static int countDeploy = 0;
    private static final Logger log = LoggerFactory.getLogger(ConfigProviderConsul.class);
    private Vertx vertx;
    private int pullEveryMs;
    private long periodic;
    private List<String> urlMods = new ArrayList<>();
    private String urlSync;
    private HttpClient client;
    private String nodeName;
    private JsonObject originalConfig;
    private ConfigChangeEventConsul lastEvent = new ConfigChangeEventConsul();
    private final List<Handler<ConfigChangeEvent>> handlers = new ArrayList<>();

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
        lastEvent = (ConfigChangeEventConsul) event;
    }

    private void reload() {
        final List<Future> futures = new ArrayList<>();
        for (final String url : urlMods) {
            final Future<JsonArray> future = Future.future();
            futures.add(future);
            client.getAbs(url + "?recurse", res -> {
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
            }).end();
        }
        CompositeFuture.all(futures).setHandler(res -> {
            if (res.succeeded()) {
                countDeploy++;
                final ConfigChangeEventConsul event = new ConfigChangeEventConsul(originalConfig, res.result().list(),
                        lastEvent);
                pushEvent(event.onEnd(onFinish -> {
                    initTimer();
                    if (onFinish) {
                        final HttpClientRequest req = client.putAbs(urlSync + nodeName, resPut -> {
                            // do nothing
                            log.info(String.format("Node %s is synced with consul", nodeName));
                        });
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
                    }
                }).onEmpty(resEmpty -> {
                    initTimer();
                }));
            } else {
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
        this.vertx = vertx;
        originalConfig = config;
        pullEveryMs = config.getInteger("pullEverySeconds", 1) * 1000;
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
        client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false));
        reload();
        return this;
    }

    @Override
    public ConfigProvider stop(Vertx vertx) {
        vertx.cancelTimer(periodic);
        return this;
    }

    private static class ConfigChangeEventConsul extends ConfigChangeEvent {
        private Map<String, Integer> indexesByKey = new HashMap<>();
        private Map<String, String> valueByKey = new HashMap<>();
        private Map<String, JsonObject> lazyValueAsJsonByKey = new HashMap<>();
        private List<JsonObject> toRestart = new ArrayList<>();
        private List<JsonObject> toDeploy = new ArrayList<>();
        private List<JsonObject> toUndeploy = new ArrayList<>();
        private List<String> keys = new ArrayList<>();
        private final JsonObject originalConfig;

        ConfigChangeEventConsul() {
            originalConfig = new JsonObject();
        }

        ConfigChangeEventConsul(JsonObject originalConfig, List<JsonArray> services, ConfigChangeEventConsul previous) {
            this.originalConfig = new JsonObject(originalConfig.getMap());
            // merge keys
            final Map<String, JsonObject> servicesMerged = new HashMap<>();
            for (JsonArray serviceListArray : services) {
                for (Object serviceObject : serviceListArray) {
                    final JsonObject serviceJson = (JsonObject) serviceObject;
                    final String[] keys = serviceJson.getString("Key", "").split("/");
                    final String key = keys[keys.length - 1];
                    if (servicesMerged.containsKey(key) && countDeploy == 1) {
                        log.info("Overriding application : " + key);
                    }
                    servicesMerged.put(key, serviceJson);
                }
            }
            // sort keys
            final List<JsonObject> servicesList = servicesMerged.entrySet().stream().sorted((a, b) -> {
                final String keya = a.getKey();
                final String keyb = b.getKey();
                return keya.compareTo(keyb);
            }).map(e -> e.getValue()).collect(Collectors.toList());
            // compute changes
            for (final JsonObject jsonService : servicesList) {
                final int modifiedIndex = jsonService.getInteger("ModifyIndex");
                final String key = jsonService.getString("Key");
                final String value = jsonService.getString("Value");
                if (value == null)
                    continue;
                indexesByKey.put(key, modifiedIndex);
                valueByKey.put(key, value);
                keys.add(key);
                if (previous.keyExists(key)) {
                    if (previous.valueHasChanges(key, modifiedIndex)) {
                        if (getFullQualifiedName(key).equals(previous.getFullQualifiedName(key))) {
                            // restart module
                            toRestart.add(getJsonValue(key));
                        } else {
                            // redploy module
                            toDeploy.add(getJsonValue(key));
                            toUndeploy.add(previous.getJsonValue(key));
                        }
                    } else {
                        // DO NOTHING IF NOTHING HAS CHANGED
                    }
                } else {
                    toDeploy.add(getJsonValue(key));
                }
            }
            //
            for (String previousKey : previous.getSortedKeys()) {
                if (keys.contains(previousKey)) {
                    // DO NOTHING
                } else {
                    // remove module
                    toUndeploy.add(previous.getJsonValue(previousKey));
                }
            }
        }

        public List<String> getSortedKeys() {
            return keys;
        }

        public JsonObject getJsonValue(String key) {
            if (lazyValueAsJsonByKey.containsKey(key)) {
                return lazyValueAsJsonByKey.get(key);
            } else {
                if (valueByKey.containsKey(key)) {
                    if (valueByKey.get(key) == null) {
                        System.out.println();
                    }
                    final String decoded = new String(Base64.getDecoder().decode(valueByKey.get(key)));
                    lazyValueAsJsonByKey.put(key, new JsonObject(decoded));
                    return lazyValueAsJsonByKey.get(key);
                } else {
                    log.warn("Could not found jsonConfig for key : " + key);
                    return new JsonObject();
                }
            }
        }

        public String getFullQualifiedName(String key) {
            final JsonObject json = getJsonValue(key);
            final String name = json.getString("name");
            if (name == null) {
                log.warn("Could not found FullQualifiedName for key : " + key + " (" + json.encode() + ")");
                return "";
            }
            return name;
        }

        public boolean keyExists(String key) {
            return keys.contains(key);
        }

        public boolean valueHasChanges(String key, int newModifiedIndex) {
            return indexesByKey.get(key).intValue() != newModifiedIndex;
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
            for (final String key : keys) {
                services.add(getJsonValue(key));
            }
            return originalConfig.put("services", services);
        }

    }

}