package com.opendigitaleducation.launcher.discovery.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.opendigitaleducation.launcher.discovery.ServiceDiscovery;
import com.opendigitaleducation.launcher.discovery.ServiceInfo;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;

public class DefaultServiceDiscovery implements ServiceDiscovery {

    protected static final Logger log = LoggerFactory.getLogger(DefaultServiceDiscovery.class);
    private static final int RETRY_UPDATE_SERVICE = 5;

    protected final ZookeeperClusterManager zookeeperClusterManager;
    protected final Vertx vertx;

    public DefaultServiceDiscovery(Vertx vertx) {
        if (vertx.isClustered() && (((VertxInternal) vertx).getClusterManager() instanceof ZookeeperClusterManager)) {
            zookeeperClusterManager = (ZookeeperClusterManager) ((VertxInternal) vertx).getClusterManager();
        } else {
            final String message = String.format(
                "You cannot use %s when clustered mode is disable.", this.getClass().getSimpleName());
            log.error(message);
            throw new RuntimeException(message);
        }
        this.vertx = vertx;
    }

    @Override
    public Future<ServiceInfo> serviceRegistration(String name, JsonObject config) {
        final String router = zookeeperClusterManager.getConfig().getString("rootPath", "vertx");
        final NodeInfo nodeInfo = zookeeperClusterManager.getNodeInfo();
        final String ip = nodeInfo.host();
        final ServiceInfo serviceInfo = new ServiceInfo(name, router, ip, zookeeperClusterManager.getNodeId(), config);
        final Promise<Void> setNodeInfoPromise = Promise.promise();
        zookeeperClusterManager.setNodeInfo(new NodeInfo(nodeInfo.host(), nodeInfo.port(), new JsonObject()
                .put("service", serviceInfo.getName())), setNodeInfoPromise);

        return setNodeInfoPromise.future().compose(x -> vertx.sharedData().<String, List<ServiceInfo>>getAsyncMap("services"))
                .compose(services -> {
                    final List<ServiceInfo> serviceIds = new ArrayList<>();
                    serviceIds.add(serviceInfo);
                    return services.putIfAbsent(serviceInfo.getName(), serviceIds)
                        .compose(res -> {
                            if (res != null) {
                                return appendAndFilterServiceIds(services, serviceInfo, res, 0);
                            } else {
                                return Future.succeededFuture(serviceInfo);
                            }
                        });
                });
    }

    private Future<ServiceInfo> appendAndFilterServiceIds(AsyncMap<String, List<ServiceInfo>> services,
            ServiceInfo serviceInfo, List<ServiceInfo> oldList, int retry) {
        final List<ServiceInfo> newList = oldList.stream().filter(x ->
                zookeeperClusterManager.getNodes().contains(x.getNodeId())).collect(Collectors.toList());
        newList.add(serviceInfo);
        return //services.replaceIfPresent(serviceInfo.getName(), oldList, newList).compose(updated -> {
                services.put(serviceInfo.getName(), newList).compose(updated -> {
                    return Future.succeededFuture(serviceInfo);
            // if (updated || retry >= RETRY_UPDATE_SERVICE) {
            //     return Future.succeededFuture(serviceInfo);
            // } else {
            //     final int r = retry + 1;
            //     log.warn("Retry update services async map : " + r);
            //     return services.get(serviceInfo.getName()).compose(res ->
            //         appendAndFilterServiceIds(services, serviceInfo, res, r));
            // }
        });
    }

    @Override
    public Future<Map<String, List<ServiceInfo>>> getServicesInfos(List<String> services) {
        final List<Future<List<ServiceInfo>>> futures = new ArrayList<>();
        for (String service: services) {
            futures.add(getServiceInfos(service));
        }
        return Future.all(futures).compose(x -> {
            final Map<String, List<ServiceInfo>> servicesInfos = new HashMap<>();
            int i = 0;
            for (String service: services) {
                final List<ServiceInfo> s = futures.get(i++).result();
                if (!s.isEmpty()) {
                    servicesInfos.put(service, s);
                }
            }
            return Future.succeededFuture(servicesInfos);
        });
    }

    @Override
    public Future<List<ServiceInfo>> getServiceInfos(String service) {
        return vertx.sharedData().<String, List<ServiceInfo>>getAsyncMap("services")
                .compose(services -> services.get(service))
                .compose(infos -> Future.succeededFuture(
                    Optional.ofNullable(infos).orElse(Collections.emptyList()).stream()
                        .filter(x -> zookeeperClusterManager.getNodes().contains(x.getNodeId()))
                        .collect(Collectors.toList()))
                );
    }

}
