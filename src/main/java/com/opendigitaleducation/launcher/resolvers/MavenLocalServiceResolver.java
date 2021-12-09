package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.io.File;

import static com.opendigitaleducation.launcher.utils.DefaultAsyncResult.handleAsyncError;
import static com.opendigitaleducation.launcher.utils.DefaultAsyncResult.handleAsyncResult;

public class MavenLocalServiceResolver extends AbstactServiceResolver {

    @Override
    public void resolve(String identifier, Handler<AsyncResult<String>> handler) {
        final String [] id = identifier.split("~");
        if (id.length != 3) {
            handleAsyncError(new NotFoundServiceException("invalid.identifier"), handler);
            return;
        }
        final String ext = getExtensionForId(identifier);
        final String path = System.getProperty("user.home") + File.separator + ".m2" + File.separator +
            "repository" + File.separator + id[0].replaceAll("\\.", File.separator) + File.separator + id[1] +
            File.separator + id[2] + File.separator + id[1] + "-" + id[2] + ext;
        vertx.fileSystem().exists(path, ar -> {
            if (ar.succeeded() && ar.result()) {
                handleAsyncResult(path, handler);
            } else {
                handleAsyncError(new NotFoundServiceException(), handler);
            }
        });
    }

}
