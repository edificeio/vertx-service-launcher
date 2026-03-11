package com.opendigitaleducation.launcher.discovery.impl;

import static java.lang.String.format;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NodeExistsException;

import com.opendigitaleducation.launcher.discovery.ServiceInfo;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class TraefikServiceDiscovery extends DefaultServiceDiscovery {

    private final List<ServiceInfo> registeredServices = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean sessionLost = false;

    public TraefikServiceDiscovery(Vertx vertx) {
        super(vertx);
        zookeeperClusterManager.getCuratorFramework().getConnectionStateListenable().addListener(
            (client, newState) -> {
                if (newState == ConnectionState.LOST) {
                    log.warn("Zookeeper session lost, ephemeral Traefik nodes will be recreated on reconnection");
                    sessionLost = true;
                } else if (newState == ConnectionState.RECONNECTED && sessionLost) {
                    log.info("Zookeeper reconnected after session loss, recreating ephemeral Traefik nodes");
                    sessionLost = false;
                    for (final ServiceInfo serviceInfo : new ArrayList<>(registeredServices)) {
                        traefikServiceRegistration(serviceInfo)
                            .onSuccess(v -> log.info("Ephemeral Traefik nodes recreated for: " + serviceInfo.getName()))
                            .onFailure(e -> log.error("Failed to recreate ephemeral Traefik nodes for: " + serviceInfo.getName(), e));
                    }
                }
            }
        );
    }

    @Override
    public Future<ServiceInfo> serviceRegistration(String name, JsonObject config) {
        return super.serviceRegistration(name, config).compose(infos -> {
            if (infos.isHttpService()) {
                registeredServices.removeIf(s -> s.getName().equals(infos.getName()));
                registeredServices.add(infos);
                return traefikServiceRegistration(infos);
            } else {
                return Future.succeededFuture(infos);
            }
        });
    }

    private Future<ServiceInfo> traefikServiceRegistration(ServiceInfo serviceInfo) {
        Promise<ServiceInfo> promise = Promise.promise();
        try {
            final CuratorFramework curatorFramework = zookeeperClusterManager.getCuratorFramework();
            final byte[] instanceUrl = serviceInfo.getUrl().getBytes(StandardCharsets.UTF_8);

            int i = 0;
            boolean instanceLbCreated = false;
            while (!instanceLbCreated) {
                try {
                    final String path = format("/traefik/http/services/%s/loadbalancer/servers/%d/url",
                        serviceInfo.getName(), i);
                    curatorFramework.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                            .forPath(path, instanceUrl);
                    instanceLbCreated = true;
                } catch (NodeExistsException e) {
                    i++;
                }
            }

            try {
                curatorFramework.create().creatingParentsIfNeeded().forPath(
                    format("/traefik/http/services/%s/loadbalancer/healthcheck/path", serviceInfo.getName()),
                    serviceInfo.getHealthcheck().getBytes(StandardCharsets.UTF_8));
            } catch (NodeExistsException e) {
                log.debug("Health check path already created", e);
            }
            try {
                final String pathPrefix = serviceInfo.getPathPrefix();
                if("/".equals(pathPrefix) || pathPrefix.isEmpty()) {
                    curatorFramework.create().creatingParentsIfNeeded().forPath(
                        format("/traefik/http/routers/%s/rule", serviceInfo.getRouter()),
                        "Path(`/`) || PathPrefix(`/`)"
                                .getBytes(StandardCharsets.UTF_8));
                } else {
                    curatorFramework.create().creatingParentsIfNeeded().forPath(
                        format("/traefik/http/routers/%s/rule", serviceInfo.getRouter()),
                        format("Path(`%s`) || PathPrefix(`%s/`)", pathPrefix, pathPrefix)
                                .getBytes(StandardCharsets.UTF_8));
                }
            } catch (NodeExistsException e) {
                log.debug("Router path already created", e);
            }
            try {
                curatorFramework.create().creatingParentsIfNeeded().forPath(
                    format("/traefik/http/routers/%s/service", serviceInfo.getRouter()),
                    serviceInfo.getName().getBytes(StandardCharsets.UTF_8));
            } catch (NodeExistsException e) {
                log.debug("Service path already created", e);
            }
            curatorFramework.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                    .forPath(format("/traefik/%s-%s", serviceInfo.getRouter(), zookeeperClusterManager.getNodeId()),
                    instanceUrl);

            // Userbook hack
            if ("org.entcore.directory".equals(serviceInfo.getName())) {
                traefikServiceRegistration(new ServiceInfo(
                    "org.entcore~userbook~version",
                    serviceInfo.getRouter().replaceAll("directory", "userbook"),
                    serviceInfo.getIp(), serviceInfo.getPort(), "/userbook",
                    serviceInfo.getNodeId(), serviceInfo.isHttpService(), serviceInfo.isSsl(), serviceInfo.getHealthcheck()));
            }
            promise.complete(serviceInfo);
        } catch (Exception e) {
            promise.fail(e);
        }
        return promise.future();
    }

}
