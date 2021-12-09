package com.opendigitaleducation.launcher.config;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.ZipUtils;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigBuilderTemplate extends ConfigBuilder {
    static final int DEFAULT_PRIORITY = 100;
    static final Pattern versionRegex = Pattern.compile("(.+)~(.+)~(.+)");
    private final Vertx vertx;
    private final String servicePath;
    private final ServiceResolverFactory serviceResolver;

    ConfigBuilderTemplate(final Vertx vertx, final String servicePath){
        this.vertx = vertx;
        this.servicePath = servicePath;
        this.serviceResolver = new ServiceResolverFactory();
        this.serviceResolver.init(vertx, servicePath, "-deployment.jar");
    }

    @Override
    public Future<List<ServiceConfig>> build(final List<JsonArray> services) {
        // merge keys
        final Map<String, JsonObject> servicesMerged = mergeKeys(services);
        //all values
        final List<String> allValues = new ArrayList<>();
        final Map<String, String> valuesByKeys = new HashMap<>();
        for(final JsonObject json : servicesMerged.values()){
            final String key = json.getString("Key");
            try {
                final String value = parseValue(json);
                final String[] splittedKeys = key.split("/");
                final String finalKey = splittedKeys[splittedKeys.length-1];
                if(value != null){
                    allValues.add(value);
                    valuesByKeys.put(finalKey, value);
                }
            }catch(Exception e){
                log.warn("Fail to parse key="+key, e);
            }
        }
        //get groupIds/artefactIds/version
        final List<String> versions = new ArrayList<>();
        for(final String value : allValues){
            try {
                if (versionRegex.matcher(value).find()) {
                    versions.add(value);
                }
            }catch(Exception e){
                log.warn("Fail to check value="+value, e);
            }
        }
        //download templates
        return downloadTemplates(versions).compose(templates->{
            //generate template json from key/values and template
            return generateConfig(templates, valuesByKeys);
            //return json generated
        }).map(template->{
            //SORT BY priority field (default value = 100)
            final JsonObject allJson = new JsonObject(template);
            final JsonArray jsonServices = allJson.getJsonArray("services", new JsonArray());
            final List<ServiceConfig> sorted = jsonServices.stream().filter(e->{
                return e instanceof JsonObject;
            }).map(e->{
                return (JsonObject)e;
            }).sorted((a,b)->{
                final Integer prioA = a.getInteger("priority", DEFAULT_PRIORITY);
                final Integer prioB = a.getInteger("priority", DEFAULT_PRIORITY);
                return prioA.compareTo(prioB);
            }).map(e->{
                return new ServiceConfigImpl(e.getString("name", ""), e);
            }).collect(Collectors.toList());
            return sorted;
        });
    }

    protected Future<List<String>> downloadTemplates(final List<String> identifiers){
        final List<Future> futures = new ArrayList<>();
        for(final String identifier : identifiers){
            final Promise<String> promise = Promise.promise();
            serviceResolver.resolve(identifier, jar->{
                if (jar.succeeded()) {
                    final String jarPath = jar.result();
                    final String folderPath = jarPath.replace("-deployment.jar","");
                    final String [] id = identifier.split("~");
                    if (id.length != 3) {
                        promise.fail("Invalid identifier: "+identifier);
                        return;
                    }
                    final String name = id[1];
                    final String destZip = folderPath + File.separator;
                    ZipUtils.unzipJar(vertx, jar.result(), destZip, res -> {
                        if (res.succeeded()) {
                            final String confPath = destZip + File.separator + name + File.separator + "conf.json.template";
                            vertx.fileSystem().readFile(confPath, ar -> {
                                if(ar.succeeded()){
                                    promise.complete(ar.result().toString());
                                }else{
                                    promise.fail(ar.cause());
                                }
                            });
                        } else {
                            promise.fail(res.cause());
                        }
                    });
                } else {
                    promise.fail("Deployment not found (JAR): " + identifier);
                }
            });
            futures.add(promise.future().otherwise(""));
        }
        return CompositeFuture.all(futures).map(templates->{
            return templates.result().list().stream().map(e -> (String)e).filter(e-> e!= null && !e.isEmpty()).collect(Collectors.toList());
        });
    }

    protected Future<String> generateConfig(final List<String> templates, final Map<String, String> properties){
        try {
            final String tplServices = String.join(",",templates);
            final String tplGlobal = String.format("{services:[%s]}",tplServices);
            Template template;
            final Reader original = new StringReader(tplGlobal);
            try {
                SimpleTemplateEngine engine = new SimpleTemplateEngine();
                template = engine.createTemplate(original);
            } finally {
                original.close();
            }
            final Writer writer = new StringWriter();
            try {
                template.make(properties).writeTo(writer);
                final String result = writer.toString();
                return Future.succeededFuture(result);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    class ServiceConfigImpl implements  ServiceConfig{
        private final String key;
        private final JsonObject config;

        public ServiceConfigImpl(String key, JsonObject config) {
            this.key = key;
            this.config = config;
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
            return !((ServiceConfigImpl)previous).config.toString().equals(config.toString());
        }

    }
}
