package com.kubiki.daedalus.context;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class GlobalTemplateContext {
    private final Map<String, String> globals = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        globals.put(key, value);
    }

    public String get(String key) {
        return globals.get(key);
    }

    public Map<String, String> getAll() {
        return globals;
    }
}
