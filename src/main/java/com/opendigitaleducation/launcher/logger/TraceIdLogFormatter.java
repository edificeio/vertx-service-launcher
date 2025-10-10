package com.opendigitaleducation.launcher.logger;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import static com.opendigitaleducation.launcher.interceptor.TraceIdInboundInterceptor.TRACE_ID;

public class TraceIdLogFormatter extends Formatter {

    private final String format;
    private static final String DEFAULT_FORMAT = "%1$tF %1$tT.%1$tL %7$s%4$s %5$s%6$s%n";

    public TraceIdLogFormatter() {
        String fmt = LogManager.getLogManager().getProperty("java.util.logging.SimpleFormatter.format");
        this.format = (fmt != null ? fmt : DEFAULT_FORMAT);
    }

    @Override
    public String format(LogRecord record) {
        String traceId = null;
        try {
            Context context = Vertx.currentContext();
            if(context.getLocal(TRACE_ID) != null) {
                traceId = "[" + context.getLocal(TRACE_ID) + "] ";
            }
        } catch (RuntimeException e) {
            //we are out of the context, can happen with endHandler or worker if we don't join the context
        }
        LocalDateTime instant = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault());
        String source;
        if (record.getSourceClassName() != null) {
            source = record.getSourceClassName();
            if (record.getSourceMethodName() != null) {
                source += " " + record.getSourceMethodName();
            }
        } else {
            source = record.getLoggerName();
        }
        String message = formatMessage(record);
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }
        if(traceId == null) {
            return String.format(format,
                instant,
                source,
                record.getLoggerName(),
                record.getLevel().getLocalizedName(),
                message,
                throwable,
                "");
        }
        return String.format(format,
            instant,
            source,
            record.getLoggerName(),
            record.getLevel().getLocalizedName(),
            message,
            throwable,
            traceId);
    }
}
