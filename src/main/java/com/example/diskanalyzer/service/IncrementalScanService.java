package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import com.example.diskanalyzer.model.ScanSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 増分スキャンサービス
 * 前回のスキャン結果と比較して変更されたファイルのみを再スキャンする
 */
public class IncrementalScanService {
  private static final Logger logger = LoggerFactory.getLogger(IncrementalScanService.class);

  private final FileScanner fileScanner;
  private final ScanCacheService cacheService;
  private final ForkJoinPool pool;

  public IncrementalScanService() {
    this.fileScanner = new FileScanner();
    this.cacheService = new ScanCacheService();
    this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  }

  /**
   * 増分スキャンを実行する
   */
  public ScanResult incrementalScan(Path rootPath) throws IOException {
    logger.info("増分スキャン開始: {}", rootPath);
    long startTime = System.currentTimeMillis();

    // 前回のスナップショットを取得
    Optional<ScanSnapshot> lastSnapshot = cacheService.getLatestSnapshot(rootPath);

    if (lastSnapshot.isEmpty()) {
      logger.info("前回のスナップショットが見つからないため、フルスキャンを実行");
      return performFullScan(rootPath);
    }

    ScanSnapshot previousSnapshot = lastSnapshot.get();
    logger.info("前回のスナップショットを発見: {}", previousSnapshot.getScanTime());

    // 変更されたファイルを検出
    Set<Path> changedFiles = detectChangedFiles(rootPath, previousSnapshot);
    logger.info("変更されたファイル数: {}", changedFiles.size());

    if (changedFiles.isEmpty()) {
      logger.info("変更が検出されませんでした");
      return previousSnapshot.toScanResult();
    }

    // 変更されたファイルのみを再スキャン
    List<FileNode> newFiles = rescanChangedFiles(rootPath, changedFiles, previousSnapshot);

    // 新しいスナップショットを作成
    ScanResult result = createUpdatedScanResult(previousSnapshot, newFiles, changedFiles);

    // スナップショットを保存
    ScanSnapshot newSnapshot = new ScanSnapshot(
        rootPath,
        LocalDateTime.now(),
        result.getFiles(),
        result.getExtensionStats(),
        result.getTotalSize(),
        result.getTotalFiles(),
        result.getTotalDirectories(),
        System.currentTimeMillis() - startTime,
        "1.0");
    cacheService.saveSnapshot(newSnapshot);

    long endTime = System.currentTimeMillis();
    logger.info("増分スキャン完了: 所要時間: {} ms", endTime - startTime);

    return result;
  }

  /**
   * フルスキャンを実行する
   */
  public ScanResult performFullScan(Path rootPath) throws IOException {
    logger.info("フルスキャン実行: {}", rootPath);
    long startTime = System.currentTimeMillis();

    ScanResult result = fileScanner.scan(rootPath);

    // スナップショットを保存
    ScanSnapshot snapshot = new ScanSnapshot(
        rootPath,
        LocalDateTime.now(),
        result.getFiles(),
        result.getExtensionStats(),
        result.getTotalSize(),
        result.getTotalFiles(),
        result.getTotalDirectories(),
        System.currentTimeMillis() - startTime,
        "1.0");
    cacheService.saveSnapshot(snapshot);

    return result;
  }

