package com.opendigitaleducation.launcher.utils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystemException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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

    public static Future<Void> copyIfDestNotExists(final Vertx vertx, final String source, final String dest){
        final Promise<Void> promise = Promise.promise();
        vertx.fileSystem().exists(dest, exists -> {
            if(exists.succeeded()){
                if(exists.result()){//dont copy
                    promise.complete(null);
                }else{//check if source exists
                    vertx.fileSystem().exists(source, sExists -> {
                       if(sExists.succeeded()){
                           if(sExists.result()){
                               //copy
                               vertx.fileSystem().copy(source, dest, promise);
                           }else{
                                promise.fail("source not exists");
                           }
                       } else{
                           promise.fail(sExists.cause());
                       }
                    });
                }
            }else{
                promise.fail(exists.cause());
            }
        });
        return promise.future();
    }

    public static Future<Void> moveIfSourceExists(final Vertx vertx, final String source, final String dest){
        final Promise<Void> promise = Promise.promise();
        vertx.fileSystem().exists(source, exists -> {
            if(exists.succeeded()){
                if(exists.result()){// move
                    vertx.fileSystem().move(source, dest, promise);
                }else{//dont move
                    promise.complete(null);
                }
            }else{
                promise.fail(exists.cause());
            }
        });
        return promise.future();
    }

    public static Future<List<Path>> listFilesRecursively(final Vertx vertx, final String basePath){
        final Promise<List<Path>> result = Promise.promise();
        vertx.executeBlocking(promise->{
            try {
                final List<Path> list = new ArrayList<>();
                final Path source = Paths.get(basePath);
                Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            list.add(source.relativize(file));
                            return FileVisitResult.CONTINUE;
                        }
                });
            } catch (IOException e) {
                throw new FileSystemException(e);
            }
        },result);
        return result.future();
    }

}
