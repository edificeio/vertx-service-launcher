package com.opendigitaleducation.launcher.deployer;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.DefaultAsyncResult;
import com.opendigitaleducation.launcher.utils.ZipUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomDeployerFront implements CustomDeployer {
    static Logger log = LoggerFactory.getLogger(CustomDeployerFront.class);
    private static final String MODSINFO_MAP_NAME= "modsInfoMap";
    private static final String MODSINFO_CHANGED_EVENT_NAME= "modsInfoChanged";
    private static final String ASSETS_TYPES = "assets";
    private final Vertx vertx;
    private final String servicesPath;
    private final String assetPath;
    private final ServiceResolverFactory serviceResolver;
    private final DateFormat deployedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");


    static final Map<String, String> outputForTypes = new HashMap<>();
    {
        outputForTypes.put("theme", "themes");
        outputForTypes.put("themes", "themes");
        outputForTypes.put("js", "js");
        outputForTypes.put(ASSETS_TYPES, "assets");
    }

    public CustomDeployerFront(Vertx vertx, String servicesPath, String assetPath, ServiceResolverFactory resolver) {
        this.vertx = vertx;
        this.assetPath = assetPath;
        this.servicesPath = servicesPath;
        this.serviceResolver = resolver;

    }

    public static boolean isAssetsService(JsonObject service) {
        final String type = service.getString("type");
        return (ASSETS_TYPES.equals(type));
    }

    public static boolean canDeployService(JsonObject service) {
        final String type = service.getString("type");
        return outputForTypes.containsKey(type);
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
        final String assetDir = service.getString("assets-dir", "assets");
        final String[] lNameVersion = getModOwnerNameVersion(service);
        final String destDirName = service.getString("output-dir-name", lNameVersion[1]);
        final String outputPath = assetPath + (assetPath.endsWith(File.separator) ? "" : File.separator) + assetDir
                + File.separator + outputDir + File.separator + destDirName;
        return outputPath;
    }

    /**
     * Return an array containing respectively the owner, name and MAYBE the version.
     * Be sure to check the array size before accessing the version, at index 2.
     * @param service The service JSON 
     * @return an array of size 2 or 3
     * @throws Exception when the service has no name at index 1.
     */
    protected String[] getModOwnerNameVersion(JsonObject service) throws Exception {
        final String moduleName = service.getString("name");
        final String[] lNameVersion = moduleName.split("~");
        if (lNameVersion.length < 2) {
            throw new Exception("[FrontDeployer] Invalid name : " + moduleName);
        }
        return lNameVersion;
    }

    protected void makeDirAndCopyRecursive(String from, String to, Handler<AsyncResult<Void>> result) {
        vertx.fileSystem().mkdirs(to, resMkdir -> {
            if (resMkdir.succeeded()) {
                vertx.fileSystem().copyRecursive( from, to, true, result );
            } else {
                result.handle(resMkdir);
            }
        });
    }

    protected void doDeploy(JsonObject service, Handler<AsyncResult<Void>> result) {
        try {
            final String outputPath = getOutputPath(service);
            final String distPath = getDistPath(service);
            final String[] lNameVersion = getModOwnerNameVersion(service);
            final Promise<Void> deployment = Promise.promise();
            if(service.getBoolean("skip-clean", false)){
                final String moduleName = service.getString("name");
                log.info("Skipping clean for mods: "+moduleName);
                // dont clean
                makeDirAndCopyRecursive(distPath, outputPath, deployment::handle);
            } else {
                // clean and recreate
                vertx.fileSystem().deleteRecursive(outputPath, true, resDelete -> {
                    makeDirAndCopyRecursive(distPath, outputPath, deployment::handle);
                });
            }

            deployment.future().onComplete( resDeploy -> {
                if( resDeploy.succeeded() ) {
                    // Prepare the new module infos.
                    final String version = lNameVersion.length > 2 ? lNameVersion[2] : "";
                    Map<String, Object> infosMap = new HashMap<String, Object>();
                    infosMap.put( "name", lNameVersion[1] );
                    infosMap.put( "version", version );
                    infosMap.put( "outputPath", outputPath );
                    infosMap.put( "distPath", distPath );
                    infosMap.put( "deployedAt", deployedAt.format(new Date()) );
                    // Update the information in local shared map, then signal the change.
                    // At least one Lambda (modVersion) in web-utils is listening to this event.
                    final Map<String, JsonObject> sharedMods = vertx.sharedData().getLocalMap(MODSINFO_MAP_NAME);
                    final JsonObject infos = new JsonObject(infosMap);
                    sharedMods.put(lNameVersion[1], infos);
                    vertx.eventBus().publish(MODSINFO_CHANGED_EVENT_NAME, infos);

                    log.info("CustomDeployerFront.deployment.onComplete : "+ infosMap.toString());

                    // Finish handling the deployment.
                    result.handle(new DefaultAsyncResult<>(null));
                } else {
                    result.handle( resDeploy );
                }
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
            final String moduleName = service.getString("name");
            if(service.getBoolean("skip-clean", false)) {
                log.info("Skipping clean for mods: "+moduleName);
                result.handle(new DefaultAsyncResult<>((Void)null));
                return;
            }
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
