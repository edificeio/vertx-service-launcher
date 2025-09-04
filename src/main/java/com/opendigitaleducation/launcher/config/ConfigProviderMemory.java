package com.opendigitaleducation.launcher.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

        final JsonArray deployableServices;
        final List<String> enabledServices = Optional.ofNullable(System.getenv("ENABLED_SERVICES"))
            .map(x -> Arrays.asList(x.split(",")))
            .orElse(Collections.emptyList())
            .stream()
            .filter(x -> !x.trim().isEmpty())
            .collect(Collectors.toList());
        if (enabledServices.isEmpty()) {
            deployableServices = services;
        } else {
            deployableServices = new JsonArray();
            services.stream()
                .filter(x -> {
                    final String serviceName = ((JsonObject) x).getString("name");
                    return enabledServices.contains(serviceName.substring(0, serviceName.lastIndexOf("~")));
                })
                .forEach(deployableServices::add);
        }

        if (log.isDebugEnabled()) {
            log.debug("Deployable services : " + deployableServices.encodePrettily());
        }

        handler.handle(new ConfigChangeEventMemory(config, deployableServices));
        return this;
    }

    @Override
    public ConfigProvider start(Vertx vertx, JsonObject config) {
        this.config = config;
        return this;
    }

    @Override
    public ConfigProvider triggerChange(ConfigChangeEvent event) {
        //DO NOTHING
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

    @Override
    public ConfigProvider addListener(ConfigProviderListener listener) {
        return this;
    }

    @Override
    public JsonObject getServiceByName(String name) {
        final JsonArray services = config.getJsonArray("services", new JsonArray());
        for (Object o: services) {
            if (!(o instanceof JsonObject)) continue;
            final JsonObject service = (JsonObject) o;
            if (service.getString("name", "").contains("~" + name + "~")) {
                return service;
            }
        }
        return null;
    }

}
