package com.opendigitaleducation.launcher.config;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface ConfigProvider {

    static ConfigProvider create(JsonObject config) {
        if (config.containsKey(ConfigProviderConsul.CONSUL_MODS_CONFIG)) {
            return new ConfigProviderConsul();
        } else {
            return new ConfigProviderMemory();
        }
    }

    ConfigProvider onConfigChange(Handler<ConfigChangeEvent> handler);

    ConfigProvider start(Vertx vertx, JsonObject config);

    ConfigProvider stop(Vertx vertx);
}