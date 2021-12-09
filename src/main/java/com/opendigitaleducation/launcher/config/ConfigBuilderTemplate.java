package com.opendigitaleducation.launcher.config;

import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.ZipUtils;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigBuilderTemplate extends ConfigBuilder {
    static final double DEFAULT_PRIORITY = 100;
    static DateFormat format = new SimpleDateFormat("yyyyMMdd-HHmm");
    static final Pattern versionRegex = Pattern.compile("(.+)~(.+)~(.+)");
    private final Vertx vertx;
    private final String servicePath;
    private final ServiceResolverFactory serviceResolverFatJar;
    private final ServiceResolverFactory serviceResolverTarGz;
    private final boolean dumpTemplate;
    private final boolean dumpTemplateOnError;


    ConfigBuilderTemplate(final Vertx vertx, final String servicePath, final JsonObject config){
        this.vertx = vertx;
        this.servicePath = servicePath;
        this.dumpTemplate = config.getBoolean("dump-template", false);
        this.dumpTemplateOnError = config.getBoolean("dump-template-onerror", true);
        this.serviceResolverFatJar = new ServiceResolverFactory();
        this.serviceResolverTarGz = new ServiceResolverFactory();
        this.serviceResolverFatJar.init(vertx, servicePath);
        this.serviceResolverTarGz.init(vertx, servicePath, ".tar.gz");
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
                }else{
                    valuesByKeys.put(finalKey, "");
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
            return generateJinja2Template(templates, valuesByKeys);
            //return json generated
        }).compose(template->{
            final Promise<List<ServiceConfig>> promiseTpl = Promise.promise();
            try {
                //SORT BY priority field (default value = 100)
                final JsonObject allJson = new JsonObject(template);
                final JsonArray jsonServices = allJson.getJsonArray("services", new JsonArray());
                final List<ServiceConfig> sorted = jsonServices.stream().filter(e -> {
                    return e instanceof JsonObject;
                }).map(e -> {
                    return (JsonObject) e;
                }).sorted((a, b) -> {
                    final Double prioA = a.getDouble("priority", DEFAULT_PRIORITY);
                    final Double prioB = a.getDouble("priority", DEFAULT_PRIORITY);
                    return prioA.compareTo(prioB);
                }).map(e -> {
                    return new ServiceConfigImpl(e.getString("name", ""), e);
                }).collect(Collectors.toList());
                if(this.dumpTemplate){
                    final Date date = new Date();
                    final String dateFormat = format.format(date);
                    final String fileName = servicePath+File.separator+".."+File.separator+"dump"+File.separator+dateFormat+"-debug.json";
                    vertx.fileSystem().writeFile(fileName, Buffer.buffer(template), ee->{});
                }
                promiseTpl.complete(sorted);
            }catch(Exception e){
                if(this.dumpTemplateOnError){
                    final Date date = new Date();
                    final String dateFormat = format.format(date);
                    final String fileName = servicePath+File.separator+".."+File.separator+"dump"+File.separator+dateFormat+"-error.json";
                    vertx.fileSystem().writeFile(fileName, Buffer.buffer(template), ee->{});
                }
                promiseTpl.fail(e);
            }
            return promiseTpl.future();
        });
    }

    protected Future<List<String>> downloadTemplates(final List<String> identifiers){
        final List<Future> futures = new ArrayList<>();
        for(final String identifier : identifiers){
            final Promise<String> promise = Promise.promise();
            final String [] id = identifier.split("~");
            if (id.length != 3) {
                promise.fail("Invalid identifier: "+identifier);
            }else {
                final String name = id[1];
                resolve(identifier, name).onComplete(destZipAr -> {
                    if (destZipAr.succeeded()) {
                        final String destZip = destZipAr.result();
                        readConfig(destZip, name).onComplete(promise);
                    } else {
                        promise.fail(destZipAr.cause());
                    }
                });
            }
            futures.add(promise.future().otherwise(""));
        }
        return CompositeFuture.all(futures).map(templates->{
            return templates.result().list().stream().map(e -> (String)e).filter(e-> e!= null && !e.isEmpty()).collect(Collectors.toList());
        });
    }

    protected Future<String> readConfig(final String destZip, final String name){
        final Promise<String> promise = Promise.promise();
        final String confPathDefault = destZip + File.separator + name + File.separator + "conf.j2";
        vertx.fileSystem().readFile(confPathDefault, ar1 -> {
            if(ar1.succeeded()){
                promise.complete(ar1.result().toString());
            }else{
                final String confPathRoot = destZip + File.separator + "conf.j2";
                vertx.fileSystem().readFile(confPathRoot, ar2 -> {
                    if(ar2.succeeded()){
                        promise.complete(ar2.result().toString());
                    }else{
                        promise.fail(ar2.cause());
                    }
                });
            }
        });
        return promise.future();
    }

    protected Future<String> resolve(final String identifier, final String id){
        final Promise<String> promise = Promise.promise();
        final String destZip = this.servicePath + File.separator + identifier + File.separator;
        serviceResolverFatJar.resolve(identifier, jar->{
            if (jar.succeeded()) {
                ZipUtils.unzipJar(vertx, jar.result(), destZip, res -> {
                    if(res.succeeded()){
                        promise.complete(destZip);
                    }else{
                        promise.fail(res.cause());
                    }
                });
            } else {
                serviceResolverTarGz.resolve(identifier, tar -> {
                    if(tar.succeeded()){
                        ZipUtils.unzip(vertx, tar.result(), destZip, res -> {
                            if(res.succeeded()){
                                promise.complete(destZip);
                            }else{
                                promise.fail(res.cause());
                            }
                        });
                    }else{
                        promise.fail("Could not found (JAR/TAR): " + identifier);
                    }
                });
            }
        });
        return promise.future();
    }

    protected Future<String> generateSimpleTemplate(final List<String> templates, final Map<String, String> properties){
        try {
            final String tplServices = String.join(",",templates);
            final String tplGlobal = String.format("{\"services\":[%s]}",tplServices);
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

    protected Future<String> generateJinja2Template(final List<String> templates, final Map<String, String> properties){
        try {
            final String tplServices = String.join(",",templates);
            final String tplGlobal = String.format("{\"services\":[%s]}",tplServices);
            final Jinjava jinjava = new Jinjava();
            final Map<String, Object> context = Maps.newHashMap();
            for(final String key : properties.keySet()){
                final String value = properties.get(key);
                //parse json if needed
                try{
                    context.put(key, new JsonObject(value));
                }catch(Exception e){
                    try{
                        context.put(key, new JsonArray(value));
                    }catch(Exception ee){
                        context.put(key, value);
                    }
                }
            }
            final String renderedTemplate = jinjava.render(tplGlobal, context);
            return Future.succeededFuture(renderedTemplate);
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
