package com.paipi.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobConfig {
    private String sqlMode; // 模式
    private SettingConfig setting;
    private List<ContentConfig> content;

    // getter/setter
    public String getSqlMode() {
        return sqlMode;
    }

    public void setSqlMode(String sqlMode) {
        this.sqlMode = sqlMode;
    }

    public SettingConfig getSetting() {
        return setting;
    }

    public void setSetting(SettingConfig setting) {
        this.setting = setting;
    }

    public List<ContentConfig> getContent() {
        return content;
    }

    public void setContent(List<ContentConfig> content) {
        this.content = content;
    }

    // 内部类ContentConfig，包含common/reader/writer/transformer
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ContentConfig {
        private DataSourceConfig common;
        private DataSourceConfig reader;
        private DataSourceConfig writer;
        @JsonProperty("transformer")
        private List<TransformerConfig> transformer;

        // getter/setter
        public DataSourceConfig getCommon() {
            return common;
        }

        public void setCommon(DataSourceConfig common) {
            this.common = common;
        }

        public DataSourceConfig getReader() {
            return reader;
        }

        public void setReader(DataSourceConfig reader) {
            this.reader = reader;
        }

        public DataSourceConfig getWriter() {
            return writer;
        }

        public void setWriter(DataSourceConfig writer) {
            this.writer = writer;
        }

        public List<TransformerConfig> getTransformer() {
            return transformer;
        }

        public void setTransformer(List<TransformerConfig> transformer) {
            this.transformer = transformer;
        }
    }
} 