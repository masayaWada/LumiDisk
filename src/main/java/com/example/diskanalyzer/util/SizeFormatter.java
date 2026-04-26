package com.example.diskanalyzer.util;

/**
 * バイト数を人間可読なサイズ文字列にフォーマットするユーティリティ。
 */
public final class SizeFormatter {

  private SizeFormatter() {
    // utility class
  }

  /**
   * バイト数を "1.5 MB" 形式の単位付き文字列にフォーマットする。
   *
   * @param bytes フォーマット対象のバイト数 (非負前提、負値も受け付けるが結果は B 表示)
   * @return 単位付きの読みやすい文字列
   */
  public static String format(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024L * 1024L) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024L * 1024L * 1024L) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
  }
}
