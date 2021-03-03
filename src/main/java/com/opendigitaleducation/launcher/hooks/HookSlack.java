package com.opendigitaleducation.launcher.hooks;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

public class HookSlack implements Hook {
    static final Logger log = LoggerFactory.getLogger(HookSlack.class);
    private final String slackHook;
    private final String nodeName;
    private final String hostName;
    private final Vertx vertx;
    public HookSlack(final Vertx vertx, final JsonObject config){
        String tmpHostName;
        this.vertx = vertx;
        this.slackHook = config.getString("slackHook");
        this.nodeName = config.getString("node", config.getString("consuleNode", "unknown"));
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            tmpHostName = addr.getHostName();
        } catch (UnknownHostException e) {
            log.error("Slack hook failed hostname: ",e);
            tmpHostName = "";
        }
        this.hostName = tmpHostName;
    }

    @Override
    public void emit(JsonObject service, Set<HookEvents> events){
        try{
            if(events.contains(HookEvents.Deployed)) {
                final String name = service.getString("name").replaceAll("~", " ");
                final HttpClient client = vertx.createHttpClient();
                final HttpClientRequest req = client.postAbs(slackHook, e -> {
                    if (e.statusCode() != 200) {
                        log.error("Slack hook bad status: " + e.statusCode() + "/" + e.statusMessage());
                    }
                });
                req.exceptionHandler(e -> {
                    log.error("Slack hook failed: ", e);
                });
                final String text = String.format("%s déployé sur %s %s", name, hostName, nodeName);
                req.end(new JsonObject().put("text", text).toString());
            }
        }catch(Exception e){
            log.error("Slack hook error: ", e);
        }
    }
}
