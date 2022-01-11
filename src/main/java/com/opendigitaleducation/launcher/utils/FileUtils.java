package com.opendigitaleducation.launcher.utils;

import java.io.File;

public class FileUtils {
    private static final String EMPTY_STRING = "";
    private static final int NOT_FOUND = -1;
    public static final char EXTENSION_SEPARATOR = '.';

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

    public static String getName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.substring(indexOfLastSeparator(fileName) + 1);
    }

    public static int indexOfLastSeparator(final String fileName) {
        final char UNIX_NAME_SEPARATOR = '/';
        final char WINDOWS_NAME_SEPARATOR = '\\';
        if (fileName == null) {
            return -1;
        }
        final int lastUnixPos = fileName.lastIndexOf(UNIX_NAME_SEPARATOR);
        final int lastWindowsPos = fileName.lastIndexOf(WINDOWS_NAME_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    public static String getExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null) {
            return null;
        }
        final int index = indexOfExtension(fileName);
        if (index == NOT_FOUND) {
            return null;
        }
        return fileName.substring(index + 1);
    }

    public static int indexOfExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null) {
            return NOT_FOUND;
        }
        final int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        final int lastSeparator = indexOfLastSeparator(fileName);
        return lastSeparator > extensionPos ? NOT_FOUND : extensionPos;
    }

}
