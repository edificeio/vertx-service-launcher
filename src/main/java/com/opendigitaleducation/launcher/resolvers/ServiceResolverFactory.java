package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public void init(final Vertx vertx, final String servicesPath, final String forceExtension) {
        init(vertx, servicesPath);
        for(final ServiceResolver resolver : serviceResolvers) {
            if(resolver instanceof  AbstactServiceResolver){
                ((AbstactServiceResolver) resolver).setForceExtension(Optional.of(forceExtension));
            }
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

    public void close() {
        if (serviceResolvers != null) {
            for (ServiceResolver serviceResolver : serviceResolvers) {
                serviceResolver.close();
            }
        }
    }

}
