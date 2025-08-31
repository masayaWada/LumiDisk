package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.ScanSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * スキャン結果のキャッシュ管理サービス
 * 増分スキャンで使用する
 */
public class ScanCacheService {
  private static final Logger logger = LoggerFactory.getLogger(ScanCacheService.class);
  private static final String CACHE_DIR = "cache";
  private static final String SNAPSHOT_EXTENSION = ".snapshot.json";
  private static final int MAX_CACHE_SIZE = 10; // 最大キャッシュ数

  private final ObjectMapper objectMapper;
  private final Path cacheDirectory;

  public ScanCacheService() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

    // キャッシュディレクトリの設定
    String userHome = System.getProperty("user.home");
    this.cacheDirectory = Paths.get(userHome, ".lumidisk", CACHE_DIR);

    try {
      Files.createDirectories(cacheDirectory);
    } catch (IOException e) {
      logger.error("キャッシュディレクトリの作成に失敗", e);
    }
  }

  /**
   * スナップショットを保存する
   */
  public void saveSnapshot(ScanSnapshot snapshot) {
    try {
      String fileName = generateFileName(snapshot.getRootPath(), snapshot.getScanTime());
      Path filePath = cacheDirectory.resolve(fileName);

      objectMapper.writeValue(filePath.toFile(), snapshot);
      logger.info("スナップショットを保存しました: {}", filePath);

      // 古いキャッシュを削除
      cleanupOldCache();

    } catch (IOException e) {
      logger.error("スナップショットの保存に失敗", e);
    }
  }

  /**
   * 指定パスの最新スナップショットを取得する
   */
  public Optional<ScanSnapshot> getLatestSnapshot(Path rootPath) {
    try {
      List<ScanSnapshot> snapshots = getAllSnapshots(rootPath);
      return snapshots.stream()
          .max(Comparator.comparing(ScanSnapshot::getScanTime));
    } catch (Exception e) {
      logger.error("スナップショットの取得に失敗: {}", rootPath, e);
      return Optional.empty();
    }
  }

  /**
   * 指定パスのすべてのスナップショットを取得する
   */
  public List<ScanSnapshot> getAllSnapshots(Path rootPath) {
    List<ScanSnapshot> snapshots = new ArrayList<>();

    try {
      if (!Files.exists(cacheDirectory)) {
        return snapshots;
      }

      String pathHash = String.valueOf(rootPath.toString().hashCode());

      Files.list(cacheDirectory)
          .filter(path -> path.getFileName().toString().startsWith(pathHash))
          .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
          .forEach(path -> {
            try {
              ScanSnapshot snapshot = objectMapper.readValue(path.toFile(), ScanSnapshot.class);
              snapshots.add(snapshot);
            } catch (IOException e) {
              logger.warn("スナップショットの読み込みに失敗: {}", path, e);
            }
          });

    } catch (IOException e) {
      logger.error("スナップショット一覧の取得に失敗", e);
    }

    return snapshots;
  }

  /**
   * スナップショットを削除する
   */
  public boolean deleteSnapshot(ScanSnapshot snapshot) {
    try {
      String fileName = generateFileName(snapshot.getRootPath(), snapshot.getScanTime());
      Path filePath = cacheDirectory.resolve(fileName);

      if (Files.exists(filePath)) {
        Files.delete(filePath);
        logger.info("スナップショットを削除しました: {}", filePath);
        return true;
      }
    } catch (IOException e) {
      logger.error("スナップショットの削除に失敗", e);
    }
    return false;
  }

  /**
   * 指定パスのすべてのスナップショットを削除する
   */
  public void deleteAllSnapshots(Path rootPath) {
    try {
      String pathHash = String.valueOf(rootPath.toString().hashCode());

      Files.list(cacheDirectory)
          .filter(path -> path.getFileName().toString().startsWith(pathHash))
          .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
          .forEach(path -> {
            try {
              Files.delete(path);
              logger.info("スナップショットを削除しました: {}", path);
            } catch (IOException e) {
              logger.warn("スナップショットの削除に失敗: {}", path, e);
            }
          });
    } catch (IOException e) {
      logger.error("スナップショット一括削除に失敗", e);
    }
  }

  /**
   * キャッシュサイズを取得する
   */
  public long getCacheSize() {
    try {
      if (!Files.exists(cacheDirectory)) {
        return 0;
      }

      return Files.list(cacheDirectory)
          .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
          .mapToLong(path -> {
            try {
              return Files.size(path);
            } catch (IOException e) {
              return 0;
            }
          })
          .sum();
    } catch (IOException e) {
      logger.error("キャッシュサイズの取得に失敗", e);
      return 0;
    }
  }

  /**
   * キャッシュをクリアする
   */
  public void clearCache() {
    try {
      if (!Files.exists(cacheDirectory)) {
        return;
      }

      Files.list(cacheDirectory)
          .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              logger.warn("キャッシュファイルの削除に失敗: {}", path, e);
            }
          });

      logger.info("キャッシュをクリアしました");
    } catch (IOException e) {
      logger.error("キャッシュクリアに失敗", e);
    }
  }

  /**
   * ファイル名を生成する
   */
  private String generateFileName(Path rootPath, LocalDateTime scanTime) {
    String pathHash = String.valueOf(rootPath.toString().hashCode());
    String timestamp = scanTime.toString().replace(":", "-");
    return pathHash + "_" + timestamp + SNAPSHOT_EXTENSION;
  }

  /**
   * 古いキャッシュを削除する
   */
  private void cleanupOldCache() {
    try {
      List<Path> cacheFiles = new ArrayList<>();
      Files.list(cacheDirectory)
          .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
          .forEach(cacheFiles::add);

      if (cacheFiles.size() > MAX_CACHE_SIZE) {
        // 作成日時順でソートして古いものから削除
        cacheFiles.sort((a, b) -> {
          try {
            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
          } catch (IOException e) {
            return 0;
          }
        });

        int deleteCount = cacheFiles.size() - MAX_CACHE_SIZE;
        for (int i = 0; i < deleteCount; i++) {
          try {
            Files.delete(cacheFiles.get(i));
            logger.info("古いキャッシュを削除しました: {}", cacheFiles.get(i));
          } catch (IOException e) {
            logger.warn("古いキャッシュの削除に失敗: {}", cacheFiles.get(i), e);
          }
        }
      }
    } catch (IOException e) {
      logger.error("キャッシュクリーンアップに失敗", e);
    }
  }
}
