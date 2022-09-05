package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.VertxServiceLauncher;
import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.compress.utils.FileNameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArtefactListenerLocal extends ArtefactListenerAbstract<ArtefactListenerNexus.ConfigChangeEventNexus> {
    public static final String WATCHER_ENABLE = "watcherEnabled";
    public static final String WATCHER_SPAN = "watcherLockSeconds";
    public static final String WATCHER_PORT = "watcherPort";
    private static final Logger log = LoggerFactory.getLogger(ArtefactListenerLocal.class);
    private HttpServer httpServer;

    public ArtefactListenerLocal(final ConfigProvider aConfigProvider) {
        super(aConfigProvider);
    }


    @Override
    public ArtefactListener start(Vertx vertx, JsonObject config) {
        if (httpServer != null) {
            httpServer.close();
        }
        final int port = config.getInteger(WATCHER_PORT, 7999);
        final int seconds = config.getInteger(WATCHER_SPAN, 1);
        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(reqH -> {
            if ("GET".equalsIgnoreCase(reqH.method().name())) {
                try {
                    final List<String> modules = new ArrayList<>();
                    final String name = reqH.getParam("name");
                    final String extension = FileNameUtils.getExtension(name);
                    if (recentlyTriggered.contains(name)) {
                        reqH.response().setStatusCode(200).end(new JsonArray(modules).encode());
                        return;
                    }
                    for (final Map.Entry<String, JsonObject> entry : names.entrySet()) {
                        final String serviceName = entry.getValue().getString("name", "");
                        if (name.contains(serviceName)) {
                            final JsonObject value = entry.getValue();
                            //nexus need some delay to return the last artefact when deploy
                            log.info("Trigger deploy from nexus for module : " + name);
                            //if no extension => dir (cont clean) else clean
                            pushServiceAddedRecently(vertx, name, seconds);
                            final VertxServiceLauncher.Clean clean = extension == null || extension.trim().isEmpty() ? VertxServiceLauncher.Clean.None : VertxServiceLauncher.Clean.Dir;
                            final ConfigChangeEvent atr = new ArtefactListenerNexus.ConfigChangeEventNexus(value).setCleanType(clean);
                            if(clean.equals(VertxServiceLauncher.Clean.None)){
                                atr.getServicesToUndeploy().clear();
                            }
                            pushEvent(atr);
                            modules.add(entry.getKey());
                        }
                    }
                    reqH.response().setStatusCode(200).end(new JsonArray(modules).encode());
                } catch (Exception e) {
                    reqH.response().setStatusCode(500).end(new JsonObject().put("error", "unknown").encode());
                    log.error("Failed to respond: ", e);
                }
            } else {
                reqH.response().setStatusCode(405).end();
            }
        }).listen(port);
        log.info("Listening nexus repository on port : " + port);
        return this;
    }

    @Override
    public ArtefactListener stop() {
        httpServer.close();
        return this;
    }
}
