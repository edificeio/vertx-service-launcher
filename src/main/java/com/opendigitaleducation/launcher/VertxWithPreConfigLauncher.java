package com.opendigitaleducation.launcher;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.impl.VertxMetricsFactoryImpl;

import java.util.logging.Logger;

/**
 * Vertx launcher that allows for the customization of the instance of vertx
 * itself before starting it.
 * It allows many things like registering micrometer backend(s) beforehand
 * (and that cannot be done afterwards).
 */
public class VertxWithPreConfigLauncher extends Launcher {

    private Logger logger = Logger.getLogger(VertxWithPreConfigLauncher.class.getCanonicalName());
    /**
     * Metrics options coming from the configuration (-conf argument).
     */
    private JsonObject metricsOptions = null;
    private int workerPoolSize = VertxOptions.DEFAULT_WORKER_POOL_SIZE;

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
            final MicrometerMetricsOptions metricsOptionsObj = new MicrometerMetricsOptions(metricsOptions);
            if(metricsOptionsObj.getFactory() == null) {
                metricsOptionsObj.setFactory(new VertxMetricsFactoryImpl());
            }
            options.setMetricsOptions(metricsOptionsObj);
        }
        logger.info("maxWorkerPoolSize is " + options.getWorkerPoolSize());
        options.setWorkerPoolSize(workerPoolSize);
        super.beforeStartingVertx(options);
    }

    @Override
    public void afterConfigParsed(JsonObject config) {
        this.metricsOptions = config.getJsonObject("metricsOptions");
        this.workerPoolSize = config.getInteger("workerPoolSize", VertxOptions.DEFAULT_WORKER_POOL_SIZE);
    }
}
