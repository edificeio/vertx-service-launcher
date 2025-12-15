package com.opendigitaleducation.launcher.interceptor;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Inbound interceptor that track trace id to propagate it to context. Used in @{@link com.opendigitaleducation.launcher.logger.TraceIdLogFormatter}
 *
 * @param <T>
 */
public class TraceIdInboundInterceptor<T> implements Handler<DeliveryContext<T>> {

    private static final Logger logger = LoggerFactory.getLogger(TraceIdInboundInterceptor.class);
    public static final String TRACE_TIME = "X-Cloud-Trace-Time";
    public static final String TRACE_ID   = "X-Cloud-Trace-Context";
    public static final String TRACE_ADDRESS   = "X-Cloud-Trace-Address";

    @Override
    public void handle(DeliveryContext<T> event) {
        setTraceIdQuietly(event.message());
        event.next();
    }

    private void setTraceIdQuietly(Message<?> message) {
        if (message.headers().contains(TRACE_ID)) {
            final Context context = Vertx.currentContext();
            if(context != null) {
                try {
                    context.putLocal(TRACE_TIME, String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
                    context.putLocal(TRACE_ID, message.headers().get(TRACE_ID));
                    context.putLocal(TRACE_ADDRESS, message.address());
                    if (message.address() != null && !message.address().contains("__vertx.reply")) {
                        logger.debug(String.format("Bus query @%s", message.address()));
                    }
                } catch (RuntimeException e) {
                    //silent
                }
            }
        }
    }
}
