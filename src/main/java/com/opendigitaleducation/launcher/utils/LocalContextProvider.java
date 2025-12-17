package com.opendigitaleducation.launcher.utils;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

import static com.opendigitaleducation.launcher.interceptor.TraceIdInboundInterceptor.TRACE_ID;
import static com.opendigitaleducation.launcher.interceptor.TraceIdOutboundInterceptor.TRACE_MTTR;

public class LocalContextProvider {

    public static String getTraceId() {
        return getFromKey(TRACE_ID);
    }

    public static String getMTTR() {
        return getFromKey(TRACE_MTTR);
    }

    private static String getFromKey(String key) {
        String value = "";
        try {
            Context context = Vertx.currentContext();
            if(context.getLocal(key) != null) {
                value = context.getLocal(key).toString();
            }
        } catch (RuntimeException e) {
            //we are out of the context, can happen with endHandler or worker if we don't join the context
        }
        return value;
    }

}
