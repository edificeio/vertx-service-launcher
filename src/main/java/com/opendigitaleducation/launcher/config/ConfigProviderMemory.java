package com.opendigitaleducation.launcher.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigProviderMemory implements ConfigProvider {
    private static final Logger log = LoggerFactory.getLogger(ConfigProviderMemory.class);
    private JsonObject config = new JsonObject();

    public ConfigProviderMemory() {
    }

    @Override
    public ConfigProvider onConfigChange(Handler<ConfigChangeEvent> handler) {
        final JsonArray services = config.getJsonArray("services", new JsonArray());
        if (services.size() == 0) {
            log.error("Missing services to deploy.");
            return this;
        }
        handler.handle(new ConfigChangeEventMemory(config, services));
        return this;
    }

    @Override
    public ConfigProvider start(Vertx vertx, JsonObject config) {
        this.config = config;
        return this;
    }

    @Override
    public ConfigProvider stop(Vertx vertx) {
        return this;
    }

    private static class ConfigChangeEventMemory extends ConfigChangeEvent {
        private final List<JsonObject> services;
        private final JsonObject originalConfig;

        ConfigChangeEventMemory(JsonObject original, JsonArray services) {
            this.originalConfig = new JsonObject(original.getMap());
            this.services = services.stream().map(e -> (JsonObject) e).collect(Collectors.toList());
        }

        @Override
        public List<JsonObject> getServicesToRestart() {
            return new ArrayList<>();
        }

        @Override
        public List<JsonObject> getServicesToUndeploy() {
            return new ArrayList<>();
        }

        @Override
        public List<JsonObject> getServicesToDeploy() {
            return services;
        }

        @Override
        public JsonObject getDump() {
            return originalConfig.put("services", new JsonArray(services));
        }

    }

}