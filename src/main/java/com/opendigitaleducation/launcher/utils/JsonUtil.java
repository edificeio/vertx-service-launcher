package com.opendigitaleducation.launcher.utils;

import io.vertx.core.json.JsonObject;

import java.util.Scanner;

public class JsonUtil {

    public static JsonObject loadFromResource(String resource) {
        String src = new Scanner(JsonUtil.class.getClassLoader()
            .getResourceAsStream(resource), "UTF-8")
            .useDelimiter("\\A").next();
        return new JsonObject(src);
    }

}
