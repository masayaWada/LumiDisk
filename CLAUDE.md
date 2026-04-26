# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

LumiDisk は JavaFX 製の Windows / macOS 向けディスク使用量分析ツール。`Files.walkFileTree` + `ForkJoinPool` でマルチスレッドスキャンを行い、円グラフ・テーブル・ツリーマップで可視化する。

## ビルド・実行コマンド

```bash
./gradlew run                # アプリ起動 (JavaFX plugin が module-path を解決)
./gradlew build              # コンパイル + テスト
./gradlew test               # JUnit 5 テスト (現状 src/test は未作成)
./gradlew test --tests <FQCN>  # 単一テストクラスのみ
./gradlew clean              # build/ 削除
```

- Gradle Wrapper 同梱。Java 21 toolchain が `build.gradle.kts` で固定されている。
- `mainClass` は `com.example.diskanalyzer.MainApp`。
- `gradle/wrapper/gradle-wrapper.jar` は `b59e615` で track 済み。万一再生成が必要なら `gradle wrapper --gradle-version 9.0.0`。
- **`.gitignore` 順序の落とし穴**: `*.jar` (行 59) より **後** に `!gradle/wrapper/gradle-wrapper.jar` の negation を再宣言している (`.gitignore` 末尾)。前段の `!` だけでは `*.jar` で上書きされるため、`.gitignore` を編集する際はこの順序を崩さないこと。
- **macOS で Java 未導入の場合**: `brew install openjdk@21` 後、`JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` を設定。README に詳細手順あり。

## アーキテクチャ概要

`com.example.diskanalyzer` 配下で MVC + Service 層構成。読むときは「Controller がスレッドを起動し、Service が ForkJoinPool で並列処理し、Model を返す」という流れを押さえると速い。

### スキャンの実行パス
1. `MainController.handleScan` が `ScanTask`（JavaFX `Task` サブクラス）を生成し、デーモンスレッドで起動。
2. `ScanTask` は `FileScanner.scan(Path)` を呼ぶ。`FileScanner` は内部で **独自の `ForkJoinPool`** を作り、`Files.walkFileTree` を 1 サブミットでまるごと回す（並列度はコア数）。
3. 集計は `ConcurrentLinkedQueue<FileNode>` + `AtomicLong` / `AtomicInteger` / `ConcurrentHashMap<String, AtomicLong>` で行い、最後に `ScanResult` にまとめる。
4. `MainController` は `Platform.runLater` で結果を受け取り、`VirtualizedTableController` でテーブル更新、`updatePieChart` で円グラフ更新。

### Service の ForkJoinPool ライフサイクル規約 (Phase 1 で確立)
- pool は **メソッド呼び出しごとに生成**し、try-finally で堅牢 shutdown (`awaitTermination(5s)` → `shutdownNow()`)。singleton フィールドの長寿命 pool は禁止。
- 公開 `shutdown()` は冪等 (現在 pool が無ければ no-op、shutdown 済みなら再 shutdown しない)。Controller の `setOnSucceeded` / `setOnFailed` 両方から呼ばれる前提。
- Process I/O ストリーム (`getInputStream` / `getErrorStream` / `getOutputStream`) は `waitFor()` 後の finally で必ず close (`FileManagerService.closeProcessStreams` 参照)。

### 永続化レイヤの規約 (Phase 3 で確立)
- スナップショット JSON は Jackson + `JavaTimeModule` + 専用 `FileTime` Module で読み書きする (`ScanCacheService.fileTimeModule()`)。`FileTime` は Jackson 既定では serialize 不能。
- 永続化対象モデル (`FileNode` / `ScanSnapshot`) はコンストラクタに `@JsonCreator` + 各引数に `@JsonProperty` を付ける。final フィールドのため deserialize できない silent バグを防ぐ。
- `FAIL_ON_UNKNOWN_PROPERTIES` は無効。derived getter (例: `FileNode.getExtension()`) が JSON に出ても deserialize で落ちないようにする。
- モデルにフィールド追加するときは: (1) コンストラクタの `@JsonProperty` を更新、(2) 既存スナップショット JSON との互換 (古い JSON に新フィールドが無い場合のデフォルト値) を意識する。

### 増分スキャンとキャッシュ
- `IncrementalScanService.incrementalScan(Path)` は前回の `ScanSnapshot` を `ScanCacheService` から取得し、サイズと `lastModifiedTime` の差分のあるパスだけ再スキャンする。
- スナップショットは `~/.lumidisk/cache/<pathHash>_<timestamp>.snapshot.json` に Jackson + JavaTimeModule で保存。最大 10 件、超過分は古いものから削除。
- 初回スキャン時はスナップショットがないため自動でフルスキャンへフォールバックする。

### 重複検出と可視化
- `DuplicateDetectionService` はまずファイルサイズで候補を絞り、同サイズが 2 件以上あるものだけ SHA-256 ハッシュ（8KB チャンク）を ForkJoinPool で並列計算する。`DuplicateGroup` を返す。
- `VisualizationService` はツリーマップ用の `TreeNode` ツリーと、拡張子別統計 (`ExtensionStats`) を生成する。両方ともダイアログ表示のためのデータ構造で、専用ビューコンポーネントは未実装（テキスト Alert で表示）。

### 大規模データの UI 対応
- `VirtualizedTableController` が `TableView` をページング表示する自前の仮想化レイヤ。`MainController.updateUI` が `currentScanResult.getFiles()` を渡して初期化／更新する。`fileTable` に直接 `setItems` するパスは削除イベント時の `updateFileTable()` のみ。

