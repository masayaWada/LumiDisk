package com.example.diskanalyzer.controller.dialog;

import com.example.diskanalyzer.model.DuplicateGroup;
import com.example.diskanalyzer.util.SizeFormatter;
import javafx.scene.control.Alert;

import java.util.List;

/**
 * 重複ファイル検出結果を表示するダイアログ。
 * 計画書の "DuplicateDialogController を抽出" に対応。
 */
public final class DuplicateDialogController {

  private DuplicateDialogController() {
    // static-only
  }

  /**
   * 重複ファイルダイアログを表示する。
   *
   * @param duplicates 検出された重複グループ一覧
   */
  public static void show(List<DuplicateGroup> duplicates) {
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

    content.append("\n総無駄容量: ").append(SizeFormatter.format(totalWastedSpace));

    dialog.setContentText(content.toString());
    dialog.showAndWait();
  }
}
