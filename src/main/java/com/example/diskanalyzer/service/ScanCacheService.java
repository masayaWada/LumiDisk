package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.ScanSnapshot;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
    this(defaultCacheDirectory(), defaultObjectMapper());
  }

  /**
   * テストや代替設定向けに、キャッシュディレクトリと ObjectMapper を注入できるコンストラクタ。
   *
   * @param cacheDirectory スナップショット保存先
   * @param objectMapper   シリアライズに使う ObjectMapper (caller が JavaTime 等を構成済み想定)
   */
  public ScanCacheService(Path cacheDirectory, ObjectMapper objectMapper) {
    if (cacheDirectory == null) {
      throw new IllegalArgumentException("cacheDirectory must not be null");
    }
    if (objectMapper == null) {
      throw new IllegalArgumentException("objectMapper must not be null");
    }
    this.cacheDirectory = cacheDirectory;
    this.objectMapper = objectMapper;

    try {
      Files.createDirectories(cacheDirectory);
    } catch (IOException e) {
      logger.error("キャッシュディレクトリの作成に失敗", e);
    }
  }

  /** デフォルトのキャッシュディレクトリ ({@code ~/.lumidisk/cache}) を返す。 */
  private static Path defaultCacheDirectory() {
    String userHome = System.getProperty("user.home");
    return Paths.get(userHome, ".lumidisk", CACHE_DIR);
  }

  /** JavaTime + FileTime + 整形済みの既定 ObjectMapper を返す。 */
  private static ObjectMapper defaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(fileTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    // モデルクラスに getter のみのフィールド (例: FileNode.extension) があるため、
    // 既存スナップショット JSON にあっても deserialize 失敗しないよう寛容に扱う。
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }

  /**
   * {@link FileTime} を ISO-8601 文字列で serialize/deserialize する Jackson モジュール。
   * Jackson 既定では FileTime は BeanSerializer で処理しようとして失敗するため
   * ({@code No serializer found for class java.nio.file.attribute.FileTime})、
   * 専用のシリアライザで {@link Instant} と相互変換する。
   */
  private static SimpleModule fileTimeModule() {
    SimpleModule module = new SimpleModule("FileTimeModule");
    module.addSerializer(FileTime.class, new JsonSerializer<FileTime>() {
      @Override
      public void serialize(FileTime value, JsonGenerator gen, SerializerProvider serializers)
          throws IOException {
        gen.writeString(value.toInstant().toString());
      }
    });
    module.addDeserializer(FileTime.class, new JsonDeserializer<FileTime>() {
      @Override
      public FileTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return FileTime.from(Instant.parse(p.getValueAsString()));
      }
    });
    return module;
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

      try (Stream<Path> stream = Files.list(cacheDirectory)) {
        stream
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
      }

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

      try (Stream<Path> stream = Files.list(cacheDirectory)) {
        stream
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
      }
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

      try (Stream<Path> stream = Files.list(cacheDirectory)) {
        return stream
            .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
            .mapToLong(path -> {
              try {
                return Files.size(path);
              } catch (IOException e) {
                return 0;
              }
            })
            .sum();
      }
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

      try (Stream<Path> stream = Files.list(cacheDirectory)) {
        stream
            .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
            .forEach(path -> {
              try {
                Files.delete(path);
              } catch (IOException e) {
                logger.warn("キャッシュファイルの削除に失敗: {}", path, e);
              }
            });
      }

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
      try (Stream<Path> stream = Files.list(cacheDirectory)) {
        stream
            .filter(path -> path.getFileName().toString().endsWith(SNAPSHOT_EXTENSION))
            .forEach(cacheFiles::add);
      }

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
