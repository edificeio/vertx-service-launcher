package com.opendigitaleducation.launcher;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxInfluxDbOptions;

public class MainLauncher {

    public static void main(String[] args) {
        final VertxOptions vertxOptions = new VertxOptions();
        if (System.getProperty("influxdb.uri") != null) {
            vertxOptions.setMetricsOptions(new MicrometerMetricsOptions()
                .setInfluxDbOptions(new VertxInfluxDbOptions()
                    .setEnabled(true)
                    .setUri(System.getProperty("influxdb.uri"))
                    .setDb(System.getProperty("influxdb.name"))
                    .setUserName(System.getProperty("influxdb.username"))
                    .setPassword(System.getProperty("influxdb.password")))
                .setEnabled(true));
        }

        vertxOptions.setFileResolverCachingEnabled(false);

        if (System.getProperty("vertx.options.maxWorkerExecuteTime") != null) {
            vertxOptions.setMaxWorkerExecuteTime(
                Long.parseLong(System.getProperty("vertx.options.maxWorkerExecuteTime")));
        }

        if (System.getProperty("vertx.options.workerPoolSize") != null) {
            vertxOptions.setWorkerPoolSize(
                Integer.parseInt(System.getProperty("vertx.options.workerPoolSize")));
        }

        if ("true".equals(System.getProperty("cluster"))) {
            vertxOptions.setClustered(true);
            vertxOptions.setClusterHost(System.getProperty("cluster-host"));
            Vertx.clusteredVertx(vertxOptions, vertxAsyncResult -> {
                if (vertxAsyncResult.succeeded()) {
                    final Vertx vertx = vertxAsyncResult.result();
                    launchVerticle(vertx);
                } else {
                    System.err.println("Error loading vertx cluster : " + vertxAsyncResult.cause());
                }
            });
        } else {
            final Vertx vertx = Vertx.vertx(vertxOptions);
            launchVerticle(vertx);
        }

    }

    private static void launchVerticle(Vertx vertx) {
        vertx.fileSystem().readFile(System.getProperty("conf"), ar -> {
            if (ar.succeeded()) {
                vertx.deployVerticle(VertxServiceLauncher.class.getName(),
                    new DeploymentOptions().setConfig(new JsonObject(ar.result())));
            }
        });
    }

}
