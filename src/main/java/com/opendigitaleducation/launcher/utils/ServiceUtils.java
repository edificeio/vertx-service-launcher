package com.opendigitaleducation.launcher.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

public class ServiceUtils {

    /**
     * Returns the absolute path of the module to load given its identifier and a root dir where all the services are
     * located.
     * @param identifier Identifier of the module to load (in the form of groupId~artifactId~version
     * @param servicesPath Root dir where all the services are
     * @param vertx Vertx instance
     * @return The absolute path of the service root dir
     */
    public static Future<String> getServicePathFromIdentifier(final String identifier, final String servicesPath, final Vertx vertx) {
        String[] artifact = identifier.split("~");
        if(artifact.length == 3) {
            return succeededFuture(servicesPath + File.separator +
                identifier + File.separator);
        } else if (artifact.length == 2){
            // If the version was not specified then a directory must be present on the
            // filesystem with a version already specified
            return getChildrenDirectoryWithNameWithPrefix(servicesPath, identifier, vertx)
                .map(ServiceUtils::getLatestVersions)
                .flatMap(maybeLatestVersion ->
                    maybeLatestVersion.map(Future::succeededFuture).orElseGet(() -> failedFuture("cannot find a directory in " + servicesPath + " for " + identifier))
                )
                .map(latestVersion -> latestVersion);
        } else {
            return failedFuture("Invalid artifact id, it should be in the form groupId~artifactId~version: " + identifier);
        }
    }

    /**
     * Gets the latest version among a list of identifiers of the same artifact.
     * Examples :
     * - group~artifact~1.2.0, group~artifact~1.3.0 => group~artifact~1.3.0
     * - group~artifact~1.2.0, group~artifact~1.2-SNAPSHOT => group~artifact~1.2.0
     * - group~artifact~1.3-SNAPSHOT, group~artifact~1.2.4 => group~artifact~1.3-SNAPSHOT
     * - group~artifact~1.3-SNAPSHOT, group~artifact~1.2-SNAPSHOT => group~artifact~1.3-SNAPSHOT
     * @param versions List of versions
     * @return The latest version
     */
    public static Optional<String> getLatestVersions(final List<String> versions) {
        return versions.stream()
            .map(v -> Pair.of(v, Paths.get(v).getFileName().toString().split("~")))
            .map(v -> Pair.of(v.getKey(), Arrays.stream(v.getValue()).map(p -> p.replaceAll("-.+", "")).collect(toList())))
            .map(v ->Pair.of(v.getKey(), v.getValue().size() >= 3 ? v.getValue().get(2) : "0"))
            .sorted((v1, v2) -> compareSemVerVersion(v2.getValue(), v1.getValue()))
            .map(Pair::getKey)
            .findFirst();
    }

    private static int compareSemVerVersion(final String value1, final String value2) {
        final String[] parts1 = value1.split("\\.");
        final String[] parts2 = value2.split("\\.");
        for(int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
            final String part1 = parts1[i];
            final String part2 = parts2[i];
            if (!part1.equals(part2)) {
                int intPart1;
                try {
                    intPart1 = Integer.parseInt(part1);
                } catch (NumberFormatException e) {
                    return -1;
                }
                int intPart2;
                try {
                    intPart2 = Integer.parseInt(part2);
                } catch (NumberFormatException e) {
                    return 1;
                }
                return intPart1 - intPart2;
            }
        }
        return 0;
    }

    private static Future<List<String>> getChildrenDirectoryWithNameWithPrefix(final String rootDirectory,
                                                                               final String directoryPrefix,
                                                                               final Vertx vertx) {
        final FileSystem fs = vertx.fileSystem();
        return fs.readDir(rootDirectory)
            .flatMap(childrenOfRoot -> {
                final List<Future<Optional<String>>> listOfDirectories = childrenOfRoot.stream()
                    .filter(childName -> Paths.get(childName).getFileName().toString().startsWith(directoryPrefix))
                    .map(childName -> fs.props(childName).map(p -> p.isDirectory() ? of(childName) : Optional.<String>empty()))
                    .collect(toList());
                return Future.all(listOfDirectories).map(f -> {
                    final List<Optional<String>> props = f.result().list();
                    return props.stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toList());
                });
            });
    }
}
