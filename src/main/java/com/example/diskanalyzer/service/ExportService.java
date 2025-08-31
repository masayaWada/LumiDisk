package com.example.diskanalyzer.service;

import com.example.diskanalyzer.model.FileNode;
import com.example.diskanalyzer.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * スキャン結果のエクスポートサービス
 */
public class ExportService {
  private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /**
   * CSV形式でエクスポート
   */
  public void exportToCsv(ScanResult scanResult, Path outputPath) throws IOException {
    logger.info("CSVエクスポート開始: {}", outputPath);

    try (FileWriter writer = new FileWriter(outputPath.toFile())) {
      // ヘッダー行
      writer.write("path,type,size_bytes,modified_iso,ext,is_hidden\n");

      // データ行
      for (FileNode file : scanResult.getFiles()) {
        writer.write(String.format("%s,%s,%d,%s,%s,%s\n",
            escapeCsv(file.getPath().toString()),
            file.isDirectory() ? "dir" : "file",
            file.getSize(),
            file.getModifiedDateTime().format(ISO_FORMATTER),
            escapeCsv(file.getExtension()),
            file.isHidden()));
      }
    }

    logger.info("CSVエクスポート完了: {} 件", scanResult.getFiles().size());
  }

  /**
   * JSON形式でエクスポート
   */
  public void exportToJson(ScanResult scanResult, Path outputPath) throws IOException {
    logger.info("JSONエクスポート開始: {}", outputPath);

    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    ExportData exportData = new ExportData(
        scanResult.getFiles(),
        scanResult.getExtensionStats(),
        scanResult.getTotalSize(),
        scanResult.getTotalFiles(),
        scanResult.getTotalDirectories(),
        scanResult.getScanDuration());

    mapper.writeValue(outputPath.toFile(), exportData);

    logger.info("JSONエクスポート完了: {} 件", scanResult.getFiles().size());
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  /**
   * エクスポート用のデータクラス
   */
  public static class ExportData {
    public final List<FileNode> files;
    public final java.util.Map<String, Long> extensionStats;
    public final long totalSize;
    public final int totalFiles;
    public final int totalDirectories;
    public final long scanDuration;

    public ExportData(List<FileNode> files, java.util.Map<String, Long> extensionStats,
        long totalSize, int totalFiles, int totalDirectories, long scanDuration) {
      this.files = files;
      this.extensionStats = extensionStats;
      this.totalSize = totalSize;
      this.totalFiles = totalFiles;
      this.totalDirectories = totalDirectories;
      this.scanDuration = scanDuration;
    }
  }
}
