package com.opendigitaleducation.launcher.deployer;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface ModuleDeployer {

    static ModuleDeployer create(final Vertx vertx, final JsonObject config) {
        final ModuleDeployer def = new ModuleDeployerDefault(vertx, config);
        return new ModuleDeployerRetry(config, def, vertx);
    }

    Future<Void> deploy(JsonObject services);

    Future<Void> undeploy(JsonObject service);

    Future<Void> restart(JsonObject service);

    Future<Void> init();

    default Future<Void> restartAll(List<JsonObject> services) {
        Future<Void> currentFuture = Future.succeededFuture();
        for (final JsonObject service : services) {
            // wait restart to avoid duplicate mods
            currentFuture = currentFuture.compose(res -> {
                return restart(service);
            });
        }
        return currentFuture;
    }

    default Future<Void> undeployAll(List<JsonObject> services) {
        Future<Void> currentFuture = Future.succeededFuture();
        for (final JsonObject service : services) {
            // wait undeploy to avoid duplicate mods
            currentFuture = currentFuture.compose(res -> {
                return undeploy(service);
            });
        }
        return currentFuture;
    }

    default Future<Void> deployAll(List<JsonObject> services) {
        Future<Void> currentFuture = Future.succeededFuture();
        final List<Future> futures = new ArrayList<>();
        for (final JsonObject service : services) {
            if (service.getBoolean("waitDeploy", false)) {
                currentFuture = currentFuture.compose(res -> {
                    return deploy(service);
                });
            } else {
                currentFuture = currentFuture.compose(res -> {
                    futures.add(deploy(service));
                    return Future.succeededFuture();
                });
            }
        }
        futures.add(currentFuture);
        return CompositeFuture.all(futures).map(e -> null);
    }

    Future<Void> clean(JsonObject service);

    default Future<Void> cleanAll(List<JsonObject> services) {
        List<Future> futures = new ArrayList<>();
        for (final JsonObject service : services) {
            futures.add(clean(service));
        }
        return CompositeFuture.all(futures).map(e -> null);
    }

    static String getServiceIdQuietly(JsonObject service) {
        try {
            return getServiceId(service);
        } catch (Exception e) {
            return service.getString("name", "");
        }
    }

    static String getServiceId(JsonObject service) throws Exception {
        final String id = service.getString("name");
        if (id == null) {
            throw new Exception("[getServiceId] Invalid identifier : " + id);
        }
        String[] artifact = id.split("~");
        if (artifact.length != 3) {
            throw new Exception("[getServiceId] Invalid artifact : " + id);
        }
        return id;
    }

    static String getServiceName(JsonObject service) throws Exception {
        final String id = service.getString("name");
        if (id == null) {
            throw new Exception("[getServiceId] Invalid identifier : " + id);
        }
        String[] artifact = id.split("~");
        if (artifact.length != 3) {
            throw new Exception("[getServiceId] Invalid artifact : " + id);
        }
        return artifact[1];
    }
}
