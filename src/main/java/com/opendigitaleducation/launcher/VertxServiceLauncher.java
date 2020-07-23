package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.deployer.ModuleDeployer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VertxServiceLauncher extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(VertxServiceLauncher.class);

    private ConfigProvider configProvider;
    private ModuleDeployer deployer;

    @Override
    public void start() throws Exception {
        deployer = ModuleDeployer.create(vertx, config());
        configProvider = ConfigProvider.create(config()).start(vertx, config());
        configProvider.onConfigChange(resConfig -> {
            if (resConfig.hasPendingTasks()) {
                deployer.undeployAll(resConfig.getServicesToUndeploy()).compose(undeploy -> {
                    // deploy must be after undeploy (some service are undeploy then deploy if
                    // version changed)
                    return deployer.deployAll(resConfig.getServicesToDeploy());
                }).compose(deploy -> {
                    return deployer.restartAll(resConfig.getServicesToRestart());
                }).setHandler(res -> {
                    if (res.succeeded()) {
                        resConfig.end(true);
                    } else {
                        resConfig.end(false);
                        log.error("Config has not been applyed because of : " + res.cause().getMessage());
                    }
                });
            } else {
                resConfig.empty();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        configProvider.stop(vertx);
        super.stop();
    }

}
