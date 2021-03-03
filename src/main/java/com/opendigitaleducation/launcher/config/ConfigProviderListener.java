package com.opendigitaleducation.launcher.config;

import io.vertx.core.Handler;

public interface ConfigProviderListener {

    default void beforeConfigChange(ConfigChangeEvent event){

    }

    default void afterConfigChange(ConfigChangeEvent event, boolean success){

    }
}
