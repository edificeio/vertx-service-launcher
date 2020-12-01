package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.deployer.ModuleDeployer;
import com.opendigitaleducation.launcher.listeners.ArtefactListener;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

public class VertxServiceLauncher extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(VertxServiceLauncher.class);

    private ConfigProvider configProvider;
    private Optional<ArtefactListener> artefactListener = Optional.empty();
    private ModuleDeployer deployer;
    private void onChangeEvent(final ConfigChangeEvent resConfig, final boolean clean){
        if (resConfig.hasPendingTasks()) {
            deployer.undeployAll(resConfig.getServicesToUndeploy()).compose(res -> {
                if (clean) {
                    return deployer.cleanAll(resConfig.getServicesToUndeploy());
                } else {
                    return Future.succeededFuture();
                }
            }).compose(undeploy -> {
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
    }
    @Override
    public void start() throws Exception {
        final Boolean clean = config().getBoolean("clean", true);
        deployer = ModuleDeployer.create(vertx, config());
        configProvider = ConfigProvider.create(config()).start(vertx, config());
        configProvider.onConfigChange(resConfig -> {
            onChangeEvent(resConfig, clean);
        });
        artefactListener = ArtefactListener.create(configProvider, config());
        if(artefactListener.isPresent()){
            artefactListener.get().start(vertx, config());
            artefactListener.get().onArtefactChange( res -> {
               onChangeEvent(res, true);
            });
        }
    }

    @Override
    public void stop() throws Exception {
        configProvider.stop(vertx);
        if(artefactListener.isPresent()){
            artefactListener.get().stop();
        }
        super.stop();
    }

}
