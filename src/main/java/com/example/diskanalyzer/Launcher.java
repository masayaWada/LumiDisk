package com.example.diskanalyzer;

/**
 * 非モジュラ JavaFX を {@code jpackage} で配布する際のエントリポイント。
 * Application を継承しないため、JavaFX が classpath 起動時に出す
 * "JavaFX runtime components are missing" 検査を回避できる。
 */
public class Launcher {

  public static void main(String[] args) {
    MainApp.main(args);
  }
}
