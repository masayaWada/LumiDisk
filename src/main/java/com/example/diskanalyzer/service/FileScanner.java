package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * マルチスレッド対応のファイルスキャンサービス
 */
public class FileScanner {
  private static final Logger logger = LoggerFactory.getLogger(FileScanner.class);

  private final ForkJoinPool pool;
  private final int parallelism;

  public FileScanner() {
    this.parallelism = Runtime.getRuntime().availableProcessors();
    this.pool = new ForkJoinPool(parallelism);
  }

  public FileScanner(int parallelism) {
    this.parallelism = parallelism;
    this.pool = new ForkJoinPool(parallelism);
  }

  /**
   * 指定されたパス配下をスキャンして結果を返す
   */
  public ScanResult scan(Path root) throws IOException {
    logger.info("スキャン開始: {}", root);
    long startTime = System.currentTimeMillis();

    ConcurrentLinkedQueue<FileNode> results = new ConcurrentLinkedQueue<>();
    AtomicLong totalSize = new AtomicLong(0);
    AtomicInteger fileCount = new AtomicInteger(0);
    AtomicInteger directoryCount = new AtomicInteger(0);
    Map<String, AtomicLong> extensionStats = new ConcurrentHashMap<>();

    try {
      pool.submit(() -> {
        try {
          Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              try {
                boolean isHidden = Files.isHidden(file);
                FileNode fileNode = new FileNode(
                    file,
                    attrs.size(),
                    attrs.lastModifiedTime(),
                    false,
                    isHidden);

                results.add(fileNode);
                totalSize.addAndGet(attrs.size());
                fileCount.incrementAndGet();

                // 拡張子統計
                String ext = fileNode.getExtension();
                if (!ext.isEmpty()) {
                  extensionStats.computeIfAbsent(ext, k -> new AtomicLong(0))
                      .addAndGet(attrs.size());
                }

              } catch (IOException e) {
                logger.warn("ファイルアクセスエラー: {}", file, e);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              try {
                boolean isHidden = Files.isHidden(dir);
                FileNode dirNode = new FileNode(
                    dir,
                    0,
                    attrs.lastModifiedTime(),
                    true,
                    isHidden);

                results.add(dirNode);
                directoryCount.incrementAndGet();

              } catch (IOException e) {
                logger.warn("ディレクトリアクセスエラー: {}", dir, e);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              logger.warn("ファイルアクセス失敗: {}", file, exc);
              return FileVisitResult.CONTINUE;
            }
          });
        } catch (IOException e) {
          logger.error("スキャン中にエラーが発生", e);
        }
      }).join();

    } finally {
      pool.shutdown();
    }

    long endTime = System.currentTimeMillis();
    long scanDuration = endTime - startTime;

    // 拡張子統計を通常のMapに変換
    Map<String, Long> finalExtensionStats = new HashMap<>();
    extensionStats.forEach((ext, size) -> finalExtensionStats.put(ext, size.get()));

    logger.info("スキャン完了: {} ファイル, {} ディレクトリ, 総サイズ: {}, 所要時間: {} ms",
        fileCount.get(), directoryCount.get(),
        formatSize(totalSize.get()), scanDuration);

    return new ScanResult(
        new ArrayList<>(results),
        finalExtensionStats,
        totalSize.get(),
        fileCount.get(),
        directoryCount.get(),
        scanDuration);
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
  }
}
