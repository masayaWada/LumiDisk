package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.DuplicateGroup;
import com.example.diskanalyzer.model.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * 重複ファイル検出サービス
 * ファイルのハッシュ値を計算して重複を検出する
 */
public class DuplicateDetectionService {
  private static final Logger logger = LoggerFactory.getLogger(DuplicateDetectionService.class);
  private static final int CHUNK_SIZE = 8192; // 8KB chunks for hashing
  private final ForkJoinPool pool;

  public DuplicateDetectionService() {
    this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  }

  public DuplicateDetectionService(int parallelism) {
    this.pool = new ForkJoinPool(parallelism);
  }

  /**
   * ファイルリストから重複ファイルを検出する
   */
  public List<DuplicateGroup> findDuplicates(List<FileNode> files) {
    logger.info("重複ファイル検出開始: {} ファイル", files.size());
    long startTime = System.currentTimeMillis();

    // ファイルサイズでグループ化（同じサイズのファイルのみ重複の可能性がある）
    Map<Long, List<FileNode>> sizeGroups = new HashMap<>();
    for (FileNode file : files) {
      if (!file.isDirectory() && file.getSize() > 0) {
        sizeGroups.computeIfAbsent(file.getSize(), k -> new ArrayList<>()).add(file);
      }
    }

    // サイズが2つ以上のファイルのみを対象にハッシュ計算
    List<FileNode> candidatesForHashing = new ArrayList<>();
    for (List<FileNode> group : sizeGroups.values()) {
      if (group.size() > 1) {
        candidatesForHashing.addAll(group);
      }
    }

    logger.info("ハッシュ計算対象: {} ファイル", candidatesForHashing.size());

    // ハッシュ計算を並列実行
    Map<String, List<FileNode>> hashGroups = new ConcurrentHashMap<>();
    HashCalculationTask task = new HashCalculationTask(candidatesForHashing, 0, candidatesForHashing.size());
    pool.submit(task).join();

    // 結果を収集
    for (FileNode file : candidatesForHashing) {
      String hash = file.getHash();
      if (hash != null) {
        hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
      }
    }

    // 重複グループを作成
    List<DuplicateGroup> duplicateGroups = new ArrayList<>();
    for (Map.Entry<String, List<FileNode>> entry : hashGroups.entrySet()) {
      List<FileNode> duplicateFiles = entry.getValue();
      if (duplicateFiles.size() > 1) {
        FileNode firstFile = duplicateFiles.get(0);
        DuplicateGroup group = new DuplicateGroup(
            entry.getKey(),
            firstFile.getSize(),
            firstFile.getExtension());
        for (FileNode file : duplicateFiles) {
          group.addFile(file);
        }
        duplicateGroups.add(group);
      }
    }

    // 無駄な容量順でソート
    duplicateGroups.sort((a, b) -> Long.compare(b.getWastedSpace(), a.getWastedSpace()));

    long endTime = System.currentTimeMillis();
    logger.info("重複ファイル検出完了: {} グループ, 所要時間: {} ms",
        duplicateGroups.size(), endTime - startTime);

    return duplicateGroups;
  }

  /**
   * ファイルのハッシュ値を計算する
   */
  public String calculateFileHash(Path filePath) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[CHUNK_SIZE];

      try (var inputStream = Files.newInputStream(filePath)) {
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          md.update(buffer, 0, bytesRead);
        }
      }

      byte[] hashBytes = md.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException | IOException e) {
      logger.error("ハッシュ計算エラー: {}", filePath, e);
      return null;
    }
  }

  /**
   * ハッシュ計算を並列実行するためのタスク
   */
  private class HashCalculationTask extends RecursiveTask<Void> {
    private final List<FileNode> files;
    private final int start;
    private final int end;
    private static final int THRESHOLD = 10; // 閾値以下は並列化しない

    public HashCalculationTask(List<FileNode> files, int start, int end) {
      this.files = files;
      this.start = start;
      this.end = end;
    }

    @Override
    protected Void compute() {
      if (end - start <= THRESHOLD) {
        // 閾値以下の場合は直接処理
        for (int i = start; i < end; i++) {
          FileNode file = files.get(i);
          String hash = calculateFileHash(file.getPath());
          file.setHash(hash);
        }
      } else {
        // 閾値より大きい場合は分割して並列処理
        int mid = (start + end) / 2;
        HashCalculationTask leftTask = new HashCalculationTask(files, start, mid);
        HashCalculationTask rightTask = new HashCalculationTask(files, mid, end);

        leftTask.fork();
        rightTask.compute();
        leftTask.join();
      }
      return null;
    }
  }

  /**
   * リソースを解放する
   */
  public void shutdown() {
    pool.shutdown();
  }
}
