package com.opendigitaleducation.launcher.logger;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class AccessLogHandler extends StreamHandler {

  public AccessLogHandler() {
    super(System.out, new JsonAccessFormatter());
  }

  @Override
  public void publish(LogRecord record) {
    super.publish(record);
    flush();
  }
}
