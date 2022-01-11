package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.config.ConfigProviderListenerAssets;
import com.opendigitaleducation.launcher.config.ConfigProviderListenerConsulCDN;
import com.opendigitaleducation.launcher.deployer.ModuleDeployer;
import com.opendigitaleducation.launcher.listeners.ArtefactListener;
import com.opendigitaleducation.launcher.utils.FileUtils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

public class VertxServiceLauncher extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(VertxServiceLauncher.class);
    private static int countDeployments = 0;
    private ConfigProvider configProvider;
    private Optional<ArtefactListener> artefactListener = Optional.empty();
    private ModuleDeployer deployer;
    private void onChangeEvent(final ConfigChangeEvent resConfig, final boolean clean){
        if (resConfig.hasPendingTasks()) {
            countDeployments++;
            final int count = countDeployments;
            log.info(String.format("Starting deployment %s: (deployed=%s, undeployed=%s, restart=%s)", countDeployments, resConfig.getServicesToDeploy().size(), resConfig.getServicesToUndeploy().size(), resConfig.getServicesToRestart().size()));
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
                log.info(String.format("End deployment %s: (deployed=%s, undeployed=%s, restart=%s)", count, resConfig.getServicesToDeploy().size(), resConfig.getServicesToUndeploy().size(), resConfig.getServicesToRestart().size()));
                if (res.succeeded()) {
                    resConfig.end(true);
                } else {
                    resConfig.end(false);
                    log.error("Config has not been applied because of : " + res.cause().getMessage());
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
        if(config().getBoolean("redeploy-assets-onclean", true)) {
            configProvider.addListener(new ConfigProviderListenerAssets());
        }
        if(config().getBoolean("consulCdnEnabled", false)) {
            final String servicesPath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
            configProvider.addListener(new ConfigProviderListenerConsulCDN(config(), vertx, servicesPath));
        }
        configProvider.onConfigChange(resConfig -> {
            onChangeEvent(resConfig, clean || resConfig.isForceClean());
        });
        artefactListener = ArtefactListener.create(configProvider, config());
        if(artefactListener.isPresent()){
            artefactListener.get().start(vertx, config());
            artefactListener.get().onArtefactChange( res -> {
                configProvider.triggerChange(res.setForceClean(true));
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
