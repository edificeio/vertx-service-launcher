package com.opendigitaleducation.launcher.discovery.impl;

import java.util.List;
import java.util.Map;

import com.opendigitaleducation.launcher.discovery.ServiceDiscovery;
import com.opendigitaleducation.launcher.discovery.ServiceInfo;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class NopServiceDiscovery implements ServiceDiscovery {

    @Override
    public Future<ServiceInfo> serviceRegistration(String serviceName, JsonObject config) {
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Map<String, List<ServiceInfo>>> getServicesInfos(List<String> services) {
        return Future.succeededFuture(null);
    }

    @Override
    public Future<List<ServiceInfo>> getServiceInfos(String service) {
        return Future.succeededFuture(null);
    }

}
