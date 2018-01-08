package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class AbstactServiceResolver implements ServiceResolver {

    protected Vertx vertx;
    protected String servicesPath;
    protected static final Logger log = LoggerFactory.getLogger(ServiceResolver.class);

    public void init(Vertx vertx, String servicesPath) {
        this.vertx = vertx;
        this.servicesPath = servicesPath;
    }

}
