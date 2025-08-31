# LumiDisk API リファレンス

## 概要

このドキュメントは、LumiDiskプロジェクトの主要なクラスとメソッドのAPI仕様を定義します。

## パッケージ構成

### com.example.diskanalyzer

メインアプリケーションクラス

#### MainApp

アプリケーションのエントリーポイント

```java
public class MainApp extends Application
```

**メソッド**

| メソッド                    | 説明                                       | パラメータ           | 戻り値 |
| --------------------------- | ------------------------------------------ | -------------------- | ------ |
| `start(Stage primaryStage)` | JavaFXアプリケーションの開始               | `Stage primaryStage` | `void` |
| `main(String[] args)`       | アプリケーションのメインエントリーポイント | `String[] args`      | `void` |

---

### com.example.diskanalyzer.controller

UIコントローラークラス

#### MainController

メイン画面のコントローラー

```java
public class MainController implements Initializable
```

**フィールド**

| フィールド              | 型                    | 説明                   |
| ----------------------- | --------------------- | ---------------------- |
| `selectDirectoryButton` | `Button`              | ディレクトリ選択ボタン |
| `scanButton`            | `Button`              | スキャン開始ボタン     |
| `exportCsvButton`       | `Button`              | CSV出力ボタン          |
| `exportJsonButton`      | `Button`              | JSON出力ボタン         |
| `progressBar`           | `ProgressBar`         | 進捗バー               |
| `statusLabel`           | `Label`               | ステータスラベル       |
| `scanInfoLabel`         | `Label`               | スキャン情報ラベル     |
| `pieChart`              | `PieChart`            | 円グラフ               |
| `displayCountComboBox`  | `ComboBox<String>`    | 表示件数選択           |
| `fileTable`             | `TableView<FileNode>` | ファイル一覧テーブル   |

**メソッド**

| メソッド                                             | 説明                               | パラメータ                                 | 戻り値 |
| ---------------------------------------------------- | ---------------------------------- | ------------------------------------------ | ------ |
| `initialize(URL location, ResourceBundle resources)` | コントローラーの初期化             | `URL location`, `ResourceBundle resources` | `void` |
| `handleSelectDirectory(ActionEvent event)`           | ディレクトリ選択イベントハンドラー | `ActionEvent event`                        | `void` |
| `handleScan(ActionEvent event)`                      | スキャン開始イベントハンドラー     | `ActionEvent event`                        | `void` |
| `handleExportCsv(ActionEvent event)`                 | CSV出力イベントハンドラー          | `ActionEvent event`                        | `void` |
| `handleExportJson(ActionEvent event)`                | JSON出力イベントハンドラー         | `ActionEvent event`                        | `void` |
| `handleDisplayCountChange(ActionEvent event)`        | 表示件数変更イベントハンドラー     | `ActionEvent event`                        | `void` |

#### ScanTask

バックグラウンドスキャンタスク

```java
public class ScanTask extends Task<ScanResult>
```

**フィールド**

| フィールド    | 型            | 説明               |
| ------------- | ------------- | ------------------ |
| `rootPath`    | `Path`        | スキャン対象パス   |
| `fileScanner` | `FileScanner` | ファイルスキャナー |

**メソッド**

| メソッド      | 説明               | パラメータ | 戻り値       |
| ------------- | ------------------ | ---------- | ------------ |
| `call()`      | タスクの実行       | なし       | `ScanResult` |
| `succeeded()` | 成功時の処理       | なし       | `void`       |
| `failed()`    | 失敗時の処理       | なし       | `void`       |
| `cancelled()` | キャンセル時の処理 | なし       | `void`       |

---

### com.example.diskanalyzer.model

データモデルクラス

#### FileNode

ファイル・ディレクトリ情報を保持するモデル

```java
public class FileNode
```

**フィールド**

| フィールド    | 型         | 説明               |
| ------------- | ---------- | ------------------ |
| `path`        | `Path`     | ファイルパス       |
| `size`        | `long`     | サイズ（バイト）   |
| `modified`    | `FileTime` | 更新日時           |
| `isDirectory` | `boolean`  | ディレクトリフラグ |
| `isHidden`    | `boolean`  | 隠しファイルフラグ |
| `extension`   | `String`   | 拡張子             |

