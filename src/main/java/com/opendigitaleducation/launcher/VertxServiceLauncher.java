package com.opendigitaleducation.launcher;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;

import static com.opendigitaleducation.launcher.FolderServiceFactory.FACTORY_PREFIX;

public class VertxServiceLauncher extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(VertxServiceLauncher.class);

    @Override
    public void start() throws Exception {
        JsonArray services = config().getJsonArray("services");
        if (services == null || services.size() == 0) {
            log.error("Missing services to deploy.");
            return;
        }

        deployServices(services);
    }

    private void deployServices(JsonArray services) {
        deployServices(services, 0);
    }

    private void deployServices(JsonArray services, final int index) {
        if (index >= services.size()) return;
        final JsonObject service = services.getJsonObject(index);
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
			deployServices(services, index + 1);
		}
        JsonObject config = service.getJsonObject("config");
        if (config == null) {
            config = new JsonObject();
        }
        config.put("cwd", System.getProperty("vertx.services.path") + File.separator + name);
        if (!config.containsKey("assets-path") && config().getString("assets-path") != null) {
            config.put("assets-path", config().getString("assets-path"));
        }
        final DeploymentOptions deploymentOptions = new DeploymentOptions()
            .setConfig(config)
            .setWorker(service.getBoolean("worker", false))
            .setMultiThreaded(service.getBoolean("multi-threaded", false));
        if (service.getBoolean("waitDeploy", false)) {
            vertx.deployVerticle(FACTORY_PREFIX + ":" + name, deploymentOptions, ar -> {
                if (ar.succeeded()) {
                    deployServices(services, index + 1);
                } else {
                    log.error("Error deploying required service  : " + name, ar.cause());
                }
            });
        } else {
            vertx.deployVerticle(FACTORY_PREFIX + ":" + name, deploymentOptions, ar -> {
                if (ar.failed()) {
                    log.error("Error deploying required service  : " + name, ar.cause());
                }
            });
            deployServices(services, index + 1);
        }
    }

}
