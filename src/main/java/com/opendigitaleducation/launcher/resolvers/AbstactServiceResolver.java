package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

public abstract class AbstactServiceResolver implements ServiceResolver {

    protected Vertx vertx;
    protected String servicesPath;
    protected Optional<String> forceExtension = Optional.empty();
    protected static final Logger log = LoggerFactory.getLogger(ServiceResolver.class);

    public void setForceExtension(Optional<String> forceExtension) {
        this.forceExtension = forceExtension;
    }

    protected String getExtensionForId(final String id){
        if(forceExtension.isPresent()){
            return forceExtension.get();
        }
        return ExtensionRegistry.getExtensionForId(id);
    }

    @Override
    public void init(Vertx vertx, String servicesPath) {
        this.vertx = vertx;
        this.servicesPath = servicesPath;
    }

    @Override
    public void close() {

    }

}
