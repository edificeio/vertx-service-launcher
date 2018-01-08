package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static com.opendigitaleducation.launcher.utils.DefaultAsyncResult.handleAsyncError;

public class ServiceResolverFactory {

    private List<ServiceResolver> serviceResolvers;

    public void init(Vertx vertx, String servicesPath) {
        serviceResolvers = new ArrayList<>();
        ServiceLoader<ServiceResolver> resolvers = ServiceLoader.load(ServiceResolver.class);
        for(ServiceResolver resolver : resolvers) {
            resolver.init(vertx, servicesPath);
            serviceResolvers.add(resolver);
        }
    }

    public void resolve(String identifier, Handler<AsyncResult<String>> handler) {
        resolve(0, identifier, handler);
    }

    private void resolve(int index, String identifier, Handler<AsyncResult<String>> handler) {
        if (index >= serviceResolvers.size()) {
            handleAsyncError(new NotFoundServiceException(), handler);
            return;
        }
        serviceResolvers.get(index).resolve(identifier, res -> {
            if (res.succeeded()) {
                handler.handle(res);
            } else {
                resolve(index + 1, identifier, handler);
            }
        });
    }

}
