package com.paipi.config;

import java.util.Map;

public class DataSourceConfig {
    private String name; // 数据源类型
    private Map<String, Object> parameter; // 参数map，兼容所有数据源

    // getter/setter
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