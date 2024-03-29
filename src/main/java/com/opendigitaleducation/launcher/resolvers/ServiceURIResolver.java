package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public interface ServiceURIResolver {

    void init(Vertx vertx, String servicesPath);

    void resolveURI(String identifier, Handler<AsyncResult<String>> handler);

    void close();

}
