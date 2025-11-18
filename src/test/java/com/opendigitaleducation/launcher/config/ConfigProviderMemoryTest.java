package com.opendigitaleducation.launcher.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigProviderMemoryTest {

    @Test
    public void testDispatchSharedConfiguration_withValidSharedConf() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("foo", "foo-value")
                .put("bar", new JsonObject()
                    .put("innerProp", "inner Prop value")
                    .put("innerObject", new JsonObject()
                        .put("here", "there"))))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject()
                        .put("someProp", "some Value"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("some Value", serviceConfig.getString("someProp"));
        assertEquals("foo-value", serviceConfig.getString("foo"));
        assertNotNull(serviceConfig.getJsonObject("bar"));
        assertEquals("inner Prop value", serviceConfig.getJsonObject("bar").getString("innerProp"));
    }

    @Test
    public void testDispatchSharedConfiguration_doesNotOverrideExistingKeys() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("foo", "foo-value")
                .put("bar", new JsonObject()
                    .put("innerProp", "inner Prop value")
                    .put("innerObject", new JsonObject()
                        .put("here", "there"))))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "other-service")
                    .put("config", new JsonObject()
                        .put("thing", "some gniht")
                        .put("bar", new JsonObject()
                            .put("innerObject", new JsonObject()
                                .put("here", "there"))))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("some gniht", serviceConfig.getString("thing"));
        assertEquals("foo-value", serviceConfig.getString("foo"));
        // bar should not be overridden - service already has it
        assertNotNull(serviceConfig.getJsonObject("bar"));
        assertNull(serviceConfig.getJsonObject("bar").getString("innerProp"));
        assertEquals("there", serviceConfig.getJsonObject("bar").getJsonObject("innerObject").getString("here"));
    }

    @Test
    public void testDispatchSharedConfiguration_withNoSharedConf() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject()
                        .put("someProp", "some Value"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then - service config should remain unchanged
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("some Value", serviceConfig.getString("someProp"));
        assertEquals(1, serviceConfig.size());
    }

    @Test
    public void testDispatchSharedConfiguration_withEmptySharedConf() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject())
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject()
                        .put("someProp", "some Value"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then - service config should remain unchanged
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("some Value", serviceConfig.getString("someProp"));
        assertEquals(1, serviceConfig.size());
    }

    @Test
    public void testDispatchSharedConfiguration_withMultipleServices() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("shared1", "value1")
                .put("shared2", "value2"))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "service1")
                    .put("config", new JsonObject()
                        .put("prop1", "val1")))
                .add(new JsonObject()
                    .put("name", "service2")
                    .put("config", new JsonObject()
                        .put("prop2", "val2")))
                .add(new JsonObject()
                    .put("name", "service3")
                    .put("config", new JsonObject()
                        .put("shared1", "override"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject service1Config = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        JsonObject service2Config = globalConfig.getJsonArray("services")
            .getJsonObject(1)
            .getJsonObject("config");
        JsonObject service3Config = globalConfig.getJsonArray("services")
            .getJsonObject(2)
            .getJsonObject("config");
        
        assertEquals("value1", service1Config.getString("shared1"));
        assertEquals("value2", service1Config.getString("shared2"));
        
        assertEquals("value1", service2Config.getString("shared1"));
        assertEquals("value2", service2Config.getString("shared2"));
        
        // service3 already has shared1, so it shouldn't be overridden
        assertEquals("override", service3Config.getString("shared1"));
        assertEquals("value2", service3Config.getString("shared2"));
    }

    @Test
    public void testDispatchSharedConfiguration_withEmptyServices() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("foo", "bar"))
            .put("services", new JsonArray());

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then - should not throw exception
        assertTrue(globalConfig.getJsonArray("services").isEmpty());
    }

    @Test
    public void testDispatchSharedConfiguration_withComplexNestedObjects() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("database", new JsonObject()
                    .put("host", "localhost")
                    .put("port", 5432)
                    .put("credentials", new JsonObject()
                        .put("user", "admin")
                        .put("password", "secret"))))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject()
                        .put("appName", "MyApp"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("MyApp", serviceConfig.getString("appName"));
        assertNotNull(serviceConfig.getJsonObject("database"));
        assertEquals("localhost", serviceConfig.getJsonObject("database").getString("host"));
        assertEquals(5432, (int) serviceConfig.getJsonObject("database").getInteger("port"));
        assertNotNull(serviceConfig.getJsonObject("database").getJsonObject("credentials"));
    }

    @Test
    public void testDispatchSharedConfiguration_withArrayValues() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("allowedHosts", new JsonArray()
                    .add("host1")
                    .add("host2")
                    .add("host3")))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject()
                        .put("serviceProp", "value"))));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertNotNull(serviceConfig.getJsonArray("allowedHosts"));
        assertEquals(3, serviceConfig.getJsonArray("allowedHosts").size());
        assertEquals("host1", serviceConfig.getJsonArray("allowedHosts").getString(0));
    }

    @Test
    public void testDispatchSharedConfiguration_withPrimitiveTypes() {
        // Given
        JsonObject globalConfig = new JsonObject()
            .put("sharedConf", new JsonObject()
                .put("stringValue", "text")
                .put("intValue", 42)
                .put("boolValue", true)
                .put("doubleValue", 3.14)
                .put("nullValue", (String) null))
            .put("services", new JsonArray()
                .add(new JsonObject()
                    .put("name", "my-service")
                    .put("config", new JsonObject())));

        // When
        ConfigProviderMemory.dispatchSharedConfiguration(globalConfig);

        // Then
        JsonObject serviceConfig = globalConfig.getJsonArray("services")
            .getJsonObject(0)
            .getJsonObject("config");
        
        assertEquals("text", serviceConfig.getString("stringValue"));
        assertEquals(42, (int) serviceConfig.getInteger("intValue"));
        assertEquals(true, serviceConfig.getBoolean("boolValue"));
        assertEquals(3.14, serviceConfig.getDouble("doubleValue"), 0.001);
        assertTrue(serviceConfig.containsKey("nullValue"));
        assertNull(serviceConfig.getValue("nullValue"));
    }

    @Test
    public void testGetServiceNameWithoutVersion_withVersionSpecified() {
        // Given
        String serviceName = "io.vertx~mod-mongo-persistor~4.1-zookeeper-SNAPSHOT";

        // When
        String result = ConfigProviderMemory.getServiceNameWithoutVersion(serviceName);

        // Then
        assertEquals("io.vertx~mod-mongo-persistor", result);
    }

    @Test
    public void testGetServiceNameWithoutVersion_withoutVersionSpecified() {
        // Given
        String serviceName = "org.entcore~app-registry";

        // When
        String result = ConfigProviderMemory.getServiceNameWithoutVersion(serviceName);

        // Then
        assertEquals("org.entcore~app-registry", result);
    }

    @Test
    public void testGetServiceNameWithoutVersion_withSingleTilde() {
        // Given
        String serviceName = "simple~service";

        // When
        String result = ConfigProviderMemory.getServiceNameWithoutVersion(serviceName);

        // Then
        assertEquals("simple~service", result);
    }

    @Test
    public void testGetServiceNameWithoutVersion_withNoTilde() {
        // Given
        String serviceName = "simpleservice";

        // When
        String result = ConfigProviderMemory.getServiceNameWithoutVersion(serviceName);

        // Then
        assertEquals("simpleservice", result);
    }

}
