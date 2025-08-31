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
}
