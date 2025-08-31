package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import com.example.diskanalyzer.model.TreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 高度な可視化機能を提供するサービス
 */
public class VisualizationService {
  private static final Logger logger = LoggerFactory.getLogger(VisualizationService.class);

  /**
   * ファイルリストからツリーマップ用のノード構造を作成する
   */
  public TreeNode createTreeMap(ScanResult scanResult) {
    logger.info("ツリーマップ作成開始: {} ファイル", scanResult.getFiles().size());

    // ルートノードを作成
    TreeNode root = new TreeNode("Root", "", 0, true);

    // ファイルをパスでグループ化
    Map<String, List<FileNode>> pathGroups = new HashMap<>();
    for (FileNode file : scanResult.getFiles()) {
      Path path = file.getPath();
      String parentPath = path.getParent() != null ? path.getParent().toString() : "";
      pathGroups.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(file);
    }

    // ツリー構造を構築
    buildTreeStructure(root, pathGroups, scanResult.getTotalSize());

    logger.info("ツリーマップ作成完了");
    return root;
  }

  /**
   * ツリー構造を再帰的に構築する
   */
  private void buildTreeStructure(TreeNode parent, Map<String, List<FileNode>> pathGroups, long totalSize) {
    String parentPath = parent.getPath();
    List<FileNode> files = pathGroups.get(parentPath);

    if (files == null) {
      return;
    }

    // ディレクトリとファイルを分離
    List<FileNode> directories = files.stream()
        .filter(FileNode::isDirectory)
        .collect(Collectors.toList());

    List<FileNode> fileNodes = files.stream()
        .filter(file -> !file.isDirectory())
        .collect(Collectors.toList());

    // ディレクトリノードを作成
    for (FileNode dir : directories) {
      TreeNode dirNode = new TreeNode(dir.getName(), dir.getPath().toString(), 0, true);
      parent.addChild(dirNode);

      // 再帰的に子ノードを構築
      buildTreeStructure(dirNode, pathGroups, totalSize);

      // ディレクトリサイズを計算（子ノードの合計）
      long dirSize = calculateDirectorySize(dirNode);
      dirNode = new TreeNode(dir.getName(), dir.getPath().toString(), dirSize, true);
      parent.getChildren().set(parent.getChildren().size() - 1, dirNode);
    }

    // ファイルノードを作成
    for (FileNode file : fileNodes) {
      TreeNode fileNode = new TreeNode(file.getName(), file.getPath().toString(), file.getSize(), false);
      parent.addChild(fileNode);
    }

    // サイズ順でソート
    parent.getChildren().sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
  }

  /**
   * ディレクトリサイズを計算する
   */
  private long calculateDirectorySize(TreeNode node) {
    if (!node.isDirectory()) {
      return node.getSize();
    }

    long totalSize = 0;
    for (TreeNode child : node.getChildren()) {
      totalSize += calculateDirectorySize(child);
    }
    return totalSize;
  }

  /**
   * 拡張子別統計を作成する
   */
  public Map<String, ExtensionStats> createExtensionStats(ScanResult scanResult) {
    logger.info("拡張子統計作成開始");

    Map<String, ExtensionStats> stats = new HashMap<>();

    for (FileNode file : scanResult.getFiles()) {
      if (!file.isDirectory()) {
        String extension = file.getExtension();
        if (extension.isEmpty()) {
          extension = "その他";
        }

        stats.computeIfAbsent(extension, k -> new ExtensionStats(k))
            .addFile(file);
      }
    }

    logger.info("拡張子統計作成完了: {} 種類", stats.size());
    return stats;
  }

  /**
   * 拡張子統計クラス
   */
  public static class ExtensionStats {
    private final String extension;
    private int fileCount;
    private long totalSize;
    private long minSize;
    private long maxSize;
    private final List<FileNode> files;

    public ExtensionStats(String extension) {
      this.extension = extension;
      this.fileCount = 0;
      this.totalSize = 0;
      this.minSize = Long.MAX_VALUE;
      this.maxSize = 0;
      this.files = new ArrayList<>();
    }

    public void addFile(FileNode file) {
      files.add(file);
      fileCount++;
      totalSize += file.getSize();
      minSize = Math.min(minSize, file.getSize());
      maxSize = Math.max(maxSize, file.getSize());
    }

    public String getExtension() {
      return extension;
    }

    public int getFileCount() {
      return fileCount;
    }

    public long getTotalSize() {
      return totalSize;
    }

    public long getMinSize() {
      return minSize == Long.MAX_VALUE ? 0 : minSize;
    }

    public long getMaxSize() {
      return maxSize;
    }

    public double getAverageSize() {
      return fileCount > 0 ? (double) totalSize / fileCount : 0.0;
    }

    public String getFormattedTotalSize() {
      return formatSize(totalSize);
    }

    public String getFormattedMinSize() {
      return formatSize(getMinSize());
    }

    public String getFormattedMaxSize() {
      return formatSize(maxSize);
    }

    public String getFormattedAverageSize() {
      return formatSize((long) getAverageSize());
    }

    public List<FileNode> getFiles() {
      return files;
    }

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

    @Override
    public String toString() {
      return "ExtensionStats{" +
          "extension='" + extension + '\'' +
          ", fileCount=" + fileCount +
          ", totalSize=" + totalSize +
          '}';
    }
  }

  /**
   * 大容量ファイルを検出する
   */
  public List<FileNode> findLargeFiles(ScanResult scanResult, long minSize) {
    return scanResult.getFiles().stream()
        .filter(file -> !file.isDirectory())
        .filter(file -> file.getSize() >= minSize)
        .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
        .collect(Collectors.toList());
  }

  /**
   * 古いファイルを検出する（指定日数以上前）
   */
  public List<FileNode> findOldFiles(ScanResult scanResult, int daysOld) {
    long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);

    return scanResult.getFiles().stream()
        .filter(file -> !file.isDirectory())
        .filter(file -> file.getModified().toMillis() < cutoffTime)
        .sorted((a, b) -> a.getModified().compareTo(b.getModified()))
        .collect(Collectors.toList());
  }

  /**
   * 一時ファイルを検出する
   */
  public List<FileNode> findTemporaryFiles(ScanResult scanResult) {
    Set<String> tempExtensions = Set.of(
        "tmp", "temp", "log", "cache", "bak", "backup", "old", "~");

    Set<String> tempDirectories = Set.of(
        "temp", "tmp", "cache", "logs", "log", "backup", "bak");

    return scanResult.getFiles().stream()
        .filter(file -> {
          if (file.isDirectory()) {
            return tempDirectories.contains(file.getName().toLowerCase());
          } else {
            String extension = file.getExtension().toLowerCase();
            return tempExtensions.contains(extension) ||
                file.getName().toLowerCase().startsWith("~") ||
                file.getName().toLowerCase().endsWith(".tmp");
          }
        })
        .collect(Collectors.toList());
  }
}
