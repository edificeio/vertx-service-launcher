package com.opendigitaleducation.launcher.deployer;

import com.opendigitaleducation.launcher.resolvers.ServiceResolverFactory;
import com.opendigitaleducation.launcher.utils.DefaultAsyncResult;
import com.opendigitaleducation.launcher.utils.FileUtils;
import com.opendigitaleducation.launcher.utils.ZipUtils;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class CustomDeployerOverride implements CustomDeployer {
    public static final String OVERRIDE_TYPES = "override";
    public static final String OLD_EXT = ".old";
    static Logger log = LoggerFactory.getLogger(CustomDeployerOverride.class);
    private final Vertx vertx;
    private final String servicesPath;
    private final String assetPath;
    private final ServiceResolverFactory serviceResolver;

    public CustomDeployerOverride(final Vertx vertx, final String servicesPath, final String assetPath, final ServiceResolverFactory resolver) {
        this.vertx = vertx;
        this.assetPath = assetPath;
        this.servicesPath = servicesPath;
        this.serviceResolver = resolver;

    }

    public static boolean isOverride(final JsonObject service){
        final String type = service.getString("type");
        return OVERRIDE_TYPES.equals(type);
    }

    @Override
    public boolean canDeploy(final JsonObject service) {
        return isOverride(service);
    }

    protected String[] getModOwnerNameVersion(final JsonObject service) throws Exception {
        final String moduleName = service.getString("name");
        final String[] lNameVersion = moduleName.split("~");
        if (lNameVersion.length < 2) {
            throw new Exception("[CustomDeployerOverride] Invalid name : " + moduleName);
        }
        return lNameVersion;
    }

    protected String getServicePath(final JsonObject service) throws Exception {
        final String id = ModuleDeployer.getServiceId(service);
        return servicesPath + File.separator + id + File.separator;
    }

    public static String getOverrideDir(final JsonObject service){
        final String dist = service.getString("override-dir", "overrides");
        return dist;
    }

    protected String getOverridePath(final JsonObject service) throws Exception {
        final String dist = getOverrideDir(service);
        final String servicePath = getServicePath(service);
        final String distPath = servicePath + File.separator + dist;
        return distPath;
    }

    protected String getOutputPath(final JsonObject service) throws Exception {
        final String outputDir = service.getString("output-dir", "");
        final String assetDir = service.getString("assets-dir", "assets");
        final String[] lNameVersion = getModOwnerNameVersion(service);
        final String destDirName = service.getString("output-dir-name", lNameVersion[1]);
        final String outputPath = assetPath + (assetPath.endsWith(File.separator) ? "" : File.separator) + assetDir
            + File.separator + outputDir + File.separator + destDirName;
        return outputPath;
    }

    protected void doDeploy(final JsonObject service, final Handler<AsyncResult<Void>> result) {
        try {
            final String overridePathStr = getOverridePath(service);
            final String outputPathStr = getOutputPath(service);
            final Path overridePath = Paths.get(overridePathStr);
            FileUtils.listFilesRecursively(vertx, overridePathStr).compose(files->{
                final List<Future> futures = new ArrayList<>();
                for(final Path file : files){
                    final Path relative = file.relativize(overridePath);
                    final Path outputPath = Paths.get(outputPathStr, relative.toString());
                    final String outputFile = outputPath.toString();
                    final String oldFile = outputFile + OLD_EXT;
                    final String inputFile = file.toString();
                    final Future<Void> future = FileUtils.copyIfDestNotExists(vertx, outputFile, oldFile).compose(copy -> {
                        final Promise<Void> promise = Promise.promise();
                        vertx.fileSystem().copy(inputFile, outputFile, promise);
                        return promise.future();
                    });
                    futures.add(future);
                }
                return CompositeFuture.all(futures);
            }).map(e -> {
                return (Void)null;
            }).onComplete(result);
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
            final String overridePathStr = getOverridePath(service);
            final String outputPathStr = getOutputPath(service);
            final Path overridePath = Paths.get(overridePathStr);
            FileUtils.listFilesRecursively(vertx, overridePathStr).compose(files-> {
                final List<Future> futures = new ArrayList<>();
                for (final Path file : files) {
                    final Path relative = file.relativize(overridePath);
                    final Path outputPath = Paths.get(outputPathStr, relative.toString());
                    final String outputFile = outputPath.toString();
                    final String oldFile = outputFile + OLD_EXT;
                    futures.add(FileUtils.moveIfSourceExists(vertx, oldFile, outputFile));
                }
                return CompositeFuture.all(futures);
            }).map(e -> {
                return (Void)null;
            }).onComplete(result);
        } catch (Exception e) {
            result.handle(new DefaultAsyncResult<>(e));
        }
    }
}
