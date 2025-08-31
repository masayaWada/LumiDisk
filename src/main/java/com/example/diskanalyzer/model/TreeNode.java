package com.example.diskanalyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * ツリーマップ表示用のノードクラス
 */
public class TreeNode {
  private final String name;
  private final String path;
  private final long size;
  private final boolean isDirectory;
  private final List<TreeNode> children;
  private TreeNode parent;

  public TreeNode(String name, String path, long size, boolean isDirectory) {
    this.name = name;
    this.path = path;
    this.size = size;
    this.isDirectory = isDirectory;
    this.children = new ArrayList<>();
  }

  public String getName() {
    return name;
  }

  public String getPath() {
    return path;
  }

  public long getSize() {
    return size;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public List<TreeNode> getChildren() {
    return children;
  }

  public TreeNode getParent() {
    return parent;
  }

  public void setParent(TreeNode parent) {
    this.parent = parent;
  }

  public void addChild(TreeNode child) {
    child.setParent(this);
    children.add(child);
  }

  public boolean hasChildren() {
    return !children.isEmpty();
  }

  public int getChildCount() {
    return children.size();
  }

  public String getFormattedSize() {
    if (size < 1024) {
      return size + " B";
    } else if (size < 1024 * 1024) {
      return String.format("%.1f KB", size / 1024.0);
    } else if (size < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", size / (1024.0 * 1024.0));
    } else {
      return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
  }

  public double getSizePercentage(long totalSize) {
    if (totalSize == 0)
      return 0.0;
    return (double) size / totalSize * 100.0;
  }

  @Override
  public String toString() {
    return "TreeNode{" +
        "name='" + name + '\'' +
        ", size=" + size +
        ", isDirectory=" + isDirectory +
        ", children=" + children.size() +
        '}';
  }
}
