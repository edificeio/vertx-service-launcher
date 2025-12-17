package com.opendigitaleducation.launcher.interceptor;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static com.opendigitaleducation.launcher.interceptor.TraceIdInboundInterceptor.*;

public class TraceIdOutboundInterceptor<T> implements Handler<DeliveryContext<T>> {

    private static final Logger LOG = LoggerFactory.getLogger(TraceIdOutboundInterceptor.class);
    public static final String TRACE_MTTR = "Trace-MTTR";

    @Override
    public void handle(DeliveryContext<T> event) {
        setTraceIdQuietly(event.message());
        event.next();
    }

    private void setTraceIdQuietly(Message<T> message) {
        try {
            String traceId = Vertx.currentContext().getLocal(TRACE_ID);
            String traceTime = Vertx.currentContext().getLocal(TRACE_TIME);
            String traceAddress = Vertx.currentContext().getLocal(TRACE_ADDRESS);
            if (traceId != null) {
                message.headers().add(TRACE_ID, traceId);
            }
            if (!StringUtils.isEmpty(traceTime)) {
                long originTime = Long.parseLong(traceTime);
                long finishTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - originTime;
                Vertx.currentContext().putLocal(TRACE_MTTR, finishTime);
                if (traceAddress != null && !traceAddress.contains("__vertx.reply")) {
                    LOG.info(String.format("End of Bus Query %s", Objects.toString(traceAddress, "")));
                }
            }
        } catch (RuntimeException e) {
            //silent
        }
    }
}
