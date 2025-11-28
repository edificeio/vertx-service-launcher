package com.opendigitaleducation.launcher.logger;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class ENTLogHandler extends StreamHandler {

  public ENTLogHandler() {
    super(System.out, new JsonENTFormatter());
  }

  @Override
  public void publish(LogRecord record) {
    super.publish(record);
    flush();
  }
}
