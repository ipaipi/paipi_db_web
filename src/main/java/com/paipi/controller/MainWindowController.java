package com.paipi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paipi.config.DataSourceConfig;
import com.paipi.config.JobConfig;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class MainWindowController {
    private final File historyFile = new File(System.getProperty("user.home"), "paipi_db_history.json");
    private final File configFile = new File(System.getProperty("user.home"), "paipi_db_config.json");
    // 只保留FXML中真实存在的控件
    @FXML
    private AnchorPane configTabPane;
    @FXML
    private TextField jarPathField;
    @FXML
    private ProgressBar runProgressBar;
    @FXML
    private TextArea runLogTextArea;
    @FXML
    private ListView<String> runHistoryListView;
    @FXML
    private TextArea jsonArea;
    private boolean running = false;
    private Thread runThread;
    private Process runProcess;
    private File lastResultFile;
    private JobConfig jobConfig = new JobConfig();
    private String externalJarPath = "paipi_db-1.0-SNAPSHOT-jar-with-dependencies.jar";
    private ConfigStepPaneController configStepPaneController;

    @FXML
    private void onRunTask() {
        // 运行前同步最新jobConfig
        if (configStepPaneController != null) {
            this.jobConfig = configStepPaneController.getJobConfig();
        }
        if (running) {
            logRun("已有任务在运行");
            return;
        }
        if (!validateAllParams()) return;
        if (jarPathField != null) externalJarPath = jarPathField.getText();
        boolean jarExists = false;
        if (externalJarPath != null && !externalJarPath.trim().isEmpty()) {
            File f = new File(externalJarPath);
            if (f.exists() && f.isFile()) {
                jarExists = true;
            } else {
                // 尝试用classpath方式判断
                try {
                    java.net.URL jarUrl = getClass().getResource("/paipi_db-1.0-SNAPSHOT-jar-with-dependencies.jar");
                    if (jarUrl != null) {
                        jarExists = true;
                        externalJarPath = new java.io.File(jarUrl.toURI()).getAbsolutePath();
                        jarPathField.setText(externalJarPath);
                    }
                } catch (Exception ignored) {}
            }
        }
        if (!jarExists) {
            logRun("JAR路径无效，请先选择正确的JAR文件");
            return;
        }
        running = true;
        runProgressBar.setProgress(0.0);
        runLogTextArea.clear();
        logRun("任务开始...");
        lastResultFile = null;
        try {
            // 导出配置为临时文件
            File tmpFile = File.createTempFile("paipi_job_", ".json");
            tmpFile.deleteOnExit();
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            java.util.Map<String, Object> root = new java.util.HashMap<>();
            root.put("job", jobConfig);
            String json = om.writeValueAsString(root);
            Files.write(tmpFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            logRun("配置文件路径: " + tmpFile.getAbsolutePath());
            // 启动外部JAR进程
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-Dfile.encoding=UTF-8", "-jar", externalJarPath, tmpFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            runProcess = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            runThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null && running) {
                        final String logLine = line;
                        javafx.application.Platform.runLater(() -> {
                            logRun(logLine);
                            if (logLine.startsWith("[PROGRESS] ")) {
                                try {
                                    int progress = Integer.parseInt(logLine.substring(11).trim());
                                    runProgressBar.setProgress(progress / 100.0);
                                } catch (Exception ignored) {
                                }
                            }
                            if (logLine.startsWith("[RESULT] ")) {
                                lastResultFile = new File(logLine.substring(9).trim());
                                logRun("结果文件: " + lastResultFile.getAbsolutePath());
                            }
                        });
                    }
                    int exitCode = runProcess.waitFor();
                    javafx.application.Platform.runLater(() -> {
                        if (exitCode == 0) {
                            logRun("任务完成");
                            String record = "任务完成: " + java.time.LocalDateTime.now();
                            runHistoryListView.getItems().add(0, record);
                            saveHistory();
                        } else {
                            logRun("任务异常退出，exitCode=" + exitCode);
                        }
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> logRun("异常: " + e.getMessage()));
                } finally {
                    running = false;
                }
            });
            runThread.start();
        } catch (Exception e) {
            logRun("启动任务失败: " + e.getMessage());
            running = false;
        }
    }

    @FXML
    private void onStopTask() {
        if (running) {
            running = false;
            if (runProcess != null) runProcess.destroy();
            logRun("请求停止任务...");
        } else {
            logRun("当前无运行中的任务");
        }
    }

    @FXML
    private void onDownloadResult() {
        if (lastResultFile != null && lastResultFile.exists()) {
            try {
                java.awt.Desktop.getDesktop().open(lastResultFile);
            } catch (Exception e) {
                logRun("打开结果文件失败: " + e.getMessage());
            }
        } else {
            logRun("无可用结果文件");
        }
    }

    @FXML
    public void initialize() {
        // 加载ConfigStepPane.fxml并保存controller引用
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConfigStepPane.fxml"));
            Node configStepPane = loader.load();
            configStepPaneController = loader.getController();
            if (configTabPane != null && configStepPane != null) configTabPane.getChildren().setAll(configStepPane);
        } catch (Exception e) {
            System.err.println("加载任务配置分步表单失败: " + e.getMessage());
        }
        // 绑定JSON展示区内容实时刷新
        if (jsonArea != null && configStepPaneController != null) {
            configStepPaneController.setOnConfigChanged(jobConfig -> {
                try {
                    ObjectMapper om = new ObjectMapper();
                    om.enable(SerializationFeature.INDENT_OUTPUT);
                    java.util.Map<String, Object> root = new java.util.HashMap<>();
                    root.put("job", jobConfig);
                    String json = om.writeValueAsString(root);
                    jsonArea.setText(json);
                } catch (Exception ex) {
                    jsonArea.setText("JSON序列化失败: " + ex.getMessage());
                }
            });
            configStepPaneController.triggerConfigChanged();
        }
        // 加载历史记录
        loadHistory();
        if (runHistoryListView != null) runHistoryListView.setOnMouseClicked(this::onHistoryClick);
        loadJarPath();
        if (jarPathField != null) jarPathField.setText(externalJarPath);
        // 自动尝试设置JAR路径
        if (jarPathField != null && (jarPathField.getText() == null || jarPathField.getText().trim().isEmpty())) {
            try {
                java.net.URL jarUrl = getClass().getResource("/paipi_db-1.0-SNAPSHOT-jar-with-dependencies.jar");
                if (jarUrl != null) {
                    String jarPath = new java.io.File(jarUrl.toURI()).getAbsolutePath();
                    jarPathField.setText(jarPath);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void log(String msg) {
        // logTextArea is removed, so this method is no longer functional
    }

    private void logRun(String msg) {
        if (runLogTextArea != null) runLogTextArea.appendText(msg + "\n");
        if (msg.startsWith("[ERROR]") || msg.startsWith("[EXCEPTION]")) {
            javafx.application.Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
                alert.showAndWait();
            });
        }
    }

    private boolean validateAllParams() {
        if (jobConfig == null || jobConfig.getContent() == null || jobConfig.getContent().isEmpty()) {
            logRun("请先填写任务参数");
            return false;
        }
        JobConfig.ContentConfig content = jobConfig.getContent().get(0);
        DataSourceConfig mainConfig = null;
        String mode = null;
        if (content.getCommon() != null) {
            mainConfig = content.getCommon();
            // 通过sqlMode判断模式
            String sqlModeStr = jobConfig.getSqlMode();
            if ("0".equals(sqlModeStr)) {
                mode = "DB_TO_FILE";
            } else if ("1".equals(sqlModeStr)) {
                mode = "EXECUTE_SQL";
            } else if ("2".equals(sqlModeStr)) {
                mode = "DB_TO_DB";
            }
        } else if (content.getReader() != null) {
            mainConfig = content.getReader();
            mode = "DB_TO_DB";
        }
        if (mainConfig == null) {
            logRun("请填写数据源参数");
            return false;
        }
        java.util.Map<String, Object> param = mainConfig.getParameter();
        if (param == null) {
            logRun("请填写数据源参数");
            return false;
        }
        String type = mainConfig.getName();
        if (type == null || type.trim().isEmpty()) {
            logRun("请选择数据源类型");
            return false;
        }
        boolean isExecuteSqlMode = "EXECUTE_SQL".equals(mode);
        if (isExecuteSqlMode) {
            // 只校验连接参数和SQL语句
            if (param.get("connection") instanceof java.util.Map) {
                java.util.Map conn = (java.util.Map) param.get("connection");
                if (conn.get("ip") == null || conn.get("ip").toString().trim().isEmpty()) {
                    logRun("数据库地址(ip)为必填项");
                    return false;
                }
                if (conn.get("port") == null || conn.get("port").toString().trim().isEmpty()) {
                    logRun("端口(port)为必填项");
                    return false;
                }
                try {
                    Integer.parseInt(conn.get("port").toString());
                } catch (Exception e) {
                    logRun("端口(port)必须为数字");
                    return false;
                }
                if (conn.get("username") == null || conn.get("username").toString().trim().isEmpty()) {
                    logRun("用户名(username)为必填项");
                    return false;
                }
                if (conn.get("password") == null || conn.get("password").toString().trim().isEmpty()) {
                    logRun("密码(password)为必填项");
                    return false;
                }
                if (conn.get("database") == null || conn.get("database").toString().trim().isEmpty()) {
                    logRun("数据库名(database)为必填项");
                    return false;
                }
            } else {
                logRun("数据库连接参数(connection)为必填项");
                return false;
            }
            // SQL语句和SQL文件至少有一个
            String sql = (String) param.getOrDefault("sql", "");
            String sqlFile = (String) param.getOrDefault("sqlFile", "");
            if ((sql == null || sql.trim().isEmpty()) && (sqlFile == null || sqlFile.trim().isEmpty())) {
                logRun("SQL语句(sql)或SQL文件(sqlFile)必须至少填写一个");
                return false;
            }
            return true;
        }
        // 按类型分支校验
        if ("Mysql".equals(type) || "Postgresql".equals(type) || "Hive".equals(type)) {
            if (param.get("connection") instanceof java.util.Map) {
                java.util.Map conn = (java.util.Map) param.get("connection");
                if (conn.get("ip") == null || conn.get("ip").toString().trim().isEmpty()) {
                    logRun("数据库地址(ip)为必填项");
                    return false;
                }
                if (conn.get("port") == null || conn.get("port").toString().trim().isEmpty()) {
                    logRun("端口(port)为必填项");
                    return false;
                }
                try {
                    Integer.parseInt(conn.get("port").toString());
                } catch (Exception e) {
                    logRun("端口(port)必须为数字");
                    return false;
                }
                if (conn.get("username") == null || conn.get("username").toString().trim().isEmpty()) {
                    logRun("用户名(username)为必填项");
                    return false;
                }
                if (conn.get("password") == null || conn.get("password").toString().trim().isEmpty()) {
                    logRun("密码(password)为必填项");
                    return false;
                }
                if (conn.get("database") == null || conn.get("database").toString().trim().isEmpty()) {
                    logRun("数据库名(database)为必填项");
                    return false;
                }
            } else {
                logRun("数据库连接参数(connection)为必填项");
                return false;
            }
            if (param.get("table") == null || param.get("table").toString().trim().isEmpty()) {
                logRun("表名(table)为必填项");
                return false;
            }
            if (param.get("column") == null || !(param.get("column") instanceof java.util.List) || ((java.util.List<?>) param.get("column")).isEmpty()) {
                logRun("字段列表(column)为必填项");
                return false;
            }
        } else if ("TxtFile".equals(type)) {
            if (param.get("filePath") == null || param.get("filePath").toString().trim().isEmpty()) {
                logRun("文件路径(filePath)为必填项");
                return false;
            }
            if (param.get("sep") == null || param.get("sep").toString().trim().isEmpty()) {
                logRun("分隔符(sep)为必填项");
                return false;
            }
        } else if ("HBase".equals(type)) {
            if (param.get("hbaseConfig") == null || param.get("hbaseConfig").toString().trim().isEmpty()) {
                logRun("HBase连接配置(hbaseConfig)为必填项");
                return false;
            }
            if (param.get("table") == null || param.get("table").toString().trim().isEmpty()) {
                logRun("表名(table)为必填项");
                return false;
            }
            if (param.get("rowkey") == null || param.get("rowkey").toString().trim().isEmpty()) {
                logRun("rowkey字段(rowkey)为必填项");
                return false;
            }
            if (param.get("column") == null || !(param.get("column") instanceof java.util.List) || ((java.util.List<?>) param.get("column")).isEmpty()) {
                logRun("字段列表(column)为必填项");
                return false;
            }
        }
        // 其它类型可扩展...
        return true;
    }

    private void saveHistory() {
        try {
            java.util.List<String> list = new java.util.ArrayList<>(runHistoryListView.getItems());
            Files.write(historyFile.toPath(), list, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private void loadHistory() {
        try {
            if (historyFile.exists()) {
                java.util.List<String> list = Files.readAllLines(historyFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                runHistoryListView.getItems().setAll(list);
            }
        } catch (Exception ignored) {
        }
    }

    private void onHistoryClick(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY && runHistoryListView.getSelectionModel().getSelectedIndex() >= 0) {
            ContextMenu menu = new ContextMenu();
            MenuItem del = new MenuItem("删除记录");
            del.setOnAction(e -> {
                int idx = runHistoryListView.getSelectionModel().getSelectedIndex();
                runHistoryListView.getItems().remove(idx);
                saveHistory();
            });
            menu.getItems().add(del);
            menu.show(runHistoryListView, event.getScreenX(), event.getScreenY());
        }
    }

    @FXML
    private void onChooseJar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择JAR文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR文件", "*.jar"));
        File file = fileChooser.showOpenDialog(jarPathField.getScene().getWindow());
        if (file != null) {
            externalJarPath = file.getAbsolutePath();
            jarPathField.setText(externalJarPath);
            saveJarPath();
        }
    }

    private void saveJarPath() {
        try {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("jarPath", externalJarPath);
            Files.write(configFile.toPath(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map));
        } catch (Exception ignored) {
        }
    }

    private void loadJarPath() {
        try {
            if (configFile.exists()) {
                java.util.Map map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(configFile, java.util.Map.class);
                if (map.get("jarPath") != null) externalJarPath = map.get("jarPath").toString();
            }
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onCopyJson() {
        if (jsonArea != null) {
            String text = jsonArea.getText();
            if (text != null && !text.isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "JSON已复制到剪贴板！", ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        }
    }
} 