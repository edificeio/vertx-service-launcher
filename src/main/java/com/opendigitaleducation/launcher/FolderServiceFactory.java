package com.opendigitaleducation.launcher;

import io.vertx.core.*;
import io.vertx.service.ServiceVerticleFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Scanner;

public class FolderServiceFactory extends ServiceVerticleFactory {

    public static final String SERVICES_PATH = "vertx.services.path";
    private Vertx vertx;
    private String servicesPath;

    @Override
    public void init(Vertx vertx) {
        this.vertx = vertx;
        this.servicesPath = System.getProperty(SERVICES_PATH);
    }

    @Override
    public void resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Future<String> resolution) {
        String[] artifact = identifier.split("~");
        if (artifact.length != 3) {
            resolution.fail("Invalid artifact : " + identifier);
           return;
        }

        final String servicePath = servicesPath + File.separator +
            identifier.substring(prefix().length() + 1) + File.separator;
        vertx.fileSystem().readFile(servicePath + "META-INF" + File.separator + "MANIFEST.MF", ar -> {
			if (ar.succeeded()) {
                Scanner s = new Scanner(ar.result().toString());
                String id = null;
                while (s.hasNextLine()) {
                    final String line = s.nextLine();
                    if (line.contains("Main-Verticle:")) {
                        String [] item = line.split(":");
                        if (item.length == 3) {
                            id = item[2];
                            deploymentOptions.setExtraClasspath(Collections.singletonList(servicePath));
                            deploymentOptions.setIsolationGroup("__vertx_folder_" + artifact[1]);
                            try {
                                URLClassLoader urlClassLoader = new URLClassLoader(
                                    new URL[]{new URL("file://" + servicePath )}, classLoader);
                                FolderServiceFactory.super.resolve(id, deploymentOptions, urlClassLoader, resolution);
                            } catch (MalformedURLException e) {
                                resolution.fail(e);
                            }
                        } else {
                            resolution.fail("Invalid service identifier : " + line);
                        }
                        break;
                    }
                }
                s.close();
                if (id == null && !resolution.isComplete()) {
                    resolution.fail("Service not found : " + identifier);
                }
            } else {
                resolution.fail(ar.cause());
            }
		});
    }

    @Override
    public String prefix() {
        return "folderService";
    }

}
