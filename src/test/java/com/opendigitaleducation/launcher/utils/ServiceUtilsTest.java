package com.opendigitaleducation.launcher.utils;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(VertxUnitRunner.class)
public class ServiceUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Vertx vertx;
    private String servicesPath;

    @Before
    public void setUp() throws IOException {
        vertx = Vertx.vertx();
        servicesPath = tempFolder.newFolder("services").getAbsolutePath();
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testGetServicePathFromIdentifier_withFullIdentifier(TestContext context) {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service~1.0.0";

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertSuccess(result -> {
                // Then
                final String expected = servicesPath + File.separator + identifier + File.separator;
                context.assertEquals(expected, result);
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withPartialIdentifier_success(TestContext context) throws IOException {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service";

        // Create directories with versions
        new File(servicesPath, "com.opendigitaleducation~my-service~1.0.0").mkdirs();
        new File(servicesPath, "com.opendigitaleducation~my-service~1.2.0").mkdirs();
        new File(servicesPath, "com.opendigitaleducation~my-service~1.1.0").mkdirs();

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertSuccess(result -> {
                // Then - should return one of the versions (latest based on string comparison)
                context.assertTrue(result.contains("com.opendigitaleducation~my-service~1.2.0"), "Was expectging 1.2.0 but got " + result);
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withPartialIdentifier_noMatchingDirectory(TestContext context) {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~non-existent";

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertFailure(error -> {
                // Then
                context.assertTrue(error.getMessage().contains("cannot find a directory"));
                context.assertTrue(error.getMessage().contains(identifier));
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withPartialIdentifier_onlyFiles(TestContext context) throws IOException {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service";

        // Create a file instead of directory (should be ignored)
        new File(servicesPath, "com.opendigitaleducation~my-service~1.0.0").createNewFile();

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertFailure(error -> {
                // Then
                context.assertTrue(error.getMessage().contains("cannot find a directory"));
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withInvalidIdentifier_oneSegment(TestContext context) {
        // Given
        final Async async = context.async();
        final String identifier = "invalid-identifier";

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertFailure(error -> {
                // Then
                context.assertTrue(error.getMessage().contains("Invalid artifact id"));
                context.assertTrue(error.getMessage().contains("groupId~artifactId~version"));
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withInvalidIdentifier_fourSegments(TestContext context) {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service~1.0.0~extra";

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertFailure(error -> {
                // Then
                context.assertTrue(error.getMessage().contains("Invalid artifact id"));
                async.complete();
            }));
    }

    @Test
    public void testGetLatestVersions_withMultipleVersions() {
        // Given
        final List<String> versions = Arrays.asList(
            "com.opendigitaleducation~my-service~1.0.0",
            "com.opendigitaleducation~my-service~0.9.0",
            "com.opendigitaleducation~my-service~1.5.0"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        assertEquals("com.opendigitaleducation~my-service~1.5.0", result.get());
    }

    @Test
    public void testGetLatestVersions_withSnapshots() {
        // Given
        final List<String> versions = Arrays.asList(
            "com.opendigitaleducation~my-service~1.0-SNAPSHOT",
            "com.opendigitaleducation~my-service~0.9.0",
            "com.opendigitaleducation~my-service~1.1.0"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        assertEquals("com.opendigitaleducation~my-service~1.1.0", result.get());
    }

    @Test
    public void testGetLatestVersions_withOnlySnapshots() {
        // Given
        final List<String> versions = Arrays.asList(
            "com.opendigitaleducation~my-service~1.0-SNAPSHOT",
            "com.opendigitaleducation~my-service~1.0.3",
            "com.opendigitaleducation~my-service~1.1-SNAPSHOT"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        assertEquals("com.opendigitaleducation~my-service~1.1-SNAPSHOT", result.get());
    }

    @Test
    public void testGetLatestVersions_withSingleVersion() {
        // Given
        final List<String> versions = Collections.singletonList(
            "com.opendigitaleducation~my-service~1.0.0"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        assertEquals("com.opendigitaleducation~my-service~1.0.0", result.get());
    }

    @Test
    public void testGetLatestVersions_withEmptyList() {
        // Given
        final List<String> versions = Collections.emptyList();

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetLatestVersions_withVersionsLessThanThreeSegments() {
        // Given - directories without version part (less than 3 segments)
        final List<String> versions = Arrays.asList(
            "com.opendigitaleducation~another-service",
            "com.opendigitaleducation~another-service~1.2-SNAPSHOT"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        // The first one will have "0" as version, second will have "1.0.0"
        assertEquals("com.opendigitaleducation~another-service~1.2-SNAPSHOT", result.get());
    }

    @Test
    public void testGetLatestVersions_withAlphabeticVersionComparison() {
        // Given - versions that demonstrate string comparison behavior
        final List<String> versions = Arrays.asList(
            "com.opendigitaleducation~my-service~10.0.0",
            "com.opendigitaleducation~my-service~2.0.0",
            "com.opendigitaleducation~my-service~1.0.0"
        );

        // When
        final Optional<String> result = ServiceUtils.getLatestVersions(versions);

        // Then
        assertTrue(result.isPresent());
        // String comparison: "1.0.0" < "10.0.0" < "2.0.0"
        assertEquals("com.opendigitaleducation~my-service~10.0.0", result.get());
    }

    @Test
    public void testGetServicePathFromIdentifier_withPartialIdentifier_multipleVersions(TestContext context) throws IOException {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service";

        // Create multiple version directories
        new File(servicesPath, "com.opendigitaleducation~my-service~0.9.0").mkdirs();
        new File(servicesPath, "com.opendigitaleducation~my-service~1.0.0").mkdirs();
        new File(servicesPath, "com.opendigitaleducation~my-service~1.1.0").mkdirs();

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertSuccess(result -> {
                // Then - should return the first one after sorting (alphabetically first)
                context.assertTrue(result.contains("com.opendigitaleducation~my-service~1.1.0"));
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withPartialIdentifier_mixedFilesAndDirectories(TestContext context) throws IOException {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service";

        // Create files (should be ignored)
        new File(servicesPath, "com.opendigitaleducation~my-service~0.5.0").createNewFile();
        // Create directories (should be found)
        new File(servicesPath, "com.opendigitaleducation~my-service~1.0.0").mkdirs();

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, servicesPath, vertx)
            .onComplete(context.asyncAssertSuccess(result -> {
                // Then - should only consider the directory
                context.assertTrue(result.contains("com.opendigitaleducation~my-service~1.0.0"));
                async.complete();
            }));
    }

    @Test
    public void testGetServicePathFromIdentifier_withNonExistentServicesPath(TestContext context) {
        // Given
        final Async async = context.async();
        final String identifier = "com.opendigitaleducation~my-service";
        final String nonExistentPath = servicesPath + File.separator + "non-existent";

        // When
        ServiceUtils.getServicePathFromIdentifier(identifier, nonExistentPath, vertx)
            .onComplete(context.asyncAssertFailure(error -> {
                // Then - should fail when trying to read the directory
                async.complete();
            }));
    }
}
