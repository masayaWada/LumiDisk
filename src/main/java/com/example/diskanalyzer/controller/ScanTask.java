package com.example.diskanalyzer.controller;

import com.example.diskanalyzer.model.ScanResult;
import com.example.diskanalyzer.service.FileScanner;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * バックグラウンドでファイルスキャンを実行するJavaFX Task
 */
public class ScanTask extends Task<ScanResult> {
  private static final Logger logger = LoggerFactory.getLogger(ScanTask.class);

  private final Path rootPath;
  private final FileScanner fileScanner;

  public ScanTask(Path rootPath) {
    this.rootPath = rootPath;
    this.fileScanner = new FileScanner();
  }

  @Override
  protected ScanResult call() throws Exception {
    logger.info("スキャンタスク開始: {}", rootPath);

    try {
      updateMessage("スキャン中...");
      updateProgress(0, 1);

      ScanResult result = fileScanner.scan(rootPath);

      updateMessage("スキャン完了");
      updateProgress(1, 1);

      logger.info("スキャンタスク完了: {} ファイル, {} ディレクトリ",
          result.getTotalFiles(), result.getTotalDirectories());

      return result;

    } catch (Exception e) {
      logger.error("スキャンタスクでエラーが発生", e);
      updateMessage("エラー: " + e.getMessage());
      throw e;
    }
  }

  @Override
  protected void succeeded() {
    logger.info("スキャンタスクが正常に完了しました");
  }

  @Override
  protected void failed() {
    logger.error("スキャンタスクが失敗しました", getException());
  }

  @Override
  protected void cancelled() {
    logger.info("スキャンタスクがキャンセルされました");
  }
}
