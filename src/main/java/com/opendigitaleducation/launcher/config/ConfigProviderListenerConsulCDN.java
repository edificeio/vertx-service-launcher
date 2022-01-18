package com.opendigitaleducation.launcher.config;

import com.opendigitaleducation.launcher.deployer.CustomDeployerFront;
import com.opendigitaleducation.launcher.deployer.ModuleDeployer;
import com.opendigitaleducation.launcher.resolvers.MavenServiceURIResolver;
import com.opendigitaleducation.launcher.resolvers.ServiceURIResolver;
import com.opendigitaleducation.launcher.utils.FileUtils;
import com.opendigitaleducation.launcher.utils.StringUtil;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class ConfigProviderListenerConsulCDN implements  ConfigProviderListener {
    static Logger log = LoggerFactory.getLogger(ConfigProviderListenerConsulCDN.class);
    private final HttpClient client;
    private final Optional<String> consulToken;
    private final ServiceURIResolver serviceResolver;
    private final Set<String> consulUrl = new HashSet<>();

    public ConfigProviderListenerConsulCDN(final JsonObject config, final Vertx vertx, final String servicesPath){
        this.serviceResolver = new MavenServiceURIResolver();
        this.serviceResolver.init(vertx, servicesPath);
        final HttpClientOptions options = new HttpClientOptions().setKeepAlive(config.getBoolean("consulKeepAlive", false));
        if(config.containsKey("consulToken")){
            consulToken = Optional.ofNullable(config.getString("consulToken"));
        }else{
            consulToken = Optional.empty();
        }
        final Object consulUrls = config.getValue("consulCdnUrl");
        if (!initModsUrls(consulUrls)) {
            log.error("Invalid consulCdnUrl: " + consulUrls);
        }
        client = vertx.createHttpClient(options);
    }

    public void afterConfigChange(ConfigChangeEvent event, boolean success){
        final Map<String, JsonObject> toPush = new HashMap<>();
        Integer i = 0;
        final List<Future> futures = new ArrayList<>();
        final List<JsonObject> allServices = new ArrayList<>();
        allServices.addAll(event.getServicesToDeploy());
        allServices.addAll(event.getServicesToRestart());
        for(final JsonObject service : allServices){
            try{
                final String id = ModuleDeployer.getServiceId(service);
                final String name = ModuleDeployer.getServiceName(service);
                final boolean cdnSkip = service.getBoolean("cdnSkip", false);
                if(cdnSkip){
                    log.info("Skip cdn deployment for service=" + name);
                    continue;
                }
                final String type = service.getString("type");
                final String baseDir = service.getString("cdnBaseDir", getDefaultBaseDir(type, name));
                final String srcDir = service.getString("cdnSrcDir", getDefaultSrcDir(type, service));
                final String destDir = service.getString("destDir", getDefaultDestDir(type, service));
                //to push
                final JsonObject cdnInfos = new JsonObject();
                cdnInfos.put("baseDir", baseDir);
                cdnInfos.put("srcDir", srcDir);
                cdnInfos.put("destDir", destDir);
                final String keyDef = StringUtil.padLeftZeros(i.toString(), 5) + "-";
                //final String key = service.getString("consulKey", keyDef);
                final String fullKey = keyDef + (baseDir + "_" + destDir).replace("/", "_");
                final Promise<Void> promise = Promise.promise();
                futures.add(promise.future());
                serviceResolver.resolveURI(id, uriRes -> {
                    try {
                        if (uriRes.succeeded()) {
                            final String uri = uriRes.result();
                            final String filename = FileUtils.getName(uri);
                            final String extension = FileUtils.getExtension(uri).replaceAll(".gz", "");
                            cdnInfos.put("url", uri);
                            //"extension": "tar" | "jar" | "gz"
                            cdnInfos.put("extension", extension);
                            cdnInfos.put("filename", filename);
                            toPush.put(fullKey, cdnInfos);
                        } else {
                            log.error("Could not found uri for service=" + name, uriRes.cause());
                        }
                    }catch (Exception e){
                        log.error("Error while pushing service=" + name, e);
                    }
                    promise.complete();
                });
                i++;
            }catch(Exception e){
                log.error("Could not push service "+service.getString("name")+" to CDN", e);
            }
        }
        CompositeFuture.all(futures).onComplete(e->{
            for(final String key : toPush.keySet()){
                for(final String url : consulUrl) {
                    final HttpClientRequest req = client.putAbs(url + key, resPut -> {
                        if (resPut.statusCode() > 299) {
                            log.error("Could not push to cdn service=" + key, e);
                        }
                    });
                    if (consulToken.isPresent()) {
                        req.putHeader("X-Consul-Token", consulToken.get());
                    }
                    req.exceptionHandler(err -> {
                        log.error("Could not push to cdn service=" + key, e);
                    });
                    final JsonObject cdnInfos = toPush.get(key);
                    req.end(cdnInfos.toBuffer());
                }
            }
        });
    }

    String getDefaultBaseDir(final String type, final String name){
        //"baseDir": "." | "name" | "assets/themes/..." | "assets/js/..."
        if(CustomDeployerFront.ASSETS_TYPES.equals(type)){
            return ".";
        }else if(CustomDeployerFront.THEME_TYPES.equals(type) || CustomDeployerFront.THEMES_TYPES.equals(type)){
            return CustomDeployerFront.getBaseThemes()+"/"+name;
        }else if(CustomDeployerFront.JS_TYPES.equals(type)){
            return CustomDeployerFront.getBaseJS()+"/"+name;
        }else{
            return name;
        }
    }

    String getDefaultDestDir(final String type, final JsonObject service){
        //"destDir": "assets" | "public" | "."
        if(CustomDeployerFront.ASSETS_TYPES.equals(type)){
            return CustomDeployerFront.getAssetsDir();
        }else if(CustomDeployerFront.THEME_TYPES.equals(type) || CustomDeployerFront.THEMES_TYPES.equals(type)){
            return ".";
        }else if(CustomDeployerFront.JS_TYPES.equals(type)){
            return ".";
        }else{
            return "public";
        }
    }

    String getDefaultSrcDir(final String type, final JsonObject service){
        //"srcDir": "assets" | "public" | "dist"
        if(CustomDeployerFront.ASSETS_TYPES.equals(type)){
            return CustomDeployerFront.getAssetsDir();
        }else if(CustomDeployerFront.THEME_TYPES.equals(type) || CustomDeployerFront.THEMES_TYPES.equals(type)){
            return CustomDeployerFront.getDistDir(service);
        }else if(CustomDeployerFront.JS_TYPES.equals(type)){
            return CustomDeployerFront.getDistDir(service);
        }else{
            return "public";
        }
    }

    private boolean initModsUrls(final Object urlMods) {
        if (urlMods == null) {
            return false;
        } else if (urlMods instanceof String) {
            final String url = cleanModUrl((String) urlMods);
            this.consulUrl.add(url);
            return url.trim().length() > 1;
        } else if (urlMods instanceof JsonArray) {
            for (final Object val : ((JsonArray) urlMods)) {
                if (val instanceof String) {
                    this.consulUrl.add(cleanModUrl((String) val));
                }
            }
            return this.consulUrl.size() > 0;
        } else {
            return false;
        }
    }

    private String cleanModUrl(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
}
