package com.opendigitaleducation.launcher.logger;

import java.io.IOException;
import java.util.logging.FileHandler;

public class AccessFileHandler extends FileHandler {

    public AccessFileHandler() throws IOException, SecurityException {
    }

    public AccessFileHandler(String pattern) throws IOException, SecurityException {
        super(pattern);
    }

    public AccessFileHandler(String pattern, boolean append) throws IOException, SecurityException {
        super(pattern, append);
    }

}
