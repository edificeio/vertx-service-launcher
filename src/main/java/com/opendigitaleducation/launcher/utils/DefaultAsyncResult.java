package com.opendigitaleducation.launcher.utils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class DefaultAsyncResult<T> implements AsyncResult<T> {

    private final T object;
    private final Throwable exception;

    public DefaultAsyncResult(T object) {
        this.object = object;
        this.exception = null;
    }

    public DefaultAsyncResult(Throwable exception) {
        this.object = null;
        this.exception = exception;
    }

    @Override
    public T result() {
        return object;
    }

    @Override
    public Throwable cause() {
        return exception;
    }

    @Override
    public boolean succeeded() {
        return exception == null;
    }

    @Override
    public boolean failed() {
        return exception != null;
    }

    public static <T> void handleAsyncResult(T result, Handler<AsyncResult<T>> handler) {
        handler.handle(new DefaultAsyncResult<>(result));
    }

    public static <T> void handleAsyncError(Throwable exception, Handler<AsyncResult<T>> handler) {
        handler.handle(new DefaultAsyncResult<>(exception));
    }

}
