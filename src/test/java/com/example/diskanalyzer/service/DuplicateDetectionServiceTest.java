package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.DuplicateGroup;
import com.example.diskanalyzer.model.FileNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DuplicateDetectionService} の振る舞い検証。
 * サイズで候補を絞った上でハッシュ計算する 2 段階判定が正しいことを確認する。
 */
class DuplicateDetectionServiceTest {

  @Test
  void findDuplicates_returnsEmpty_whenNoFilesShareSize(@TempDir Path tempDir) throws IOException {
    Path a = writeFile(tempDir, "a.txt", "hello");
    Path b = writeFile(tempDir, "b.txt", "hello world!"); // 異なるサイズ

    List<FileNode> files = List.of(toNode(a), toNode(b));

    DuplicateDetectionService svc = new DuplicateDetectionService(1);
    List<DuplicateGroup> groups = svc.findDuplicates(files);

    assertTrue(groups.isEmpty(), "サイズが異なるなら重複検出ゼロ");
  }

  @Test
  void findDuplicates_groupsFilesWithSameContent(@TempDir Path tempDir) throws IOException {
    Path a = writeFile(tempDir, "a.txt", "shared content");
    Path b = writeFile(tempDir, "b.txt", "shared content");
    Path c = writeFile(tempDir, "c.txt", "shared content");
    Path d = writeFile(tempDir, "d.txt", "different!!!!!"); // 同サイズだが内容違い (14 bytes も "shared content" と同じ)

    // サイズが同じだが内容違い → ハッシュで分離されることを確認
    assertEquals(Files.size(a), Files.size(d), "前提: サイズが同じこと");

    List<FileNode> files = List.of(toNode(a), toNode(b), toNode(c), toNode(d));

    DuplicateDetectionService svc = new DuplicateDetectionService(1);
    List<DuplicateGroup> groups = svc.findDuplicates(files);

    assertEquals(1, groups.size(), "同一内容の 3 ファイルのみ 1 グループにまとまる");
    DuplicateGroup group = groups.get(0);
    assertEquals(3, group.getDuplicateCount());
  }

  @Test
  void findDuplicates_skipsZeroSizeFiles(@TempDir Path tempDir) throws IOException {
    Path empty1 = writeFile(tempDir, "e1.txt", "");
    Path empty2 = writeFile(tempDir, "e2.txt", "");
    List<FileNode> files = List.of(toNode(empty1), toNode(empty2));

    DuplicateDetectionService svc = new DuplicateDetectionService(1);
    List<DuplicateGroup> groups = svc.findDuplicates(files);

    assertTrue(groups.isEmpty(), "サイズ 0 は重複検出対象外 (実装方針)");
  }

  @Test
  void findDuplicates_skipsDirectories(@TempDir Path tempDir) throws IOException {
    Path dir = Files.createDirectory(tempDir.resolve("dirA"));
    Path file = writeFile(tempDir, "x.txt", "abc");

    List<FileNode> nodes = new ArrayList<>();
    nodes.add(new FileNode(dir, 0, FileTime.from(Instant.now()), true, false));
    nodes.add(toNode(file));

    DuplicateDetectionService svc = new DuplicateDetectionService(1);
    List<DuplicateGroup> groups = svc.findDuplicates(nodes);

    assertTrue(groups.isEmpty());
  }

  // --- helpers ---

  private static Path writeFile(Path dir, String name, String content) throws IOException {
    Path p = dir.resolve(name);
    Files.writeString(p, content);
    return p;
  }

  private static FileNode toNode(Path path) throws IOException {
    return new FileNode(
        path,
        Files.size(path),
        Files.getLastModifiedTime(path),
        false,
        false);
  }
}
