package com.opendigitaleducation.launcher.hooks;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;

public class HookCustomEvent implements Hook {
    public static final String ON_DEPLOYED = "onDeployed";
    public static final String ON_UNDEPLOED = "onUndeployed";
    public static final String ON_RESTARTED = "onRestarted";

    static final Logger log = LoggerFactory.getLogger(HookCustomEvent.class);
    private final EventBus eventBus;
    protected HookCustomEvent(final Vertx vertx){
        this.eventBus = vertx.eventBus();
    }

    protected void publish(final JsonObject service, final String event){
        final Object value = service.getValue(event);
        if(value instanceof JsonArray){
            for(final Object v : (JsonArray)value){
                log.info("Triggering event: "+ v.toString());
                eventBus.publish(v.toString(), service);
            }
        }else{
            log.info("Triggering event: "+ value.toString());
            eventBus.publish(value.toString(), service);
        }
    }

    @Override
    public void emit(final JsonObject service, final Set<HookEvents> events) {
        try{
            if(service.containsKey(ON_DEPLOYED) && events.contains(HookEvents.Deployed)){
                publish(service, ON_DEPLOYED);
            }
            if(service.containsKey(ON_UNDEPLOED) && events.contains(HookEvents.Undeployed)){
                publish(service, ON_UNDEPLOED);
            }
            if(service.containsKey(ON_RESTARTED) && events.contains(HookEvents.Restarted)){
                publish(service, ON_RESTARTED);
            }
        }catch(Exception e){
            log.error("Slack hook error: ", e);
        }
    }
}
