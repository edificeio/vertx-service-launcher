package com.opendigitaleducation.launcher.hooks;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HookList implements Hook{
    static final Logger log = LoggerFactory.getLogger(HookList.class);
    private final List<Hook> all = new ArrayList<>();

    public HookList add(final Hook hook){
        this.all.add(hook);
        return this;
    }

    @Override
    public void emit(final JsonObject service, final Set<HookEvents> events) {
        for(final Hook h : all){
            try{
                h.emit(service, events);
            }catch(Exception e){
                log.error("Hook list error: ", e);
            }
        }
    }
}
