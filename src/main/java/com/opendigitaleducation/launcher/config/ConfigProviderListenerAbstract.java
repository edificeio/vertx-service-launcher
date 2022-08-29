package com.opendigitaleducation.launcher.config;

import com.opendigitaleducation.launcher.deployer.CustomDeployerFront;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This abstract class keep a map of deployed services
 */
public abstract class ConfigProviderListenerAbstract implements ConfigProviderListener {

    protected Map<String, JsonObject> deployed = new HashMap<>();

    @Override
    public void afterConfigChange(ConfigChangeEvent event, boolean success) {
        if(success) {
            //remove all front service undeployed (must be before if service is undeployed then deployed)
            for (final JsonObject service : event.getServicesToUndeploy()) {
                if (CustomDeployerFront.canDeployService(service)) {
                    final String name = service.getString("name");
                    deployed.remove(name);
                }
            }
            //add all front service deployed to a map
            for (final JsonObject service : event.getServicesToDeploy()) {
                if (CustomDeployerFront.canDeployService(service)) {
                    final String name = service.getString("name");
                    deployed.put(name, service);
                }
            }
            for (final JsonObject service : event.getServicesToRestart()) {
                if (CustomDeployerFront.canDeployService(service)) {
                    final String name = service.getString("name");
                    deployed.put(name, service);
                }
            }
        }
    }
}
