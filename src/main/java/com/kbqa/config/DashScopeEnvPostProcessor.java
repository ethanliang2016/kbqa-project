package com.kbqa.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DashScopeEnvPostProcessor implements EnvironmentPostProcessor {

    private static final String API_KEY = "spring.ai.dashscope.api-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = environment.getProperty(API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put(API_KEY, apiKey);
        props.put("spring.ai.dashscope.chat.api-key", apiKey);
        props.put("spring.ai.dashscope.embedding.api-key", apiKey);

        environment.getPropertySources().addFirst(new MapPropertySource("dashscope-api-key-override", props));
    }
}
