package com.example.diskanalyzer.controller;

import com.example.diskanalyzer.model.FileNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 仮想化テーブルコントローラー
 * 大量のデータを効率的に表示するためのコントローラー
 */
public class VirtualizedTableController {
  private static final Logger logger = LoggerFactory.getLogger(VirtualizedTableController.class);

  private static final int PAGE_SIZE = 100; // 1ページあたりの表示件数
  private static final int PREFETCH_SIZE = 200; // プリフェッチする件数

  private final TableView<FileNode> table;
  private final ObservableList<FileNode> displayedItems;
  private final List<FileNode> allItems;

  private int currentPage = 0;
  private int totalPages = 0;
  private boolean isLoading = false;
  private AtomicInteger loadCounter = new AtomicInteger(0);

  public VirtualizedTableController(TableView<FileNode> table, List<FileNode> allItems) {
    this.table = table;
    this.allItems = allItems;
    this.displayedItems = FXCollections.observableArrayList();
    this.table.setItems(displayedItems);

    calculateTotalPages();
    loadInitialPage();
  }

  /**
   * 初期ページを読み込む
   */
  private void loadInitialPage() {
    loadPage(0);
  }

  /**
   * 指定ページを読み込む
   */
  public void loadPage(int page) {
    if (isLoading || page < 0 || page >= totalPages) {
      return;
    }

    isLoading = true;
    currentPage = page;

    Task<Void> loadTask = new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, allItems.size());

        List<FileNode> pageItems = allItems.subList(startIndex, endIndex);

        Platform.runLater(() -> {
          displayedItems.clear();
          displayedItems.addAll(pageItems);
          isLoading = false;

          // テーブルを先頭にスクロール
          if (!displayedItems.isEmpty()) {
            table.scrollTo(0);
          }
        });

        return null;
      }
    };

    Thread loadThread = new Thread(loadTask);
    loadThread.setDaemon(true);
    loadThread.start();
  }

  /**
   * 次のページを読み込む
   */
  public void loadNextPage() {
    if (currentPage < totalPages - 1) {
      loadPage(currentPage + 1);
    }
  }

  /**
   * 前のページを読み込む
   */
  public void loadPreviousPage() {
    if (currentPage > 0) {
      loadPage(currentPage - 1);
    }
  }

  /**
   * 最初のページに戻る
   */
  public void loadFirstPage() {
    loadPage(0);
  }

  /**
   * 最後のページに移動
   */
  public void loadLastPage() {
    loadPage(totalPages - 1);
  }

  /**
   * 指定されたアイテムを含むページを読み込む
   */
  public void loadPageContaining(FileNode item) {
    int index = allItems.indexOf(item);
    if (index >= 0) {
      int page = index / PAGE_SIZE;
      loadPage(page);

      // アイテムを選択状態にする
      Platform.runLater(() -> {
        table.getSelectionModel().select(item);
        table.scrollTo(item);
      });
    }
  }

  /**
   * 非同期でデータをプリフェッチする
   */
  public void prefetchData() {
    if (isLoading) {
      return;
    }

    Task<Void> prefetchTask = new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        int currentLoadId = loadCounter.incrementAndGet();

        // 現在のページの前後をプリフェッチ
        int startPage = Math.max(0, currentPage - 1);
        int endPage = Math.min(totalPages - 1, currentPage + 1);

        for (int page = startPage; page <= endPage; page++) {
          if (loadCounter.get() != currentLoadId) {
            // 新しいリクエストが来た場合は中断
            break;
          }

          int startIndex = page * PAGE_SIZE;
          int endIndex = Math.min(startIndex + PREFETCH_SIZE, allItems.size());

          // データをプリフェッチ（実際の処理はここでは行わない）
          List<FileNode> prefetchItems = allItems.subList(startIndex, endIndex);

          // プリフェッチ完了をログ出力
          logger.debug("ページ {} のプリフェッチ完了: {} 件", page, prefetchItems.size());
        }

        return null;
      }
    };

    Thread prefetchThread = new Thread(prefetchTask);
    prefetchThread.setDaemon(true);
    prefetchThread.start();
  }

  /**
   * データを更新する
   */
  public void updateData(List<FileNode> newItems) {
    // 現在の選択を保持
    FileNode selectedItem = table.getSelectionModel().getSelectedItem();

    // データを更新
    allItems.clear();
    allItems.addAll(newItems);

    calculateTotalPages();

    // 選択されたアイテムがまだ存在する場合はそのページを表示
    if (selectedItem != null && allItems.contains(selectedItem)) {
      loadPageContaining(selectedItem);
    } else {
      loadPage(0);
    }
  }

  /**
   * フィルタリングを適用する
   */
  public void applyFilter(java.util.function.Predicate<FileNode> filter) {
    List<FileNode> filteredItems = allItems.stream()
        .filter(filter)
        .collect(java.util.stream.Collectors.toList());

    updateData(filteredItems);
  }

  /**
   * ソートを適用する
   */
  public void applySort(java.util.Comparator<FileNode> comparator) {
    allItems.sort(comparator);
    loadPage(currentPage);
  }

  /**
   * 総ページ数を計算する
   */
  private void calculateTotalPages() {
    totalPages = (int) Math.ceil((double) allItems.size() / PAGE_SIZE);
    if (totalPages == 0) {
      totalPages = 1;
    }
  }

  /**
   * 現在のページ番号を取得する
   */
  public int getCurrentPage() {
    return currentPage;
  }

  /**
   * 総ページ数を取得する
   */
  public int getTotalPages() {
    return totalPages;
  }

  /**
   * 現在のページのアイテム数を取得する
   */
  public int getCurrentPageItemCount() {
    return displayedItems.size();
  }

  /**
   * 総アイテム数を取得する
   */
  public int getTotalItemCount() {
    return allItems.size();
  }

  /**
   * ページ情報を取得する
   */
  public String getPageInfo() {
    if (totalPages == 0) {
      return "0 / 0 ページ (0 件)";
    }

    int startItem = currentPage * PAGE_SIZE + 1;
    int endItem = Math.min((currentPage + 1) * PAGE_SIZE, allItems.size());

    return String.format("%d / %d ページ (%d - %d / %d 件)",
        currentPage + 1, totalPages, startItem, endItem, allItems.size());
  }

  /**
   * ローディング状態を取得する
   */
  public boolean isLoading() {
    return isLoading;
  }
}
