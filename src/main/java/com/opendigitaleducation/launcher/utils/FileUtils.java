package com.opendigitaleducation.launcher.utils;

import java.io.File;

public class FileUtils {

    public static String absolutePath(String path) {
        if (path != null && !path.startsWith(File.separator)) {
            return new File(path).getAbsolutePath();
        }
        return path;
    }

}
