package com.opendigitaleducation.launcher;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies that a module whose main verticle class lives ONLY inside the module directory (i.e. not on the
 * launcher/app classpath) deploys through {@link FolderServiceFactory}. This proves two things at once on the
 * running JDK:
 * <ul>
 *   <li>no {@code IllegalStateException: Current classloader must be URLClassLoader} is thrown
 *       (the Java 8 isolation-group path is no longer used), and</li>
 *   <li>the module main verticle is actually loaded and started via the module's own URLClassLoader
 *       (the marker file it writes on start only exists if it ran).</li>
 * </ul>
 *
 * <p>The services path is set in {@link BeforeClass} so it is in place before {@link RunTestOnContext} creates
 * the Vertx instance that auto-registers the SPI {@link FolderServiceFactory}.
 */
@RunWith(VertxUnitRunner.class)
public class FolderServiceFactoryTest {

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    private static final String IDENTIFIER = "test~mod-isolated~1.0";

    private static Path servicesDir;
    private static Path moduleDir;

    private Path markerFile;

    @BeforeClass
    public static void setUpModule() throws Exception {
        servicesDir = Files.createTempDirectory("vsl-services");
        moduleDir = servicesDir.resolve(IDENTIFIER);
        Files.createDirectories(moduleDir.resolve("META-INF"));

        // Service descriptor + manifest, exactly as a real module fat jar exposes them.
        Files.write(moduleDir.resolve("META-INF").resolve("MANIFEST.MF"),
            "Manifest-Version: 1.0\nMain-Verticle: service:mod\n".getBytes(StandardCharsets.UTF_8));
        Files.write(moduleDir.resolve("mod.json"),
            new JsonObject().put("main", "isolated.IsolatedTestVerticle").encode().getBytes(StandardCharsets.UTF_8));

        compileIsolatedVerticleInto(moduleDir);

        // Must be set before the Vertx instance (and its SPI-registered FolderServiceFactory) is created.
        System.setProperty(FolderServiceFactory.SERVICES_PATH, servicesDir.toString());
    }

    @Before
    public void freshMarker() throws Exception {
        markerFile = Files.createTempFile("vsl-marker", ".flag");
        Files.delete(markerFile); // must be (re)created by the deployed verticle
    }

    /** Compiles a verticle into {@code moduleDir/isolated/IsolatedTestVerticle.class} and nowhere else. */
    private static void compileIsolatedVerticleInto(Path moduleDir) throws Exception {
        Path srcDir = Files.createTempDirectory("vsl-src");
        Path pkg = srcDir.resolve("isolated");
        Files.createDirectories(pkg);
        String source =
            "package isolated;\n" +
            "import io.vertx.core.AbstractVerticle;\n" +
            "import io.vertx.core.Promise;\n" +
            "import java.nio.file.Files;\n" +
            "import java.nio.file.Paths;\n" +
            "public class IsolatedTestVerticle extends AbstractVerticle {\n" +
            "  public void start(Promise<Void> startPromise) throws Exception {\n" +
            "    Files.write(Paths.get(config().getString(\"marker\")), \"ok\".getBytes());\n" +
            "    startPromise.complete();\n" +
            "  }\n" +
            "}\n";
        Files.write(pkg.resolve("IsolatedTestVerticle.java"), source.getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Test requires a JDK (javac) to run");
        }
        int rc = compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", moduleDir.toString(),
            pkg.resolve("IsolatedTestVerticle.java").toString());
        if (rc != 0) {
            throw new IllegalStateException("Failed to compile isolated test verticle");
        }
        // Guard: the class must NOT be resolvable from the app classloader, otherwise the test proves nothing.
        try {
            Class.forName("isolated.IsolatedTestVerticle");
            throw new IllegalStateException("isolated.IsolatedTestVerticle leaked onto the app classpath");
        } catch (ClassNotFoundException expected) {
            // good: only reachable via the module URLClassLoader
        }
    }

    @Test
    public void deploysModuleViaItsOwnUrlClassLoader(TestContext ctx) {
        // The context that initiates the folderService deployment must carry the "services" array,
        // mirroring the launcher's main verticle.
        final JsonObject launcherConfig = new JsonObject().put("services",
            new JsonArray().add(new JsonObject().put("name", IDENTIFIER)));

        rule.vertx().deployVerticle(new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
                final DeploymentOptions moduleDeployment = new DeploymentOptions()
                    .setConfig(new JsonObject().put("marker", markerFile.toString()));
                vertx.deployVerticle(FolderServiceFactory.FACTORY_PREFIX + ":" + IDENTIFIER, moduleDeployment)
                    .<Void>mapEmpty()
                    .onComplete(startPromise);
            }
        }, new DeploymentOptions().setConfig(launcherConfig), ctx.asyncAssertSuccess(id -> {
            ctx.assertTrue(Files.exists(markerFile),
                "module main verticle did not start (marker file missing) -> it was not loaded via its URLClassLoader");
        }));
    }
}
