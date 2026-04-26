package com.example.diskanalyzer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LumiDisk メインアプリケーション
 */
public class MainApp extends Application {

  /**
   * Logback 初期化前にログ出力先を OS 別に決定する。
   * static イニシャライザは Logger 初期化より先に実行されるため、ここで
   * {@code LUMIDISK_LOG_DIR} system property を設定すれば logback.xml から参照できる。
   */
  static {
    configureLogDirectory();
  }

  private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

  @Override
  public void start(Stage primaryStage) throws Exception {
    logger.info("LumiDisk アプリケーション開始");

    try {
      FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/main.fxml"));
      Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

      primaryStage.setTitle("LumiDisk - ディスク使用量分析ツール");
      primaryStage.setScene(scene);
      primaryStage.setMinWidth(800);
      primaryStage.setMinHeight(600);

      // アプリケーション終了時の処理
      primaryStage.setOnCloseRequest(event -> {
        logger.info("LumiDisk アプリケーション終了");
        System.exit(0);
      });

      primaryStage.show();
      logger.info("メインウィンドウ表示完了");

    } catch (Exception e) {
      logger.error("アプリケーション起動エラー", e);
      throw e;
    }
  }

  public static void main(String[] args) {
    logger.info("LumiDisk 起動開始");
    launch(args);
  }

  /**
   * OS に応じたログ出力先を決定し、{@code LUMIDISK_LOG_DIR} system property に設定する。
   * 既に設定されている場合は尊重する (テストや起動時オプションで上書き可能)。
   *
   * <ul>
   *   <li>macOS: {@code ~/Library/Logs/LumiDisk}</li>
   *   <li>Windows: {@code %LOCALAPPDATA%/LumiDisk/logs} (取得不可なら {@code ~/LumiDisk/logs})</li>
   *   <li>その他: {@code ~/.lumidisk/logs}</li>
   * </ul>
   */
  private static void configureLogDirectory() {
    if (System.getProperty("LUMIDISK_LOG_DIR") != null) {
      return;
    }
    String osName = System.getProperty("os.name", "").toLowerCase();
    String userHome = System.getProperty("user.home", ".");
    String logDir;
    if (osName.contains("mac")) {
      logDir = userHome + "/Library/Logs/LumiDisk";
    } else if (osName.contains("windows")) {
      String localAppData = System.getenv("LOCALAPPDATA");
      String base = (localAppData != null && !localAppData.isBlank()) ? localAppData : userHome;
      logDir = base + "/LumiDisk/logs";
    } else {
      logDir = userHome + "/.lumidisk/logs";
    }
    System.setProperty("LUMIDISK_LOG_DIR", logDir);
  }
}
