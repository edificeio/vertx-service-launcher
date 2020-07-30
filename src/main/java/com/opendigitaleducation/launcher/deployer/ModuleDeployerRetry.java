package com.opendigitaleducation.launcher.deployer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ModuleDeployerRetry implements ModuleDeployer {
    private final int retryCount;
    private final ModuleDeployer original;
    private final Vertx vertx;
    private final int retryDelay;
    private static Logger log = LoggerFactory.getLogger(ModuleDeployerRetry.class);

    public ModuleDeployerRetry(JsonObject config, ModuleDeployer deployer, Vertx aVertx) {
        retryCount = config.getInteger("maxRetry", 3);
        retryDelay = config.getInteger("retryDelay", 5000);
        this.original = deployer;
        this.vertx = aVertx;
    }

    private Future<Void> timer() {
        final Future<Void> future = Future.future();
        vertx.setTimer(retryDelay, resTime -> {
            future.complete();
        });
        return future;
    }

    @Override
    public Future<Void> deploy(JsonObject service) {
        Future<Void> future = original.deploy(service);
        for (int i = 2; i <= retryCount; i++) {
            final String count = i + "";
            future = future.recover(res -> {
                return timer().compose(r -> {
                    log.error(String.format("Failed to deploy service %s retrying (%s/%s) ",
                            ModuleDeployer.getServiceIdQuietly(service), count, retryCount + ""));
                    return original.deploy(service);
                });
            });
        }
        return future;
    }

    @Override
    public Future<Void> undeploy(JsonObject service) {
        Future<Void> future = original.undeploy(service);
        for (int i = 2; i <= retryCount; i++) {
            final String count = i + "";
            future = future.recover(res -> {
                return timer().compose(r -> {
                    log.error(String.format("Failed to undeploy service %s retrying (%s/%s) ",
                            ModuleDeployer.getServiceIdQuietly(service), count, retryCount + ""));
                    return original.undeploy(service);
                });
            });
        }
        return future;
    }

    @Override
    public Future<Void> restart(JsonObject service) {
        Future<Void> future = original.restart(service);
        for (int i = 2; i <= retryCount; i++) {
            final String count = i + "";
            future = future.recover(res -> {
                return timer().compose(r -> {
                    log.error(String.format("Failed to restart service %s retrying (%s/%s) ",
                            ModuleDeployer.getServiceIdQuietly(service), count + "", retryCount + ""));
                    return original.restart(service);
                });
            });
        }
        return future;
    }

    @Override
    public Future<Void> clean(JsonObject service) {
        // dont need to retry
        return original.clean(service);
    }

}