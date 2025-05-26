package com.opendigitaleducation.launcher.discovery;

import java.util.List;
import java.util.Map;

import com.opendigitaleducation.launcher.discovery.impl.NopServiceDiscovery;
import com.opendigitaleducation.launcher.discovery.impl.TraefikServiceDiscovery;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface ServiceDiscovery {

    static ServiceDiscovery create(final Vertx vertx) {
        return vertx.isClustered() ? new TraefikServiceDiscovery(vertx) : new NopServiceDiscovery();
    }

    Future<ServiceInfo> serviceRegistration(String moduleName, JsonObject config);

    Future<List<ServiceInfo>> getServiceInfos(String service);

    Future<Map<String, List<ServiceInfo>>> getServicesInfos(List<String> services);

}
