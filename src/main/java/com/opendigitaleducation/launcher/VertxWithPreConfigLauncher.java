package com.opendigitaleducation.launcher;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.impl.VertxMetricsFactoryImpl;

import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Vertx launcher that allows for the customization of the instance of vertx
 * itself before starting it.
 * It allows many things like registering micrometer backend(s) beforehand
 * (and that cannot be done afterwards).
 */
public class VertxWithPreConfigLauncher extends Launcher {

    private static final Logger logger = Logger.getLogger(VertxWithPreConfigLauncher.class.getCanonicalName());
    /**
     * Metrics options coming from the configuration (-conf argument).
     */
    private JsonObject metricsOptions = null;
    private JsonObject eventBusOptions = null;
    private int workerPoolSize = VertxOptions.DEFAULT_WORKER_POOL_SIZE;

    /**
     * Main entry point.
     *
     * @param args the user command line arguments.
     */
    public static void main(String[] args) {
        logger.info("==================== Starting Vertx ====================");
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

        // Configure EventBus TLS if options are provided
        if(eventBusOptions != null && eventBusOptions.getBoolean("ssl", false)) {
            logger.info("==================== EventBus TLS Configuration ====================");
            logger.info("Configuring EventBus with TLS encryption");
            final EventBusOptions ebOptions = new EventBusOptions(eventBusOptions);

            // Auto-detect hostname if needed
            String ebHost = ebOptions.getHost();
            if("auto".equals(ebHost)) {
                try {
                    // Get the actual hostname this container can be reached by
                    String hostname = InetAddress.getLocalHost().getHostAddress();
                    logger.info("  - Auto-detected hostname: " + hostname);
                    ebHost = hostname;
                } catch (UnknownHostException e) {
                    logger.warning("Could not resolve hostname, setting 0.0.0.0: " + e.getMessage());
                    ebHost = "0.0.0.0";
                }
                ebOptions.setHost(ebHost);
            } else {
                logger.fine("EventBus host is : " + ebHost);
            }

            // Configure PEM key/certificate if provided
            final JsonObject keyCertConfig = eventBusOptions.getJsonObject("pemKeyCertOptions");
            if(keyCertConfig != null) {
                final String certPath = keyCertConfig.getString("certPath");
                final String keyPath = keyCertConfig.getString("keyPath");
                logger.info("  - Certificate path: " + certPath);
                logger.info("  - Key path: " + keyPath);
                final PemKeyCertOptions pemKeyCert = new PemKeyCertOptions()
                    .setCertPath(certPath)
                    .setKeyPath(keyPath);
                ebOptions.setKeyCertOptions(pemKeyCert);
            }

            // Configure PEM trust options if provided
            final JsonObject trustConfig = eventBusOptions.getJsonObject("pemTrustOptions");
            if(trustConfig != null) {
                final String trustPath = trustConfig.getString("certPath");
                logger.info("  - Trust certificate path: " + trustPath);
                final PemTrustOptions pemTrust = new PemTrustOptions()
                    .addCertPath(trustPath);
                ebOptions.setTrustOptions(pemTrust);
            }

            logger.info("  - EventBus host: " + ebOptions.getHost());
            logger.info("  - EventBus port: " + ebOptions.getPort());
            logger.info("  - Client auth: " + ebOptions.getClientAuth());
            logger.info("  - SSL enabled: " + ebOptions.isSsl());

            options.setEventBusOptions(ebOptions);
            logger.info("EventBus TLS configured successfully");
            logger.info("====================================================================");
        } else {
            logger.warning("⚠️  EventBus TLS is NOT enabled - communication will be UNENCRYPTED!");
        }
        logger.fine("maxWorkerPoolSize is " + options.getWorkerPoolSize());
        options.setWorkerPoolSize(workerPoolSize);
        logger.fine("maxWorkerPoolSize is now " + options.getWorkerPoolSize());
        super.beforeStartingVertx(options);
    }

    @Override
    public void afterConfigParsed(JsonObject config) {
        this.metricsOptions = config.getJsonObject("metricsOptions");
        this.eventBusOptions = config.getJsonObject("eventBusOptions");
        this.workerPoolSize = config.getInteger("workerPoolSize", VertxOptions.DEFAULT_WORKER_POOL_SIZE);
    }
}
