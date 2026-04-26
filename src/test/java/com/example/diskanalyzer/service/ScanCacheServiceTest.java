package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScanCacheService} の振る舞い検証。
 * 一時キャッシュディレクトリに対して保存・最新取得・古いスナップショット削除を確認する。
 */
class ScanCacheServiceTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());

    // FileTime 用の serialize/deserialize を登録
    SimpleModule fileTimeModule = new SimpleModule();
    fileTimeModule.addSerializer(FileTime.class, new JsonSerializer<FileTime>() {
      @Override
      public void serialize(FileTime value, JsonGenerator gen, SerializerProvider serializers)
          throws IOException {
        gen.writeString(value.toInstant().toString());
      }
    });
    fileTimeModule.addDeserializer(FileTime.class, new JsonDeserializer<FileTime>() {
      @Override
      public FileTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return FileTime.from(Instant.parse(p.getValueAsString()));
      }
    });
    mapper.registerModule(fileTimeModule);

    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Test
  void saveAndGetLatestSnapshot_roundTripsContent(@TempDir Path cacheDir) {
    ScanCacheService svc = new ScanCacheService(cacheDir, mapper);
    Path rootPath = Path.of("/some/scanned/path");

    ScanSnapshot snapshot = makeSnapshot(rootPath, LocalDateTime.now(), 3, 1234L);
    svc.saveSnapshot(snapshot);

    Optional<ScanSnapshot> latest = svc.getLatestSnapshot(rootPath);
    assertTrue(latest.isPresent());
    assertEquals(rootPath, latest.get().getRootPath());
    assertEquals(3, latest.get().getTotalFiles());
    assertEquals(1234L, latest.get().getTotalSize());
  }

  @Test
  void getLatestSnapshot_isEmpty_whenPathHasNoSnapshot(@TempDir Path cacheDir) {
    ScanCacheService svc = new ScanCacheService(cacheDir, mapper);
    Optional<ScanSnapshot> latest = svc.getLatestSnapshot(Path.of("/never/scanned"));
    assertTrue(latest.isEmpty());
  }

  @Test
  void getLatestSnapshot_picksMostRecentByScanTime(@TempDir Path cacheDir) {
    ScanCacheService svc = new ScanCacheService(cacheDir, mapper);
    Path rootPath = Path.of("/some/path");

    LocalDateTime older = LocalDateTime.of(2025, 1, 1, 10, 0);
    LocalDateTime newer = LocalDateTime.of(2026, 4, 27, 12, 0);

    svc.saveSnapshot(makeSnapshot(rootPath, older, 1, 100L));
    svc.saveSnapshot(makeSnapshot(rootPath, newer, 2, 200L));

    Optional<ScanSnapshot> latest = svc.getLatestSnapshot(rootPath);
    assertTrue(latest.isPresent());
    assertEquals(newer, latest.get().getScanTime());
    assertEquals(2, latest.get().getTotalFiles());
  }

  @Test
  void cleanupOldCache_keepsOnlyMaxCacheSize(@TempDir Path cacheDir) throws IOException {
    ScanCacheService svc = new ScanCacheService(cacheDir, mapper);
    Path rootPath = Path.of("/repeated/scan");

    // 12 件保存 (上限 10 件) → 古い 2 件が削除される想定
    for (int i = 0; i < 12; i++) {
      LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 0, 0).plusMinutes(i);
      svc.saveSnapshot(makeSnapshot(rootPath, ts, i, (long) i));
      // ファイル mtime を順序に揃えるため少し待つ代わりに、touch で再現
    }

    // saveSnapshot は内部で cleanupOldCache を呼ぶため、最大 10 件に収まるはず
    long count;
    try (var stream = Files.list(cacheDir)) {
      count = stream
          .filter(p -> p.getFileName().toString().endsWith(".snapshot.json"))
          .count();
    }
    assertTrue(count <= 10, "古いキャッシュが削除されて最大 10 件以下になる (実際: " + count + ")");
  }

  // --- helpers ---

  private static ScanSnapshot makeSnapshot(
      Path rootPath, LocalDateTime scanTime, int totalFiles, long totalSize) {
    FileNode dummy = new FileNode(
        rootPath.resolve("dummy.txt"),
        totalSize,
        FileTime.from(Instant.now()),
        false,
        false);
    return new ScanSnapshot(
        rootPath,
        scanTime,
        List.of(dummy),
        new HashMap<>(),
        totalSize,
        totalFiles,
        0,
        0L,
        "1.0");
  }
}
