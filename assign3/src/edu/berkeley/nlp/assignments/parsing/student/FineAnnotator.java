package edu.berkeley.nlp.assignments.parsing.student;

import java.util.*;
import java.util.ArrayList;

import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Filter;
import edu.berkeley.nlp.assignments.parsing.*;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.Trees;

class FineAnnotator
{
  static Tree<String> annotateTree(Tree<String> unannotated) {
    return binarizeTree(unannotated, "");
  }

  static Tree<String> binarizeTree(Tree<String> tree, String parent) {
    String label = tree.getLabel();
    String parentLabel = parent.isEmpty() ? "" : "^" + parent;
    if (tree.isLeaf()) return new Tree<String>(label);
    if (tree.getChildren().size() == 1) {
      return new Tree<String>(label + parentLabel, Collections.singletonList(
              binarizeTree(tree.getChildren().get(0), label)
      ));
    }
    // otherwise, it's a binary-or-more local tree, so decompose it into a sequence of binary and unary trees.
    String labelHeader = "@" + label + "->";
    Tree<String> intermediateTree = binarizeTreeHelper(tree, 0, labelHeader, "", "", label);
    return new Tree<String>(label + parentLabel, intermediateTree.getChildren());
  }

  static Tree<String> binarizeTreeHelper(Tree<String> tree, int numChildrenGenerated,
                                         String labelHeader, String prev, String prev2, String parent) {
    Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(binarizeTree(leftTree, parent));
    if (numChildrenGenerated < tree.getChildren().size() - 1) {
      Tree<String> rightTree = binarizeTreeHelper(tree, numChildrenGenerated + 1, labelHeader, leftTree.getLabel(), prev, parent);
      children.add(rightTree);
    }
    return new Tree<String>(
            labelHeader + (prev2.isEmpty() ? "" : ".." + prev2) + (prev.isEmpty() ? "" : "_" + prev),
            children);
  }

  static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {
    Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>()
    {
      public boolean accept(String s) {
        return s.startsWith("@");
      }
    });
    Tree<String> unAnnotatedTree = (new Trees.LabelNormalizer()).transformTree(debinarizedTree);
    return unAnnotatedTree;
  }
}
