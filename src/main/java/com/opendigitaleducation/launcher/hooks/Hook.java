package com.opendigitaleducation.launcher.hooks;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface Hook {
    enum HookEvents{
        Deployed, Undeployed, Restarted
    }

    default void emit(final JsonObject service, final HookEvents event){
        final Set<HookEvents> events = new HashSet<>();
        events.add(event);
        emit(service, events);
    }

    void emit(JsonObject service, Set<HookEvents> events);

    static Optional<Hook> create(final Vertx vertx, final JsonObject config){
        if(config.containsKey("slackHook")){
            return Optional.of(new HookSlack(vertx, config));
        }else{
            return Optional.empty();
        }
    }
}
