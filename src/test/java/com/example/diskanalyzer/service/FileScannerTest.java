package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileScanner} の振る舞い検証。
 * 一時ディレクトリにファイル/ディレクトリを生成し、スキャン結果と統計を確認する。
 */
class FileScannerTest {

  @Test
  void scan_emptyDirectory_returnsZeroFilesAndOneRootDirectory(@TempDir Path tempDir) throws IOException {
    FileScanner scanner = new FileScanner();
    try {
      ScanResult result = scanner.scan(tempDir);

      assertEquals(0, result.getTotalFiles());
      // ルートディレクトリ自体は visit されるため 1
      assertEquals(1, result.getTotalDirectories());
      assertEquals(0L, result.getTotalSize());
    } finally {
      scanner.shutdown();
    }
  }

  @Test
  void scan_singleFile_aggregatesSizeAndExtension(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "hello world"); // 11 bytes

    FileScanner scanner = new FileScanner();
    try {
      ScanResult result = scanner.scan(tempDir);

      assertEquals(1, result.getTotalFiles());
      assertEquals(11L, result.getTotalSize());
      Map<String, Long> stats = result.getExtensionStats();
      assertEquals(11L, stats.get("txt"));
    } finally {
      scanner.shutdown();
    }
  }

  @Test
  void scan_nestedStructure_walksRecursively(@TempDir Path tempDir) throws IOException {
    Path sub = Files.createDirectory(tempDir.resolve("sub"));
    Files.writeString(tempDir.resolve("a.log"), "12345");      // 5
    Files.writeString(sub.resolve("b.log"), "1234567");        // 7
    Files.writeString(sub.resolve("c.dat"), "abcd");           // 4

    FileScanner scanner = new FileScanner();
    try {
      ScanResult result = scanner.scan(tempDir);

      assertEquals(3, result.getTotalFiles());
      // tempDir + sub の 2 ディレクトリ
      assertEquals(2, result.getTotalDirectories());
      assertEquals(16L, result.getTotalSize());
      assertEquals(12L, result.getExtensionStats().get("log"));
      assertEquals(4L, result.getExtensionStats().get("dat"));
    } finally {
      scanner.shutdown();
    }
  }

  @Test
  void scan_withInjectedPool_doesNotShutdownInjectedPool(@TempDir Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("x.bin"), "x");

    ForkJoinPool externalPool = new ForkJoinPool(2);
    try {
      FileScanner scanner = new FileScanner(externalPool);
      ScanResult result = scanner.scan(tempDir);
      assertEquals(1, result.getTotalFiles());

      // 注入された pool は shutdown() しても解放されない (caller 所有)
      scanner.shutdown();
      assertFalse(externalPool.isShutdown(), "注入された pool は外部所有のため shutdown されないはず");
    } finally {
      externalPool.shutdownNow();
    }
  }

  @Test
  void scan_returnsFilesContainingAllEntries(@TempDir Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("one.txt"), "1");
    Files.writeString(tempDir.resolve("two.txt"), "22");

    FileScanner scanner = new FileScanner();
    try {
      ScanResult result = scanner.scan(tempDir);
      List<FileNode> files = result.getFiles();
      // tempDir 自身 + 2 ファイル = 3 エントリ
      assertEquals(3, files.size());
      long fileCount = files.stream().filter(n -> !n.isDirectory()).count();
      assertEquals(2, fileCount);
    } finally {
      scanner.shutdown();
    }
  }
}
