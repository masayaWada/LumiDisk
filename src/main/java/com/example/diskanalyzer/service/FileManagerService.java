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
      String command;
      if (Files.isDirectory(path)) {
        // ディレクトリの場合はそのディレクトリを開く
        command = "open " + path.toString();
      } else {
        // ファイルの場合は親ディレクトリを開いてファイルを選択
        command = "open -R " + path.toString();
      }

      Process process = Runtime.getRuntime().exec(command);
      int exitCode = process.waitFor();

      if (exitCode == 0) {
        logger.info("Finderで表示しました: {}", path);
        return true;
      } else {
        logger.error("Finder表示コマンドが失敗しました: {}", command);
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
      String command;
      if (Files.isDirectory(path)) {
        // ディレクトリの場合はそのディレクトリを開く
        command = "explorer \"" + path.toString() + "\"";
      } else {
        // ファイルの場合は親ディレクトリを開いてファイルを選択
        command = "explorer /select,\"" + path.toString() + "\"";
      }

      Process process = Runtime.getRuntime().exec(command);
      int exitCode = process.waitFor();

      if (exitCode == 0) {
        logger.info("エクスプローラーで表示しました: {}", path);
        return true;
      } else {
        logger.error("エクスプローラー表示コマンドが失敗しました: {}", command);
        return false;
      }
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
          String command;
          if (Files.isDirectory(path)) {
            command = fileManager + " \"" + path.toString() + "\"";
          } else {
            // ファイルの場合は親ディレクトリを開く
            command = fileManager + " \"" + path.getParent().toString() + "\"";
          }

          Process process = Runtime.getRuntime().exec(command);
          int exitCode = process.waitFor();

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
      Process process = Runtime.getRuntime().exec("which " + command);
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      return false;
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
