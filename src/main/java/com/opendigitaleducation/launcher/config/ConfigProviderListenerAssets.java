package com.opendigitaleducation.launcher.config;

import com.opendigitaleducation.launcher.deployer.CustomDeployerFront;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ConfigProviderListenerAssets implements ConfigProviderListener {
    static Logger log = LoggerFactory.getLogger(ConfigProviderListenerAssets.class);

    private Map<String, JsonObject> deployed = new HashMap<>();
    @Override
    public void beforeConfigChange(ConfigChangeEvent event) {
        //
        final Map<String, JsonObject> toUndeploy = new HashMap<>();
        final Map<String, JsonObject> toDeploy = new HashMap<>();
        //if assets will be cleaned add all others front service to deploy
        AssetOperation redeployAssets = AssetOperation.Void;
        for(final JsonObject service : event.getServicesToDeploy()){
            if(CustomDeployerFront.isAssetsService(service)){
                redeployAssets = AssetOperation.Deploy;
            } else if(CustomDeployerFront.canDeployService(service)){
                final String name = service.getString("name");
                toDeploy.put(name, service);
            }
        }
        for(final JsonObject service : event.getServicesToRestart()){
            if(CustomDeployerFront.isAssetsService(service)){
                redeployAssets = AssetOperation.Restart;
            } else if(CustomDeployerFront.canDeployService(service)){
                final String name = service.getString("name");
                toDeploy.put(name, service);
            }
        }
        for(final JsonObject service : event.getServicesToUndeploy()){
            if(CustomDeployerFront.isAssetsService(service)){
                redeployAssets = AssetOperation.UnDeploy;
            } else if(CustomDeployerFront.canDeployService(service)){
                final String name = service.getString("name");
                toUndeploy.put(name, service);
            }
        }
        //add assets to redeploy
        if(!redeployAssets.equals(AssetOperation.Void)){
            for(final String name : deployed.keySet()){
                final JsonObject service = deployed.get(name);
                if(!CustomDeployerFront.isAssetsService(service) && !toDeploy.containsKey(name) && !toUndeploy.containsKey(name)){
                    event.getServicesToRestart().add(service);
                    log.info("Redeploy dependant module because of assets clean: "+name);
                }
            }
        }
    }

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

    enum AssetOperation{
        Void, Deploy, Restart, UnDeploy
    }
}
