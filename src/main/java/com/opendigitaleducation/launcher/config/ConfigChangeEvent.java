package com.opendigitaleducation.launcher.config;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public abstract class ConfigChangeEvent {
    private List<Handler<Boolean>> endHandlers = new ArrayList<>();
    private List<Handler<Void>> emptyHandlers = new ArrayList<>();

    public abstract JsonObject getDump();

    public abstract List<JsonObject> getServicesToRestart();

    public abstract List<JsonObject> getServicesToUndeploy();

    public abstract List<JsonObject> getServicesToDeploy();

    public boolean hasPendingTasks() {
        return getServicesToRestart().size() > 0 || getServicesToUndeploy().size() > 0
                || getServicesToDeploy().size() > 0;
    }

    public ConfigChangeEvent onEmpty(Handler<Void> handler) {
        emptyHandlers.add(handler);
        return this;
    }

    public ConfigChangeEvent onEnd(Handler<Boolean> handler) {
        endHandlers.add(handler);
        return this;
    }

    public ConfigChangeEvent empty() {
        for (final Handler<Void> h : emptyHandlers) {
            h.handle(null);
        }
        return this;
    }

    public ConfigChangeEvent end(Boolean success) {
        for (final Handler<Boolean> h : endHandlers) {
            h.handle(success);
        }
        return this;
    }
}