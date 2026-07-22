package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.FileUtils;
import com.opendigitaleducation.launcher.utils.ServiceUtils;
import com.opendigitaleducation.launcher.utils.ZipUtils;
import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.service.ServiceVerticleFactory;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class FolderServiceFactory extends ServiceVerticleFactory {

    private static final Logger logger = LoggerFactory.getLogger(FolderServiceFactory.class);
    protected static final String SERVICES_PATH = "vertx.services.path";
    public static final String FACTORY_PREFIX = "folderService";

    private Vertx vertx;
    private String servicesPath;
    private ServiceResolverFactory serviceResolver;

    @Override
    public void init(Vertx vertx) {
        this.vertx = vertx;
        this.servicesPath = FileUtils.absolutePath(System.getProperty(SERVICES_PATH));
        this.serviceResolver = new ServiceResolverFactory();
        this.serviceResolver.init(vertx, servicesPath);
    }

    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        final DeploymentOptions deploymentOptions = new DeploymentOptions();
        final JsonArray services = vertx.getOrCreateContext().config().getJsonArray("services");
        services.stream()
            .filter(s -> verticleName.endsWith(((JsonObject)s).getString("name")))
            .map(s -> (JsonObject)s)
            .forEach(s -> {
                if(s.containsKey("worker")) {
                    deploymentOptions.setWorker(s.getBoolean("worker", false));
                }
                if(s.containsKey("threadingModel")) {
                    deploymentOptions.setThreadingModel(ThreadingModel.valueOf(s.getString("threadingModel", ThreadingModel.EVENT_LOOP.name())));
                }
                if(s.containsKey("workerPoolSize")) {
                    deploymentOptions.setWorkerPoolSize(s.getInteger("workerPoolSize"));
                }
                if(s.containsKey("workerPoolName")) {
                    deploymentOptions.setWorkerPoolName(s.getString("workerPoolName"));
                }
            });

        createVerticle(verticleName, deploymentOptions, classLoader, promise);
    }

    @Override
    protected void createVerticle(String id, DeploymentOptions deploymentOptions, ClassLoader classLoader, Promise<Callable<Verticle>> resolution) {
        if (id == null || !id.startsWith(prefix())) {
            resolution.fail("Invalid identifier : " + id);
            return;
        }
        final String identifier = id.substring(prefix().length() + 1);
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx).onSuccess(sp -> {
            String servicePath = sp + File.separator;
            vertx.fileSystem().exists(servicePath, ar -> {
                if (ar.succeeded() && ar.result()) {
                    deploy(identifier, deploymentOptions, classLoader, resolution, servicePath);
                } else {
                    serviceResolver.resolve(identifier, jar -> {
                        if (jar.succeeded()) {
                            ZipUtils.unzip(vertx, jar.result(), servicePath, res -> {
                                if (res.succeeded()) {
                                    deploy(identifier, deploymentOptions, classLoader, resolution, servicePath);
                                } else {
                                    resolution.fail(res.cause());
                                }
                            });
                        } else {
                            logger.error("An error occurred while loading the jar of " + identifier, jar.cause());
                            resolution.fail("Service not found (JAR): " + identifier);
                        }
                    });
                }
            });
        }).onFailure(th -> resolution.fail(th.getMessage()));
    }

    private void deploy(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Promise<Callable<Verticle>> resolution, String servicePath) {
        vertx.fileSystem().readFile(servicePath + "META-INF" + File.separator + "MANIFEST.MF", ar -> {
			if (ar.succeeded()) {
                Scanner s = new Scanner(ar.result().toString());
                String id = null;
                while (s.hasNextLine()) {
                    final String line = s.nextLine();
                    if (line.contains("Main-Verticle:")) {
                        String [] item = line.split(":");
                        if (item.length == 3) {
                            id = item[2].trim();
                            try {
                                // Per-module isolation is provided by this URLClassLoader (parent-first
                                // delegation to the minimal launcher classloader, so each module loads its
                                // own copy of the module classes). setIsolationGroup/setExtraClasspath are
                                // deliberately not used: they rely on the system classloader being a
                                // URLClassLoader, which is no longer the case on Java 9+.
                                URLClassLoader urlClassLoader = new URLClassLoader(
                                    new URL[]{new URL("file://" + servicePath )}, classLoader);
                                deployFromDescriptor(id, deploymentOptions, urlClassLoader, resolution);
                            } catch (MalformedURLException e) {
                                logger.error("Error while trying to deploy " + identifier, e);
                                resolution.fail(e);
                            }
                        } else {
                            resolution.fail("Invalid service identifier : " + line);
                        }
                        break;
                    }
                }
                s.close();
                if (id == null && !resolution.future().isComplete()) {
                    resolution.fail("Service not found (MANIFEST): " + identifier);
                }
            } else {
                resolution.fail(ar.cause());
            }
		});
    }

    /**
     * Reads the {@code <serviceId>.json} service descriptor from the module classloader and completes the
     * resolution with a verticle that deploys the module's {@code main} verticle with {@code classLoader}
     * set explicitly on the deployment options. Passing the classloader through the deployment options makes
     * Vert.x bypass its (Java 8 only) isolation-group machinery entirely, so the module loads from its own
     * URLClassLoader on any JDK. This mirrors {@link ServiceVerticleFactory} except that the classloader is
     * preserved (the parent implementation drops it when rebuilding the options from JSON).
     */
    private void deployFromDescriptor(String serviceId, DeploymentOptions deploymentOptions, URLClassLoader classLoader, Promise<Callable<Verticle>> resolution) {
        final String descriptorFile = serviceId + ".json";
        final JsonObject descriptor;
        try (InputStream is = classLoader.getResourceAsStream(descriptorFile)) {
            if (is == null) {
                resolution.fail("Cannot find service descriptor file " + descriptorFile + " on classpath");
                return;
            }
            try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                descriptor = new JsonObject(scanner.next());
            }
        } catch (Exception e) {
            resolution.fail(e);
            return;
        }
        final String main = descriptor.getString("main");
        if (main == null) {
            resolution.fail(descriptorFile + " does not contain a main field");
            return;
        }
        final JsonObject mergedOptions = deploymentOptions.toJson()
            .mergeIn(descriptor.getJsonObject("options", new JsonObject()));
        resolution.complete(() -> new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
                final DeploymentOptions moduleOptions = new DeploymentOptions(mergedOptions).setClassLoader(classLoader);
                if (moduleOptions.getConfig() == null) {
                    moduleOptions.setConfig(new JsonObject());
                }
                moduleOptions.getConfig().mergeIn(context.config());
                vertx.deployVerticle(main, moduleOptions, res -> {
                    if (res.succeeded()) {
                        startPromise.complete();
                    } else {
                        startPromise.fail(res.cause());
                    }
                });
            }
        });
    }

    @Override
    public void close() {
        if (serviceResolver != null) {
            serviceResolver.close();
        }
    }

    @Override
    public String prefix() {
        return FACTORY_PREFIX;
    }

}
