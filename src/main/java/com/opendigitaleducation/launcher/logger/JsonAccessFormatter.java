package com.opendigitaleducation.launcher.logger;

import io.vertx.core.json.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonAccessFormatter extends Formatter {

  @Override
  public String format(LogRecord record) {
    JsonObject logEntry = new JsonObject()
      .put("timestamp", Instant.ofEpochMilli(record.getMillis()).toString())
      .put("logger", record.getLoggerName());
    final String message = record.getMessage();
    try {
      JsonObject messageJson = new JsonObject(message);
      logEntry.mergeIn(messageJson);
    } catch (Exception e) {
      logEntry.put("message", message);
    }

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
