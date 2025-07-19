package com.paipi.controller;

import com.paipi.config.TransformerConfig;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.*;

public class TransformerEditDialogController {
    @FXML
    private ComboBox<String> typeComboBox;
    @FXML
    private GridPane paramGrid;
    private Stage dialogStage;
    private boolean okClicked = false;
    private TransformerConfig config = new TransformerConfig();
    private String lastType = null;

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public TransformerConfig getTransformerConfig() {
        return config;
    }

    public void setTransformerConfig(TransformerConfig cfg) {
        if (cfg != null) {
            this.config = cfg;
            typeComboBox.setValue(cfg.getName());
            refreshParamGrid();
        }
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    public void initialize() {
        typeComboBox.getItems().addAll("trim", "replace", "filter", "dateformat", "md5encrypt", "zeroonetoboolean");
        // 类型切换时弹窗确认
        typeComboBox.setOnAction(e -> onTypeChange());
        refreshParamGrid();
    }

    private void onTypeChange() {
        String newType = typeComboBox.getValue();
        if (lastType == null) {
            lastType = newType;
            refreshParamGrid();
            return;
        }
        if (!Objects.equals(newType, lastType)) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "切换类型会清空已填写的参数，是否继续？", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText(null);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                config.setParameter(new HashMap<>());
                refreshParamGrid();
                lastType = newType;
            } else {
                typeComboBox.setValue(lastType);
            }
        }
    }

    private void refreshParamGrid() {
        paramGrid.getChildren().clear();
        String type = typeComboBox.getValue();
        if (type == null) return;
        config.setName(type);
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (paramArr[0] == null) {
            paramArr[0] = new HashMap<>();
            config.setParameter(paramArr[0]);
        }
        int row = 0;
        switch (type) {
            case "trim":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField, 1, row);
                colIdxField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                break;
            case "replace":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField2 = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField2, 1, row);
                colIdxField2.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                row++;
                paramGrid.add(new Label("oldValue *"), 0, row);
                TextField oldValField = new TextField((String) paramArr[0].getOrDefault("oldValue", ""));
                paramGrid.add(oldValField, 1, row);
                oldValField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("oldValue", newV));
                row++;
                paramGrid.add(new Label("newValue *"), 0, row);
                TextField newValField = new TextField((String) paramArr[0].getOrDefault("newValue", ""));
                paramGrid.add(newValField, 1, row);
                newValField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("newValue", newV));
                break;
            case "filter":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField3 = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField3, 1, row);
                colIdxField3.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                row++;
                paramGrid.add(new Label("value *"), 0, row);
                TextField valueField = new TextField((String) paramArr[0].getOrDefault("value", ""));
                paramGrid.add(valueField, 1, row);
                valueField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("value", newV));
                row++;
                paramGrid.add(new Label("op(=,!=,>,<) *"), 0, row);
                TextField opField = new TextField((String) paramArr[0].getOrDefault("op", "="));
                paramGrid.add(opField, 1, row);
                opField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("op", newV));
                break;
            case "dateformat":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField4 = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField4, 1, row);
                colIdxField4.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                row++;
                paramGrid.add(new Label("fromFormat *"), 0, row);
                TextField fromFmtField = new TextField((String) paramArr[0].getOrDefault("fromFormat", ""));
                paramGrid.add(fromFmtField, 1, row);
                fromFmtField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("fromFormat", newV));
                row++;
                paramGrid.add(new Label("toFormat *"), 0, row);
                TextField toFmtField = new TextField((String) paramArr[0].getOrDefault("toFormat", ""));
                paramGrid.add(toFmtField, 1, row);
                toFmtField.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("toFormat", newV));
                break;
            case "md5encrypt":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField5 = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField5, 1, row);
                colIdxField5.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                break;
            case "zeroonetoboolean":
                paramGrid.add(new Label("columnIndex(逗号分隔) *"), 0, row);
                TextField colIdxField6 = new TextField(paramArr[0].get("columnIndex") != null ? joinList(paramArr[0].get("columnIndex")) : "");
                paramGrid.add(colIdxField6, 1, row);
                colIdxField6.textProperty().addListener((obs, oldV, newV) -> paramArr[0].put("columnIndex", parseIntList(newV)));
                break;
        }
    }

    @FXML
    private void onOk() {
        if (!validateParams()) return;
        okClicked = true;
        if (dialogStage != null) dialogStage.close();
    }

    private boolean validateParams() {
        String type = typeComboBox.getValue();
        final Map<String, Object>[] paramArr = new Map[]{config.getParameter()};
        if (type == null) {
            showInfo("请选择类型");
            return false;
        }
        switch (type) {
            case "trim":
            case "md5encrypt":
            case "zeroonetoboolean":
                if (paramArr[0].get("columnIndex") == null || !isIntList(paramArr[0].get("columnIndex"))) {
                    showInfo("columnIndex为必填项且必须为数字列表");
                    return false;
                }
                break;
            case "replace":
                if (paramArr[0].get("columnIndex") == null || !isIntList(paramArr[0].get("columnIndex"))) {
                    showInfo("columnIndex为必填项且必须为数字列表");
                    return false;
                }
                if (paramArr[0].get("oldValue") == null || ((String) paramArr[0].get("oldValue")).trim().isEmpty()) {
                    showInfo("oldValue为必填项");
                    return false;
                }
                if (paramArr[0].get("newValue") == null || ((String) paramArr[0].get("newValue")).trim().isEmpty()) {
                    showInfo("newValue为必填项");
                    return false;
                }
                break;
            case "filter":
                if (paramArr[0].get("columnIndex") == null || !isIntList(paramArr[0].get("columnIndex"))) {
                    showInfo("columnIndex为必填项且必须为数字列表");
                    return false;
                }
                if (paramArr[0].get("value") == null || ((String) paramArr[0].get("value")).trim().isEmpty()) {
                    showInfo("value为必填项");
                    return false;
                }
                if (paramArr[0].get("op") == null || !isValidOp((String) paramArr[0].get("op"))) {
                    showInfo("op为必填项且只能为=,!=,>,<");
                    return false;
                }
                break;
            case "dateformat":
                if (paramArr[0].get("columnIndex") == null || !isIntList(paramArr[0].get("columnIndex"))) {
                    showInfo("columnIndex为必填项且必须为数字列表");
                    return false;
                }
                if (paramArr[0].get("fromFormat") == null || ((String) paramArr[0].get("fromFormat")).trim().isEmpty()) {
                    showInfo("fromFormat为必填项");
                    return false;
                }
                if (paramArr[0].get("toFormat") == null || ((String) paramArr[0].get("toFormat")).trim().isEmpty()) {
                    showInfo("toFormat为必填项");
                    return false;
                }
                break;
        }
        return true;
    }

    private boolean isIntList(Object v) {
        if (!(v instanceof List)) return false;
        for (Object o : (List<?>) v) {
            if (!(o instanceof Integer)) return false;
        }
        return !((List<?>) v).isEmpty();
    }

    private boolean isValidOp(String op) {
        return op != null && (op.equals("=") || op.equals("!=") || op.equals(">") || op.equals("<"));
    }

    @FXML
    private void onCancel() {
        okClicked = false;
        if (dialogStage != null) dialogStage.close();
    }

    private String joinList(Object v) {
        if (v instanceof List)
            return String.join(",", ((List<?>) v).stream().map(Object::toString).toArray(String[]::new));
        return v == null ? "" : v.toString();
    }

    private List<Integer> parseIntList(String s) {
        List<Integer> list = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return list;
        for (String part : s.split(",")) {
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.showAndWait();
    }
} 