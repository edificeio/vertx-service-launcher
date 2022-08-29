package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.VertxServiceLauncher;
import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.utils.FileUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public class ArtefactListenerFileSystem extends ArtefactListenerAbstract<ConfigChangeEvent> {
    public static final String WATCHER_CONF = "watcherConf";
    public static final String WATCHER_ENABLE = "watcherEnabled";
    public static final String WATCHER_PATH = "watcherPath";
    public static final String WATCHER_DELAY = "watcherDelayMillis";
    private static final Logger log = LoggerFactory.getLogger(ArtefactListenerFileSystem.class);
    private final WatchService watcher;
    private final String assetPath;
    private final String servicePath;
    private List<Function<Void, Void>> onStop = new ArrayList<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();

    public ArtefactListenerFileSystem(final ConfigProvider aConfigProvider, final JsonObject config) throws IOException {
        super(aConfigProvider);
        assetPath = config.getString("assets-path");
        watcher = FileSystems.getDefault().newWatchService();
        servicePath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
    }

    @Override
    public ArtefactListener start(final Vertx vertx, final JsonObject config) {
        try {
            final String watcherConf = config.getString(WATCHER_CONF, Paths.get(assetPath,"watcher.conf").toString());
            final String watchDirs = config.getString(WATCHER_PATH, "mods");
            log.info("Starting watcher: " + watchDirs);
            final long delay = config.getLong(WATCHER_DELAY, 500l);
            loadWatcherList(vertx, watchDirs, watcherConf).onComplete(resList -> {
                final long timer = vertx.setPeriodic(delay, timerId -> {
                    try {
                        final List<Path> paths = processEvents();
                        for (final Path path : paths) {
                            final String name = path.getFileName().toString();
                            if (name.equalsIgnoreCase(watcherConf)) {
                                loadWatcherList(vertx, watchDirs, watcherConf);
                            } else {
                                if (recentlyTriggered.contains(name)) {
                                    log.info("SKIP redeploy for module (" + name + "): already deployed");
                                    return;
                                }
                                for (final Map.Entry<String, JsonObject> entry : names.entrySet()) {
                                    if (name.contains(entry.getKey())) {
                                        final JsonObject value = entry.getValue();
                                        //nexus need some delay to return the last artefact when deploy
                                        log.info("TRIGGER redeploy for module (" + name + ")");
                                        //if watching directory => dont clean else if watching archive => clean dir
                                        final VertxServiceLauncher.Clean clean = Files.isDirectory(path) ? VertxServiceLauncher.Clean.None : VertxServiceLauncher.Clean.Dir;
                                        pushEvent(new ArtefactListenerNexus.ConfigChangeEventNexus(value).setCleanType(clean));
                                    }
                                }
                            }
                        }
                    } catch (final Exception exc) {
                        log.error("Error occured while processing events: ", exc);
                    }
                });
                onStop.add(e -> {
                    vertx.cancelTimer(timer);
                    return null;
                });
            });
            log.info("Watcher started successfully");
            return this;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ArtefactListener stop() {
        try {
            log.info("Stopping watcher...");
            clearWatchList();
            for (final Function<Void, Void> f : onStop) {
                f.apply(null);
            }
            watcher.close();
        } catch (Exception e) {
            log.error("Failed stopping watcher watcher: ", e);
            throw new RuntimeException(e);
        }
        return this;
    }

    private void clearWatchList() {
        for (final WatchKey key : watchKeys.keySet()) {
            key.cancel();
        }
        watchKeys.clear();
    }

    private Future<Void> loadWatcherList(final Vertx vertx, final String watchDir, final String config) {
        final Promise<Void> promise = Promise.promise();
        vertx.fileSystem().readFile(config, res -> {
            if (res.succeeded()) {
                try {
                    clearWatchList();
                    final String[] lines = res.result().toString().split("\n");
                    //listen watcher file
                    {
                        final Path dirPath = Paths.get(config).getParent();
                        final WatchKey dirKey = dirPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                        log.info("Watcher registered for: " + dirPath);
                        watchKeys.put(dirKey, dirPath);
                    }
                    //listen mods dir
                    {
                        final Path dirPath = Paths.get(servicePath);
                        final WatchKey dirKey = dirPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                        log.info("Watcher registered for: " + dirPath);
                        watchKeys.put(dirKey, dirPath);
                    }
                    //listen listed dir
                    for (final String line : lines) {
                        final Path dirPath = Paths.get(watchDir, line);
                        if(Files.isDirectory(dirPath)){
                            final WatchKey dirKey = dirPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                            log.info("Watcher registered for: " + dirPath);
                            watchKeys.put(dirKey, dirPath);
                        }else{
                            final WatchKey dirKey = dirPath.getParent().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                            log.info("Watcher registered for: " + dirPath.getParent());
                            watchKeys.put(dirKey, dirPath.getParent());
                        }
                    }
                    promise.complete(null);
                } catch (Exception e) {
                    log.error("Failed to load watcher: ", e);
                    promise.fail(e);
                }
            } else {
                log.error("Failed to load watcher config: ", res.cause());
                promise.fail(res.cause());
            }
        });
        return promise.future();
    }

    private List<Path> processEvents() {
        WatchKey key;
        final List<Path> paths = new ArrayList<>();
        while ((key = watcher.poll()) != null) {
            // watch key is null if no queued key is available
            if (key == null) break;

            log.info("Events received, start processing... (key: " + key + ")");
            final Path dir = watchKeys.get(key);
            if (dir == null) {
                log.warn("watchKey not recognized! (" + key + ")");
                continue;
            }
            for (final WatchEvent<?> event : key.pollEvents()) {
                log.info("Processing changes for: " + dir);
                final WatchEvent<Path> watchEvent = (WatchEvent<Path>) event;
                final WatchEvent.Kind<Path> kind = watchEvent.kind();
                if (kind.name().equals(StandardWatchEventKinds.OVERFLOW.name())) {
                    continue;
                }
                //The filename is the context of the event.
                final Path filename = watchEvent.context();
                paths.add(filename);
            }
            //Reset the key -- this step is critical if you want to receive
            //further watch events. If the key is no longer valid, the directory
            //is inaccessible so exit the loop.
            boolean valid = key.reset();
            if (!valid) {
                log.warn("watchKey invalidated, removing from the list (" + key + ")");
                watchKeys.remove(key);
                // Exit if no keys remain
                if (watchKeys.isEmpty()) {
                    break;
                }
            }
        }
        return paths;
    }
}
