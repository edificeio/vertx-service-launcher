package com.opendigitaleducation.launcher.deployer;

import static com.opendigitaleducation.launcher.FolderServiceFactory.FACTORY_PREFIX;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.opendigitaleducation.launcher.hooks.Hook;
import com.opendigitaleducation.launcher.resolvers.ExtensionRegistry;
import com.opendigitaleducation.launcher.utils.FileUtils;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;

public class ModuleDeployerDefault implements ModuleDeployer {
    private final Hook hook;
    private final String assetPath;
    private final boolean cluster;
    private final String node;
    private final Vertx vertx;
    private final String servicesPath;
    private final String absoluteServicePath;
    private final JsonObject metricsOptions;
    private CustomDeployerManager customDeployer;
    private final LocalMap<String, String> versionMap;
    /** Map holding useful metadata about deployed modules. */
    private final LocalMap<String, JsonObject> detailedVersionMap;
    private final LocalMap<String, String> deploymentsIdMap;
    /** Entry keys of MANIFEST.MF file of deployed modules that should be used to populate detailed version metadata.*/
    private static final Map<String, String> manifestKeysForVersion;
    private static Logger log = LoggerFactory.getLogger(ModuleDeployerDefault.class);

    static {
        final Map<String, String> map = new HashMap<>();
        map.put("SCM-Commit-Id", "commitId");
        map.put("SCM-Branch", "branch");
        map.put("Build-Time", "buildTime");
        manifestKeysForVersion = Collections.unmodifiableMap(map);
    }