  /**
   * 変更されたファイルを検出する
   */
  private Set<Path> detectChangedFiles(Path rootPath, ScanSnapshot previousSnapshot) {
    Set<Path> changedFiles = new HashSet<>();
    Map<Path, FileNode> previousFiles = new HashMap<>();

    // 前回のファイル情報をマップに変換
    for (FileNode file : previousSnapshot.getFiles()) {
      previousFiles.put(file.getPath(), file);
    }

    try {
      Files.walkFileTree(rootPath, new java.nio.file.SimpleFileVisitor<Path>() {
        @Override
        public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          FileNode previousFile = previousFiles.get(file);

          if (previousFile == null) {
            // 新しいファイル
            changedFiles.add(file);
          } else {
            // ファイルが変更されているかチェック
            if (previousFile.getSize() != attrs.size() ||
                !previousFile.getModified().equals(attrs.lastModifiedTime())) {
              changedFiles.add(file);
            }
          }
          return java.nio.file.FileVisitResult.CONTINUE;
        }

        @Override
        public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          FileNode previousDir = previousFiles.get(dir);

          if (previousDir == null) {
            // 新しいディレクトリ
            changedFiles.add(dir);
          } else {
            // ディレクトリが変更されているかチェック
            if (!previousDir.getModified().equals(attrs.lastModifiedTime())) {
              changedFiles.add(dir);
            }
          }
          return java.nio.file.FileVisitResult.CONTINUE;
        }

        @Override
        public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
          logger.warn("ファイルアクセス失敗: {}", file, exc);
          return java.nio.file.FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      logger.error("ファイル変更検出中にエラーが発生", e);
    }

    // 削除されたファイルも検出
    for (Path previousPath : previousFiles.keySet()) {
      if (!Files.exists(previousPath)) {
        changedFiles.add(previousPath);
      }
    }

    return changedFiles;
  }

  /**
   * 変更されたファイルを再スキャンする
   */
  private List<FileNode> rescanChangedFiles(Path rootPath, Set<Path> changedFiles, ScanSnapshot previousSnapshot) {
    ConcurrentLinkedQueue<FileNode> newFiles = new ConcurrentLinkedQueue<>();

    // 前回のファイル情報をマップに変換
    Map<Path, FileNode> previousFiles = new HashMap<>();
    for (FileNode file : previousSnapshot.getFiles()) {
      previousFiles.put(file.getPath(), file);
    }

    // 変更されたファイルを並列処理
    pool.submit(() -> {
      changedFiles.parallelStream().forEach(path -> {
        try {
          if (Files.exists(path)) {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            boolean isHidden = Files.isHidden(path);

            FileNode fileNode = new FileNode(
                path,
                attrs.size(),
                attrs.lastModifiedTime(),
                attrs.isDirectory(),
                isHidden);

            newFiles.add(fileNode);
          }
        } catch (IOException e) {
          logger.warn("ファイル再スキャンエラー: {}", path, e);
        }
      });
    }).join();

    return new ArrayList<>(newFiles);
  }

  /**
   * 更新されたスキャン結果を作成する
   */
  private ScanResult createUpdatedScanResult(ScanSnapshot previousSnapshot, List<FileNode> newFiles,
      Set<Path> changedFiles) {
    Map<Path, FileNode> updatedFiles = new HashMap<>();

    // 前回のファイル情報をコピー
    for (FileNode file : previousSnapshot.getFiles()) {
      if (!changedFiles.contains(file.getPath())) {
        updatedFiles.put(file.getPath(), file);
      }
    }

    // 新しいファイル情報を追加
    for (FileNode file : newFiles) {
      updatedFiles.put(file.getPath(), file);
    }

    // 統計情報を再計算
    List<FileNode> allFiles = new ArrayList<>(updatedFiles.values());
    Map<String, Long> extensionStats = new HashMap<>();
    long totalSize = 0;
    int totalFiles = 0;
    int totalDirectories = 0;

    for (FileNode file : allFiles) {
      if (file.isDirectory()) {
        totalDirectories++;
      } else {
        totalFiles++;
        totalSize += file.getSize();

        String ext = file.getExtension();
        if (!ext.isEmpty()) {
          extensionStats.merge(ext, file.getSize(), Long::sum);
        }
      }
    }

    return new ScanResult(allFiles, extensionStats, totalSize, totalFiles, totalDirectories, 0);
  }

  /**
   * キャッシュサービスを取得する
   */
  public ScanCacheService getCacheService() {
    return cacheService;
  }

  /**
   * リソースを解放する
   */
  public void shutdown() {
    pool.shutdown();
  }
}
