package com.opendigitaleducation.launcher.logger;

import com.opendigitaleducation.launcher.utils.LocalContextProvider;
import io.vertx.core.json.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonENTFormatter extends Formatter {

  @Override
  public String format(LogRecord record) {
    JsonObject logEntry = new JsonObject()
      .put("timestamp", Instant.ofEpochMilli(record.getMillis()).toString())
      .put("level", record.getLevel().toString())
      .put("logger", record.getLoggerName())
      .put("traceId", LocalContextProvider.getTraceId())
      .put("message", record.getMessage())
      .put("mttr", LocalContextProvider.getMTTR());

    if (record.getThrown() != null) {
        try {
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          record.getThrown().printStackTrace(pw);
          pw.close();
          logEntry.put("exception", sw.toString());
        } catch (Exception ex) {
          logEntry.put("exception", record.getThrown().toString());
        }
    }

    return logEntry.encode() + System.lineSeparator();
  }
}
