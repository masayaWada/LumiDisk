package com.example.diskanalyzer.controller;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import com.example.diskanalyzer.service.ExportService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.collections.FXCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

/**
 * メイン画面のコントローラー
 */
public class MainController implements Initializable {
  private static final Logger logger = LoggerFactory.getLogger(MainController.class);

  @FXML
  private Button selectDirectoryButton;
  @FXML
  private Button scanButton;
  @FXML
  private Button exportCsvButton;
  @FXML
  private Button exportJsonButton;
  @FXML
  private ProgressBar progressBar;
  @FXML
  private Label statusLabel;
  @FXML
  private Label scanInfoLabel;
  @FXML
  private PieChart pieChart;
  @FXML
  private ComboBox<String> displayCountComboBox;
  @FXML
  private TableView<FileNode> fileTable;
  @FXML
  private TableColumn<FileNode, String> nameColumn;
  @FXML
  private TableColumn<FileNode, String> pathColumn;
  @FXML
  private TableColumn<FileNode, String> sizeColumn;
  @FXML
  private TableColumn<FileNode, String> typeColumn;
  @FXML
  private TableColumn<FileNode, String> modifiedColumn;

  private Path selectedPath;
  private ScanResult currentScanResult;
  private final ExportService exportService = new ExportService();

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    logger.info("MainController初期化");

    // テーブルカラムの設定
    nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    pathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
    sizeColumn.setCellValueFactory(cellData -> {
      FileNode file = cellData.getValue();
      return new javafx.beans.property.SimpleStringProperty(file.getFormattedSize());
    });
    typeColumn.setCellValueFactory(cellData -> {
      FileNode file = cellData.getValue();
      return new javafx.beans.property.SimpleStringProperty(
          file.isDirectory() ? "ディレクトリ" : "ファイル");
    });
    modifiedColumn.setCellValueFactory(cellData -> {
      FileNode file = cellData.getValue();
      return new javafx.beans.property.SimpleStringProperty(
          file.getModifiedDateTime().toString());
    });

    // サイズカラムのソート設定（実際のバイト数でソート）
    sizeColumn.setComparator((size1, size2) -> {
      // フォーマットされたサイズ文字列から実際のバイト数を取得して比較
      long bytes1 = parseSizeToBytes(size1);
      long bytes2 = parseSizeToBytes(size2);
      return Long.compare(bytes1, bytes2);
    });

    // 表示件数ComboBoxの設定
    displayCountComboBox.setItems(FXCollections.observableArrayList("10", "25", "50", "100", "ALL"));
    displayCountComboBox.setValue("10"); // デフォルト値
    displayCountComboBox.setOnAction(this::handleDisplayCountChange);

    // 初期状態の設定
    scanButton.setDisable(true);
    exportCsvButton.setDisable(true);
    exportJsonButton.setDisable(true);
    progressBar.setVisible(false);

