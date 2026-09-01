package com.opendigitaleducation.launcher.utils;

public interface LogUtil {
    String HOSTNAME = System.getenv("HOSTNAME") == null ? "n/a" : System.getenv("HOSTNAME");
}