    public ModuleDeployerDefault(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        assetPath = config.getString("assets-path");
        absoluteServicePath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
        // metrics options
        metricsOptions = config.getJsonObject("metricsOptions");
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
        detailedVersionMap = vertx.sharedData().getLocalMap("detailedVersions");
        //
        this.servicesPath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
        customDeployer = new CustomDeployerManager(vertx, servicesPath, assetPath);
        hook = Hook.create(vertx, config);
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
        final String servicePath = absoluteServicePath + File.separator + name;
        config.put("cwd", servicePath);
        config.put("metricsOptions", metricsOptions);
        if (!config.containsKey("assets-path") && assetPath != null) {
            config.put("assets-path", assetPath);
        }
        final String address = config.getString("address");
        if (cluster && !node.isEmpty() && address != null) {
            config.put("address", node + address);
        }
        final DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config)
                .setWorker(service.getBoolean("worker", false))
                .setInstances(config.getInteger("instances", 1));
        if(config.containsKey("workerPoolSize")) {
            deploymentOptions.setWorkerPoolSize(config.getInteger("workerPoolSize"));
            log.info("Setting worker pool size to " + deploymentOptions.getWorkerPoolSize());
        }
        if(config.containsKey("workerPoolName")) {
            deploymentOptions.setWorkerPoolName(config.getString("workerPoolName"));
            log.info("Setting worker pool name to " + deploymentOptions.getWorkerPoolSize());
        }
        // register extension
        ExtensionRegistry.register(ModuleDeployer.getServiceIdQuietly(service), service);
        // custom deployer
        final Promise<Void> promise = Promise.promise();
        if (customDeployer.canDeploy(service)) {
            customDeployer.deploy(service, res -> {
                if (res.succeeded()) {
                    log.info("Custom deployment succeed :" + name);
                    promise.complete();
                    hook.emit(service, Hook.HookEvents.Deployed);
                } else {
                    log.error("Custom deployment failed :" + name, res.cause());
                    promise.fail(res.cause());
                }
            });
            return promise.future();
        }
        //
        vertx.deployVerticle(FACTORY_PREFIX + ":" + name, deploymentOptions, ar -> {
            if (ar.succeeded()) {
                log.info("Mod has been deployed successfully : " + name);
                addAppVersion(name, ar.result(), servicePath);
                promise.complete();
                hook.emit(service, Hook.HookEvents.Deployed);
            } else {
                log.error("Error deploying required service  : " + name, ar.cause());
                promise.fail(ar.cause());
            }
        });
        return promise.future();
    }

    @Override
    public Future<Void> undeploy(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        final Promise<Void> future = Promise.promise();
        // custom deployer
        if (customDeployer.canDeploy(service)) {
            customDeployer.undeploy(service, res -> {
                if (res.succeeded()) {
                    log.info("Custom undeployment succeed :" + name);
                    future.complete();
                    hook.emit(service, Hook.HookEvents.Undeployed);
                } else {
                    log.error("Custom undeployment failed :" + name, res.cause());
                    future.fail(res.cause());
                }
            });
            return future.future();
        }
        // defaut undeployer
        log.info("Starting undeployment of mod : " + name);
        if (deploymentsIdMap.containsKey(name)) {
            vertx.undeploy(deploymentsIdMap.get(name), ar -> {
                if (ar.succeeded()) {
                    removeAppVersion(name);
                    log.info("Mod has been undeployed successfully : " + name);
                    future.complete();
                    hook.emit(service, Hook.HookEvents.Undeployed);
                } else {
                    log.error("Error undeploying required service  : " + name, ar.cause());
                    future.fail(ar.cause());
                }
            });
        } else {
            log.warn("Deployment ID not found for service : " + name);
            future.complete();
        }
        return future.future();
    }

    @Override
    public Future<Void> clean(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        final Promise<Void> future = Promise.promise();
        try {
            log.info("Cleaning mod : " + name);
            final String servicePath = getServicePath(service);
            final String ext = ExtensionRegistry.getExtensionForService(service);
            final String artefact = FileUtils.pathWithExtension(servicePath, ext);
            log.info("Deleting dirs : " + servicePath+";"+artefact);
            final Future<Void> deleteDir = vertx.fileSystem().deleteRecursive(servicePath, true);
            final Future<Void> deleteArtefact = vertx.fileSystem().delete(artefact);
            //
            Future.all(deleteDir, deleteArtefact).onComplete(resDel -> {
                if (resDel.failed()) {
                    log.error("Mod has been cleaned successfully but service directory/artefact could not be cleaned: ",
                            resDel.cause());
                } else {
                    log.info("Mod has been cleaned successfully : " + name);
                }
                future.complete();
            });
        } catch (Exception e) {
            log.error("Failed to clean service : " + name, e);
            future.complete();
        }
        return future.future();
    }

    @Override
    public Future<Void> restart(JsonObject service) {
        final String name = service.getString("name");
        if (name == null || name.isEmpty()) {
            return Future.succeededFuture();
        }
        log.info("Starting restart of mod : " + name);
        //
        final Promise<Void> future = Promise.promise();
        undeploy(service).onComplete(ar0 -> {
            if (ar0.succeeded()) {
                deploy(service).onComplete(future);
            } else {
                log.error("Error restarting (undeploying) required service  : " + name, ar0.cause());
                future.fail(ar0.cause());
            }
        });
        return future.future();
    }

    private void addAppVersion(final String moduleName,
                               final String deploymentId,
                               final String servicePath) {
        final String[] lNameVersion = moduleName.split("~");

        if (lNameVersion.length == 3) {
            final String moduleKey = lNameVersion[0] + "." + lNameVersion[1];
            final String version = lNameVersion[2];
            versionMap.put(moduleKey, version);
            deploymentsIdMap.put(moduleName, deploymentId);// use module name in case of multiple mods with different
                                                           // versions
            // Construct the detailedVersion map by parsing the manifest file of the module and
            // extracting only the desired pieces of information
            // If there is no manifest, only the version of the module has specified in entcore.json
            // will be available.
            vertx.fileSystem().readFile(servicePath + "/META-INF" + File.separator + "MANIFEST.MF", ar -> {
                final JsonObject detailedVersion = new JsonObject();
                detailedVersion.put("version", version);
                if (ar.succeeded()) {
                    Scanner s = new Scanner(ar.result().toString());
                    while (s.hasNextLine()) {
                        final String line = s.nextLine();
                        final String[] items = line.split(":", 2);
                        if (items.length == 2) {
                            final String key = items[0];
                            if(manifestKeysForVersion.containsKey(key)) {
                                detailedVersion.put(manifestKeysForVersion.get(key), items[1].trim());
                            }
                        }
                    }
                }
                detailedVersionMap.put(moduleKey, detailedVersion);
            });
        }
    }

    private void removeAppVersion(final String moduleName) {
        final String[] lNameVersion = moduleName.split("~");
        if (lNameVersion.length == 3) {
            final String moduleKey = lNameVersion[0] + "." + lNameVersion[1];
            versionMap.remove(moduleKey, lNameVersion[2]);
            detailedVersionMap.remove(moduleKey);
            deploymentsIdMap.remove(moduleName);
        }
    }
}
