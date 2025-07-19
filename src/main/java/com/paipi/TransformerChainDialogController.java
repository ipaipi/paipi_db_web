package com.paipi;

import com.paipi.config.JobConfig;
import com.paipi.config.TransformerConfig;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class TransformerChainDialogController {
    @FXML
    private TableView<TransformerConfig> transformerTable;
    @FXML
    private TableColumn<TransformerConfig, String> colIndex;
    @FXML
    private TableColumn<TransformerConfig, String> colType;
    @FXML
    private TableColumn<TransformerConfig, String> colParam;
    private ObservableList<TransformerConfig> transformerList = FXCollections.observableArrayList();
    private Stage dialogStage;
    private boolean okClicked = false;
    private JobConfig.ContentConfig contentConfig;

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setContentConfig(JobConfig.ContentConfig content) {
        this.contentConfig = content;
    }

    public java.util.List<TransformerConfig> getTransformerList() {
        return transformerList;
    }

    public void setTransformerList(java.util.List<TransformerConfig> list) {
        transformerList.setAll(list);
        transformerTable.setItems(transformerList);
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    @FXML
    public void initialize() {
        transformerTable.setItems(transformerList);
        // 设置TableColumn的cellValueFactory
        if (colIndex != null && colType != null && colParam != null) {
            colIndex.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(transformerList.indexOf(cellData.getValue()) + 1)));
            colType.setCellValueFactory(new PropertyValueFactory<>("name"));
            // 美化参数列显示
            colParam.setCellValueFactory(cellData -> new SimpleStringProperty(formatTransformerParam(cellData.getValue())));
        }
    }

    /**
     * 按类型美化参数展示
     */
    private String formatTransformerParam(TransformerConfig cfg) {
        if (cfg == null || cfg.getParameter() == null) return "";
        Map<String, Object> p = cfg.getParameter();
        String type = cfg.getName();
        switch (type) {
            case "trim":
            case "md5encrypt":
            case "zeroonetoboolean":
                return "列: " + joinList(p.get("columnIndex"));
            case "replace":
                return "列: " + joinList(p.get("columnIndex")) +
                        "，旧值: " + p.getOrDefault("oldValue", "") +
                        "，新值: " + p.getOrDefault("newValue", "");
            case "filter":
                return "列: " + joinList(p.get("columnIndex")) +
                        "，操作: " + p.getOrDefault("op", "") +
                        "，值: " + p.getOrDefault("value", "");
            case "dateformat":
                return "列: " + joinList(p.get("columnIndex")) +
                        "，from: " + p.getOrDefault("fromFormat", "") +
                        "，to: " + p.getOrDefault("toFormat", "");
            default:
                return p.toString();
        }
    }

    private String joinList(Object v) {
        if (v instanceof java.util.List)
            return String.join(",", ((java.util.List<?>) v).stream().map(Object::toString).toArray(String[]::new));
        return v == null ? "" : v.toString();
    }

    @FXML
    private void onAdd() {
        try {
            TransformerConfig config = new TransformerConfig();
            if (showEditDialog(config)) {
                TransformerConfig saved = new TransformerConfig();
                saved.setName(config.getName());
                if (config.getParameter() != null)
                    saved.setParameter(new HashMap<>(config.getParameter()));
                transformerList.add(saved);
                int idx = transformerList.size() - 1;
                transformerTable.getSelectionModel().select(idx);
                transformerTable.scrollTo(idx);
            }
        } catch (Exception e) {
            showInfo("添加Transformer失败: " + e.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        int idx = transformerTable.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showInfo("请先选中要编辑的Transformer");
            return;
        }
        try {
            TransformerConfig config = transformerList.get(idx);
            TransformerConfig copy = new TransformerConfig();
            copy.setName(config.getName());
            if (config.getParameter() != null)
                copy.setParameter(new HashMap<>(config.getParameter()));
            if (showEditDialog(copy)) {
                transformerList.set(idx, copy);
                transformerTable.getSelectionModel().select(idx);
                transformerTable.scrollTo(idx);
            }
        } catch (Exception e) {
            showInfo("编辑Transformer失败: " + e.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        int idx = transformerTable.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showInfo("请先选中要删除的Transformer");
            return;
        }
        transformerList.remove(idx);
        if (!transformerList.isEmpty()) {
            int next = Math.min(idx, transformerList.size() - 1);
            transformerTable.getSelectionModel().select(next);
        }
    }

    @FXML
    private void onMoveUp() {
        int idx = transformerTable.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return;
        TransformerConfig t = transformerList.remove(idx);
        transformerList.add(idx - 1, t);
        transformerTable.getSelectionModel().select(idx - 1);
        transformerTable.scrollTo(idx - 1);
    }

    @FXML
    private void onMoveDown() {
        int idx = transformerTable.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= transformerList.size() - 1) return;
        TransformerConfig t = transformerList.remove(idx);
        transformerList.add(idx + 1, t);
        transformerTable.getSelectionModel().select(idx + 1);
        transformerTable.scrollTo(idx + 1);
    }

    @FXML
    private void onOk() {
        okClicked = true;
        if (contentConfig != null) {
            contentConfig.setTransformer(new java.util.ArrayList<>(transformerList));
        }
        if (dialogStage != null) dialogStage.close();
    }

    @FXML
    private void onCancel() {
        okClicked = false;
        if (dialogStage != null) dialogStage.close();
    }

    private boolean showEditDialog(TransformerConfig config) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TransformerEditDialog.fxml"));
        Parent page = loader.load();
        Stage dialogStage = new Stage();
        dialogStage.setTitle("编辑Transformer参数");
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(transformerTable.getScene().getWindow());
        dialogStage.setScene(new javafx.scene.Scene(page));
        TransformerEditDialogController controller = loader.getController();
        controller.setDialogStage(dialogStage);
        controller.setTransformerConfig(config);
        dialogStage.showAndWait();
        if (controller.isOkClicked()) {
            TransformerConfig edited = controller.getTransformerConfig();
            config.setName(edited.getName());
            config.setParameter(edited.getParameter());
            return true;
        }
        return false;
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.showAndWait();
    }
} 