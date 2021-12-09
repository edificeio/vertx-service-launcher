package com.opendigitaleducation.launcher.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ConfigBuilder {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    abstract Future<List<ServiceConfig>> build(final List<JsonArray> services);

    protected Map<String, JsonObject> mergeKeys(final List<JsonArray> services){
        final Map<String, JsonObject> servicesMerged = new HashMap<>();
        for (JsonArray serviceListArray : services) {
            for (Object serviceObject : serviceListArray) {
                final JsonObject serviceJson = (JsonObject) serviceObject;
                final String[] keys = serviceJson.getString("Key", "").split("/");
                final String key = keys[keys.length - 1];
                if (servicesMerged.containsKey(key) && ConfigProviderConsul.countDeploy == 1) {
                    log.info("Overriding application : " + key);
                }
                servicesMerged.put(key, serviceJson);
            }
        }
        return servicesMerged;
    }

    protected List<JsonObject> sortByKey(final Map<String, JsonObject> servicesMerged){
        final List<JsonObject> servicesList = servicesMerged.entrySet().stream().sorted((a, b) -> {
            final String keya = a.getKey();
            final String keyb = b.getKey();
            return keya.compareTo(keyb);
        }).map(e -> e.getValue()).collect(Collectors.toList());
        return servicesList;
    }

    protected String parseValue(final String value){
        if(value == null) return value;
        final String decoded = new String(Base64.getDecoder().decode(value));
        return decoded;
    }

    protected String parseValue(final JsonObject json){
        final String value = json.getString("Value");
        return parseValue(value);
    }

    static ConfigBuilder fromJsons(){
        return new ConfigBuilderJson();
    }

    static ConfigBuilder fromTemplate(final Vertx vertx, final String servicePath){
        return new ConfigBuilderTemplate(vertx, servicePath);
    }

    interface ServiceConfig{
        Logger log = LoggerFactory.getLogger(ServiceConfig.class);
        String getKey();
        JsonObject getConfig();
        boolean hasChanged(final ServiceConfig previous);

        default String getFullQualifiedName() {
            final String name = getConfig().getString("name");
            if (name == null) {
                log.warn("Could not found FullQualifiedName for key : " + getKey() + " (" + getConfig().encode() + ")");
                return "";
            }
            return name;
        }
    }
}
