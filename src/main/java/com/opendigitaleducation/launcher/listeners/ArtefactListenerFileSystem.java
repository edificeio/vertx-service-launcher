package com.opendigitaleducation.launcher.listeners;

import com.opendigitaleducation.launcher.VertxServiceLauncher;
import com.opendigitaleducation.launcher.config.ConfigChangeEvent;
import com.opendigitaleducation.launcher.config.ConfigProvider;
import com.opendigitaleducation.launcher.utils.FileUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
    private final String baseDir;
    private final String servicePath;
    private List<Function<Void, Void>> onStop = new ArrayList<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();

    public ArtefactListenerFileSystem(final ConfigProvider aConfigProvider, final JsonObject config) throws IOException {
        super(aConfigProvider);
        baseDir = config.getString("assets-path");
        watcher = FileSystems.getDefault().newWatchService();
        servicePath = FileUtils.absolutePath(System.getProperty("vertx.services.path"));
    }

    @Override
    public ArtefactListener start(final Vertx vertx, final JsonObject config) {
        try {
            final String watcherConf = config.getString(WATCHER_CONF, Paths.get(baseDir,"watcher.conf").toString());
            final String watchDirs = config.getString(WATCHER_PATH, servicePath);
            log.info("Starting watcher: " + watchDirs);
            final long delay = config.getLong(WATCHER_DELAY, 500l);
            loadWatcherList(vertx, watcherConf).onComplete(resList -> {
                final long timer = vertx.setPeriodic(delay, timerId -> {
                    try {
                        final List<Path> paths = processEvents();
                        for (final Path path : paths) {
                            final String name = path.getFileName().toString();
                            if (watcherConf.endsWith(name)) {
                                loadWatcherList(vertx, watcherConf);
                            } else {
                                for (final Map.Entry<String, JsonObject> entry : names.entrySet()) {
                                    final String serviceName = entry.getValue().getString("name", "");
                                    if (name.contains(serviceName)) {
                                        if (recentlyTriggered.contains(serviceName)) {
                                            log.info("SKIP redeploy for module (" + name + "): already triggered");
                                            continue;
                                        }
                                        recentlyTriggered.add(serviceName);
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
                    //clear recently
                    recentlyTriggered.clear();
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

    private Future<Void> loadWatcherList(final Vertx vertx, final String config) {
        final Promise<Void> promise = Promise.promise();
        vertx.fileSystem().readFile(config, res -> {
            if (res.succeeded()) {
                try {
                    clearWatchList();
                    final String[] lines = res.result().toString().split("\n");
                    //listen watcher file
                    final WatchEvent.Kind<Path>[] kinds = Arrays.asList(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY).toArray(new WatchEvent.Kind[3]);
                    {
                        final Path dirPath = Paths.get(config).getParent();
                        final WatchKey dirKey = dirPath.register(watcher, kinds);
                        log.info("Watcher registered for: " + dirPath);
                        watchKeys.put(dirKey, dirPath);
                    }
                    //listen mods dir
                    {
                        final Path dirPath = Paths.get(servicePath);
                        final WatchKey dirKeys = dirPath.register(watcher, kinds);
                        log.info("Watcher registered for: " + dirPath);
                        watchKeys.put(dirKeys, dirPath);
                    }
                    //listen listed dir
                    for (final String lin : lines) {
                        final String[] parts = lin.split(":");
                        final Path destPath = Paths.get(baseDir, parts[1]);
                        if(Files.isDirectory(destPath)){
                            final WatchKey dirKey = destPath.register(watcher, kinds);
                            log.info("Watcher registered for: " + destPath);
                            watchKeys.put(dirKey, destPath);
                        }else{
                            if(Paths.get(servicePath).equals(destPath.getParent())){
                                log.info("Watcher registered for mods: " + destPath);
                            }else{
                                log.info("Watcher registering recursively for: " + destPath.getParent());
                                final Map<WatchKey, Path> dirKeys = FileUtils.registerRecursive(watcher,destPath.getParent(), kinds);
                                log.info("Watcher registered for: " + destPath.getParent());
                                watchKeys.putAll(dirKeys);
                            }
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
            final Path dir = watchKeys.get(key);
            log.info("Events received, start processing... (dir: " + dir + ")");
            if (dir == null) {
                log.warn("watchKey not recognized! (" + key + ")");
                continue;
            }
            for (final WatchEvent<?> event : key.pollEvents()) {
                final WatchEvent<Path> watchEvent = (WatchEvent<Path>) event;
                log.info("Processing changes for: " + watchEvent.context());
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