**コンストラクタ**

| コンストラクタ                                                                             | 説明                         | パラメータ                                                                               |
| ------------------------------------------------------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------- |
| `FileNode(Path path, long size, FileTime modified, boolean isDirectory, boolean isHidden)` | FileNodeのインスタンスを作成 | `Path path`, `long size`, `FileTime modified`, `boolean isDirectory`, `boolean isHidden` |

**メソッド**

| メソッド                | 説明                                 | パラメータ | 戻り値          |
| ----------------------- | ------------------------------------ | ---------- | --------------- |
| `getPath()`             | ファイルパスを取得                   | なし       | `Path`          |
| `getSize()`             | サイズを取得                         | なし       | `long`          |
| `getModified()`         | 更新日時を取得                       | なし       | `FileTime`      |
| `isDirectory()`         | ディレクトリかどうかを判定           | なし       | `boolean`       |
| `isHidden()`            | 隠しファイルかどうかを判定           | なし       | `boolean`       |
| `getExtension()`        | 拡張子を取得                         | なし       | `String`        |
| `getName()`             | ファイル名を取得                     | なし       | `String`        |
| `getModifiedDateTime()` | 更新日時をLocalDateTimeで取得        | なし       | `LocalDateTime` |
| `getFormattedSize()`    | フォーマットされたサイズ文字列を取得 | なし       | `String`        |

#### ScanResult

スキャン結果を保持するモデル

```java
public class ScanResult
```

**フィールド**

| フィールド         | 型                  | 説明           |
| ------------------ | ------------------- | -------------- |
| `files`            | `List<FileNode>`    | ファイル一覧   |
| `extensionStats`   | `Map<String, Long>` | 拡張子統計     |
| `totalSize`        | `long`              | 総サイズ       |
| `totalFiles`       | `int`               | ファイル数     |
| `totalDirectories` | `int`               | ディレクトリ数 |
| `scanDuration`     | `long`              | スキャン時間   |

**コンストラクタ**

| コンストラクタ                                                                                                                                | 説明                           | パラメータ                                                                                                                                  |
| --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `ScanResult(List<FileNode> files, Map<String, Long> extensionStats, long totalSize, int totalFiles, int totalDirectories, long scanDuration)` | ScanResultのインスタンスを作成 | `List<FileNode> files`, `Map<String, Long> extensionStats`, `long totalSize`, `int totalFiles`, `int totalDirectories`, `long scanDuration` |

**メソッド**

| メソッド                     | 説明                                       | パラメータ | 戻り値              |
| ---------------------------- | ------------------------------------------ | ---------- | ------------------- |
| `getFiles()`                 | ファイル一覧を取得                         | なし       | `List<FileNode>`    |
| `getExtensionStats()`        | 拡張子統計を取得                           | なし       | `Map<String, Long>` |
| `getTotalSize()`             | 総サイズを取得                             | なし       | `long`              |
| `getTotalFiles()`            | ファイル数を取得                           | なし       | `int`               |
| `getTotalDirectories()`      | ディレクトリ数を取得                       | なし       | `int`               |
| `getScanDuration()`          | スキャン時間を取得                         | なし       | `long`              |
| `getFormattedTotalSize()`    | フォーマットされた総サイズ文字列を取得     | なし       | `String`            |
| `getFormattedScanDuration()` | フォーマットされたスキャン時間文字列を取得 | なし       | `String`            |

---

### com.example.diskanalyzer.service

ビジネスロジックサービス

#### FileScanner

ファイルシステムスキャンサービス

```java
public class FileScanner
```

**フィールド**

| フィールド    | 型             | 説明           |
| ------------- | -------------- | -------------- |
| `pool`        | `ForkJoinPool` | 並列処理プール |
| `parallelism` | `int`          | 並列度         |

**コンストラクタ**

| コンストラクタ                 | 説明                                | パラメータ        |
| ------------------------------ | ----------------------------------- | ----------------- |
| `FileScanner()`                | デフォルト並列度でFileScannerを作成 | なし              |
| `FileScanner(int parallelism)` | 指定並列度でFileScannerを作成       | `int parallelism` |

**メソッド**

