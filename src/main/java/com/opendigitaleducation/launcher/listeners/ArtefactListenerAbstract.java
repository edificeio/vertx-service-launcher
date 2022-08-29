package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.*;

public abstract class ArtefactListenerAbstract<T extends ConfigChangeEvent> implements ArtefactListener{
    protected static final Logger log = LoggerFactory.getLogger(ArtefactListenerAbstract.class);
    protected T lastEvent;
    protected final ConfigProvider configProvider;
    protected final Map<String, JsonObject> names = new HashMap<>();
    protected final Set<String> recentlyTriggered = new HashSet<>();
    protected final List<Handler<ConfigChangeEvent>> handlers = new ArrayList<>();

    public ArtefactListenerAbstract(final ConfigProvider aConfigProvider){
        this.configProvider = aConfigProvider;
        aConfigProvider.onConfigChange(config -> {
            for(final JsonObject service : config.getServicesToDeploy()){
                addService(service);
            }
            for(final JsonObject service : config.getServicesToRestart()){
                addService(service);
            }
            for(final JsonObject service : config.getServicesToUndeploy()){
                addService(service);
            }
        });
    }

    protected void addService(JsonObject service){
        final String name = service.getString("name", "");
        if(!name.isEmpty()){
            final String [] cols = name.split("~");
            if(cols.length == 3){
                cols[0] = cols[0].replaceAll("\\.", "/");
                cols[1] = cols[1].replaceAll("\\.", "/");
                final String newName = String.join("/", cols);
                names.put(newName, service);
            }
        }
    }

    protected void pushServiceAddedRecently(final Vertx vertx, final String name, final long seconds){
        recentlyTriggered.add(name);
        vertx.setTimer(seconds * 1000, r -> {
            recentlyTriggered.remove(name);
        });
    }

    protected void pushEvent(ConfigChangeEvent event) {
        for (Handler<ConfigChangeEvent> h : handlers) {
            h.handle(event);
        }
        lastEvent = (T) event;
    }

    @Override
    public ArtefactListener onArtefactChange(Handler<ConfigChangeEvent> handler) {
        handlers.add(handler);
        if (lastEvent != null) {
            handler.handle(lastEvent);
        }
        return this;
    }

}
