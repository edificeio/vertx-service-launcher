package com.opendigitaleducation.launcher.resolvers;

import com.opendigitaleducation.launcher.utils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JarServiceResolver extends AbstactServiceResolver {

    private ConcurrentMap<String, String> jarsInPath = new ConcurrentHashMap<>();

    @Override
    public void init(Vertx vertx, String servicesPath) {
        super.init(vertx, servicesPath);
        loadJarInPath();
    }

    private void loadJarInPath() {
        try {
            List<String> jars = this.vertx.fileSystem().readDirBlocking(servicesPath, ".*-fat.jar");
            for (String jarPath : jars) {
                final String jarName;
                if (jarPath.contains(File.separator)) {
                    jarName = jarPath.substring(jarPath.lastIndexOf(File.separatorChar) + 1);
                } else {
                    jarName = jarPath;
                }
                jarsInPath.putIfAbsent(jarName.replaceFirst("-fat.jar", ""), jarPath);
            }
        } catch (RuntimeException e) {
            log.error("Error listing jars in services path.", e);
        }
    }

    @Override
    public void resolve(String identifier, Handler<AsyncResult<String>> handler) {
        final String jar = jarsInPath.get(identifier);
        if (jar != null) {
            DefaultAsyncResult.handleAsyncResult(jar, handler);
        } else {
            DefaultAsyncResult.handleAsyncError(new NotFoundServiceException(), handler);
        }
    }

}
