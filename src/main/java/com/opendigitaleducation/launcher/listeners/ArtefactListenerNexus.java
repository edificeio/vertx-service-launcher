package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.VertxServiceLauncher;
import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ArtefactListenerNexus extends ArtefactListenerAbstract<ArtefactListenerNexus.ConfigChangeEventNexus> {
    static String HEADER_KEY = "x-nexus-webhook-signature";
    public static final String NEXUS_ENABLE = "nexusListenerEnabled";
    public static final String NEXUS_PORT = "nexusListenerPort";
    public static final String NEXUS_SPAN = "nexusListenerLockSeconds";
    public static final String NEXUS_DELAY = "nexusListenerDelaySeconds";
    public static final String NEXUS_HMAC_ENABLE = "nexusHmacEnable";
    public static final String NEXUS_HMACKEY = "nexusHmacKey";
    private static final Logger log = LoggerFactory.getLogger(ArtefactListenerNexus.class);
    private HttpServer httpServer;
    public ArtefactListenerNexus(final ConfigProvider aConfigProvider){
        super(aConfigProvider);
    }


    @Override
    public ArtefactListener start(Vertx vertx, JsonObject config){
        if(httpServer != null){
            httpServer.close();
        }
        final boolean enableHmac = config.getBoolean(NEXUS_HMAC_ENABLE, false);
        final String hmacKey = config.getString(NEXUS_HMACKEY, "");
        final int port = config.getInteger(NEXUS_PORT, 7999);
        final int seconds = config.getInteger(NEXUS_SPAN, 30);
        final int delaySeconds = config.getInteger(NEXUS_DELAY, 5);
        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(reqH->{
            if("POST".equalsIgnoreCase(reqH.method().name())){
                reqH.bodyHandler(body -> {
                    try {
                        final List<String> modules = new ArrayList<>();
                        if (enableHmac) {
                            final String verify = reqH.getHeader(HEADER_KEY);
                            final boolean valid = HMAC.hashIsValid(hmacKey, body.toString(), verify);
                            if(!valid){
                                final String computed = HMAC.computeHash(hmacKey, body.toString());
                                log.error("HMAC signature invalid: "+verify+" / "+computed);
                                reqH.response().setStatusCode(403).end(new JsonObject().put("error", "invalid hmac").encode());
                                return;
                            }
                        }
                        final JsonObject res = new JsonObject(body);
                        if (res.containsKey("action") && "CREATED".equals(res.getString("action"))) {
                            if (res.containsKey("asset")) {
                                final JsonObject asset = res.getJsonObject("asset", new JsonObject());
                                final String name = asset.getString("name", "");
                                final boolean acceptExtension = name.endsWith("-fat.jar") || name.endsWith("tar.gz");
                                if (recentlyTriggered.contains(name) || !acceptExtension) {
                                    if(!name.endsWith(".sha1") && !name.endsWith(".md5") && !name.endsWith(".pom")){
                                        log.info("SKIP Trigger deploy from nexus for module : " + name);
                                    }
                                    reqH.response().setStatusCode(200).end(new JsonArray(modules).encode());
                                    return;
                                }
                                for (final Map.Entry<String, JsonObject> entry : names.entrySet()) {
                                    if (name.contains(entry.getKey())) {
                                        final JsonObject value = entry.getValue();
                                        //nexus need some delay to return the last artefact when deploy
                                        vertx.setTimer(delaySeconds * 1000, r->{
                                            log.info("Trigger deploy from nexus for module : " + name);
                                            pushEvent(new ConfigChangeEventNexus(value).setCleanType(VertxServiceLauncher.Clean.All));
                                        });
                                        modules.add(entry.getKey());
                                    }
                                }
                                pushServiceAddedRecently(vertx, name, seconds);
                            }
                        }
                        reqH.response().setStatusCode(200).end(new JsonArray(modules).encode());
                    }catch (Exception e){
                        reqH.response().setStatusCode(500).end(new JsonObject().put("error", "unknown").encode());
                        log.error("Failed to respond: ",e);
                    }
                });
            }else{
                reqH.response().setStatusCode(405).end();
            }
        }).listen(port);
        log.info("Listening nexus repository on port : "+port);
        return this;
    }

    @Override
    public ArtefactListener stop(){
        httpServer.close();
        return this;
    }


    public static class ConfigChangeEventNexus extends ConfigChangeEvent {
        private final List<JsonObject> services = new ArrayList<>();

        ConfigChangeEventNexus(JsonObject service) {
            services.add(service);
        }

        @Override
        public List<JsonObject> getServicesToRestart() {
            return new ArrayList<>();
        }

        @Override
        public List<JsonObject> getServicesToUndeploy() {
            return services;
        }

        @Override
        public List<JsonObject> getServicesToDeploy() {
            return services;
        }

        @Override
        public JsonObject getDump() {
            final JsonObject services = new JsonObject();
            return services.put("services", new JsonArray(this.services));
        }

    }


    public static class HMAC
    {

        public static String computeHash(String secret, String payload) throws InvalidKeyException, NoSuchAlgorithmException
        {
            final String digest = "HmacSHA256";
            final Mac mac = Mac.getInstance(digest);
            mac.init(new SecretKeySpec(secret.getBytes(), digest));
            final String base64Hash = new String(Base64.getEncoder().encode(mac.doFinal(payload.getBytes())));
            return base64Hash;
        }

        public static boolean hashIsValid(String secret, String payload, String verify) throws InvalidKeyException, NoSuchAlgorithmException
        {
            return verify.equals(computeHash(secret, payload));
        }
    }
}
