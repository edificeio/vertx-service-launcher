package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.VertxServiceLauncher;
import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

public interface ArtefactListener {
    static List<ArtefactListener> create(final ConfigProvider configProvider, final JsonObject config) throws Exception {
        final List<ArtefactListener> listeners = new ArrayList<>();
        if (config.getBoolean(ArtefactListenerNexus.NEXUS_ENABLE, false)) {
            listeners.add(new ArtefactListenerNexus(configProvider));
        }
        if (config.getBoolean(ArtefactListenerLocal.WATCHER_ENABLE, false)) {
            listeners.add(new ArtefactListenerLocal(configProvider));
        }
        if (config.getBoolean(ArtefactListenerFileSystem.WATCHER_ENABLE, false)) {
            listeners.add(new ArtefactListenerFileSystem(configProvider,config));
        }
        return listeners;
    }
    ArtefactListener start(Vertx vertx, JsonObject config);
    ArtefactListener stop();
    ArtefactListener onArtefactChange(Handler<ConfigChangeEvent> handler);
}
