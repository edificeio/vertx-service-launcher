package com.opendigitaleducation.launcher.config;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public abstract class ConfigChangeEvent {
    private boolean forceClean = false;
    private List<Handler<Boolean>> endHandlers = new ArrayList<>();
    private List<Handler<Void>> emptyHandlers = new ArrayList<>();

    public boolean isForceClean() {
        return forceClean;
    }

    public abstract JsonObject getDump();

    public List<JsonObject> getAll(){
        final List<JsonObject> all = new ArrayList<>();
        all.addAll(getServicesToRestart());
        all.addAll(getServicesToUndeploy());
        all.addAll(getServicesToDeploy());
        return all;
    }

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

    public List<Handler<Boolean>> getEndHandlers() {
        return endHandlers;
    }

    public ConfigChangeEvent setForceClean(boolean forceClean) {
        this.forceClean = forceClean;
        return this;
    }
}