### ユーティリティサービス（service/ 配下）
- `ExportService.exportToCsv` / `exportToJson` — `ScanResult` を CSV/JSON で書き出す。Jackson 利用。
- `FileDeleteService` — 選択ファイルを OS のゴミ箱（Trash / 回収箱）に送る。`MainController` の右クリックメニューから呼ばれる。
- `FileManagerService` — Finder（macOS）/ Explorer（Windows）で対象パスを開く。OS 判定はここで完結。

### サイズ表示の落とし穴
`FileNode.getFormattedSize()` は "1.5 MB" のような **単位付き文字列** を返す。`MainController` の sizeColumn は文字列カラムなので、`parseSizeToBytes` を `Comparator` に渡してバイト換算で比較するソート設定が入っている。新しいサイズ表示を追加するときはこの規約を維持すること（KB/MB/GB/B 以外のサフィックスを増やす場合は `parseSizeToBytes` も拡張する）。

## コーディング規約 (`docs/コーディング規約.md` 抜粋)

- インデント **2 スペース**、行長 **120 文字以内**、K&R 中括弧。
- `public` クラス・メソッド・フィールドには JavaDoc を書く。
- 命名: クラス PascalCase / メソッド・変数 camelCase / 定数 UPPER_SNAKE_CASE。
- `try-with-resources` を優先。例外は具体型でキャッチし `logger.warn`/`error` で記録、必要なら `ScanException` 等にラップ。
- ストリーム API と不変コレクション (`List.of(...)`) を優先。

## コミットメッセージ (`docs/コミットメッセージガイドライン.md`)

Conventional Commits 形式。本文・件名は日本語可。

```
<type>(<scope>): <subject>

<body 任意・72 文字で改行>

refs #<issue>
```

- 主な type: `feat` / `fix` / `docs` / `refactor` / `perf` / `test` / `chore` / `ci` / `build` / `style`。
- 主な scope: `ui` / `controller` / `service` / `model` / `scanner` / `export` / `chart` / `table` / `docs` / `config` / `build`。
- subject は 50 字以内・小文字始まり・末尾ピリオドなし。

## ログ

- Logback 設定は `src/main/resources/logback.xml`。
- 出力先（実運用）: Windows `%LOCALAPPDATA%/LumiDisk/logs/`、macOS `~/Library/Logs/LumiDisk/`。ローテーション 10MB × 5。
- 出力先は `MainApp` の `static {}` ブロックで `LUMIDISK_LOG_DIR` system property を設定し、`logback.xml` から `${LUMIDISK_LOG_DIR:-logs}` で参照する仕組み。**`Logger` フィールドより先に static ブロックを置く順序依存**があるので、`MainApp` を編集するときは初期化順序を崩さないこと。

## 既知の制約・注意点

- シンボリックリンク・ショートカットの追跡は行わない。
- Windows の代替データストリーム (ADS) はサイズ集計対象外。
- macOS は Full Disk Access が無いと一部ディレクトリをスキップする。
- テスト基盤は `src/test/java/com/example/diskanalyzer/service/` に JUnit 5 + `@TempDir` 構成で整備済み (現在 13 ケース)。新規テストを足すときは同パッケージに同じスタイルで追加する。
- **Gradle 9 必須依存**: `build.gradle.kts` で `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` を宣言済み。これが無いと `gradle test` が "Failed to load JUnit Platform" で失敗する。
- 配布バイナリは無署名。Gatekeeper / SmartScreen 警告が出る前提でユーザ向け説明を更新すること。
- 配布パッケージングは `./gradlew jpackage` で実行可能 (`build.gradle.kts` に手書き Exec タスクとして定義済み)。出力先 `build/jpackage/`、OS 別に dmg/exe/deb を生成。`jpackage` はクロスコンパイル不可なので、exe は Windows、dmg は macOS でそれぞれビルドする必要があり、両方を一度に作るには `.github/workflows/release.yml` のマトリクス CI を使う。
- 非モジュラ JavaFX で jpackage する都合上、エントリポイントには `Launcher` クラス (Application を継承しない wrapper) を使う。`MainApp` を直接 `--main-class` に渡すと "JavaFX runtime components are missing" で起動しない。`./gradlew run` 用には引き続き `application.mainClass` が `MainApp` を指している (openjfx プラグインが module-path を組むため変更不要)。
- `org.beryx.runtime` プラグインは Gradle 9.0 と非互換 (`Project.exec()` 削除の影響で `:jre` タスクが落ちる) のため採用していない。Gradle 8 系に戻る場合のみ再検討する。
- 永続化はキャッシュ用 JSON のみで運用 (Phase 4 で `org.xerial:sqlite-jdbc` を依存から外した)。SQLite を前提にしないこと。
- `docs/改善計画.md` 等のドキュメントに記載されたファイルパスは、コードベース実体とずれていることがある (例: `scanner/FileScanner.java` 表記の実体は `service/FileScanner.java`)。引用前に `find`/`grep` で確認すること。

## 詳細ドキュメント

- `docs/アーキテクチャ設計書.md` — MVC + Service レイヤー詳細、データフロー図
- `docs/APIリファレンス.md` — 主要クラスの API
- `docs/コーディング規約.md` — JavaFX / Java スタイル詳細
- `docs/コミットメッセージガイドライン.md` — コミット規約全文
- `docs/開発ガイド.md` — 環境構築・テスト・リリースフロー
