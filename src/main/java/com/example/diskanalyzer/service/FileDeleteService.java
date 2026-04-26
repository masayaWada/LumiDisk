package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * ファイル・ディレクトリ削除サービス
 */
public class FileDeleteService {
  private static final Logger logger = LoggerFactory.getLogger(FileDeleteService.class);

  /** OS 別のシステムディレクトリ (削除禁止対象)。プロセス起動時に 1 度だけ解決する。 */
  private static final List<Path> SYSTEM_ROOTS = resolveSystemRoots();

  /**
   * ファイル・ディレクトリを削除する
   *
   * @param fileNode 削除対象のファイルノード
   * @return 削除に成功した場合true
   */
  public boolean deleteFile(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      if (fileNode.isDirectory()) {
        // ディレクトリの場合は再帰的に削除
        deleteDirectoryRecursively(path);
        logger.info("ディレクトリを削除しました: {}", path);
      } else {
        // ファイルの場合は直接削除
        Files.delete(path);
        logger.info("ファイルを削除しました: {}", path);
      }

      return true;
    } catch (IOException e) {
      logger.error("ファイル削除に失敗しました: {}", fileNode.getPath(), e);
      return false;
    }
  }

  /**
   * 複数のファイル・ディレクトリを削除する
   *
   * @param fileNodes 削除対象のファイルノードリスト
   * @return 削除に成功したファイル数
   */
  public int deleteFiles(List<FileNode> fileNodes) {
    int successCount = 0;

    for (FileNode fileNode : fileNodes) {
      if (deleteFile(fileNode)) {
        successCount++;
      }
    }

    logger.info("ファイル削除完了: {}/{} 件成功", successCount, fileNodes.size());
    return successCount;
  }

  /**
   * ディレクトリを再帰的に削除する。途中で 1 件でも失敗があれば、すべての試行後に
   * 集約した IOException を throw する (部分削除で成功扱いにしない)。
   *
   * @param directory 削除対象ディレクトリ
   * @throws IOException 削除に失敗した場合
   */
  private void deleteDirectoryRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }

    List<IOException> failures = Collections.synchronizedList(new ArrayList<>());
    try (Stream<Path> stream = Files.walk(directory)) {
      stream
          .sorted(Comparator.reverseOrder()) // ファイル → ディレクトリの順
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              logger.warn("ファイル削除に失敗しました: {}", path, e);
              failures.add(e);
            }
          });
    }

    if (!failures.isEmpty()) {
      IOException aggregate = new IOException(
          "ディレクトリの再帰削除で " + failures.size() + " 件の失敗が発生しました: " + directory);
      for (IOException f : failures) {
        aggregate.addSuppressed(f);
      }
      throw aggregate;
    }
  }

  /**
   * ファイルが削除可能かどうかをチェックする
   *
   * @param fileNode チェック対象のファイルノード
   * @return 削除可能な場合true
   */
  public boolean canDelete(FileNode fileNode) {
    try {
      Path path = fileNode.getPath();

      // ファイルが存在するかチェック
      if (!Files.exists(path)) {
        return false;
      }

      // 書き込み権限があるかチェック
      Path parent = path.getParent();
      if (parent == null || !Files.isWritable(parent)) {
        return false;
      }

      // システムファイルや重要なディレクトリは削除不可
      if (isSystemFile(path)) {
        return false;
      }

      return true;
    } catch (Exception e) {
      logger.warn("ファイル削除可能性チェックに失敗: {}", fileNode.getPath(), e);
      return false;
    }
  }

  /**
   * システムファイル / 重要ディレクトリ配下かどうかを判定する。
   * 文字列マッチではなく {@link Path#startsWith(Path)} ベースで厳密に判定する。
   * シンボリックリンクは {@code toRealPath} で解決した上で比較する。
   *
   * @param path チェック対象のパス
   * @return システムファイルの場合 true
   */
  private boolean isSystemFile(Path path) {
    Path target = normalizeForCheck(path);
    if (target == null) {
      // 正規化できないパス (アクセス不能など) はフェイルセーフで保護対象扱い
      return true;
    }
    for (Path root : SYSTEM_ROOTS) {
      if (target.startsWith(root)) {
        return true;
      }
    }
    return false;
  }

  /**
   * パスを比較用に正規化する。実体が存在すれば {@code toRealPath} でシンボリック
   * リンクを解決し、存在しない場合は絶対パスへ正規化する。
   */
  private Path normalizeForCheck(Path path) {
    try {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return path.toRealPath();
      }
      return path.toAbsolutePath().normalize();
    } catch (IOException e) {
      logger.warn("パスの正規化に失敗しました: {}", path, e);
      return null;
    }
  }

  /**
   * 現在の OS に応じたシステムディレクトリの一覧を構築する。
   */
  private static List<Path> resolveSystemRoots() {
    String osName = System.getProperty("os.name", "").toLowerCase();
    List<Path> roots = new ArrayList<>();

    if (osName.contains("mac")) {
      addIfPresent(roots, "/System");
      addIfPresent(roots, "/Library");
      addIfPresent(roots, "/Applications");
      addIfPresent(roots, "/Users/Shared");
      addIfPresent(roots, "/usr");
      addIfPresent(roots, "/bin");
      addIfPresent(roots, "/sbin");
    } else if (osName.contains("windows")) {
      addIfPresentEnv(roots, "WINDIR");
      addIfPresentEnv(roots, "SystemRoot");
      addIfPresentEnv(roots, "ProgramFiles");
      addIfPresentEnv(roots, "ProgramFiles(x86)");
      addIfPresentEnv(roots, "ProgramW6432");
    } else {
      // Linux / その他 UNIX 系
      addIfPresent(roots, "/etc");
      addIfPresent(roots, "/var");
      addIfPresent(roots, "/usr");
      addIfPresent(roots, "/bin");
      addIfPresent(roots, "/sbin");
      addIfPresent(roots, "/boot");
      addIfPresent(roots, "/proc");
      addIfPresent(roots, "/sys");
    }

    return Collections.unmodifiableList(roots);
  }

  private static void addIfPresent(List<Path> roots, String pathString) {
    Path path = Paths.get(pathString);
    try {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        roots.add(path.toRealPath());
      } else {
        roots.add(path.toAbsolutePath().normalize());
      }
    } catch (IOException e) {
      roots.add(path.toAbsolutePath().normalize());
    }
  }

  private static void addIfPresentEnv(List<Path> roots, String envName) {
    String value = System.getenv(envName);
    if (value != null && !value.isBlank()) {
      addIfPresent(roots, value);
    }
  }
}
