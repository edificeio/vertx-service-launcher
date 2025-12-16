package com.opendigitaleducation.launcher.utils;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

import static com.opendigitaleducation.launcher.interceptor.TraceIdInboundInterceptor.TRACE_ID;

public class TraceIdProvider {

    public static String getTraceId() {
        String traceId = null;
        try {
            Context context = Vertx.currentContext();
            if(context.getLocal(TRACE_ID) != null) {
                traceId = "[" + context.getLocal(TRACE_ID) + "] ";
            }
        } catch (RuntimeException e) {
            //we are out of the context, can happen with endHandler or worker if we don't join the context
        }
        return traceId;
    }

}
