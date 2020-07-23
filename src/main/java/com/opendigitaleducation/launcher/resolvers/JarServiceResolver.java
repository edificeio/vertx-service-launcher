package com.opendigitaleducation.launcher.resolvers;

import com.opendigitaleducation.launcher.utils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.io.File;

public class JarServiceResolver extends AbstactServiceResolver {

    private Vertx vertx;

    @Override
    public void init(Vertx vertx, String servicesPath) {
        super.init(vertx, servicesPath);
        this.vertx = vertx;
    }

    protected String getServicePath(String id) {
         return servicesPath + File.separator + id + ExtensionRegistry.getExtensionForId(id);
    }

    @Override
    public void resolve(String identifier, Handler<AsyncResult<String>> handler) {
        final String jar = getServicePath(identifier);
        //check dynamically because jar could be deleted or added by deploy/undeploy
        vertx.fileSystem().exists(jar, res->{
            if(res.succeeded() && res.result()){
                DefaultAsyncResult.handleAsyncResult(jar, handler);
            } else {
                DefaultAsyncResult.handleAsyncError(new NotFoundServiceException(), handler);
            }
        });
    }

}
