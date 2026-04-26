package com.example.diskanalyzer.controller.dialog;

import com.example.diskanalyzer.model.TreeNode;
import javafx.scene.control.Alert;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ツリーマップ (上位ディレクトリの可視化) を表示するダイアログ。
 * 計画書の "TreeMapDialogController を抽出" に対応。
 */
public final class TreeMapDialogController {

  private static final int TOP_DIRECTORY_LIMIT = 10;

  private TreeMapDialogController() {
    // static-only
  }

  /**
   * ツリーマップダイアログを表示する。
   *
   * @param rootNode 集計済みツリーのルート
   */
  public static void show(TreeNode rootNode) {
    Alert dialog = new Alert(Alert.AlertType.INFORMATION);
    dialog.setTitle("ツリーマップ");
    dialog.setHeaderText("ディレクトリ構造の可視化");

    StringBuilder content = new StringBuilder();
    content.append("ルートディレクトリ: ").append(rootNode.getName()).append("\n");
    content.append("総サイズ: ").append(rootNode.getFormattedSize()).append("\n\n");

    List<TreeNode> topDirectories = rootNode.getChildren().stream()
        .filter(TreeNode::isDirectory)
        .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
        .limit(TOP_DIRECTORY_LIMIT)
        .collect(Collectors.toList());

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
}
