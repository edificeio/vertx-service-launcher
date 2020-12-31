package com.opendigitaleducation.launcher.deployer;

import com.opendigitaleducation.launcher.FolderServiceFactory;
import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.DefaultAsyncResult;
import com.opendigitaleducation.launcher.utils.ZipUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

class FrontDeployer implements CustomDeployer {
    static Logger log = LoggerFactory.getLogger(FrontDeployer.class);
    private static final String ASSETS_TYPES = "assets";
    private final Vertx vertx;
    private final String servicesPath;
    private final String assetPath;
    private final ServiceResolverFactory serviceResolver;

    static final Map<String, String> outputForTypes = new HashMap<>();
    {
        outputForTypes.put("theme", "themes");
        outputForTypes.put("themes", "themes");
        outputForTypes.put("js", "js");
        outputForTypes.put(ASSETS_TYPES, "assets");
    }

    public FrontDeployer(Vertx vertx, String servicesPath, String assetPath, ServiceResolverFactory resolver) {
        this.vertx = vertx;
        this.assetPath = assetPath;
        this.servicesPath = servicesPath;
        this.serviceResolver = resolver;

    }

    @Override
    public boolean canDeploy(JsonObject service) {
        final String type = service.getString("type");
        return outputForTypes.containsKey(type);
    }

    protected String getServicePath(JsonObject service) throws Exception {
        final String id = ModuleDeployer.getServiceId(service);
        return servicesPath + File.separator + id + File.separator;
    }

    protected String getDistPath(JsonObject service) throws Exception {
        final String type = service.getString("type");
        final String defaut = ASSETS_TYPES.equals(type) ? "assets" : "dist";
        final String dist = service.getString("dist-dir", defaut);
        final String servicePath = getServicePath(service);
        final String distPath = servicePath + File.separator + dist;
        return distPath;
    }

    protected String getOutputPath(JsonObject service) throws Exception {
        final String type = service.getString("type");
        if (ASSETS_TYPES.equals(type)) {
            return assetPath + File.separator + ASSETS_TYPES;
        }
        final String outputDir = service.getString("output-dir", outputForTypes.getOrDefault(type, ""));
        final String moduleName = service.getString("name");
        final String[] lNameVersion = moduleName.split("~");
        final String assetDir = service.getString("assets-dir", "assets");
        if (lNameVersion.length < 2) {
            throw new Exception("[FrontDeployer] Invalid name : " + moduleName);
        }
        final String outputPath = assetPath + (assetPath.endsWith(File.separator) ? "" : File.separator) + assetDir
                + File.separator + outputDir + File.separator + lNameVersion[1];
        return outputPath;
    }

    protected void doDeploy(JsonObject service, Handler<AsyncResult<Void>> result) {
        try {
            final String outputPath = getOutputPath(service);
            final String distPath = getDistPath(service);
            // clean and recreate
            vertx.fileSystem().deleteRecursive(outputPath, true, resDelete -> {
                vertx.fileSystem().mkdirs(outputPath, resMkdir -> {
                    if (resMkdir.succeeded()) {
                        vertx.fileSystem().copyRecursive(distPath, outputPath, true, resCopy -> {
                            if (resCopy.succeeded()) {
                                result.handle(new DefaultAsyncResult<>(null));
                            } else {
                                result.handle(resCopy);
                            }
                        });
                    } else {
                        result.handle(resMkdir);
                    }
                });
            });
        } catch (Exception e) {
            result.handle(new DefaultAsyncResult<>(e));
        }
    }

    @Override
    public void deploy(JsonObject service, Handler<AsyncResult<Void>> result) {
        try {
            final String id = ModuleDeployer.getServiceId(service);
            final String servicePath = getServicePath(service);
            vertx.fileSystem().exists(servicePath, ar -> {
                if (ar.succeeded() && ar.result()) {
                    doDeploy(service, result);
                } else {
                    serviceResolver.resolve(id, jar -> {
                        if (jar.succeeded()) {
                            ZipUtils.unzip(vertx, jar.result(), servicePath, res -> {
                                if (res.succeeded()) {
                                    doDeploy(service, result);
                                } else {
                                    result.handle(new DefaultAsyncResult<>(res.cause()));
                                }
                            });
                        } else {
                            result.handle(new DefaultAsyncResult<>(
                                    new Exception("[FrontDeployer] Service not found : " + id)));
                        }
                    });
                }
            });
        } catch (Exception e) {
            result.handle(new DefaultAsyncResult<>(e));
        }
    }

    @Override
    public void undeploy(JsonObject service, Handler<AsyncResult<Void>> result) {
        try {
            final String outputPath = getOutputPath(service);
            final String servicePath = getServicePath(service);
            // delete output and service path
            vertx.fileSystem().deleteRecursive(outputPath, true, res1 -> {
                if (res1.failed()) {
                    log.warn("Could not delete :"+outputPath, res1.cause());
                }
                vertx.fileSystem().deleteRecursive(servicePath, true, res2 -> {
                    if (res2.failed()) {
                        log.warn("Could not delete :"+servicePath, res2.cause());
                    }
                    result.handle(new DefaultAsyncResult<>(res2.cause()));
                });
            });
        } catch (Exception e) {
            result.handle(new DefaultAsyncResult<>(e));
        }
    }
}
