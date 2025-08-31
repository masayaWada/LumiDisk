package com.example.diskanalyzer.controller;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import com.example.diskanalyzer.model.DuplicateGroup;
import com.example.diskanalyzer.model.TreeNode;
import com.example.diskanalyzer.service.ExportService;
import com.example.diskanalyzer.service.FileDeleteService;
import com.example.diskanalyzer.service.FileManagerService;
import com.example.diskanalyzer.service.DuplicateDetectionService;
import com.example.diskanalyzer.service.IncrementalScanService;
import com.example.diskanalyzer.service.VisualizationService;
import com.example.diskanalyzer.controller.VirtualizedTableController;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.util.Map;
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
  private Button showInFinderButton;
  @FXML
  private Button deleteButton;
  @FXML
  private Button exportCsvButton;
  @FXML
  private Button exportJsonButton;
  @FXML
  private Button findDuplicatesButton;
  @FXML
  private Button incrementalScanButton;
  @FXML
  private Button treeMapButton;
  @FXML
  private Button extensionStatsButton;
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
  private final FileDeleteService deleteService = new FileDeleteService();
  private final FileManagerService fileManagerService = new FileManagerService();
  private final DuplicateDetectionService duplicateService = new DuplicateDetectionService();
  private final IncrementalScanService incrementalService = new IncrementalScanService();
  private final VisualizationService visualizationService = new VisualizationService();
  private VirtualizedTableController virtualizedTableController;

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

    // テーブルの選択変更リスナー
    fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
      boolean hasSelection = newSelection != null;
      deleteButton.setDisable(!hasSelection || !deleteService.canDelete(newSelection));
      showInFinderButton.setDisable(!hasSelection || !fileManagerService.canShowInFileManager(newSelection));
    });

    // 表示件数ComboBoxの設定
    displayCountComboBox.setItems(FXCollections.observableArrayList("10", "25", "50", "100", "ALL"));
    displayCountComboBox.setValue("10"); // デフォルト値
    displayCountComboBox.setOnAction(this::handleDisplayCountChange);

    // 初期状態の設定
    scanButton.setDisable(true);
    exportCsvButton.setDisable(true);
    exportJsonButton.setDisable(true);
    findDuplicatesButton.setDisable(true);
    incrementalScanButton.setDisable(true);
    treeMapButton.setDisable(true);
    extensionStatsButton.setDisable(true);
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
      findDuplicatesButton.setDisable(false);
      incrementalScanButton.setDisable(false);
      treeMapButton.setDisable(false);
      extensionStatsButton.setDisable(false);

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

    // 仮想化テーブルコントローラーを初期化
    if (virtualizedTableController == null) {
      virtualizedTableController = new VirtualizedTableController(fileTable, currentScanResult.getFiles());
    } else {
      virtualizedTableController.updateData(currentScanResult.getFiles());
    }

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

  /**
   * Finder/エクスプローラーで表示イベントハンドラー
   */
  @FXML
  private void handleShowInFinder(ActionEvent event) {
    FileNode selectedFile = fileTable.getSelectionModel().getSelectedItem();
    if (selectedFile == null) {
      return;
    }

    try {
      statusLabel.textProperty().unbind();
      statusLabel.setText("ファイルマネージャーで表示中...");

      boolean success = fileManagerService.showInFileManager(selectedFile);

      if (success) {
        statusLabel.setText("ファイルマネージャーで表示しました: " + selectedFile.getName());
        logger.info("ファイルマネージャー表示成功: {}", selectedFile.getPath());
      } else {
        statusLabel.setText("ファイルマネージャー表示に失敗しました: " + selectedFile.getName());
        logger.error("ファイルマネージャー表示失敗: {}", selectedFile.getPath());

        Alert errorDialog = new Alert(Alert.AlertType.ERROR);
        errorDialog.setTitle("表示エラー");
        errorDialog.setHeaderText("ファイルマネージャーでの表示に失敗しました");
        errorDialog.setContentText("ファイルマネージャーが利用できないか、ファイルにアクセスできません。");
        errorDialog.showAndWait();
      }
    } catch (Exception e) {
      statusLabel.setText("ファイルマネージャー表示中にエラーが発生しました");
      logger.error("ファイルマネージャー表示中にエラーが発生", e);

      Alert errorDialog = new Alert(Alert.AlertType.ERROR);
      errorDialog.setTitle("表示エラー");
      errorDialog.setHeaderText("予期しないエラーが発生しました");
      errorDialog.setContentText("エラー: " + e.getMessage());
      errorDialog.showAndWait();
    }
  }

  /**
   * ファイル削除イベントハンドラー
   */
  @FXML
  private void handleDelete(ActionEvent event) {
    FileNode selectedFile = fileTable.getSelectionModel().getSelectedItem();
    if (selectedFile == null) {
      return;
    }

    // 削除確認ダイアログ
    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
    confirmDialog.setTitle("ファイル削除確認");
    confirmDialog.setHeaderText("ファイルを削除しますか？");
    confirmDialog.setContentText(String.format(
        "削除対象: %s\nパス: %s\nサイズ: %s\n\nこの操作は取り消せません。",
        selectedFile.getName(),
        selectedFile.getPath(),
        selectedFile.getFormattedSize()));

    confirmDialog.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
    confirmDialog.showAndWait().ifPresent(buttonType -> {
      if (buttonType == ButtonType.YES) {
        deleteFile(selectedFile);
      }
    });
  }

  /**
   * ファイルテーブルを更新する
   */
  private void updateFileTable() {
    if (currentScanResult != null) {
      ObservableList<FileNode> fileList = FXCollections.observableArrayList(currentScanResult.getFiles());
      fileTable.setItems(fileList);
    }
  }

  /**
   * ファイル削除を実行する
   */
  private void deleteFile(FileNode fileNode) {
    try {
      statusLabel.textProperty().unbind();
      statusLabel.setText("ファイルを削除中...");

      boolean success = deleteService.deleteFile(fileNode);

      if (success) {
        statusLabel.setText("ファイルを削除しました: " + fileNode.getName());
        logger.info("ファイル削除成功: {}", fileNode.getPath());

        // スキャン結果から削除されたファイルを除去
        if (currentScanResult != null) {
          currentScanResult.getFiles().remove(fileNode);
          updatePieChart();
          updateFileTable();
        }
      } else {
        statusLabel.setText("ファイル削除に失敗しました: " + fileNode.getName());
        logger.error("ファイル削除失敗: {}", fileNode.getPath());

        Alert errorDialog = new Alert(Alert.AlertType.ERROR);
        errorDialog.setTitle("削除エラー");
        errorDialog.setHeaderText("ファイルの削除に失敗しました");
        errorDialog.setContentText("ファイルが使用中か、権限が不足している可能性があります。");
        errorDialog.showAndWait();
      }
    } catch (Exception e) {
      statusLabel.setText("ファイル削除中にエラーが発生しました");
      logger.error("ファイル削除中にエラーが発生", e);

      Alert errorDialog = new Alert(Alert.AlertType.ERROR);
      errorDialog.setTitle("削除エラー");
      errorDialog.setHeaderText("予期しないエラーが発生しました");
      errorDialog.setContentText("エラー: " + e.getMessage());
      errorDialog.showAndWait();
    }
  }

  /**
   * 重複ファイル検出イベントハンドラー
   */
  @FXML
  private void handleFindDuplicates(ActionEvent event) {
    if (currentScanResult == null) {
      return;
    }

    logger.info("重複ファイル検出開始");
    statusLabel.setText("重複ファイルを検出中...");
    findDuplicatesButton.setDisable(true);

    // バックグラウンドで重複検出を実行
    Task<List<DuplicateGroup>> duplicateTask = new Task<List<DuplicateGroup>>() {
      @Override
      protected List<DuplicateGroup> call() throws Exception {
        updateMessage("重複ファイルを検出中...");
        return duplicateService.findDuplicates(currentScanResult.getFiles());
      }
    };

    duplicateTask.setOnSucceeded(e -> {
      List<DuplicateGroup> duplicates = duplicateTask.getValue();
      Platform.runLater(() -> {
        findDuplicatesButton.setDisable(false);
        statusLabel.setText("重複ファイル検出完了: " + duplicates.size() + " グループ");

        // 重複ファイルダイアログを表示
        showDuplicateDialog(duplicates);
      });
    });

    duplicateTask.setOnFailed(e -> {
      Platform.runLater(() -> {
        findDuplicatesButton.setDisable(false);
        statusLabel.setText("重複ファイル検出エラー: " + duplicateTask.getException().getMessage());
        logger.error("重複ファイル検出エラー", duplicateTask.getException());
      });
    });

    Thread duplicateThread = new Thread(duplicateTask);
    duplicateThread.setDaemon(true);
    duplicateThread.start();
  }

  /**
   * 増分スキャンイベントハンドラー
   */
  @FXML
  private void handleIncrementalScan(ActionEvent event) {
    if (selectedPath == null) {
      return;
    }

    logger.info("増分スキャン開始: {}", selectedPath);
    statusLabel.setText("増分スキャン中...");
    incrementalScanButton.setDisable(true);

    Task<ScanResult> incrementalTask = new Task<ScanResult>() {
      @Override
      protected ScanResult call() throws Exception {
        updateMessage("増分スキャン中...");
        return incrementalService.incrementalScan(selectedPath);
      }
    };

    incrementalTask.setOnSucceeded(e -> {
      ScanResult result = incrementalTask.getValue();
      Platform.runLater(() -> {
        currentScanResult = result;
        updateUI();
        incrementalScanButton.setDisable(false);
        statusLabel.setText("増分スキャン完了");
        scanInfoLabel.setText(String.format(
            "ファイル: %d件, ディレクトリ: %d件, 総サイズ: %s, 所要時間: %s",
            result.getTotalFiles(),
            result.getTotalDirectories(),
            result.getFormattedTotalSize(),
            result.getFormattedScanDuration()));
      });
    });

    incrementalTask.setOnFailed(e -> {
      Platform.runLater(() -> {
        incrementalScanButton.setDisable(false);
        statusLabel.setText("増分スキャンエラー: " + incrementalTask.getException().getMessage());
        logger.error("増分スキャンエラー", incrementalTask.getException());
      });
    });

    Thread incrementalThread = new Thread(incrementalTask);
    incrementalThread.setDaemon(true);
    incrementalThread.start();
  }

  /**
   * ツリーマップ表示イベントハンドラー
   */
  @FXML
  private void handleTreeMap(ActionEvent event) {
    if (currentScanResult == null) {
      return;
    }

    logger.info("ツリーマップ作成開始");
    statusLabel.setText("ツリーマップを作成中...");
    treeMapButton.setDisable(true);

    Task<TreeNode> treeMapTask = new Task<TreeNode>() {
      @Override
      protected TreeNode call() throws Exception {
        updateMessage("ツリーマップを作成中...");
        return visualizationService.createTreeMap(currentScanResult);
      }
    };

    treeMapTask.setOnSucceeded(e -> {
      TreeNode rootNode = treeMapTask.getValue();
      Platform.runLater(() -> {
        treeMapButton.setDisable(false);
        statusLabel.setText("ツリーマップ作成完了");

        // ツリーマップダイアログを表示
        showTreeMapDialog(rootNode);
      });
    });

    treeMapTask.setOnFailed(e -> {
      Platform.runLater(() -> {
        treeMapButton.setDisable(false);
        statusLabel.setText("ツリーマップ作成エラー: " + treeMapTask.getException().getMessage());
        logger.error("ツリーマップ作成エラー", treeMapTask.getException());
      });
    });

    Thread treeMapThread = new Thread(treeMapTask);
    treeMapThread.setDaemon(true);
    treeMapThread.start();
  }

  /**
   * 拡張子統計表示イベントハンドラー
   */
  @FXML
  private void handleExtensionStats(ActionEvent event) {
    if (currentScanResult == null) {
      return;
    }

    logger.info("拡張子統計作成開始");
    statusLabel.setText("拡張子統計を作成中...");
    extensionStatsButton.setDisable(true);

    Task<Map<String, VisualizationService.ExtensionStats>> statsTask = new Task<Map<String, VisualizationService.ExtensionStats>>() {
      @Override
      protected Map<String, VisualizationService.ExtensionStats> call() throws Exception {
        updateMessage("拡張子統計を作成中...");
        return visualizationService.createExtensionStats(currentScanResult);
      }
    };

    statsTask.setOnSucceeded(e -> {
      Map<String, VisualizationService.ExtensionStats> stats = statsTask.getValue();
      Platform.runLater(() -> {
        extensionStatsButton.setDisable(false);
        statusLabel.setText("拡張子統計作成完了: " + stats.size() + " 種類");

        // 拡張子統計ダイアログを表示
        showExtensionStatsDialog(stats);
      });
    });

    statsTask.setOnFailed(e -> {
      Platform.runLater(() -> {
        extensionStatsButton.setDisable(false);
        statusLabel.setText("拡張子統計作成エラー: " + statsTask.getException().getMessage());
        logger.error("拡張子統計作成エラー", statsTask.getException());
      });
    });

    Thread statsThread = new Thread(statsTask);
    statsThread.setDaemon(true);
    statsThread.start();
  }

  /**
   * 重複ファイルダイアログを表示する
   */
  private void showDuplicateDialog(List<DuplicateGroup> duplicates) {
    Alert dialog = new Alert(Alert.AlertType.INFORMATION);
    dialog.setTitle("重複ファイル検出結果");
    dialog.setHeaderText("重複ファイルが見つかりました");

    StringBuilder content = new StringBuilder();
    content.append("重複グループ数: ").append(duplicates.size()).append("\n\n");

    long totalWastedSpace = 0;
    for (DuplicateGroup group : duplicates) {
      totalWastedSpace += group.getWastedSpace();
      content.append("• ").append(group.getExtension()).append(" ファイル (")
          .append(group.getDuplicateCount()).append(" 件): ")
          .append(group.getFormattedWastedSpace()).append(" の無駄\n");
    }

    content.append("\n総無駄容量: ").append(formatSize(totalWastedSpace));

    dialog.setContentText(content.toString());
    dialog.showAndWait();
  }

  /**
   * ツリーマップダイアログを表示する
   */
  private void showTreeMapDialog(TreeNode rootNode) {
    Alert dialog = new Alert(Alert.AlertType.INFORMATION);
    dialog.setTitle("ツリーマップ");
    dialog.setHeaderText("ディレクトリ構造の可視化");

    StringBuilder content = new StringBuilder();
    content.append("ルートディレクトリ: ").append(rootNode.getName()).append("\n");
    content.append("総サイズ: ").append(rootNode.getFormattedSize()).append("\n\n");

    // 上位10個のディレクトリを表示
    List<TreeNode> topDirectories = rootNode.getChildren().stream()
        .filter(TreeNode::isDirectory)
        .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
        .limit(10)
        .collect(java.util.stream.Collectors.toList());

    content.append("上位ディレクトリ:\n");
    for (TreeNode dir : topDirectories) {
      content.append("• ").append(dir.getName()).append(": ")
          .append(dir.getFormattedSize()).append(" (")
          .append(String.format("%.1f", dir.getSizePercentage(rootNode.getSize())))
          .append("%)\n");
    }

    dialog.setContentText(content.toString());
    dialog.showAndWait();
  }

  /**
   * 拡張子統計ダイアログを表示する
   */
  private void showExtensionStatsDialog(Map<String, VisualizationService.ExtensionStats> stats) {
    Alert dialog = new Alert(Alert.AlertType.INFORMATION);
    dialog.setTitle("拡張子統計");
    dialog.setHeaderText("ファイルタイプ別統計");

    StringBuilder content = new StringBuilder();

    // サイズ順でソート
    List<VisualizationService.ExtensionStats> sortedStats = stats.values().stream()
        .sorted((a, b) -> Long.compare(b.getTotalSize(), a.getTotalSize()))
        .limit(15)
        .collect(java.util.stream.Collectors.toList());

    content.append("上位ファイルタイプ:\n\n");
    for (VisualizationService.ExtensionStats stat : sortedStats) {
      content.append("• .").append(stat.getExtension()).append(": ")
          .append(stat.getFileCount()).append(" ファイル, ")
          .append(stat.getFormattedTotalSize()).append("\n");
    }

    dialog.setContentText(content.toString());
    dialog.showAndWait();
  }

  /**
   * サイズをフォーマットする
   */
  private String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
  }
}
