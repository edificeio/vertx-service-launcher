package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.FileUtils;
import com.opendigitaleducation.launcher.utils.ZipUtils;

import io.vertx.core.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.service.ServiceVerticleFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
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
        String[] artifact = identifier.split("~");
        if (artifact.length != 3) {
           resolution.fail("Invalid artifact : " + identifier);
           return;
        }
        final String servicePath = servicesPath + File.separator +
            identifier + File.separator;
        vertx.fileSystem().exists(servicePath, ar -> {
            if (ar.succeeded() && ar.result()) {
                deploy(identifier, deploymentOptions, classLoader, resolution, artifact, servicePath);
            } else {
                serviceResolver.resolve(identifier, jar -> {
                    if (jar.succeeded()) {
                        ZipUtils.unzip(vertx, jar.result(), servicePath, res -> {
                            if (res.succeeded()) {
                                deploy(identifier, deploymentOptions, classLoader, resolution, artifact, servicePath);
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
    }

    private void deploy(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Promise<Callable<Verticle>> resolution, String[] artifact, String servicePath) {
        vertx.fileSystem().readFile(servicePath + "META-INF" + File.separator + "MANIFEST.MF", ar -> {
			if (ar.succeeded()) {
                Scanner s = new Scanner(ar.result().toString());
                String id = null;
                while (s.hasNextLine()) {
                    final String line = s.nextLine();
                    if (line.contains("Main-Verticle:")) {
                        String [] item = line.split(":");
                        if (item.length == 3) {
                            id = item[2];
                            deploymentOptions.setExtraClasspath(Collections.singletonList(servicePath));
                            deploymentOptions.setIsolationGroup("__vertx_folder_" + artifact[1]);
                            try {
                                URLClassLoader urlClassLoader = new URLClassLoader(
                                    new URL[]{new URL("file://" + servicePath )}, classLoader);
                                FolderServiceFactory.super.createVerticle(id, deploymentOptions, urlClassLoader, resolution);
                                // resolution.future().onSuccess(cv -> cv.call().getVertx().getOrCreateContext().config());
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
