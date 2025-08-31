package com.example.diskanalyzer.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ファイル・ディレクトリの情報を保持するモデルクラス
 */
public class FileNode {
  private final Path path;
  private final long size;
  private final FileTime modified;
  private final boolean isDirectory;
  private final boolean isHidden;
  private final String extension;
  private String hash; // 重複検出用のハッシュ値

  public FileNode(Path path, long size, FileTime modified, boolean isDirectory, boolean isHidden) {
    this.path = path;
    this.size = size;
    this.modified = modified;
    this.isDirectory = isDirectory;
    this.isHidden = isHidden;
    this.extension = isDirectory ? "" : getFileExtension(path.getFileName().toString());
  }

  public Path getPath() {
    return path;
  }

  public long getSize() {
    return size;
  }

  public FileTime getModified() {
    return modified;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public boolean isHidden() {
    return isHidden;
  }

  public String getExtension() {
    return extension;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public String getName() {
    return path.getFileName().toString();
  }

  public LocalDateTime getModifiedDateTime() {
    return LocalDateTime.ofInstant(modified.toInstant(), ZoneId.systemDefault());
  }

  public String getFormattedSize() {
    if (size < 1024) {
      return size + " B";
    } else if (size < 1024 * 1024) {
      return String.format("%.1f KB", size / 1024.0);
    } else if (size < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", size / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
  }

  private String getFileExtension(String fileName) {
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
      return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
    return "";
  }

  @Override
  public String toString() {
    return "FileNode{" +
        "path=" + path +
        ", size=" + size +
        ", isDirectory=" + isDirectory +
        ", extension='" + extension + '\'' +
        '}';
  }
}
