package com.opendigitaleducation.launcher.config;

import java.util.*;
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
        if (services.isEmpty()) {
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
        final Set<String> disabledServices = Optional.ofNullable(System.getenv("DISABLED_SERVICES"))
            .map(x -> Arrays.asList(x.split(",")))
            .orElse(Collections.emptyList())
            .stream()
            .filter(x -> !x.trim().isEmpty())
            .collect(Collectors.toSet());
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
        final JsonArray servicesThatRemain;
        if(disabledServices.isEmpty()) {
            servicesThatRemain = deployableServices;
        } else {
            final List<Object> finalServices = deployableServices.stream().filter(x -> {
                final String serviceName = ((JsonObject) x).getString("name");
                return !disabledServices.contains(serviceName.substring(0, serviceName.lastIndexOf("~")));
            }).collect(Collectors.toList());
            servicesThatRemain = new JsonArray(finalServices);
        }

        if (log.isDebugEnabled()) {
            log.debug("Deployable services : " + servicesThatRemain.encodePrettily());
        }
        dispatchSharedConfiguration(config);

        handler.handle(new ConfigChangeEventMemory(config, servicesThatRemain));
        return this;
    }

    /**
     * Takes the configuration located in the global "sharedConf" field and put it in every service.
     * Note that only first level fields are copied as a whole, we won't recursively explore objects to copy missing
     * fields (as they might not be missing but just unset for a particular reason).
     *
     * Exemple :
     * If we have<pre>
     *     "sharedConf": {
     *     "foo": "foo-value",
     *     "bar": {
     *       "innerProp": "inner Prop value",
     *       "innerObject": {
     *         "here": "there"
     *       }
     *     }
     *   },
     * "services" : [
     * {
     *   "name": "my-service",
     *   "config":{
     *     "someProp": "some Value"
     *   }
     * },
     * {
     *   "name": "other-service",
     *   "config":{
     *     "thing": "some gniht",
     *     "bar": {
     *       "innerObject": {
     *         "here": "there"
     *       }
     *     }
     *   }
     * }
     * </pre>
     * We will end up with
     * <pre>
     *     "services" : [
     * {
     *   "name": "my-service",
     *   "config":{
     *     "someProp": "some Value",
     *     "foo": "foo-value",
     *     "bar": {
     *       "innerProp": "inner Prop value",
     *       "innerObject": {
     *         "here": "there"
     *       }
     *     }
     *   }
     * },
     * {
     *   "name": "other-service",
     *   "config":{
     *     "thing": "some gniht",
     *     "foo": "foo-value",
     *     "bar": {
     *       "innerObject": {
     *         "here": "there"
     *       }
     *     }
     *   }
     * }
     * </pre>
     * <ul>
     *     <li>my-service got all the keys from sharedConf</li>
     *     <li>other-service just got the "foo" field and the field "bar" was not changed (we didn't put bar.innerProd</li>
     * </ul>
     * @param globalConfiguration Global configuration
     */
    public static void dispatchSharedConfiguration(final JsonObject globalConfiguration) {
        final JsonObject globalSharedConf = globalConfiguration.getJsonObject("sharedConf");
        if(globalSharedConf == null) {
            log.warn("No global shared configuration is set");
        } else {
            final JsonArray services = globalConfiguration.getJsonArray("services", new JsonArray());
            for (Object rawService : services) {
                final JsonObject service = (JsonObject) rawService;
                final JsonObject serviceConfig = service.getJsonObject("config");
                for (Map.Entry<String, Object> sharedConf : globalSharedConf) {
                    final String sharedConfKey = sharedConf.getKey();
                    if(!serviceConfig.containsKey(sharedConfKey)) {
                        serviceConfig.put(sharedConfKey, sharedConf.getValue());
                    }
                }
            }
        }
    }

    @Override
    public ConfigProvider start(Vertx vertx, JsonObject config) {
        this.config = valuateConfig(config);
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


    public static JsonObject valuateConfig(final JsonObject config) {
        final JsonObject valuated = new JsonObject();
        config.stream().forEach(entry -> {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final Object newValue;
            if (value instanceof String) {
                newValue = valuateConfig((String) value);
            } else if(value instanceof JsonObject) {
                newValue = valuateConfig((JsonObject) value);
            } else if(value instanceof JsonArray) {
                newValue = valuateConfig((JsonArray) value);
            } else {
                newValue = value;
            }
            valuated.put(key, newValue);
        });
        return valuated;
    }

    public static JsonArray valuateConfig(final JsonArray array) {
        final JsonArray valuated = new JsonArray();
        array.stream().forEach(value -> {
            final Object newValue;
            if (value instanceof String) {
                newValue = valuateConfig((String) value);
            } else if(value instanceof JsonObject) {
                newValue = valuateConfig((JsonObject) value);
            } else if(value instanceof JsonArray) {
                newValue = valuateConfig((JsonArray) value);
            } else {
                newValue = value;
            }
            valuated.add(newValue);
        });
        return valuated;
    }

    public static String valuateConfig(final String config) {
        // We replace all instances of ${ENV_VAR} with the value of the environment variable ENV_VAR
        // and ${ENV_VAR:default_value} with the value of the environment variable ENV_VAR or default_value if ENV_VAR is not set
        // If ${!ENV_VAR} is used, an exception is thrown if ENV_VAR is not set
        String valuated = config;
        final String matcher = "\\$\\{(!?)([a-zA-Z0-9_]+)(:([^}]*))?\\}";
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile(matcher).matcher(config);
        while (m.find()) {
            final String envVar = m.group(2);
            final String defaultValue = m.group(4);
            final String envValue = System.getenv(envVar);
            if (envValue != null) {
                valuated = valuated.replace(m.group(0), envValue);
            } else if (m.group(1).equals("!")) {
                throw new IllegalArgumentException("Environment variable " + envVar + " is not set");
            } else if (defaultValue != null) {
                valuated = valuated.replace(m.group(0), defaultValue);
            } else {
                valuated = valuated.replace(m.group(0), "");
            }
        }
        return valuated;
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
