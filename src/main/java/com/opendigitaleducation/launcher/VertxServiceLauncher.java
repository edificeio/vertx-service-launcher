package com.opendigitaleducation.launcher;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

public class VertxServiceLauncher extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.deployVerticle("folderService:org.entcore~infra~3.0-SNAPSHOT", new DeploymentOptions()
            .setConfig(config())
        );
        vertx.deployVerticle("folderService:org.entcore~app-registry~3.0-SNAPSHOT", new DeploymentOptions()
                .setConfig(config().getJsonObject("app-registry").getJsonObject("config"))
        );
    }

}
