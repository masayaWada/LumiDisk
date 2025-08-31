# LumiDisk

**LumiDisk** は Windows / macOS 向けのディスク使用量分析ツールです。\
**マルチスレッドによる高速かつ正確なスキャン**と**増分キャッシュ**を備え、ストレージ使用量を**直感的な円グラフ**で可視化します。

------------------------------------------------------------------------

## 主な特徴

-   数百万ファイル規模でも高速に処理できるマルチスレッドスキャン
-   円グラフと詳細テーブルによるわかりやすい可視化
-   コンテキストメニューから以下の操作が可能
    -   エクスプローラー/Finderで開く
    -   圧縮（Zip 形式）
    -   削除（ゴミ箱へ移動）
-   サイズ・更新日・拡張子でのフィルタリング
-   CSV/JSON 形式でのレポート出力
-   Windows/macOS 両対応（exe/app 形式で配布可能）

------------------------------------------------------------------------

## 今後の機能拡張予定

-   重複ファイル検出（内容ハッシュによる判定）
-   ストレージ整理提案（大容量ファイルの分割・アーカイブ・クリーンアップガイド）

------------------------------------------------------------------------

## スクリーンショット & デモ

### メイン画面（円グラフと詳細テーブル）

![メイン画面スクリーンショット](docs/images/main-ui.png)

### コンテキストメニュー操作例

![コンテキストメニュー](docs/images/context-menu.png)

### スキャンの流れ（GIFデモ）

![LumiDisk デモGIF](docs/images/demo.gif)

> ※ 上記の画像はダミーです。`docs/images/`
> 配下に実際のスクリーンショットや GIF を保存してください。

------------------------------------------------------------------------

## 動作環境

-   **Windows**: 10 / 11 (x64 / ARM64 予定)
-   **macOS**: 12 以降 (Intel / Apple Silicon)
-   **メモリ**: 4GB 以上推奨
-   **Java**: 実行環境不要（配布形式は exe / app）

------------------------------------------------------------------------

## インストール方法

### Windows

1.  [Releases](./releases) ページから `LumiDisk-x.y.z.exe`
    をダウンロード
2.  インストーラを実行
    -   SmartScreen 警告が表示される場合があります（署名なしのため）\
    -   「詳細情報」→「実行」を選択してください
3.  デスクトップまたはスタートメニューから起動

### macOS

1.  [Releases](./releases) ページから `LumiDisk-x.y.z.dmg`
    をダウンロード
2.  `LumiDisk.app` を `Applications` フォルダへコピー
3.  初回起動時に Gatekeeper 警告が表示される場合があります
    -   Finder で右クリック →「開く」で実行可能

------------------------------------------------------------------------

## 使い方

1.  アプリを起動すると「スキャン対象選択」ダイアログが表示されます
2.  フォルダまたはドライブを選択して「スキャン開始」をクリック
3.  進捗バーに処理状況が表示されます
4.  スキャン完了後、以下の画面で結果を確認できます
    -   **円グラフ**: ディスク使用量の割合を視覚化
    -   **テーブル**: ファイル/フォルダの詳細リスト
5.  コンテキストメニューから操作可能
    -   エクスプローラー/Finderで開く
    -   圧縮（Zip）
    -   削除（ゴミ箱へ）

------------------------------------------------------------------------

## プロジェクト構造

```
LumiDisk/
├── build.gradle.kts        # Gradle (Kotlin DSL) 設定
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/diskanalyzer/
│   │   │       ├── MainApp.java          # JavaFX アプリ起動
│   │   │       ├── controller/
│   │   │       │   ├── MainController.java
│   │   │       │   └── ScanTask.java     # マルチスレッドスキャンタスク
│   │   │       ├── model/
│   │   │       │   ├── FileNode.java     # ファイル情報モデル
│   │   │       │   └── ScanResult.java
│   │   │       ├── service/
│   │   │       │   ├── FileScanner.java  # スキャン処理 (ForkJoinPool)
│   │   │       │   └── ExportService.java
│   │   │       └── util/
│   │   │           └── LoggerFactory.java
│   │   └── resources/
│   │       ├── main.fxml                 # JavaFX UI 定義
│   │       └── logback.xml               # ログ設定
│   └── test/java/...                     # JUnit テスト
└── README.md
```

## 技術仕様

### アーキテクチャ
- **実装言語**: Java 21
- **GUI**: JavaFX
- **配布**: jpackage（Windows: exe/MSI, macOS: .app/.dmg）
- **データベース**: SQLite（キャッシュ・設定保存用）
- **並列処理**: ForkJoinPool / Virtual Threads（I/Oバウンド最適化）
- **ログ**: Logback（ローテーション対応）

### 主要機能の実装方針
- **マルチスレッドスキャン**: `Files.walkFileTree` + `ForkJoinPool` で高速並列処理
- **増分スキャン**: 前回スナップショットとの差分適用で高速化
- **正確性優先**: OSファイル属性ベースで厳密集計
- **メモリ最適化**: 大規模データでもページング/仮想化で1GB以内を目標

## ビルド方法（開発者向け）

### 必要環境

-   JDK 21
-   Gradle 8.x
-   Git

### ビルド手順

``` bash
git clone https://github.com/yourname/LumiDisk.git
cd LumiDisk
./gradlew clean build
```

### 実行方法

``` bash
./gradlew run
```

### パッケージング（exe/app 作成）

``` bash
./gradlew jpackage
```

出力先は `build/jpackage/` 配下になります。

### build.gradle.kts（主要設定）

```kotlin
plugins {
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.example.diskanalyzer.MainApp")
}
```

------------------------------------------------------------------------

## ログ出力

-   出力先:
    -   Windows: `%LOCALAPPDATA%/LumiDisk/logs/`\
    -   macOS: `~/Library/Logs/LumiDisk/`
-   保存形式: ローテーションログ (最大10MB × 5ファイル)
-   内容: 起動/スキャン開始/完了/削除/圧縮/エクスポート

------------------------------------------------------------------------

## 既知の制約

-   署名なし配布のため、初回起動時に OS から警告が出ます
-   シンボリックリンク・ショートカットのリンク先は追跡しません
-   Windows の代替データストリームはサイズ集計対象外です
-   macOS の Full Disk Access
    を付与しない場合、一部ディレクトリが解析対象外になることがあります

------------------------------------------------------------------------

## ドキュメント

詳細な技術ドキュメントは [`docs/`](./docs/) ディレクトリを参照してください：

- **[コーディング規約](./docs/コーディング規約.md)** - プロジェクトのコーディング規約とスタイルガイド
- **[アーキテクチャ設計書](./docs/アーキテクチャ設計書.md)** - システムのアーキテクチャと設計思想
- **[API リファレンス](./docs/APIリファレンス.md)** - 主要なクラスとメソッドのAPI仕様
- **[開発ガイド](./docs/開発ガイド.md)** - 開発環境構築からリリースまでの開発フロー
- **[コミットメッセージガイドライン](./docs/コミットメッセージガイドライン.md)** - コミットメッセージの規約と形式

------------------------------------------------------------------------

## ライセンス

MIT License