    statusLabel.setText("ディレクトリを選択してください");
  }

  @FXML
  private void handleSelectDirectory(ActionEvent event) {
    logger.info("ディレクトリ選択ダイアログを開く");

    DirectoryChooser directoryChooser = new DirectoryChooser();
    directoryChooser.setTitle("スキャン対象ディレクトリを選択");

    if (selectedPath != null) {
      directoryChooser.setInitialDirectory(selectedPath.toFile());
    }

    File selectedDirectory = directoryChooser.showDialog(selectDirectoryButton.getScene().getWindow());
    if (selectedDirectory != null) {
      selectedPath = selectedDirectory.toPath();
      selectDirectoryButton.setText(selectedPath.toString());
      scanButton.setDisable(false);
      statusLabel.textProperty().unbind();
      statusLabel.setText("スキャン対象: " + selectedPath.toString());
      logger.info("ディレクトリ選択: {}", selectedPath);
    }
  }

  @FXML
  private void handleScan(ActionEvent event) {
    if (selectedPath == null) {
      return;
    }

    logger.info("スキャン開始: {}", selectedPath);

    scanButton.setDisable(true);
    progressBar.setVisible(true);
    statusLabel.textProperty().unbind();
    statusLabel.setText("スキャン中...");

    ScanTask scanTask = new ScanTask(selectedPath);

    scanTask.setOnSucceeded(this::handleScanSucceeded);
    scanTask.setOnFailed(this::handleScanFailed);
    scanTask.setOnCancelled(this::handleScanCancelled);

    progressBar.progressProperty().bind(scanTask.progressProperty());
    statusLabel.textProperty().bind(scanTask.messageProperty());

    Thread scanThread = new Thread(scanTask);
    scanThread.setDaemon(true);
    scanThread.start();
  }

  private void handleScanSucceeded(WorkerStateEvent event) {
    logger.info("スキャン成功");

    Platform.runLater(() -> {
      currentScanResult = (ScanResult) event.getSource().getValue();
      updateUI();

      scanButton.setDisable(false);
      progressBar.setVisible(false);
      exportCsvButton.setDisable(false);
      exportJsonButton.setDisable(false);

      statusLabel.textProperty().unbind();
      statusLabel.setText("スキャン完了");
      scanInfoLabel.setText(String.format(
          "ファイル: %d件, ディレクトリ: %d件, 総サイズ: %s, 所要時間: %s",
          currentScanResult.getTotalFiles(),
          currentScanResult.getTotalDirectories(),
          currentScanResult.getFormattedTotalSize(),
          currentScanResult.getFormattedScanDuration()));
    });
  }

  private void handleScanFailed(WorkerStateEvent event) {
    logger.error("スキャン失敗", event.getSource().getException());

    Platform.runLater(() -> {
      scanButton.setDisable(false);
      progressBar.setVisible(false);
      statusLabel.textProperty().unbind();
      statusLabel.setText("スキャン失敗: " + event.getSource().getException().getMessage());
    });
  }

  private void handleScanCancelled(WorkerStateEvent event) {
    logger.info("スキャンキャンセル");

    Platform.runLater(() -> {
      scanButton.setDisable(false);
      progressBar.setVisible(false);
      statusLabel.textProperty().unbind();
      statusLabel.setText("スキャンがキャンセルされました");
    });
  }

  private void updateUI() {
    if (currentScanResult == null) {
      return;
    }

    // テーブル更新
    ObservableList<FileNode> fileList = FXCollections.observableArrayList(currentScanResult.getFiles());
    fileTable.setItems(fileList);

    // 円グラフ更新（上位10件）
    updatePieChart();
  }

  private void updatePieChart() {
    if (currentScanResult == null) {
      return;
    }

    List<FileNode> files = currentScanResult.getFiles();
    ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

    // 表示件数を取得
    int displayCount = getDisplayCount();

    // ファイルとディレクトリの両方を対象に、サイズ順でソート
    var sortedFiles = files.stream()
        .filter(file -> file.getSize() > 0) // サイズが0より大きいもののみ
        .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
        .toList();

    // 表示件数に応じて制限
    var limitedFiles = displayCount > 0 ? sortedFiles.stream().limit(displayCount) : sortedFiles.stream();

    limitedFiles.forEach(file -> {
      String name = file.getName();
      if (name.length() > 20) {
        name = name.substring(0, 17) + "..."; // 長い名前は省略
      }
      double sizeInMB = file.getSize() / (1024.0 * 1024.0);
      pieChartData.add(new PieChart.Data(name, sizeInMB));
    });

    pieChart.setData(pieChartData);

    // タイトルを動的に更新
    String title = displayCount > 0 ? String.format("ファイル・ディレクトリサイズ分布 (上位%d件)", displayCount) : "ファイル・ディレクトリサイズ分布 (全件)";
    pieChart.setTitle(title);
  }

  @FXML
  private void handleExportCsv(ActionEvent event) {
    if (currentScanResult == null) {
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("CSVファイルを保存");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
    fileChooser.setInitialFileName("disk_analysis.csv");

    File file = fileChooser.showSaveDialog(exportCsvButton.getScene().getWindow());
    if (file != null) {
      try {
        exportService.exportToCsv(currentScanResult, file.toPath());
        statusLabel.textProperty().unbind();
        statusLabel.setText("CSVエクスポート完了: " + file.getName());
        logger.info("CSVエクスポート完了: {}", file.getAbsolutePath());
      } catch (Exception e) {
        logger.error("CSVエクスポートエラー", e);
        statusLabel.textProperty().unbind();
        statusLabel.setText("CSVエクスポートエラー: " + e.getMessage());
      }
    }
  }

  @FXML
  private void handleExportJson(ActionEvent event) {
    if (currentScanResult == null) {
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("JSONファイルを保存");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("JSON Files", "*.json"));
    fileChooser.setInitialFileName("disk_analysis.json");

    File file = fileChooser.showSaveDialog(exportJsonButton.getScene().getWindow());
    if (file != null) {
      try {
        exportService.exportToJson(currentScanResult, file.toPath());
        statusLabel.textProperty().unbind();
        statusLabel.setText("JSONエクスポート完了: " + file.getName());
        logger.info("JSONエクスポート完了: {}", file.getAbsolutePath());
      } catch (Exception e) {
        logger.error("JSONエクスポートエラー", e);
        statusLabel.textProperty().unbind();
        statusLabel.setText("JSONエクスポートエラー: " + e.getMessage());
      }
    }
  }

  /**
   * 表示件数ComboBoxの変更イベントハンドラー
   */
  @FXML
  private void handleDisplayCountChange(ActionEvent event) {
    if (currentScanResult != null) {
      updatePieChart();
    }
  }

  /**
   * 現在選択されている表示件数を取得
   */
  private int getDisplayCount() {
    String selectedValue = displayCountComboBox.getValue();
    if (selectedValue == null || "ALL".equals(selectedValue)) {
      return -1; // 全件表示
    }
    try {
      return Integer.parseInt(selectedValue);
    } catch (NumberFormatException e) {
      logger.warn("表示件数の解析に失敗: {}", selectedValue);
      return 10; // デフォルト値
    }
  }

  /**
   * フォーマットされたサイズ文字列をバイト数に変換
   */
  private long parseSizeToBytes(String formattedSize) {
    if (formattedSize == null || formattedSize.trim().isEmpty()) {
      return 0;
    }

    String size = formattedSize.trim().toUpperCase();
    try {
      if (size.endsWith(" GB")) {
        double gb = Double.parseDouble(size.replace(" GB", ""));
        return (long) (gb * 1024 * 1024 * 1024);
      } else if (size.endsWith(" MB")) {
        double mb = Double.parseDouble(size.replace(" MB", ""));
        return (long) (mb * 1024 * 1024);
      } else if (size.endsWith(" KB")) {
        double kb = Double.parseDouble(size.replace(" KB", ""));
        return (long) (kb * 1024);
      } else if (size.endsWith(" B")) {
        return Long.parseLong(size.replace(" B", ""));
      } else {
        // 数値のみの場合はバイトとして扱う
        return Long.parseLong(size);
      }
    } catch (NumberFormatException e) {
      logger.warn("サイズ文字列の解析に失敗: {}", formattedSize);
      return 0;
    }
  }
}