| メソッド          | 説明                   | パラメータ  | 戻り値       | 例外          |
| ----------------- | ---------------------- | ----------- | ------------ | ------------- |
| `scan(Path root)` | 指定パス配下をスキャン | `Path root` | `ScanResult` | `IOException` |

#### ExportService

エクスポートサービス

```java
public class ExportService
```

**フィールド**

| フィールド      | 型                  | 説明                  |
| --------------- | ------------------- | --------------------- |
| `ISO_FORMATTER` | `DateTimeFormatter` | ISO日時フォーマッター |

**メソッド**

| メソッド                                               | 説明                   | パラメータ                                 | 戻り値 | 例外          |
| ------------------------------------------------------ | ---------------------- | ------------------------------------------ | ------ | ------------- |
| `exportToCsv(ScanResult scanResult, Path outputPath)`  | CSV形式でエクスポート  | `ScanResult scanResult`, `Path outputPath` | `void` | `IOException` |
| `exportToJson(ScanResult scanResult, Path outputPath)` | JSON形式でエクスポート | `ScanResult scanResult`, `Path outputPath` | `void` | `IOException` |

**内部クラス**

##### ExportData

エクスポート用データクラス

```java
public static class ExportData
```

**フィールド**

| フィールド         | 型                  | 説明           |
| ------------------ | ------------------- | -------------- |
| `files`            | `List<FileNode>`    | ファイル一覧   |
| `extensionStats`   | `Map<String, Long>` | 拡張子統計     |
| `totalSize`        | `long`              | 総サイズ       |
| `totalFiles`       | `int`               | ファイル数     |
| `totalDirectories` | `int`               | ディレクトリ数 |
| `scanDuration`     | `long`              | スキャン時間   |

---

## 例外クラス

### ScanException

スキャン処理中の例外

```java
public class ScanException extends RuntimeException
```

**コンストラクタ**

| コンストラクタ                                   | 説明                             | パラメータ                          |
| ------------------------------------------------ | -------------------------------- | ----------------------------------- |
| `ScanException(String message)`                  | メッセージ付きで例外を作成       | `String message`                    |
| `ScanException(String message, Throwable cause)` | メッセージと原因付きで例外を作成 | `String message`, `Throwable cause` |

---

## データ形式

### CSV出力形式

```csv
path,type,size_bytes,modified_iso,ext,is_hidden
/path/to/file.txt,file,1024,2025-08-31T10:30:00,.txt,false
/path/to/directory,dir,0,2025-08-31T10:30:00,,false
```

### JSON出力形式

```json
{
  "files": [
    {
      "path": "/path/to/file.txt",
      "size": 1024,
      "modified": "2025-08-31T10:30:00",
      "isDirectory": false,
      "isHidden": false,
      "extension": "txt"
    }
  ],
  "extensionStats": {
    "txt": 1024,
    "pdf": 2048
  },
  "totalSize": 3072,
  "totalFiles": 2,
  "totalDirectories": 1,
  "scanDuration": 100
}
```

---

## 使用例

### 基本的なスキャン処理

```java
// FileScannerの作成
FileScanner scanner = new FileScanner();

// スキャン実行
Path targetPath = Paths.get("/path/to/scan");
ScanResult result = scanner.scan(targetPath);

// 結果の取得
List<FileNode> files = result.getFiles();
long totalSize = result.getTotalSize();
```

### エクスポート処理

```java
// ExportServiceの作成
ExportService exportService = new ExportService();

// CSV出力
Path csvPath = Paths.get("output.csv");
exportService.exportToCsv(scanResult, csvPath);

// JSON出力
Path jsonPath = Paths.get("output.json");
exportService.exportToJson(scanResult, jsonPath);
```

### JavaFXでの使用

```java
// バックグラウンドタスクの作成
ScanTask scanTask = new ScanTask(targetPath);

// イベントハンドラーの設定
scanTask.setOnSucceeded(event -> {
    ScanResult result = scanTask.getValue();
    // UI更新処理
});

// タスクの実行
Thread thread = new Thread(scanTask);
thread.setDaemon(true);
thread.start();
```

---

**最終更新**: 2025年8月31日  
**バージョン**: 1.0
