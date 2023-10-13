package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.Optional;

public interface ArtefactListener {
    static Optional<ArtefactListener> create(final ConfigProvider configProvider, final JsonObject config) {
        if (config.getBoolean(ArtefactListenerNexus.NEXUS_ENABLE, false)) {
            return Optional.of(new ArtefactListenerNexus(configProvider));
        }
        return Optional.empty();
    }
    ArtefactListener start(Vertx vertx, JsonObject config);
    ArtefactListener stop();
    ArtefactListener onArtefactChange(Handler<ConfigChangeEvent> handler);
}
