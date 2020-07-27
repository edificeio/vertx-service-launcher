package com.opendigitaleducation.launcher.deployer;

import static com.opendigitaleducation.launcher.FolderServiceFactory.FACTORY_PREFIX;

import java.io.File;
import java.util.List;

import com.opendigitaleducation.launcher.resolvers.ExtensionRegistry;
import com.opendigitaleducation.launcher.utils.FileUtils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;

public class ModuleDeployerDefault implements ModuleDeployer {
    private final String assetPath;
    private final boolean cluster;
    private final String node;
    private final Vertx vertx;
    private final String servicesPath;
    private final String absoluteServicePath;
    private CustomDeployerManager customDeployer;
    private final LocalMap<String, String> versionMap;
    private final LocalMap<String, String> deploymentsIdMap;
    private static Logger log = LoggerFactory.getLogger(ModuleDeployerDefault.class);

    public ModuleDeployerDefault(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        assetPath = config.getString("assets-path");
        absoluteServicePath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
        // cluster flag
        final LocalMap<Object, Object> serverMap = vertx.sharedData().getLocalMap("server");
        cluster = config.getBoolean("cluster", false);
        serverMap.put("cluster", cluster);
        // node flag
        node = config.getString("node", "");
        serverMap.put("node", node);
        // maps
        deploymentsIdMap = vertx.sharedData().getLocalMap("deploymentsId");
        versionMap = vertx.sharedData().getLocalMap("versions");
        //
        this.servicesPath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
        customDeployer = new CustomDeployerManager(vertx, servicesPath, assetPath);
    }

    protected String getServicePath(JsonObject service) throws Exception {
        final String id = ModuleDeployer.getServiceId(service);
        return servicesPath + File.separator + id + File.separator;
    }

    @Override
    public Future<Void> deploy(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        log.info("Starting deployment of mod : " + name);
        final JsonObject config = service.getJsonObject("config", new JsonObject());
        config.put("cwd", absoluteServicePath + File.separator + name);
        if (!config.containsKey("assets-path") && assetPath != null) {
            config.put("assets-path", assetPath);
        }
        final String address = config.getString("address");
        if (cluster && !node.isEmpty() && address != null) {
            config.put("address", node + address);
        }
        final DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config)
                .setWorker(service.getBoolean("worker", false)).setInstances(config.getInteger("instances", 1))
                .setMultiThreaded(service.getBoolean("multi-threaded", false));
        // register extension
        ExtensionRegistry.register(ModuleDeployer.getServiceIdQuietly(service), service);
        // custom deployer
        final Future<Void> future = Future.future();
        if (customDeployer.canDeploy(service)) {
            customDeployer.deploy(service, res -> {
                if (res.succeeded()) {
                    log.info("Custom deployment succeed :" + name);
                    future.complete();
                } else {
                    log.error("Custom deployment failed :" + name, res.cause());
                    future.fail(res.cause());
                }
            });
            return future;
        }
        //
        vertx.deployVerticle(FACTORY_PREFIX + ":" + name, deploymentOptions, ar -> {
            if (ar.succeeded()) {
                log.info("Mod has been deployed successfully : " + name);
                addAppVersion(name, ar.result());
                future.complete();
            } else {
                log.error("Error deploying required service  : " + name, ar.cause());
                future.fail(ar.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> undeploy(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        final Future<Void> future = Future.future();
        // custom deployer
        if (customDeployer.canDeploy(service)) {
            customDeployer.undeploy(service, res -> {
                if (res.succeeded()) {
                    log.info("Custom undeployment succeed :" + name);
                    future.complete();
                } else {
                    log.error("Custom undeployment failed :" + name, res.cause());
                    future.fail(res.cause());
                }
            });
            return future;
        }
        //
        try {
            final String servicePath = getServicePath(service);
            log.info("Starting undeployment of mod : " + name);
            final Future<Void> undeployFuture = Future.future();
            if (deploymentsIdMap.containsKey(name)) {
                vertx.undeploy(deploymentsIdMap.get(name), undeployFuture);
            } else {
                undeployFuture.complete();
            }
            //
            undeployFuture.setHandler(ar -> {
                if (ar.succeeded()) {
                    removeAppVersion(name);
                    // clean service directory
                    final String ext = ExtensionRegistry.getExtensionForService(service);
                    final Future<Void> deleteDir = Future.future();
                    final Future<Void> deleteArtefact = Future.future();
                    vertx.fileSystem().deleteRecursive(servicePath, true, deleteDir);
                    vertx.fileSystem().delete(FileUtils.pathWithExtension(servicePath, ext), deleteArtefact);
                    CompositeFuture.all(deleteDir, deleteArtefact).setHandler(resDel -> {
                        if (resDel.failed()) {
                            log.error(
                                    "Mod has been undeployed successfully but service directory/artefact could not be cleaned",
                                    resDel.cause());
                        } else {
                            log.info("Mod has been undeployed successfully : " + name);
                        }
                        future.complete();
                    });
                } else {
                    log.error("Error undeploying required service  : " + name, ar.cause());
                    future.fail(ar.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> restart(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        log.info("Starting restarting of mod : " + name);
        final Future<Void> undeployFuture = Future.future();
        if (deploymentsIdMap.containsKey(name)) {
            vertx.undeploy(deploymentsIdMap.get(name), undeployFuture);
        } else {
            undeployFuture.complete();
        }
        //
        final Future<Void> future = Future.future();
        undeployFuture.setHandler(ar0 -> {
            if (ar0.succeeded()) {
                removeAppVersion(name);
                deploy(service).setHandler(future);
            } else {
                log.error("Error restarting (undeploying) required service  : " + name, ar0.cause());
                future.fail(ar0.cause());
            }
        });
        return future;
    }

    private void addAppVersion(final String moduleName, final String deploymentId) {
        final String[] lNameVersion = moduleName.split("~");
        if (lNameVersion.length == 3) {
            versionMap.put(lNameVersion[0] + "." + lNameVersion[1], lNameVersion[2]);
            deploymentsIdMap.put(lNameVersion[0] + "." + lNameVersion[1], deploymentId);
        }
    }

    private void removeAppVersion(final String moduleName) {
        final String[] lNameVersion = moduleName.split("~");
        if (lNameVersion.length == 3) {
            versionMap.remove(lNameVersion[0] + "." + lNameVersion[1], lNameVersion[2]);
            deploymentsIdMap.remove(lNameVersion[0] + "." + lNameVersion[1]);
        }
    }
}