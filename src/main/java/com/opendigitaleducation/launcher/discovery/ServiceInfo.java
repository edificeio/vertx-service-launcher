package com.opendigitaleducation.launcher.discovery;

import static java.lang.String.format;

import java.io.Serializable;

import io.vertx.core.json.JsonObject;

public class ServiceInfo implements Serializable {

    private final String nodeId;
    private final String name;
    private final String router;
    private final String ip;
    private final String pathPrefix;
    private final Integer port;
    private final String url;
    private final boolean httpService;

    public ServiceInfo(String moduleName, String router, String ip, String nodeId, JsonObject config) {
        name = getServiceName(moduleName);
        this.router = router;
        this.ip = ip;
        this.nodeId = nodeId;
        if (config != null && config.getInteger("port") != null) {
            this.pathPrefix = getPathPrefix(config);
            this.port = config.getInteger("port");
            this.url = getInstanceUrl(false, ip, port, pathPrefix);
            this.httpService = true;
        } else {
            this.pathPrefix = null;
            this.port = null;
            this.url = null;
            this.httpService = false;
        }
    }

    public static String getInstanceUrl(boolean tls, String ip, int port, String pathPrefix) {
        return format("http%s://%s:%d%s", tls ? "s":"", ip, port, pathPrefix);
    }

    public static String getServiceName(String moduleName) {
        final String[] lNameVersion = moduleName.split("~");
        return lNameVersion.length == 3 ? lNameVersion[0] + "." + lNameVersion[1] : moduleName;
    }

    public static String getPathPrefix(JsonObject config) {
		String path = config.getString("path-prefix");
		if (path == null) {
			final String verticle = config.getString("main");
			if (verticle != null && !verticle.trim().isEmpty() && verticle.contains(".")) {
				path = verticle.substring(verticle.lastIndexOf('.') + 1).toLowerCase();
			}
		}
		if ("".equals(path) || "/".equals(path)) {
			return "";
		}
		return "/" + path;
	}

    public String getName() {
        return name;
    }

    public String getRouter() {
        return router;
    }

    public String getIp() {
        return ip;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public Integer getPort() {
        return port;
    }

    public String getUrl() {
        return url;
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isHttpService() {
        return httpService;
    }

}
