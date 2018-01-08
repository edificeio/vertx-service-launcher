package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public interface ServiceResolver {

    void init(Vertx vertx, String servicesPath);

    void resolve(String identifier, Handler<AsyncResult<String>> handler);

    void close();

}
