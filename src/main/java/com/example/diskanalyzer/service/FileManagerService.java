package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ファイルマネージャー表示サービス
 * OS別のファイルマネージャーでファイル・フォルダを表示する
 */
public class FileManagerService {
  private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);

  /**
   * ファイルマネージャーでファイル・フォルダを表示する
   * 
   * @param fileNode 表示対象のファイルノード
   * @return 表示に成功した場合true
   */
  public boolean showInFileManager(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      if (!Files.exists(path)) {
        logger.warn("ファイルが存在しません: {}", path);
        return false;
      }

      String osName = System.getProperty("os.name").toLowerCase();

      if (osName.contains("mac")) {
        return showInFinder(path);
      } else if (osName.contains("windows")) {
        return showInExplorer(path);
      } else if (osName.contains("linux")) {
        return showInLinuxFileManager(path);
      } else {
        // その他のOSの場合はDesktop APIを使用
        return showWithDesktop(path);
      }
    } catch (Exception e) {
      logger.error("ファイルマネージャー表示に失敗しました: {}", fileNode.getPath(), e);
      return false;
    }
  }

  /**
   * macOSのFinderで表示する
   * 
   * @param path 表示対象のパス
   * @return 表示に成功した場合true
   */
  private boolean showInFinder(Path path) {
    try {
      ProcessBuilder pb;
      if (Files.isDirectory(path)) {
        // ディレクトリの場合はそのディレクトリを開く
        pb = new ProcessBuilder("open", path.toString());
      } else {
        // ファイルの場合は親ディレクトリを開いてファイルを選択
        pb = new ProcessBuilder("open", "-R", path.toString());
      }

      Process process = pb.start();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } finally {
        closeProcessStreams(process);
      }

      if (exitCode == 0) {
        logger.info("Finderで表示しました: {}", path);
        return true;
      } else {
        logger.error("Finder表示コマンドが失敗しました: {} (exitCode={})", path, exitCode);
        return false;
      }
    } catch (Exception e) {
      logger.error("Finder表示に失敗しました: {}", path, e);
      return false;
    }
  }

  /**
   * Windowsのエクスプローラーで表示する
   * 
   * @param path 表示対象のパス
   * @return 表示に成功した場合true
   */
  private boolean showInExplorer(Path path) {
    try {
      ProcessBuilder pb;
      if (Files.isDirectory(path)) {
        // ディレクトリの場合はそのディレクトリを開く
        pb = new ProcessBuilder("explorer", path.toString());
      } else {
        // ファイルの場合は親ディレクトリを開いてファイルを選択
        // explorer は /select,<path> を 1 引数として受け取る (カンマ区切り、空白なし)
        pb = new ProcessBuilder("explorer", "/select," + path.toString());
      }

      Process process = pb.start();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } finally {
        closeProcessStreams(process);
      }

      // explorer は成功時でも 1 を返す既知挙動があるため exitCode で失敗判定はしない
      logger.info("エクスプローラーで表示しました: {} (exitCode={})", path, exitCode);
      return true;
    } catch (Exception e) {
      logger.error("エクスプローラー表示に失敗しました: {}", path, e);
      return false;
    }
  }

  /**
   * Linuxのファイルマネージャーで表示する
   * 
   * @param path 表示対象のパス
   * @return 表示に成功した場合true
   */
  private boolean showInLinuxFileManager(Path path) {
    try {
      // 一般的なLinuxファイルマネージャーを順番に試す
      String[] fileManagers = {
          "nautilus", // GNOME
          "dolphin", // KDE
          "thunar", // XFCE
          "pcmanfm", // LXDE
          "nemo" // Cinnamon
      };

      for (String fileManager : fileManagers) {
        if (isCommandAvailable(fileManager)) {
          Path target = Files.isDirectory(path) ? path : path.getParent();
          if (target == null) {
            continue;
          }
          ProcessBuilder pb = new ProcessBuilder(fileManager, target.toString());
          Process process = pb.start();
          int exitCode;
          try {
            exitCode = process.waitFor();
          } finally {
            closeProcessStreams(process);
          }

          if (exitCode == 0) {
            logger.info("{}で表示しました: {}", fileManager, path);
            return true;
          }
        }
      }

      logger.error("利用可能なLinuxファイルマネージャーが見つかりません");
      return false;
    } catch (Exception e) {
      logger.error("Linuxファイルマネージャー表示に失敗しました: {}", path, e);
      return false;
    }
  }

  /**
   * Desktop APIを使用して表示する（フォールバック）
   * 
   * @param path 表示対象のパス
   * @return 表示に成功した場合true
   */
  private boolean showWithDesktop(Path path) {
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop desktop = Desktop.getDesktop();

        if (Files.isDirectory(path)) {
          desktop.open(path.toFile());
        } else {
          // ファイルの場合は親ディレクトリを開く
          desktop.open(path.getParent().toFile());
        }

        logger.info("Desktop APIで表示しました: {}", path);
        return true;
      } else {
        logger.error("Desktop APIがサポートされていません");
        return false;
      }
    } catch (Exception e) {
      logger.error("Desktop API表示に失敗しました: {}", path, e);
      return false;
    }
  }

  /**
   * コマンドが利用可能かどうかをチェックする
   * 
   * @param command チェック対象のコマンド
   * @return 利用可能な場合true
   */
  private boolean isCommandAvailable(String command) {
    try {
      Process process = new ProcessBuilder("which", command).start();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } finally {
        closeProcessStreams(process);
      }
      return exitCode == 0;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Process の I/O ストリームを確実にクローズする。
   * waitFor 後に呼び出すことで FD リークを防ぐ。
   */
  private void closeProcessStreams(Process process) {
    try {
      process.getInputStream().close();
    } catch (IOException ignored) {
      // クローズ失敗は無視（プロセス終了後のクローズで起きうる）
    }
    try {
      process.getErrorStream().close();
    } catch (IOException ignored) {
    }
    try {
      process.getOutputStream().close();
    } catch (IOException ignored) {
    }
  }

  /**
   * ファイルマネージャーで表示可能かどうかをチェックする
   * 
   * @param fileNode チェック対象のファイルノード
   * @return 表示可能な場合true
   */
  public boolean canShowInFileManager(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      // ファイルが存在するかチェック
      if (!Files.exists(path)) {
        return false;
      }

      // パスが有効かチェック
      if (path.toString().trim().isEmpty()) {
        return false;
      }

      return true;
    } catch (Exception e) {
      logger.warn("ファイルマネージャー表示可能性チェックに失敗: {}", fileNode.getPath(), e);
      return false;
    }
  }
}
