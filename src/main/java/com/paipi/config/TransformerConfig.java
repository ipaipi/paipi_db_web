package com.paipi.config;

import java.util.Map;

public class TransformerConfig {
    private String name;
    private Map<String, Object> parameter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getParameter() {
        return parameter;
    }

    public void setParameter(Map<String, Object> parameter) {
        this.parameter = parameter;
    }
} 