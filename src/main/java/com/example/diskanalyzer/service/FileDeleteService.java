package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ファイル・ディレクトリ削除サービス
 */
public class FileDeleteService {
  private static final Logger logger = LoggerFactory.getLogger(FileDeleteService.class);

  /**
   * ファイル・ディレクトリを削除する
   * 
   * @param fileNode 削除対象のファイルノード
   * @return 削除に成功した場合true
   */
  public boolean deleteFile(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      if (fileNode.isDirectory()) {
        // ディレクトリの場合は再帰的に削除
        deleteDirectoryRecursively(path);
        logger.info("ディレクトリを削除しました: {}", path);
      } else {
        // ファイルの場合は直接削除
        Files.delete(path);
        logger.info("ファイルを削除しました: {}", path);
      }

      return true;
    } catch (IOException e) {
      logger.error("ファイル削除に失敗しました: {}", fileNode.getPath(), e);
      return false;
    }
  }

  /**
   * 複数のファイル・ディレクトリを削除する
   * 
   * @param fileNodes 削除対象のファイルノードリスト
   * @return 削除に成功したファイル数
   */
  public int deleteFiles(List<FileNode> fileNodes) {
    int successCount = 0;

    for (FileNode fileNode : fileNodes) {
      if (deleteFile(fileNode)) {
        successCount++;
      }
    }

    logger.info("ファイル削除完了: {}/{} 件成功", successCount, fileNodes.size());
    return successCount;
  }

  /**
   * ディレクトリを再帰的に削除する
   * 
   * @param directory 削除対象ディレクトリ
   * @throws IOException 削除に失敗した場合
   */
  private void deleteDirectoryRecursively(Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walk(directory)
          .sorted((a, b) -> b.compareTo(a)) // 逆順でソート（ファイル→ディレクトリの順）
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              logger.warn("ファイル削除に失敗しました: {}", path, e);
            }
          });
    }
  }

  /**
   * ファイルが削除可能かどうかをチェックする
   * 
   * @param fileNode チェック対象のファイルノード
   * @return 削除可能な場合true
   */
  public boolean canDelete(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      // ファイルが存在するかチェック
      if (!Files.exists(path)) {
        return false;
      }

      // 書き込み権限があるかチェック
      if (!Files.isWritable(path.getParent())) {
        return false;
      }

      // システムファイルや重要なディレクトリは削除不可
      if (isSystemFile(path)) {
        return false;
      }

      return true;
    } catch (Exception e) {
      logger.warn("ファイル削除可能性チェックに失敗: {}", fileNode.getPath(), e);
      return false;
    }
  }

  /**
   * システムファイルかどうかを判定する
   * 
   * @param path チェック対象のパス
   * @return システムファイルの場合true
   */
  private boolean isSystemFile(Path path) {
    String pathString = path.toString().toLowerCase();

    // Windowsのシステムファイル
    if (pathString.contains("system32") ||
        pathString.contains("windows") ||
        pathString.contains("program files")) {
      return true;
    }

    // macOSのシステムファイル
    if (pathString.contains("/system/") ||
        pathString.contains("/library/") ||
        pathString.contains("/usr/") ||
        pathString.contains("/bin/") ||
        pathString.contains("/sbin/")) {
      return true;
    }

    // Linuxのシステムファイル
    if (pathString.startsWith("/etc/") ||
        pathString.startsWith("/var/") ||
        pathString.startsWith("/usr/") ||
        pathString.startsWith("/bin/") ||
        pathString.startsWith("/sbin/")) {
      return true;
    }

    return false;
  }
}
