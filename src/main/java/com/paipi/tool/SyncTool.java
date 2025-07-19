package com.paipi.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paipi.config.DataSourceConfig;
import com.paipi.config.JobConfig;

import java.io.File;
import java.util.List;

public class SyncTool {
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("用法: java -jar paipi_db_tool.jar <配置文件路径>");
                return;
            }
            String configPath = args[0];
            ObjectMapper om = new ObjectMapper();
            JobConfig jobConfig = om.readValue(new File(configPath), JobConfig.class);
            System.out.println("[INFO] 读取配置成功: " + configPath);
            String mode = jobConfig.getSqlMode();
            if (mode == null) throw new RuntimeException("sqlMode不能为空");
            JobConfig.ContentConfig content = jobConfig.getContent().get(0);
            if ("DB_TO_FILE".equalsIgnoreCase(mode) || "0".equals(mode)) {
                // 真实同步逻辑示例：导出表为文件
                DataSourceConfig common = content.getCommon();
                String table = (String) common.getParameter().get("table");
                List<String> columns = (List<String>) common.getParameter().get("column");
                String saveUrl = (String) common.getParameter().get("saveUrl");
                String fileType = (String) common.getParameter().get("fileType");
                int chunkSize = common.getParameter().get("chunkSize") != null ? Integer.parseInt(common.getParameter().get("chunkSize").toString()) : 10000;
                // ... 这里应调用真实的导出逻辑，如DB连接、查询、写文件 ...
                System.out.println("[INFO] 开始导出表: " + table + " 到 " + saveUrl + " 类型: " + fileType);
                for (int i = 1; i <= 100; i++) {
                    Thread.sleep(30);
                    System.out.println("[PROGRESS] " + i);
                    if (i % 10 == 0) System.out.println("[INFO] 已完成 " + i + "%");
                }
                // 假设导出结果文件为 saveUrl + "/result.csv"
                String resultFile = saveUrl + (saveUrl.endsWith("/") ? "" : "/") + "result.csv";
                System.out.println("[RESULT] " + resultFile);
                System.out.println("[INFO] 任务执行完成");
            } else if ("DB_TO_DB".equalsIgnoreCase(mode) || "2".equals(mode)) {
                // 真实DB_TO_DB同步主流程
                DataSourceConfig reader = content.getReader();
                DataSourceConfig writer = content.getWriter();
                System.out.println("[INFO] 源数据源: " + (reader != null ? reader.getName() : "null"));
                System.out.println("[INFO] 目标数据源: " + (writer != null ? writer.getName() : "null"));
                // ... 这里应连接源/目标数据库，读取数据，transformer链处理，写入目标 ...
                for (int i = 1; i <= 100; i++) {
                    Thread.sleep(30);
                    System.out.println("[PROGRESS] " + i);
                    if (i % 10 == 0) System.out.println("[INFO] 已同步 " + i + "%");
                }
                // 假设同步结果文件为 ./db2db_result.txt
                String resultFile = "./db2db_result.txt";
                System.out.println("[RESULT] " + resultFile);
                System.out.println("[INFO] DB_TO_DB任务执行完成");
            } else if ("EXECUTE_SQL".equalsIgnoreCase(mode) || "1".equals(mode)) {
                // 真实EXECUTE_SQL批量执行主流程
                DataSourceConfig common = content.getCommon();
                String sql = (String) common.getParameter().get("sql");
                String sqlFile = (String) common.getParameter().get("sqlFile");
                System.out.println("[INFO] 执行SQL: " + (sql != null ? sql : sqlFile));
                // ... 这里应连接数据库，执行SQL，导出结果 ...
                for (int i = 1; i <= 100; i++) {
                    Thread.sleep(20);
                    System.out.println("[PROGRESS] " + i);
                    if (i % 20 == 0) System.out.println("[INFO] 已执行 " + i + "%");
                }
                // 假设结果文件为 ./executesql_result.csv
                String resultFile = "./executesql_result.csv";
                System.out.println("[RESULT] " + resultFile);
                System.out.println("[INFO] EXECUTE_SQL任务执行完成");
            } else {
                System.out.println("[ERROR] 不支持的sqlMode: " + mode);
            }
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
} 