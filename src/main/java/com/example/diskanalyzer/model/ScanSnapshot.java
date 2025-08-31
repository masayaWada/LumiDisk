package com.example.diskanalyzer.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * スキャンスナップショットを表すモデルクラス
 * 増分スキャンで使用する
 */
public class ScanSnapshot {
  private final Path rootPath;
  private final LocalDateTime scanTime;
  private final List<FileNode> files;
  private final Map<String, Long> extensionStats;
  private final long totalSize;
  private final int totalFiles;
  private final int totalDirectories;
  private final long scanDuration;
  private final String version;

  public ScanSnapshot(Path rootPath, LocalDateTime scanTime, List<FileNode> files,
      Map<String, Long> extensionStats, long totalSize, int totalFiles,
      int totalDirectories, long scanDuration, String version) {
    this.rootPath = rootPath;
    this.scanTime = scanTime;
    this.files = files;
    this.extensionStats = extensionStats;
    this.totalSize = totalSize;
    this.totalFiles = totalFiles;
    this.totalDirectories = totalDirectories;
    this.scanDuration = scanDuration;
    this.version = version;
  }

  public Path getRootPath() {
    return rootPath;
  }

  public LocalDateTime getScanTime() {
    return scanTime;
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

  public String getVersion() {
    return version;
  }

  /**
   * このスナップショットからScanResultを作成する
   */
  public ScanResult toScanResult() {
    return new ScanResult(files, extensionStats, totalSize, totalFiles, totalDirectories, scanDuration);
  }

  @Override
  public String toString() {
    return "ScanSnapshot{" +
        "rootPath=" + rootPath +
        ", scanTime=" + scanTime +
        ", totalFiles=" + totalFiles +
        ", totalDirectories=" + totalDirectories +
        ", totalSize=" + totalSize +
        ", version='" + version + '\'' +
        '}';
  }
}
