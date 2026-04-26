# LumiDisk

**Version 1.0.0**

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

-   **JDK 21**（Java 21 toolchain 固定。Java 22 以降では動作未確認）
-   **Gradle**: リポジトリ同梱の Gradle Wrapper（`./gradlew`）を使用するため通常は不要
    -   ただし `gradle/wrapper/gradle-wrapper.jar` が欠落している環境ではシステム
        の Gradle 9.x が必要（後述）
-   **Git**

### JDK 21 のインストール

#### macOS（Homebrew）

``` bash
brew install openjdk@21

# JAVA_HOME を設定（zsh の例）
echo 'export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 確認
java -version  # => openjdk version "21.x.x"
```

#### Windows

[Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21)
からインストーラを取得し、インストール時に `JAVA_HOME` 環境変数の設定オプションを
有効にしてください。

PowerShell で確認:

``` powershell
java -version
```

### ビルド手順

``` bash
git clone https://github.com/yourname/LumiDisk.git
cd LumiDisk
./gradlew clean build
```

### gradle-wrapper.jar が無い場合のフォールバック

Wrapper jar が欠落しているクローンでは `./gradlew` が
`Unable to access jarfile gradle/wrapper/gradle-wrapper.jar` で失敗します。
その場合は次のいずれかで復旧してください。

``` bash
# 方法 A: システム Gradle で wrapper を再生成（推奨）
brew install gradle           # macOS
gradle wrapper --gradle-version 9.0.0

# 方法 B: 一度だけシステム Gradle で直接ビルド
gradle build
```

### 実行方法

``` bash
./gradlew run
```

### テスト実行

``` bash
./gradlew test                  # 全テスト
./gradlew test --tests <FQCN>   # 単一テストクラス
```

### パッケージング（exe / dmg 作成）

`org.beryx.runtime` プラグインで `jlink` カスタム JRE を作り、`jpackage`
で OS ネイティブインストーラを生成します。

``` bash
./gradlew jpackage
```

成果物は `build/jpackage/` に出力されます。

| OS | 成果物 | 生成例 |
| --- | --- | --- |
| macOS | `.dmg` | `build/jpackage/LumiDisk-1.0.0.dmg` |
| Windows | `.exe` | `build/jpackage/LumiDisk-1.0.0.exe` |
| Linux | `.deb` | `build/jpackage/lumidisk_1.0.0_amd64.deb` |

> **クロスコンパイル不可**: `jpackage` は実行ホストの OS 用パッケージしか作れません。
> macOS の `.dmg` は macOS で、Windows の `.exe` は Windows でそれぞれビルドする必要があります。
> 両方を一度に作るには `.github/workflows/release.yml`
> （`v*` タグ push or 手動実行で macOS / Windows マトリクスビルド → GitHub Releases へ添付）を利用してください。

> **署名・公証**: 本リリースの配布バイナリは無署名です。macOS は Gatekeeper、
> Windows は SmartScreen の警告が初回起動時に出る前提で配布してください。

### build.gradle.kts（主要設定）

```kotlin
plugins {
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.runtime") version "1.13.1"
}

group = "com.example.diskanalyzer"
version = "1.0.0"

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
