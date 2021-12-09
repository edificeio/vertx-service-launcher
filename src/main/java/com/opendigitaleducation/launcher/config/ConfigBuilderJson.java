package com.opendigitaleducation.launcher.config;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class ConfigBuilderJson extends ConfigBuilder {

    @Override
    public Future<List<ServiceConfig>> build(final List<JsonArray> services) {
        // merge keys
        final Map<String, JsonObject> servicesMerged = mergeKeys(services);
        // sort keys
        final List<JsonObject> servicesList = sortByKey(servicesMerged);
        //create model
        final List<ConfigBuilder.ServiceConfig> models = new ArrayList<>();
        for(final JsonObject jsonService : servicesList){
            final int modifiedIndex = jsonService.getInteger("ModifyIndex");
            final String key = jsonService.getString("Key");
            final String value = jsonService.getString("Value");
            if (value == null)
                continue;
            final String decoded = parseValue(value);
            models.add(new ServiceConfigImpl(key, new JsonObject(decoded),modifiedIndex));
        }
        return Future.succeededFuture(models);
    }

    class ServiceConfigImpl implements  ServiceConfig{
        private final String key;
        private final JsonObject config;
        private final int modifiedIndex;

        public ServiceConfigImpl(String key, JsonObject config, int modifiedIndex) {
            this.key = key;
            this.config = config;
            this.modifiedIndex = modifiedIndex;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public JsonObject getConfig() {
            return config;
        }

        @Override
        public boolean hasChanged(ServiceConfig previous) {
            return ((ServiceConfigImpl)previous).modifiedIndex != modifiedIndex;
        }

    }
}
