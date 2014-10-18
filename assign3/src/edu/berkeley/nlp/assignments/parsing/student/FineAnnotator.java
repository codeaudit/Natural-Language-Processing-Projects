package edu.berkeley.nlp.assignments.parsing.student;

import java.util.*;
import java.util.ArrayList;

import edu.berkeley.nlp.ling.Tree;

class FineAnnotator
{
  static Tree<String> annotateTree(Tree<String> unannotated) {
    return binarizeTree(unannotated, "", "");
  }

  static Tree<String> binarizeTree(Tree<String> tree, String parent, String parent2) {
    String label = tree.getLabel();
    String parentLabel = parent.isEmpty() ? "" : "^" + parent;
    parentLabel = parentLabel + (parent2.isEmpty() ? "" : "^" + parent2);
    if (tree.isLeaf()) return new Tree<String>(label);
    if (tree.getChildren().size() == 1) {
      if (!tree.isPreTerminal() && !label.equals("ROOT")) {
        parentLabel = parentLabel + "-U";
      }
      Tree<String> child = tree.getChildren().get(0);
      if (child.getLabel().equals("RB") || child.getLabel().equals("DT")) {
        parent = parent + "^U";
      }
      return new Tree<String>(label + parentLabel, Collections.singletonList(
              binarizeTree(tree.getChildren().get(0), label, parent)
      ));
    }
    // otherwise, it's a binary-or-more local tree, so decompose it into a sequence of binary and unary trees.
    String labelHeader = "@" + label + "->";
    Tree<String> intermediateTree = binarizeTreeHelper(tree, 0, labelHeader, "", "", label, parent);
    return new Tree<String>(label + parentLabel, intermediateTree.getChildren());
  }

  static Tree<String> binarizeTreeHelper(Tree<String> tree, int numChildrenGenerated,
                                         String labelHeader, String prev, String prev2,
                                         String parent, String parent2) {
    Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(binarizeTree(leftTree, parent, parent2));
    if (numChildrenGenerated < tree.getChildren().size() - 1) {
      Tree<String> rightTree = binarizeTreeHelper(
              tree, numChildrenGenerated + 1, labelHeader,
              leftTree.getLabel(), prev, parent, parent2);
      children.add(rightTree);
    }
    return new Tree<String>(
            labelHeader + (prev2.isEmpty() ? "" : "_" + prev2) + (prev.isEmpty() ? "" : "_" + prev),
            children);
  }
}

