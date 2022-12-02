package com.opendigitaleducation.launcher;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;

/**
 * Vertx launcher that allows for the customization of the instance of vertx
 * itself before starting it.
 * It allows many things like registering micrometer backend(s) beforehand
 * (and that cannot be done afterwards).
 */
public class VertxWithPreConfigLauncher extends Launcher {

    /**
     * Metrics options coming from the configuration (-conf argument).
     */
    private JsonObject metricsOptions = null;

    /**
     * Main entry point.
     *
     * @param args the user command line arguments.
     */
    public static void main(String[] args) {
        new VertxWithPreConfigLauncher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        if(metricsOptions != null) {
            options.setMetricsOptions(new MicrometerMetricsOptions(metricsOptions));
        }
        super.beforeStartingVertx(options);
    }

    @Override
    public void afterConfigParsed(JsonObject config) {
        this.metricsOptions = config.getJsonObject("metricsOptions");
    }
}
