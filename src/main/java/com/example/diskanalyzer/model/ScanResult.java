package com.example.diskanalyzer.model;

import java.util.List;
import java.util.Map;

/**
 * スキャン結果を保持するモデルクラス
 */
public class ScanResult {
  private final List<FileNode> files;
  private final Map<String, Long> extensionStats;
  private final long totalSize;
  private final int totalFiles;
  private final int totalDirectories;
  private final long scanDuration;

  public ScanResult(List<FileNode> files, Map<String, Long> extensionStats,
      long totalSize, int totalFiles, int totalDirectories, long scanDuration) {
    this.files = files;
    this.extensionStats = extensionStats;
    this.totalSize = totalSize;
    this.totalFiles = totalFiles;
    this.totalDirectories = totalDirectories;
    this.scanDuration = scanDuration;
  }

  public List<FileNode> getFiles() {
    return files;
  }

  public Map<String, Long> getExtensionStats() {
    return extensionStats;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public int getTotalFiles() {
    return totalFiles;
  }

  public int getTotalDirectories() {
    return totalDirectories;
  }

  public long getScanDuration() {
    return scanDuration;
  }

  public String getFormattedTotalSize() {
    if (totalSize < 1024) {
      return totalSize + " B";
    } else if (totalSize < 1024 * 1024) {
      return String.format("%.1f KB", totalSize / 1024.0);
    } else if (totalSize < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", totalSize / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", totalSize / (1024.0 * 1024.0 * 1024.0));
    }
  }

  public String getFormattedScanDuration() {
    if (scanDuration < 1000) {
      return scanDuration + " ms";
    } else {
      return String.format("%.1f s", scanDuration / 1000.0);
    }
  }
}
