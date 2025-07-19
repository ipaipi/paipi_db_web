package com.paipi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paipi.config.DataSourceConfig;
import com.paipi.config.JobConfig;
import com.paipi.config.SettingConfig;
import com.paipi.config.TransformerConfig;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class ConfigStepPaneController {
    private static final double UNIFIED_FIELD_WIDTH = 320;
    private static final String LAST_CONFIG_PATH = System.getProperty("user.home") + File.separator + "paipi_db_last_config.json";
    @FXML
    private ComboBox<String> modeComboBox;
    @FXML
    private ComboBox<String> sourceTypeComboBox;
    @FXML
    private ComboBox<String> targetTypeComboBox;
    @FXML
    private GridPane paramGrid;
    @FXML
    private VBox transformerBox;
    @FXML
    private GridPane settingGrid;
    @FXML
    private TableView<TransformerConfig> transformerPreviewTable;
    @FXML
    private TableColumn<TransformerConfig, String> colTIndex;
    @FXML
    private TableColumn<TransformerConfig, String> colTType;
    @FXML
    private TableColumn<TransformerConfig, String> colTParam;
    private ObservableList<TransformerConfig> transformerPreviewList = FXCollections.observableArrayList();
    private TabPane dbToDbTabPane; // DB_TO_DB模式下的TabPane
    private JobConfig jobConfig = new JobConfig();
    private DataSourceConfig sourceConfig = new DataSourceConfig();
    private DataSourceConfig targetConfig = new DataSourceConfig();
    private java.util.function.Consumer<JobConfig> onConfigChanged;

    private void initDefaultSetting() {
        SettingConfig setting = new SettingConfig();
        Map<String, Object> speed = new HashMap<>();
        speed.put("channel", 1);
        setting.setSpeed(speed);
        setting.setReportPath("./report.json");
        setting.setEnablePreCheck(true);
        setting.setEnablePostCheck(true);
        Map<String, Object> channel = new HashMap<>();
        channel.put("class", "memory");
        channel.put("memoryCapacity", 10000);
        setting.setChannel(channel);
        jobConfig.setSetting(setting);
    }

    @FXML
    public void initialize() {
        initDefaultSetting();
        // 初始化模式、数据源类型
        modeComboBox.getItems().addAll("DB_TO_FILE", "DB_TO_DB", "EXECUTE_SQL");
        sourceTypeComboBox.getItems().addAll("Mysql", "Postgresql", "Hive", "TxtFile", "HBase");
        targetTypeComboBox.getItems().addAll("Mysql", "Postgresql", "Hive", "TxtFile", "HBase");
        modeComboBox.setOnAction(e -> onModeChanged());
        sourceTypeComboBox.setOnAction(e -> refreshParamGrid());
        targetTypeComboBox.setOnAction(e -> refreshParamGrid());
        // 自动加载上次保存的配置
        try {
            File last = new File(LAST_CONFIG_PATH);
            if (last.exists()) {
                String json = new String(Files.readAllBytes(last.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                ObjectMapper om = new ObjectMapper();
                Map map = om.readValue(json, Map.class);
                if (map.containsKey("job")) {
                    JobConfig cfg = om.convertValue(map.get("job"), JobConfig.class);
                    this.jobConfig = cfg;
                }
            }
        } catch (Exception e) {
            // ignore, 用默认配置
        }
        onModeChanged(); // 初始化时根据默认模式设置可见性
        refreshSettingGrid();
        initTransformerPreviewTable();
        triggerConfigChanged(); // 初始化时刷新主界面json区
    }

    private void onModeChanged() {
        String mode = modeComboBox.getValue();
        if (mode == null) mode = "DB_TO_FILE";
        // 控制transformer链配置区可见性
        if (transformerBox != null) {
            transformerBox.setVisible("DB_TO_DB".equals(mode));
            transformerBox.setManaged("DB_TO_DB".equals(mode));
        }
        switch (mode) {
            case "DB_TO_DB":
                targetTypeComboBox.setVisible(true);
                targetTypeComboBox.setDisable(false);
                break;
            default:
                targetTypeComboBox.setVisible(false);
                targetTypeComboBox.setDisable(true);
                targetTypeComboBox.setValue(null);
        }
        refreshParamGrid();
        triggerConfigChanged(); // 切换模式后刷新主界面json区
    }

    private String modeToNumber(String mode) {
        if ("DB_TO_FILE".equals(mode)) return "0";
        if ("EXECUTE_SQL".equals(mode)) return "1";
        if ("DB_TO_DB".equals(mode)) return "2";
        return mode;
    }

    private void refreshParamGrid() {
        paramGrid.getChildren().clear();
        String mode = modeComboBox.getValue();
        String sourceType = sourceTypeComboBox.getValue();
        String targetType = targetTypeComboBox.getValue();
        if (mode == null) {
            paramGrid.add(new Label("请选择同步模式"), 0, 0);
            return;
        }
        switch (mode) {
            case "DB_TO_FILE":
                if (sourceType == null) {
                    paramGrid.add(new Label("请选择源数据源类型"), 0, 0);
                    return;
                }
                buildDataSourceParamForm(paramGrid, sourceType, sourceConfig, 0, false, false);
                int exportRow = getGridNextRow(paramGrid);
                buildExportParamForm(paramGrid, sourceConfig, exportRow);
                sourceConfig.setName(mapName(sourceType));
                jobConfig.setSqlMode(modeToNumber(mode));
                JobConfig.ContentConfig content = new JobConfig.ContentConfig();
                content.setCommon(sourceConfig);
                jobConfig.setContent(Collections.singletonList(content));
                break;
            case "DB_TO_DB":
                // 添加交换按钮
                Button swapBtn = new Button("交换源和目标");
                swapBtn.setStyle("-fx-background-radius: 8; -fx-background-color: #e6f7ff; -fx-border-radius: 8; -fx-border-color: #91d5ff; -fx-border-width: 1; -fx-text-fill: #1890ff; -fx-font-weight: bold; margin-bottom: 8px;");
                swapBtn.setOnAction(e -> {
                    DataSourceConfig tmp = new DataSourceConfig();
                    tmp.setName(targetConfig.getName());
                    tmp.setParameter(targetConfig.getParameter() == null ? null : new HashMap<>(targetConfig.getParameter()));
                    targetConfig.setName(sourceConfig.getName());
                    targetConfig.setParameter(sourceConfig.getParameter() == null ? null : new HashMap<>(sourceConfig.getParameter()));
                    sourceConfig.setName(tmp.getName());
                    sourceConfig.setParameter(tmp.getParameter() == null ? null : new HashMap<>(tmp.getParameter()));
                    // 交换下拉框选项
                    String tmpType = sourceTypeComboBox.getValue();
                    sourceTypeComboBox.setValue(targetTypeComboBox.getValue());
                    targetTypeComboBox.setValue(tmpType);
                    refreshParamGrid();
                });
                paramGrid.add(swapBtn, 0, 0, 2, 1);
                dbToDbTabPane = new TabPane();
                Tab readerTab = new Tab("源数据源(reader)");
                GridPane readerGrid = new GridPane();
                buildDataSourceParamForm(readerGrid, sourceType, sourceConfig, 0, false, false); // reader: isWriter=false
                readerTab.setContent(readerGrid);
                Tab writerTab = new Tab("目标数据源(writer)");
                GridPane writerGrid = new GridPane();
                buildDataSourceParamForm(writerGrid, targetType, targetConfig, 0, false, true); // writer: isWriter=true
                writerTab.setContent(writerGrid);
                dbToDbTabPane.getTabs().addAll(readerTab, writerTab);
                paramGrid.add(dbToDbTabPane, 0, 1);
                sourceConfig.setName(mapName(sourceType));
                targetConfig.setName(mapName(targetType));
                jobConfig.setSqlMode(modeToNumber(mode));
                JobConfig.ContentConfig dbContent = new JobConfig.ContentConfig();
                dbContent.setReader(sourceConfig);
                dbContent.setWriter(targetConfig);
                jobConfig.setContent(Collections.singletonList(dbContent));
                break;
            case "EXECUTE_SQL":
                if (sourceType == null) {
                    paramGrid.add(new Label("请选择源数据源类型"), 0, 0);
                    return;
                }
                buildDataSourceParamForm(paramGrid, sourceType, sourceConfig, 0, true, false);
                int sqlRow = getGridNextRow(paramGrid);
                buildSqlBatchParamForm(paramGrid, sourceConfig, sqlRow);
                sourceConfig.setName(mapName(sourceType));
                jobConfig.setSqlMode(modeToNumber(mode));
                JobConfig.ContentConfig sqlContent = new JobConfig.ContentConfig();
                sqlContent.setCommon(sourceConfig);
                jobConfig.setContent(Collections.singletonList(sqlContent));
                break;
            default:
                paramGrid.add(new Label("暂不支持该模式"), 0, 0);
        }
        refreshSettingGrid();
        refreshTransformerPreview();
        triggerConfigChanged();
    }

    private void buildDataSourceParamForm(GridPane grid, String type, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        if (type == null) {
            grid.add(new Label("请选择数据源类型"), 0, startRow);
            return;
        }
        switch (type) {
            case "Mysql":
                buildMysqlParamForm(grid, config, startRow, isExecuteSqlMode, isWriter);
                break;
            case "Postgresql":
                buildPostgresParamForm(grid, config, startRow, isExecuteSqlMode, isWriter);
                break;
            case "Hive":
                buildHiveParamForm(grid, config, startRow, isExecuteSqlMode, isWriter);
                break;
            case "TxtFile":
                buildTxtFileParamForm(grid, config, startRow, isExecuteSqlMode, isWriter);
                break;
            case "HBase":
                buildHBaseParamForm(grid, config, startRow, isExecuteSqlMode, isWriter);
                break;
            default:
                grid.add(new Label("暂不支持该数据源"), 0, startRow);
        }
    }

    // Mysql参数表单
    private void buildMysqlParamForm(GridPane grid, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "Mysql");
        final Map<String, Object>[] connectionArr = new Map[]{(Map<String, Object>) paramArr[0].getOrDefault("connection", new HashMap<>())};
        int row = startRow;
        grid.add(new Label("连接参数"), 0, row++);
        // ip
        grid.add(new Label("数据库地址(ip) *"), 0, row);
        TextField ipField = new TextField((String) connectionArr[0].getOrDefault("ip", ""));
        ipField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(ipField, 1, row);
        ipField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("ip", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // port
        grid.add(new Label("端口(port) *"), 0, row);
        TextField portField = new TextField((String) connectionArr[0].getOrDefault("port", "3306"));
        portField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(portField, 1, row);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("port", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // username
        grid.add(new Label("用户名(username) *"), 0, row);
        TextField userField = new TextField((String) connectionArr[0].getOrDefault("username", ""));
        userField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(userField, 1, row);
        userField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("username", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // password
        grid.add(new Label("密码(password) *"), 0, row);
        PasswordField pwdField = new PasswordField();
        pwdField.setText((String) connectionArr[0].getOrDefault("password", ""));
        pwdField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(pwdField, 1, row);
        pwdField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("password", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // database
        grid.add(new Label("数据库名(database) *"), 0, row);
        TextField dbField = new TextField((String) connectionArr[0].getOrDefault("database", ""));
        dbField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(dbField, 1, row);
        dbField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("database", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // table
        grid.add(new Label("表名(table) *"), 0, row);
        TextField tableField = new TextField((String) paramArr[0].getOrDefault("table", ""));
        tableField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(tableField, 1, row);
        tableField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("table", newV);
            triggerConfigChanged();
        });
        row++;
        // column
        grid.add(new Label("字段列表(column,逗号分隔) *"), 0, row);
        TextField colField = new TextField(columnListToString(paramArr[0].get("column")));
        colField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(colField, 1, row);
        colField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> cols = Arrays.asList(newV.split(","));
            paramArr[0].put("column", cols);
            triggerConfigChanged();
        });
        row++;
        // where
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // preSql
        grid.add(new Label("前置SQL(preSql)"), 0, row);
        Object preSqlObj = paramArr[0].get("preSql");
        String preSqlStr = "";
        if (preSqlObj instanceof List) {
            List<?> preList = (List<?>) preSqlObj;
            preSqlStr = String.join(",", preList.stream().map(Object::toString).toArray(String[]::new));
        } else if (preSqlObj != null) {
            preSqlStr = preSqlObj.toString();
        }
        TextField preSqlField = new TextField(preSqlStr);
        preSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(preSqlField, 1, row);
        preSqlField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> arr = Arrays.asList(newV.split(","));
            paramArr[0].put("preSql", arr);
            triggerConfigChanged();
        });
        row++;
        // postSql
        grid.add(new Label("后置SQL(postSql)"), 0, row);
        Object postSqlObj = paramArr[0].get("postSql");
        String postSqlStr = "";
        if (postSqlObj instanceof List) {
            List<?> postList = (List<?>) postSqlObj;
            postSqlStr = String.join(",", postList.stream().map(Object::toString).toArray(String[]::new));
        } else if (postSqlObj != null) {
            postSqlStr = postSqlObj.toString();
        }
        TextField postSqlField = new TextField(postSqlStr);
        postSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(postSqlField, 1, row);
        postSqlField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> arr = Arrays.asList(newV.split(","));
            paramArr[0].put("postSql", arr);
            triggerConfigChanged();
        });
        row++;
        // JDBC附加参数
        grid.add(new Label("JDBC附加参数(jdbcUrlParam,JSON)"), 0, row);
        TextField jdbcField = new TextField(connectionArr[0].get("jdbcUrlParam") != null ? connectionArr[0].get("jdbcUrlParam").toString() : "{}");
        jdbcField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(jdbcField, 1, row);
        jdbcField.textProperty().addListener((obs, oldV, newV) -> {
            try {
                ObjectMapper om = new ObjectMapper();
                Map m = om.readValue(newV, Map.class);
                connectionArr[0].put("jdbcUrlParam", m);
                paramArr[0].put("connection", connectionArr[0]);
                triggerConfigChanged();
            } catch (Exception ex) {
            }
        });
        row++;
        // splitPK（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("分片主键(splitPK)"), 0, row);
            TextField splitPKField = new TextField((String) paramArr[0].getOrDefault("splitPK", ""));
            splitPKField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(splitPKField, 1, row);
            splitPKField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("splitPK", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // where（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // querySql（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("自定义查询SQL(querySql)"), 0, row);
            TextArea querySqlArea = new TextArea((String) paramArr[0].getOrDefault("querySql", ""));
            querySqlArea.setPrefRowCount(3);
            querySqlArea.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(querySqlArea, 1, row);
            querySqlArea.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("querySql", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // writer端专属：autoCreateTable、writeMode
        if (isWriter) {
            // autoCreateTable
            grid.add(new Label("自动建表(autoCreateTable)"), 0, row);
            CheckBox autoCreateTableBox = new CheckBox();
            autoCreateTableBox.setSelected(Boolean.TRUE.equals(paramArr[0].getOrDefault("autoCreateTable", true)));
            grid.add(autoCreateTableBox, 1, row);
            autoCreateTableBox.selectedProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("autoCreateTable", newV);
                triggerConfigChanged();
            });
            row++;
            // writeMode
            grid.add(new Label("写入模式(writeMode)"), 0, row);
            ComboBox<String> writeModeCombo = new ComboBox<>();
            writeModeCombo.getItems().addAll("insert", "replace", "update", "ignore");
            writeModeCombo.setValue((String) paramArr[0].getOrDefault("writeMode", "insert"));
            writeModeCombo.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(writeModeCombo, 1, row);
            writeModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("writeMode", newV);
                triggerConfigChanged();
            });
            row++;
        }
    }

    // Postgresql参数表单
    private void buildPostgresParamForm(GridPane grid, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "Postgresql");
        final Map<String, Object>[] connectionArr = new Map[]{(Map<String, Object>) paramArr[0].getOrDefault("connection", new HashMap<>())};
        int row = startRow;
        grid.add(new Label("连接参数"), 0, row++);
        // ip
        grid.add(new Label("数据库地址(ip) *"), 0, row);
        TextField ipField = new TextField((String) connectionArr[0].getOrDefault("ip", ""));
        ipField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(ipField, 1, row);
        ipField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("ip", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // port
        grid.add(new Label("端口(port) *"), 0, row);
        TextField portField = new TextField((String) connectionArr[0].getOrDefault("port", "5432"));
        portField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(portField, 1, row);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("port", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // username
        grid.add(new Label("用户名(username) *"), 0, row);
        TextField userField = new TextField((String) connectionArr[0].getOrDefault("username", ""));
        userField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(userField, 1, row);
        userField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("username", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // password
        grid.add(new Label("密码(password) *"), 0, row);
        PasswordField pwdField = new PasswordField();
        pwdField.setText((String) connectionArr[0].getOrDefault("password", ""));
        pwdField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(pwdField, 1, row);
        pwdField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("password", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // database
        grid.add(new Label("数据库名(database) *"), 0, row);
        TextField dbField = new TextField((String) connectionArr[0].getOrDefault("database", ""));
        dbField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(dbField, 1, row);
        dbField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("database", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // schema
        grid.add(new Label("schema(可选)"), 0, row);
        TextField schemaField = new TextField((String) connectionArr[0].getOrDefault("schema", "public"));
        schemaField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(schemaField, 1, row);
        schemaField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("schema", newV);
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // table
        grid.add(new Label("表名(table) *"), 0, row);
        TextField tableField = new TextField((String) paramArr[0].getOrDefault("table", ""));
        tableField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(tableField, 1, row);
        tableField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("table", newV);
            triggerConfigChanged();
        });
        row++;
        // column
        grid.add(new Label("字段列表(column,逗号分隔) *"), 0, row);
        TextField colField = new TextField(columnListToString(paramArr[0].get("column")));
        colField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(colField, 1, row);
        colField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> cols = Arrays.asList(newV.split(","));
            paramArr[0].put("column", cols);
            triggerConfigChanged();
        });
        row++;
        // where
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // preSql
        grid.add(new Label("前置SQL(preSql)"), 0, row);
        Object preSqlObj = paramArr[0].get("preSql");
        String preSqlStr = "";
        if (preSqlObj instanceof List) {
            List<?> preList = (List<?>) preSqlObj;
            preSqlStr = String.join(",", preList.stream().map(Object::toString).toArray(String[]::new));
        } else if (preSqlObj != null) {
            preSqlStr = preSqlObj.toString();
        }
        TextField preSqlField = new TextField(preSqlStr);
        preSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(preSqlField, 1, row);
        preSqlField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> arr = Arrays.asList(newV.split(","));
            paramArr[0].put("preSql", arr);
            triggerConfigChanged();
        });
        row++;
        // postSql
        grid.add(new Label("后置SQL(postSql)"), 0, row);
        Object postSqlObj = paramArr[0].get("postSql");
        String postSqlStr = "";
        if (postSqlObj instanceof List) {
            List<?> postList = (List<?>) postSqlObj;
            postSqlStr = String.join(",", postList.stream().map(Object::toString).toArray(String[]::new));
        } else if (postSqlObj != null) {
            postSqlStr = postSqlObj.toString();
        }
        TextField postSqlField = new TextField(postSqlStr);
        postSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(postSqlField, 1, row);
        postSqlField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> arr = Arrays.asList(newV.split(","));
            paramArr[0].put("postSql", arr);
            triggerConfigChanged();
        });
        row++;
        // JDBC附加参数
        grid.add(new Label("JDBC附加参数(jdbcUrlParam,JSON)"), 0, row);
        TextField jdbcField = new TextField(connectionArr[0].get("jdbcUrlParam") != null ? connectionArr[0].get("jdbcUrlParam").toString() : "{}");
        jdbcField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(jdbcField, 1, row);
        jdbcField.textProperty().addListener((obs, oldV, newV) -> {
            try {
                ObjectMapper om = new ObjectMapper();
                Map m = om.readValue(newV, Map.class);
                connectionArr[0].put("jdbcUrlParam", m);
                paramArr[0].put("connection", connectionArr[0]);
                triggerConfigChanged();
            } catch (Exception ex) {
            }
        });
        row++;
        // splitPK（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("分片主键(splitPK)"), 0, row);
            TextField splitPKField = new TextField((String) paramArr[0].getOrDefault("splitPK", ""));
            splitPKField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(splitPKField, 1, row);
            splitPKField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("splitPK", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // where（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // querySql（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("自定义查询SQL(querySql)"), 0, row);
            TextArea querySqlArea = new TextArea((String) paramArr[0].getOrDefault("querySql", ""));
            querySqlArea.setPrefRowCount(3);
            querySqlArea.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(querySqlArea, 1, row);
            querySqlArea.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("querySql", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // writer端专属：autoCreateTable、writeMode
        if (isWriter) {
            // autoCreateTable
            grid.add(new Label("自动建表(autoCreateTable)"), 0, row);
            CheckBox autoCreateTableBox = new CheckBox();
            autoCreateTableBox.setSelected(Boolean.TRUE.equals(paramArr[0].getOrDefault("autoCreateTable", true)));
            grid.add(autoCreateTableBox, 1, row);
            autoCreateTableBox.selectedProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("autoCreateTable", newV);
                triggerConfigChanged();
            });
            row++;
            // writeMode
            grid.add(new Label("写入模式(writeMode)"), 0, row);
            ComboBox<String> writeModeCombo = new ComboBox<>();
            writeModeCombo.getItems().addAll("insert", "update", "ignore"); // postgresql不支持replace
            writeModeCombo.setValue((String) paramArr[0].getOrDefault("writeMode", "insert"));
            writeModeCombo.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(writeModeCombo, 1, row);
            writeModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("writeMode", newV);
                triggerConfigChanged();
            });
            row++;
        }
        config.setName(mapName("Postgresql"));
    }

    // Hive参数表单
    private void buildHiveParamForm(GridPane grid, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "Hive");
        final Map<String, Object>[] connectionArr = new Map[]{(Map<String, Object>) paramArr[0].getOrDefault("connection", new HashMap<>())};
        int row = startRow;
        grid.add(new Label("连接参数"), 0, row++);
        // ip
        grid.add(new Label("HiveServer2地址(ip) *"), 0, row);
        TextField ipField = new TextField((String) connectionArr[0].getOrDefault("ip", ""));
        ipField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(ipField, 1, row);
        ipField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("ip", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // port
        grid.add(new Label("端口(port) *"), 0, row);
        TextField portField = new TextField((String) connectionArr[0].getOrDefault("port", "10000"));
        portField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(portField, 1, row);
        portField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("port", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // username
        grid.add(new Label("用户名(username) *"), 0, row);
        TextField userField = new TextField((String) connectionArr[0].getOrDefault("username", ""));
        userField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(userField, 1, row);
        userField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("username", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // password
        grid.add(new Label("密码(password) *"), 0, row);
        PasswordField pwdField = new PasswordField();
        pwdField.setText((String) connectionArr[0].getOrDefault("password", ""));
        pwdField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(pwdField, 1, row);
        pwdField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("password", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // database
        grid.add(new Label("数据库名(database) *"), 0, row);
        TextField dbField = new TextField((String) connectionArr[0].getOrDefault("database", ""));
        dbField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(dbField, 1, row);
        dbField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("database", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        // authMechanism
        grid.add(new Label("认证方式(authMechanism)"), 0, row);
        ComboBox<String> authCombo = new ComboBox<>();
        authCombo.getItems().addAll("NOSASL", "KERBEROS");
        authCombo.setValue((String) connectionArr[0].getOrDefault("authMechanism", "NOSASL"));
        authCombo.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(authCombo, 1, row);
        // 认证方式切换监听器，切换时刷新整个表单
        authCombo.valueProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("authMechanism", newV);
            paramArr[0].put("connection", connectionArr[0]);
            refreshParamGrid(); // 直接刷新整个参数表单，消除空白和对齐问题
            triggerConfigChanged();
        });
        row++;
        // Kerberos相关项，直接加到主GridPane
        if ("KERBEROS".equals(authCombo.getValue())) {
            if (!connectionArr[0].containsKey("kerberosServiceName") || connectionArr[0].get("kerberosServiceName") == null) {
                connectionArr[0].put("kerberosServiceName", new HashMap<String, Object>());
            }
            Map<String, Object> kerberosMap = (Map<String, Object>) connectionArr[0].get("kerberosServiceName");
            String[] kerberosFields = {"principal", "keytabConf", "keytabUsername", "keytabFile", "hdfsKeytabUsername", "hdfsKeytabFile"};
            String[] kerberosLabels = {"principal", "keytabConf", "keytabUsername", "keytabFile", "hdfsKeytabUsername", "hdfsKeytabFile"};
            for (int i = 0; i < kerberosFields.length; i++) {
                grid.add(new Label("Kerberos-" + kerberosLabels[i]), 0, row);
                TextField tf = new TextField(kerberosMap.getOrDefault(kerberosFields[i], "").toString());
                tf.setPrefWidth(UNIFIED_FIELD_WIDTH);
                final int idx = i;
                tf.textProperty().addListener((obs, oldV, newV) -> {
                    kerberosMap.put(kerberosFields[idx], newV);
                    connectionArr[0].put("kerberosServiceName", kerberosMap);
                    paramArr[0].put("connection", connectionArr[0]);
                    triggerConfigChanged();
                });
                grid.add(tf, 1, row);
                row++;
            }
        }
        // hdfsIP/hdfsPort/hdfsUser/hdfsTempPath
        grid.add(new Label("HDFS地址(hdfsIP)"), 0, row);
        TextField hdfsIpField = new TextField((String) connectionArr[0].getOrDefault("hdfsIP", ""));
        hdfsIpField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(hdfsIpField, 1, row);
        hdfsIpField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("hdfsIP", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("HDFS端口(hdfsPort)"), 0, row);
        TextField hdfsPortField = new TextField((String) connectionArr[0].getOrDefault("hdfsPort", "8020"));
        hdfsPortField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(hdfsPortField, 1, row);
        hdfsPortField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("hdfsPort", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("HDFS用户(hdfsUser)"), 0, row);
        TextField hdfsUserField = new TextField((String) connectionArr[0].getOrDefault("hdfsUser", ""));
        hdfsUserField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(hdfsUserField, 1, row);
        hdfsUserField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("hdfsUser", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("HDFS临时目录(hdfsTempPath)"), 0, row);
        TextField hdfsTmpField = new TextField((String) connectionArr[0].getOrDefault("hdfsTempPath", ""));
        hdfsTmpField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(hdfsTmpField, 1, row);
        hdfsTmpField.textProperty().addListener((obs, oldV, newV) -> {
            connectionArr[0].put("hdfsTempPath", newV == null ? "" : newV.trim());
            paramArr[0].put("connection", connectionArr[0]);
            triggerConfigChanged();
        });
        row++;
        if (!isExecuteSqlMode) {
            // table
            grid.add(new Label("表名(table) *"), 0, row);
            TextField tableField = new TextField((String) paramArr[0].getOrDefault("table", ""));
            tableField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(tableField, 1, row);
            tableField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("table", newV);
                triggerConfigChanged();
            });
            row++;
            // column
            grid.add(new Label("字段列表(column,逗号分隔) *"), 0, row);
            TextField colField = new TextField(columnListToString(paramArr[0].get("column")));
            colField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(colField, 1, row);
            colField.textProperty().addListener((obs, oldV, newV) -> {
                List<String> cols = Arrays.asList(newV.split(","));
                paramArr[0].put("column", cols);
                triggerConfigChanged();
            });
            row++;
            // where
            if (!isWriter) {
                grid.add(new Label("过滤条件(where)"), 0, row);
                TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
                whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
                grid.add(whereField, 1, row);
                whereField.textProperty().addListener((obs, oldV, newV) -> {
                    paramArr[0].put("where", newV);
                    triggerConfigChanged();
                });
                row++;
            }
            // preSql
            grid.add(new Label("前置SQL(preSql)"), 0, row);
            TextField preSqlField = new TextField((String) paramArr[0].getOrDefault("preSql", ""));
            preSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(preSqlField, 1, row);
            preSqlField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("preSql", newV);
                triggerConfigChanged();
            });
            row++;
            // postSql
            grid.add(new Label("后置SQL(postSql)"), 0, row);
            TextField postSqlField = new TextField((String) paramArr[0].getOrDefault("postSql", ""));
            postSqlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(postSqlField, 1, row);
            postSqlField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("postSql", newV);
                triggerConfigChanged();
            });
            row++;
        }
        config.setName(mapName("Hive"));
        // splitPK（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("分片主键(splitPK)"), 0, row);
            TextField splitPKField = new TextField((String) paramArr[0].getOrDefault("splitPK", ""));
            splitPKField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(splitPKField, 1, row);
            splitPKField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("splitPK", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // where（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // querySql（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("自定义查询SQL(querySql)"), 0, row);
            TextArea querySqlArea = new TextArea((String) paramArr[0].getOrDefault("querySql", ""));
            querySqlArea.setPrefRowCount(3);
            querySqlArea.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(querySqlArea, 1, row);
            querySqlArea.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("querySql", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // writer端专属：autoCreateTable、writeMode
        if (isWriter) {
            // autoCreateTable
            grid.add(new Label("自动建表(autoCreateTable)"), 0, row);
            CheckBox autoCreateTableBox = new CheckBox();
            autoCreateTableBox.setSelected(Boolean.TRUE.equals(paramArr[0].getOrDefault("autoCreateTable", true)));
            grid.add(autoCreateTableBox, 1, row);
            autoCreateTableBox.selectedProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("autoCreateTable", newV);
                triggerConfigChanged();
            });
            row++;
            // writeMode
            grid.add(new Label("写入模式(writeMode)"), 0, row);
            ComboBox<String> writeModeCombo = new ComboBox<>();
            writeModeCombo.getItems().addAll("insert", "update"); // hive一般只支持insert/update
            writeModeCombo.setValue((String) paramArr[0].getOrDefault("writeMode", "insert"));
            writeModeCombo.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(writeModeCombo, 1, row);
            writeModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("writeMode", newV);
                triggerConfigChanged();
            });
            row++;
        }
    }

    // TxtFile参数表单
    private void buildTxtFileParamForm(GridPane grid, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "TxtFile");
        int row = startRow;
        grid.add(new Label("文件路径(filePath) *"), 0, row);
        TextField filePathField = new TextField((String) paramArr[0].getOrDefault("filePath", ""));
        filePathField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(filePathField, 1, row);
        filePathField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("filePath", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("字段列表(column,逗号分隔) *"), 0, row);
        TextField colField = new TextField(columnListToString(paramArr[0].get("column")));
        colField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(colField, 1, row);
        colField.textProperty().addListener((obs, oldV, newV) -> {
            List<String> cols = Arrays.asList(newV.split(","));
            paramArr[0].put("column", cols);
            triggerConfigChanged();
        });
        row++;
        // 只有reader端才显示sep字段
        if (!isWriter) {
            grid.add(new Label("分隔符(sep)"), 0, row);
            TextField sepField = new TextField((String) paramArr[0].getOrDefault("sep", ","));
            sepField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(sepField, 1, row);
            sepField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("sep", newV);
                triggerConfigChanged();
            });
            row++;
        }
        grid.add(new Label("跳过首行(skipHeader)"), 0, row);
        CheckBox skipHeaderBox = new CheckBox();
        skipHeaderBox.setSelected(Boolean.TRUE.equals(paramArr[0].getOrDefault("skipHeader", false)));
        grid.add(skipHeaderBox, 1, row);
        skipHeaderBox.selectedProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("skipHeader", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("文件类型(fileType)"), 0, row);
        TextField fileTypeField = new TextField((String) paramArr[0].getOrDefault("fileType", "csv"));
        fileTypeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(fileTypeField, 1, row);
        fileTypeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("fileType", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("分块大小(chunkSize)"), 0, row);
        TextField chunkSizeField = new TextField(paramArr[0].getOrDefault("chunkSize", "").toString());
        chunkSizeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(chunkSizeField, 1, row);
        chunkSizeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("chunkSize", newV);
            triggerConfigChanged();
        });
        row++;
    }

    // HBase参数表单
    private void buildHBaseParamForm(GridPane grid, DataSourceConfig config, int startRow, boolean isExecuteSqlMode, boolean isWriter) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "HBase");
        int row = startRow;
        grid.add(new Label("连接参数"), 0, row++);
        // ...原有zkQuorum/zkPort/namespace/table/column等...
        // splitPK（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("分片主键(splitPK)"), 0, row);
            TextField splitPKField = new TextField((String) paramArr[0].getOrDefault("splitPK", ""));
            splitPKField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(splitPKField, 1, row);
            splitPKField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("splitPK", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // where（仅reader端）
        if (!isWriter && !isExecuteSqlMode) {
            grid.add(new Label("过滤条件(where)"), 0, row);
            TextField whereField = new TextField((String) paramArr[0].getOrDefault("where", ""));
            whereField.setPrefWidth(UNIFIED_FIELD_WIDTH);
            grid.add(whereField, 1, row);
            whereField.textProperty().addListener((obs, oldV, newV) -> {
                paramArr[0].put("where", newV);
                triggerConfigChanged();
            });
            row++;
        }
        // writer端专属：autoCreateTable、writeMode（HBase一般不支持）
        // ...如有需要可补充...
        // ...其余表单项...
        config.setName(mapName("HBase"));
    }

    // EXECUTE_SQL参数表单
    private void buildSqlBatchParamForm(GridPane grid, DataSourceConfig config, int startRow) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "EXECUTE_SQL");
        int row = startRow;
        grid.add(new Label("SQL语句(sql) *"), 0, row);
        TextArea sqlArea = new TextArea((String) paramArr[0].getOrDefault("sql", ""));
        sqlArea.setPrefRowCount(4);
        sqlArea.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(sqlArea, 1, row);
        sqlArea.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("sql", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("SQL文件路径(sqlFile)"), 0, row);
        TextField sqlFileField = new TextField((String) paramArr[0].getOrDefault("sqlFile", ""));
        sqlFileField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(sqlFileField, 1, row);
        sqlFileField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("sqlFile", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("导出文件路径(saveUrl)"), 0, row);
        TextField saveUrlField = new TextField((String) paramArr[0].getOrDefault("saveUrl", ""));
        saveUrlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(saveUrlField, 1, row);
        saveUrlField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("saveUrl", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("导出文件类型(fileType)"), 0, row);
        TextField fileTypeField = new TextField((String) paramArr[0].getOrDefault("fileType", "csv"));
        fileTypeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(fileTypeField, 1, row);
        fileTypeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("fileType", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("分块(chunkSize)"), 0, row);
        TextField chunkSizeField = new TextField(paramArr[0].getOrDefault("chunkSize", "").toString());
        chunkSizeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(chunkSizeField, 1, row);
        chunkSizeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("chunkSize", newV);
            triggerConfigChanged();
        });
        row++;
    }

    // DB_TO_FILE导出参数表单
    private void buildExportParamForm(GridPane grid, DataSourceConfig config, int startRow) {
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        if (paramArr[0].get("connection") == null) paramArr[0].put("connection", new HashMap<>());
        fillConnectionDefaults((Map<String, Object>) paramArr[0].get("connection"), "DB_TO_FILE");
        int row = startRow;
        grid.add(new Label("导出文件路径(saveUrl) *"), 0, row);
        TextField saveUrlField = new TextField((String) paramArr[0].getOrDefault("saveUrl", ""));
        saveUrlField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(saveUrlField, 1, row);
        saveUrlField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("saveUrl", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("导出文件类型(fileType) *"), 0, row);
        TextField fileTypeField = new TextField((String) paramArr[0].getOrDefault("fileType", "csv"));
        fileTypeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(fileTypeField, 1, row);
        fileTypeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("fileType", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("分块导出(isChunk)"), 0, row);
        CheckBox isChunkBox = new CheckBox();
        isChunkBox.setSelected(Boolean.TRUE.equals(paramArr[0].getOrDefault("isChunk", false)));
        grid.add(isChunkBox, 1, row);
        isChunkBox.selectedProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("isChunk", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("每块最大行数(chunkSize)"), 0, row);
        TextField chunkSizeField = new TextField(paramArr[0].getOrDefault("chunkSize", "10000").toString());
        chunkSizeField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(chunkSizeField, 1, row);
        chunkSizeField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("chunkSize", newV);
            triggerConfigChanged();
        });
        row++;
        grid.add(new Label("分隔符(sep)"), 0, row);
        TextField sepField = new TextField((String) paramArr[0].getOrDefault("sep", ","));
        sepField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        grid.add(sepField, 1, row);
        sepField.textProperty().addListener((obs, oldV, newV) -> {
            paramArr[0].put("sep", newV);
            triggerConfigChanged();
        });
        row++;
    }

    @FXML
    private void onEditTransformer() {
        try {
            JobConfig.ContentConfig content = null;
            if (jobConfig.getContent() != null && !jobConfig.getContent().isEmpty()) {
                content = jobConfig.getContent().get(0);
            }
            if (content == null) return;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TransformerChainDialog.fxml"));
            Parent page = loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("编辑Transformer链");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(paramGrid.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(page));
            TransformerChainDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setContentConfig(content);
            if (content.getTransformer() != null)
                controller.setTransformerList(content.getTransformer());
            dialogStage.showAndWait();
            if (controller.isOkClicked()) {
                content.setTransformer(controller.getTransformerList());
                refreshTransformerPreview();
                triggerConfigChanged();
            }
        } catch (Exception e) {
            showInfo("弹出Transformer链编辑对话框失败: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveConfig() {
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("job", jobConfig);
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            String json = om.writeValueAsString(root);
            Files.write(new File(LAST_CONFIG_PATH).toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            showInfo("配置已保存到 " + LAST_CONFIG_PATH);
        } catch (Exception e) {
            showInfo("保存配置失败: " + e.getMessage());
        }
    }

    @FXML
    private void onImportConfig() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导入JSON配置");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON文件", "*.json"));
            File file = fileChooser.showOpenDialog(paramGrid.getScene().getWindow());
            if (file != null) {
                String json = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                ObjectMapper om = new ObjectMapper();
                Map map = om.readValue(json, Map.class);
                if (map.containsKey("job")) {
                    JobConfig cfg = om.convertValue(map.get("job"), JobConfig.class);
                    this.jobConfig = cfg;
                } else {
                    this.jobConfig = om.readValue(json, JobConfig.class);
                }
                // 自动同步UI下拉框和表单数据
                if (jobConfig.getContent() != null && !jobConfig.getContent().isEmpty()) {
                    JobConfig.ContentConfig content = jobConfig.getContent().get(0);
                    if (content.getReader() != null) {
                        sourceConfig = content.getReader();
                        sourceTypeComboBox.setValue(sourceConfig.getName());
                    }
                    if (content.getWriter() != null) {
                        targetConfig = content.getWriter();
                        targetTypeComboBox.setValue(targetConfig.getName());
                    }
                    if (content.getCommon() != null) {
                        sourceConfig = content.getCommon();
                        sourceTypeComboBox.setValue(sourceConfig.getName());
                    }
                }
                modeComboBox.setValue("DB_TO_DB");
                sourceTypeComboBox.setValue(sourceConfig.getName());
                targetTypeComboBox.setValue(targetConfig.getName());
                refreshParamGrid();
                showInfo("导入成功");
            }
        } catch (Exception e) {
            showInfo("导入失败: " + e.getMessage());
        }
    }

    private boolean validateAllParams() {
        String mode = modeComboBox.getValue();
        if (mode == null) {
            showInfo("请选择同步模式");
            return false;
        }
        if (jobConfig.getContent() == null || jobConfig.getContent().isEmpty()) {
            showInfo("请先填写任务参数");
            return false;
        }
        JobConfig.ContentConfig content = jobConfig.getContent().get(0);
        DataSourceConfig mainConfig = null;
        if ("DB_TO_FILE".equals(mode) || "EXECUTE_SQL".equals(mode)) {
            mainConfig = content.getCommon();
        } else if ("DB_TO_DB".equals(mode)) {
            mainConfig = content.getReader();
        }
        if (mainConfig == null) {
            showInfo("请填写数据源参数");
            return false;
        }
        Map<String, Object> param = mainConfig.getParameter();
        if (param == null) {
            showInfo("请填写数据源参数");
            return false;
        }
        String type = mainConfig.getName();
        if (type == null || type.trim().isEmpty()) {
            showInfo("请选择数据源类型");
            return false;
        }
        // 按类型分支校验
        if ("Mysql".equals(type) || "Postgresql".equals(type) || "Hive".equals(type)) {
            if (param.get("connection") instanceof Map) {
                Map conn = (Map) param.get("connection");
                if (conn.get("ip") == null || conn.get("ip").toString().trim().isEmpty()) {
                    showInfo("数据库地址(ip)为必填项");
                    return false;
                }
                if (conn.get("port") == null || conn.get("port").toString().trim().isEmpty()) {
                    showInfo("端口(port)为必填项");
                    return false;
                }
                try {
                    Integer.parseInt(conn.get("port").toString());
                } catch (Exception e) {
                    showInfo("端口(port)必须为数字");
                    return false;
                }
                if (conn.get("username") == null || conn.get("username").toString().trim().isEmpty()) {
                    showInfo("用户名(username)为必填项");
                    return false;
                }
                if (conn.get("password") == null || conn.get("password").toString().trim().isEmpty()) {
                    showInfo("密码(password)为必填项");
                    return false;
                }
                if (conn.get("database") == null || conn.get("database").toString().trim().isEmpty()) {
                    showInfo("数据库名(database)为必填项");
                    return false;
                }
            } else {
                showInfo("数据库连接参数(connection)为必填项");
                return false;
            }
            if (param.get("table") == null || param.get("table").toString().trim().isEmpty()) {
                showInfo("表名(table)为必填项");
                return false;
            }
            if (param.get("column") == null || !(param.get("column") instanceof List) || ((List<?>) param.get("column")).isEmpty()) {
                showInfo("字段列表(column)为必填项");
                return false;
            }
        } else if ("TxtFile".equals(type)) {
            if (param.get("filePath") == null || param.get("filePath").toString().trim().isEmpty()) {
                showInfo("文件路径(filePath)为必填项");
                return false;
            }
            if (param.get("sep") == null || param.get("sep").toString().trim().isEmpty()) {
                showInfo("分隔符(sep)为必填项");
                return false;
            }
        } else if ("HBase".equals(type)) {
            if (param.get("hbaseConfig") == null || param.get("hbaseConfig").toString().trim().isEmpty()) {
                showInfo("HBase连接配置(hbaseConfig)为必填项");
                return false;
            }
            if (param.get("table") == null || param.get("table").toString().trim().isEmpty()) {
                showInfo("表名(table)为必填项");
                return false;
            }
            if (param.get("rowkey") == null || param.get("rowkey").toString().trim().isEmpty()) {
                showInfo("rowkey字段(rowkey)为必填项");
                return false;
            }
            if (param.get("column") == null || !(param.get("column") instanceof List) || ((List<?>) param.get("column")).isEmpty()) {
                showInfo("字段列表(column)为必填项");
                return false;
            }
        }
        // 其它类型可扩展...
        return true;
    }

    @FXML
    private void onExportConfig() {
        if (!validateAllParams()) return;
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("job", jobConfig);
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            om.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            String json = om.writeValueAsString(root);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出JSON配置");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON文件", "*.json"));
            File file = fileChooser.showSaveDialog(paramGrid.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                showInfo("导出成功: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            showInfo("导出失败: " + e.getMessage());
        }
    }

    // 预留运行任务方法
    @FXML
    private void onRunTask() {
        if (!validateAllParams()) return;
        showInfo("参数校验通过，可集成后端工具类执行任务");
    }

    @FXML
    private void onLoadExample() {
        try {
            String example = "{\n  \"sqlMode\": 0,\n  \"content\": [{\n    \"common\": {\n      \"name\": \"Mysql\",\n      \"parameter\": {\n        \"connection\": {\"ip\": \"127.0.0.1\",\"port\": \"3306\",\"username\": \"root\",\"password\": \"admin\",\"database\": \"demo\"},\n        \"table\": \"user\",\n        \"column\": [\"id\",\"name\"],\n        \"saveUrl\": \"./\",\n        \"fileType\": \"csv\",\n        \"isChunk\": true,\n        \"chunkSize\": 10000,\n        \"sep\": \",\"\n      }\n    }\n  }]\n}";
            ObjectMapper om = new ObjectMapper();
            Map map = om.readValue(example, Map.class);
            if (map.containsKey("job")) {
                JobConfig cfg = om.convertValue(map.get("job"), JobConfig.class);
                this.jobConfig = cfg;
            } else {
                this.jobConfig = om.readValue(example, JobConfig.class);
            }
            refreshParamGrid();
            showInfo("已加载典型配置");
        } catch (Exception e) {
            showInfo("加载典型配置失败: " + e.getMessage());
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.showAndWait();
    }

    // 获取GridPane当前最大行号+1
    private int getGridNextRow(GridPane grid) {
        int max = 0;
        for (javafx.scene.Node node : grid.getChildren()) {
            Integer row = GridPane.getRowIndex(node);
            if (row != null && row >= max) max = row + 1;
        }
        return max;
    }

    // 简单必填项校验（可扩展为更复杂的校验）
    private boolean validateRequired(GridPane grid, List<TextInputControl> requiredFields) {
        boolean valid = true;
        for (TextInputControl field : requiredFields) {
            if (field.getText() == null || field.getText().trim().isEmpty()) {
                field.setStyle("-fx-border-color: red");
                valid = false;
            } else {
                field.setStyle("");
            }
        }
        if (!valid) showInfo("请填写所有必填项，红框为必填");
        return valid;
    }

    public JobConfig getJobConfig() {
        return jobConfig;
    }

    private void refreshSettingGrid() {
        settingGrid.getChildren().clear();
        final SettingConfig[] settingArr = new SettingConfig[]{jobConfig.getSetting()};
        if (settingArr[0] == null) {
            settingArr[0] = new SettingConfig();
            jobConfig.setSetting(settingArr[0]);
        }
        int row = 0;
        // speed.channel
        settingGrid.add(new Label("并发通道数 (speed.channel) *"), 0, row);
        TextField channelField = new TextField(settingArr[0].getSpeed() != null && settingArr[0].getSpeed().get("channel") != null ? settingArr[0].getSpeed().get("channel").toString() : "1");
        channelField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        settingGrid.add(channelField, 1, row);
        channelField.textProperty().addListener((obs, oldV, newV) -> {
            try {
                int v = Integer.parseInt(newV);
                if (settingArr[0].getSpeed() == null) settingArr[0].setSpeed(new HashMap<>());
                settingArr[0].getSpeed().put("channel", v);
                triggerConfigChanged(); // 并发通道数变更时刷新JSON区
            } catch (Exception ignored) {
            }
        });
        row++;
        // reportPath
        settingGrid.add(new Label("报告路径 (reportPath) *"), 0, row);
        TextField reportField = new TextField(settingArr[0].getReportPath() != null ? settingArr[0].getReportPath() : "./report.json");
        reportField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        settingGrid.add(reportField, 1, row);
        reportField.textProperty().addListener((obs, oldV, newV) -> {
            settingArr[0].setReportPath(newV);
            triggerConfigChanged();
        });
        row++;
        // enablePreCheck
        settingGrid.add(new Label("启用前置校验 (enablePreCheck)"), 0, row);
        CheckBox preCheckBox = new CheckBox();
        preCheckBox.setSelected(Boolean.TRUE.equals(settingArr[0].getEnablePreCheck()));
        settingGrid.add(preCheckBox, 1, row);
        preCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            settingArr[0].setEnablePreCheck(newV);
            triggerConfigChanged();
        });
        row++;
        // enablePostCheck
        settingGrid.add(new Label("启用后置校验 (enablePostCheck)"), 0, row);
        CheckBox postCheckBox = new CheckBox();
        postCheckBox.setSelected(Boolean.TRUE.equals(settingArr[0].getEnablePostCheck()));
        settingGrid.add(postCheckBox, 1, row);
        postCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            settingArr[0].setEnablePostCheck(newV);
            triggerConfigChanged();
        });
        row++;
        // channel.class
        settingGrid.add(new Label("通道类型 (channel.class)"), 0, row);
        TextField classField = new TextField(settingArr[0].getChannel() != null && settingArr[0].getChannel().get("class") != null ? settingArr[0].getChannel().get("class").toString() : "memory");
        classField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        settingGrid.add(classField, 1, row);
        classField.textProperty().addListener((obs, oldV, newV) -> {
            if (settingArr[0].getChannel() == null) settingArr[0].setChannel(new HashMap<>());
            settingArr[0].getChannel().put("class", newV);
            triggerConfigChanged();
        });
        row++;
        // channel.memoryCapacity
        settingGrid.add(new Label("通道内存容量 (channel.memoryCapacity)"), 0, row);
        TextField memField = new TextField(settingArr[0].getChannel() != null && settingArr[0].getChannel().get("memoryCapacity") != null ? settingArr[0].getChannel().get("memoryCapacity").toString() : "10000");
        memField.setPrefWidth(UNIFIED_FIELD_WIDTH);
        settingGrid.add(memField, 1, row);
        memField.textProperty().addListener((obs, oldV, newV) -> {
            try {
                int v = Integer.parseInt(newV);
                if (settingArr[0].getChannel() == null) settingArr[0].setChannel(new HashMap<>());
                settingArr[0].getChannel().put("memoryCapacity", v);
                triggerConfigChanged();
            } catch (Exception ignored) {
            }
        });
        triggerConfigChanged();
    }

    private String mapName(String type) {
        if (type == null) return null;
        switch (type) {
            case "Mysql":
                return "Mysql";
            case "Postgresql":
                return "Postgresql";
            case "Hive":
                return "Hive";
            case "TxtFile":
                return "TxtFile";
            case "HBase":
                return "HBase";
            default:
                return type;
        }
    }

    private void fillConnectionDefaults(Map<String, Object> conn, String type) {
        if (conn == null) return;
        if (!conn.containsKey("ip")) conn.put("ip", "127.0.0.1");
        if (!conn.containsKey("port")) {
            if ("Mysql".equals(type)) conn.put("port", "3306");
            else if ("Postgresql".equals(type)) conn.put("port", "5432");
            else if ("Hive".equals(type)) conn.put("port", "10000");
            else conn.put("port", "");
        }
        if (!conn.containsKey("username")) conn.put("username", "root");
        if (!conn.containsKey("password")) conn.put("password", "admin");
        if (!conn.containsKey("database")) conn.put("database", "demo");
    }

    // 工具方法：兼容column为字符串、对象、混合类型
    private String columnListToString(Object colObj) {
        if (colObj instanceof List) {
            List<?> colList = (List<?>) colObj;
            List<String> colNames = new ArrayList<>();
            for (Object o : colList) {
                if (o instanceof CharSequence) {
                    colNames.add(o.toString());
                } else if (o instanceof Map) {
                    Object name = ((Map<?, ?>) o).get("name");
                    colNames.add(name != null ? name.toString() : o.toString());
                } else {
                    colNames.add(o.toString());
                }
            }
            return String.join(",", colNames);
        } else if (colObj != null) {
            return colObj.toString();
        }
        return "";
    }

    private void initTransformerPreviewTable() {
        if (transformerPreviewTable != null) {
            transformerPreviewTable.setItems(transformerPreviewList);
            if (colTIndex != null)
                colTIndex.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(transformerPreviewList.indexOf(cellData.getValue()) + 1)));
            if (colTType != null)
                colTType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
            if (colTParam != null)
                colTParam.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getParameter() == null ? "" : cellData.getValue().getParameter().toString()));
        }
    }

    private void refreshTransformerPreview() {
        transformerPreviewList.clear();
        if (jobConfig.getContent() != null && !jobConfig.getContent().isEmpty()) {
            List<TransformerConfig> list = jobConfig.getContent().get(0).getTransformer();
            if (list != null) transformerPreviewList.addAll(list);
        }
        triggerConfigChanged();
    }

    public void setOnConfigChanged(java.util.function.Consumer<JobConfig> callback) {
        this.onConfigChanged = callback;
    }

    public void triggerConfigChanged() {
        if (onConfigChanged != null) onConfigChanged.accept(getJobConfig());
    }
} 