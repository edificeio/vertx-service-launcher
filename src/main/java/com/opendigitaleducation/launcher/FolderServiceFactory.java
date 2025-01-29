package com.opendigitaleducation.launcher;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.FileUtils;
import com.opendigitaleducation.launcher.utils.ZipUtils;

import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.service.ServiceVerticleFactory;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.NoSuchElementException;
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
    public void createVerticle(String id, DeploymentOptions deploymentOptions, ClassLoader classLoader, Promise<Callable<Verticle>> resolution) {
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
                            // TODO java 21 check implications
                            //deploymentOptions.setExtraClasspath(Collections.singletonList(servicePath));
                            //deploymentOptions.setIsolationGroup("__vertx_folder_" + artifact[1]);
                            try {
                                File dir = new File(servicePath);
                                if (!dir.exists() || !dir.isDirectory()) {
                                    throw new IllegalArgumentException("Invalid directory path: " + servicesPath);
                                }

                                URL dirUrl = dir.toURI().toURL();
                                URLClassLoader urlClassLoader = new URLClassLoader(
                                    new URL[]{dirUrl}, classLoader);
                                deploymentOptions.setClassLoader(urlClassLoader);
                                doCreateVerticle(id, deploymentOptions, urlClassLoader, resolution);
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

    protected void doCreateVerticle(String verticleName, DeploymentOptions deploymentOptions, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        String identifier = VerticleFactory.removePrefix(verticleName);
        String descriptorFile = identifier + ".json";

        try (InputStream is = classLoader.getResourceAsStream(descriptorFile)) {
            if (is == null) {
                throw new IllegalArgumentException("Cannot find service descriptor file " + descriptorFile + " on classpath");
            }

            JsonObject descriptor;
            try {
                Scanner scanner = (new Scanner(is, "UTF-8")).useDelimiter("\\A");
                Throwable var12 = null;

                try {
                    String conf = scanner.next();
                    descriptor = new JsonObject(conf);
                } catch (Throwable var41) {
                    var12 = var41;
                    throw var41;
                } finally {
                    if (scanner != null) {
                        if (var12 != null) {
                            try {
                                scanner.close();
                            } catch (Throwable var40) {
                                var12.addSuppressed(var40);
                            }
                        } else {
                            scanner.close();
                        }
                    }

                }
            } catch (NoSuchElementException var43) {
                throw new IllegalArgumentException(descriptorFile + " is empty");
            } catch (DecodeException var44) {
                throw new IllegalArgumentException(descriptorFile + " contains invalid json");
            }

            String main = descriptor.getString("main");
            if (main == null) {
                throw new IllegalArgumentException(descriptorFile + " does not contain a main field");
            }

            JsonObject serviceOptions = descriptor.getJsonObject("options", new JsonObject());
            JsonObject mergedDeploymentOptions = deploymentOptions.toJson();
            mergedDeploymentOptions.mergeIn(serviceOptions);
            promise.complete((Callable)() -> new AbstractVerticle() {
                public void start(Promise<Void> startPromise) {
                    DeploymentOptions dopt = new DeploymentOptions(mergedDeploymentOptions);
                    if (dopt.getConfig() == null) {
                        dopt.setConfig(new JsonObject());
                    }

                    dopt.getConfig().mergeIn(this.context.config());
                    dopt.setClassLoader(classLoader);
                    this.vertx.deployVerticle(main, dopt, (ar) -> {
                        if (ar.succeeded()) {
                            startPromise.complete();
                        } else {
                            startPromise.fail(ar.cause());
                        }

                    });
                }
            });
        } catch (Exception e) {
            promise.fail(e);
        }

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
