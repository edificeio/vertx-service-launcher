package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.config.ConfigProviderListenerAssets;
import com.opendigitaleducation.launcher.deployer.ModuleDeployer;
import com.opendigitaleducation.launcher.interceptor.TraceIdInboundInterceptor;
import com.opendigitaleducation.launcher.interceptor.TraceIdOutboundInterceptor;
import com.opendigitaleducation.launcher.listeners.ArtefactListener;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

public class VertxServiceLauncher extends AbstractVerticle {

    private static final String SERVICE_LAUNCHER = "service-launcher.deployment";
    private static final Logger log = LoggerFactory.getLogger(VertxServiceLauncher.class);
    private static final int ERROR_RESTARTING_MODULE_CODE = 2;
    private static final int ERROR_UNKNOWN_ACTION_CODE = 1;
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
            }).onComplete(res -> {
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
        deployer = ModuleDeployer.create(vertx, config());
        deployer.init()
            .onSuccess(r -> init())
            .onFailure(ex -> log.error("Error initializing deployer", ex));
    }

    public void init() {
        log.info("init verticle launcher");
        final Boolean clean = config().getBoolean("clean", true);
        configProvider = ConfigProvider.create(config()).start(vertx, config());
        if(config().getBoolean("redeploy-assets-onclean", true)) {
            configProvider.addListener(new ConfigProviderListenerAssets());
        }
        if(config().getBoolean("consulCdnEnabled", false)) {
            throw new RuntimeException("consul.not.implemented");
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
        //interceptor for trace Id
        if(config().containsKey("sharedConf") && config().getJsonObject("sharedConf").getBoolean("log-bus-access", false)) {
            vertx.eventBus().addInboundInterceptor(new TraceIdInboundInterceptor<>());
            vertx.eventBus().addOutboundInterceptor(new TraceIdOutboundInterceptor<>());
        }
        vertx.eventBus().localConsumer(SERVICE_LAUNCHER, deploymentActions());
    }

    private Handler<Message<JsonObject>> deploymentActions() {
        return message -> {
            final String action = message.body().getString("action");
            switch (action) {
                case "restart-module":
                    final String moduleName = message.body().getString("module-name", "");
                    deployer.restart(configProvider.getServiceByName(moduleName))
                            .onSuccess( s -> message.reply(new JsonObject().put("status", "ok")))
                            .onFailure( e -> {
                                log.error("Error restarting module " + moduleName, e);
                                message.fail(ERROR_RESTARTING_MODULE_CODE, "Error restarting module " + moduleName);
                            });
                    break;
                default:
                    message.fail(ERROR_UNKNOWN_ACTION_CODE, "Unknown action");
            }
        };
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
