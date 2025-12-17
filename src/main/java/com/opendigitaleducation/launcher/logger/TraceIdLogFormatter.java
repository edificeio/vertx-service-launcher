package com.opendigitaleducation.launcher.logger;

import com.opendigitaleducation.launcher.utils.LocalContextProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

public class TraceIdLogFormatter extends Formatter {

    private final String format;
    private static final String DEFAULT_FORMAT = "%1$tF %1$tT.%1$tL %7$s%4$s %5$s%6$s%n";

    public TraceIdLogFormatter() {
        String fmt = LogManager.getLogManager().getProperty("java.util.logging.SimpleFormatter.format");
        this.format = (fmt != null ? fmt : DEFAULT_FORMAT);
    }

    @Override
    public String format(LogRecord record) {
        String traceId = LocalContextProvider.getTraceId();
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
        if(StringUtils.isEmpty(traceId)) {
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
            "[" + traceId + "] ");
    }
}
