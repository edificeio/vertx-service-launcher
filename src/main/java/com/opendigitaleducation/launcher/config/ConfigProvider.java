package com.opendigitaleducation.launcher.config;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface ConfigProvider {

    static ConfigProvider create(JsonObject config) {
        return new ConfigProviderMemory();
    }

    ConfigProvider triggerChange(ConfigChangeEvent event);

    ConfigProvider onConfigChange(Handler<ConfigChangeEvent> handler);

    ConfigProvider start(Vertx vertx, JsonObject config);

    ConfigProvider stop(Vertx vertx);

    ConfigProvider addListener(ConfigProviderListener listener);

    JsonObject getServiceByName(String name);

}
