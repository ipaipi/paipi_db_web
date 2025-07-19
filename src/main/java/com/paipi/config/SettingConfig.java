package com.paipi.config;

import java.util.Map;

public class SettingConfig {
    private Map<String, Object> speed;
    private String reportPath;
    private Boolean enablePreCheck;
    private Boolean enablePostCheck;
    private Map<String, Object> channel;

    public Map<String, Object> getSpeed() {
        return speed;
    }

    public void setSpeed(Map<String, Object> speed) {
        this.speed = speed;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public Boolean getEnablePreCheck() {
        return enablePreCheck;
    }

    public void setEnablePreCheck(Boolean enablePreCheck) {
        this.enablePreCheck = enablePreCheck;
    }

    public Boolean getEnablePostCheck() {
        return enablePostCheck;
    }

    public void setEnablePostCheck(Boolean enablePostCheck) {
        this.enablePostCheck = enablePostCheck;
    }

    public Map<String, Object> getChannel() {
        return channel;
    }

    public void setChannel(Map<String, Object> channel) {
        this.channel = channel;
    }
} 