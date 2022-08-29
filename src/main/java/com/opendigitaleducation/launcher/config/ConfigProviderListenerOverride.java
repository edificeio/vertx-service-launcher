package com.opendigitaleducation.launcher.config;

import com.opendigitaleducation.launcher.deployer.CustomDeployerFront;
import com.opendigitaleducation.launcher.deployer.CustomDeployerOverride;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This listener listen for front deployment (assets, js, themes)
 * It deploy override when front module are deployed
 */
public class ConfigProviderListenerOverride extends ConfigProviderListenerAbstract {
    static Logger log = LoggerFactory.getLogger(ConfigProviderListenerAssets.class);

    @Override
    public void beforeConfigChange(ConfigChangeEvent event) {
        boolean shouldDeployOverride = false;
        for(final JsonObject service : event.getServicesToDeploy()){
            if(CustomDeployerFront.canDeployService(service)){
                shouldDeployOverride = true;
            }
        }
        for(final JsonObject service : event.getServicesToRestart()){
            if(CustomDeployerFront.canDeployService(service)){
                shouldDeployOverride = true;
            }
        }
        for(final JsonObject service : event.getServicesToUndeploy()){
            if(CustomDeployerFront.canDeployService(service)){
                shouldDeployOverride = true;
            }
        }
        //add assets to redeploy
        if(shouldDeployOverride){
            for(final String name : deployed.keySet()){
                final JsonObject service = deployed.get(name);
                if(CustomDeployerOverride.isOverride(service)){
                    event.getServicesToRestart().add(service);
                    log.info("Redeploy override module because of front change: "+name);
                }
            }
        }
    }
}
