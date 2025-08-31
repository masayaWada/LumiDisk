package com.example.diskanalyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 重複ファイルのグループを表すモデルクラス
 */
public class DuplicateGroup {
  private final String hash;
  private final long fileSize;
  private final List<FileNode> files;
  private final String extension;

  public DuplicateGroup(String hash, long fileSize, String extension) {
    this.hash = hash;
    this.fileSize = fileSize;
    this.extension = extension;
    this.files = new ArrayList<>();
  }

  public String getHash() {
    return hash;
  }

  public long getFileSize() {
    return fileSize;
  }

  public String getExtension() {
    return extension;
  }

  public List<FileNode> getFiles() {
    return files;
  }

  public void addFile(FileNode file) {
    files.add(file);
  }

  public int getDuplicateCount() {
    return files.size();
  }

  public long getWastedSpace() {
    // 最初のファイル以外は重複として計算
    return fileSize * (files.size() - 1);
  }

  public String getFormattedWastedSpace() {
    long wasted = getWastedSpace();
    if (wasted < 1024) {
      return wasted + " B";
    } else if (wasted < 1024 * 1024) {
      return String.format("%.1f KB", wasted / 1024.0);
    } else if (wasted < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", wasted / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", wasted / (1024.0 * 1024.0 * 1024.0));
    }
  }

  public String getFormattedFileSize() {
    if (fileSize < 1024) {
      return fileSize + " B";
    } else if (fileSize < 1024 * 1024) {
      return String.format("%.1f KB", fileSize / 1024.0);
    } else if (fileSize < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
    }
  }

  @Override
  public String toString() {
    return "DuplicateGroup{" +
        "hash='" + hash + '\'' +
        ", fileSize=" + fileSize +
        ", duplicateCount=" + getDuplicateCount() +
        ", extension='" + extension + '\'' +
        '}';
  }
}
