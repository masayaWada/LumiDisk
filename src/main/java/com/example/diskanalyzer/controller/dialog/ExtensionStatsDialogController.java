package com.example.diskanalyzer.controller.dialog;

import com.example.diskanalyzer.service.VisualizationService;
import javafx.scene.control.Alert;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 拡張子別統計を表示するダイアログ。
 * 計画書の "ExtensionStatsDialogController を抽出" に対応。
 */
public final class ExtensionStatsDialogController {

  private static final int TOP_EXTENSION_LIMIT = 15;

  private ExtensionStatsDialogController() {
    // static-only
  }

  /**
   * 拡張子統計ダイアログを表示する。
   *
   * @param stats 拡張子ごとの統計マップ
   */
  public static void show(Map<String, VisualizationService.ExtensionStats> stats) {
    Alert dialog = new Alert(Alert.AlertType.INFORMATION);
    dialog.setTitle("拡張子統計");
    dialog.setHeaderText("ファイルタイプ別統計");

    StringBuilder content = new StringBuilder();

    List<VisualizationService.ExtensionStats> sortedStats = stats.values().stream()
        .sorted((a, b) -> Long.compare(b.getTotalSize(), a.getTotalSize()))
        .limit(TOP_EXTENSION_LIMIT)
        .collect(Collectors.toList());

    content.append("上位ファイルタイプ:\n\n");
    for (VisualizationService.ExtensionStats stat : sortedStats) {
      content.append("• .").append(stat.getExtension()).append(": ")
          .append(stat.getFileCount()).append(" ファイル, ")
          .append(stat.getFormattedTotalSize()).append("\n");
    }

    dialog.setContentText(content.toString());
    dialog.showAndWait();
  }
}
