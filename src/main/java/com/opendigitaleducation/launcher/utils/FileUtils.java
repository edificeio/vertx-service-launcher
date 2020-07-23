package com.opendigitaleducation.launcher.utils;

import java.io.File;

public class FileUtils {

    public static String absolutePath(String path) {
        if (path != null && !path.startsWith(File.separator)) {
            return new File(path).getAbsolutePath();
        }
        return path;
    }

    public static String pathWithExtension(String path, String ext) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1) + ext;
        } else {
            return path + ext;
        }
    }

}
