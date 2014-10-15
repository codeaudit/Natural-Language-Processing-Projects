package edu.berkeley.nlp.assignments.parsing.student;

import java.util.*;
import java.util.ArrayList;

import edu.berkeley.nlp.assignments.parsing.BinaryRule;
import edu.berkeley.nlp.assignments.parsing.TreeAnnotations;
import edu.berkeley.nlp.assignments.parsing.UnaryClosure;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.assignments.parsing.*;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.Trees;


public class GenerativeParserFactory implements ParserFactory {

  public Parser getParser(List<Tree<String>> trainTrees) {
    return new GenerativeParser(trainTrees);
  }

}

class GenerativeParser implements Parser {
  SimpleLexicon lexicon;
  Grammar grammar;
  Indexer<String> indexer;
  UnaryClosure unaryClosure;
  List<String> currentSentence;
  int numLabels;
  int length;

  GenerativeParser(List<Tree<String>> trainTrees) {
    ArrayList<Tree<String>> trees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trainTrees) {
//      if (Trees.PennTreeRenderer.render(tree).contains("EARNINGS")) {
//        Tree newTree = TreeAnnotations.annotateTreeLosslessBinarization(tree);
//        trees.add(newTree);
//        System.out.println(Trees.PennTreeRenderer.render(newTree));
//      }
      Tree newTree = TreeAnnotations.annotateTreeLosslessBinarization(tree);
      trees.add(newTree);
    }
    assert trees.size() > 0 : "No training trees";
    lexicon = new SimpleLexicon(trees);
    grammar = Grammar.generativeGrammarFromTrees(trees);
    indexer = grammar.getLabelIndexer();
    unaryClosure = new UnaryClosure(indexer, grammar.getUnaryRules());
    numLabels = indexer.size();

    if (TEST) {
      test();
      System.exit(0);
    }
  }

  double [][][] binaryScores;
  double [][][] unaryScores;
  int [][][] binaryRuleNum;
  int [][][] binaryK;
  int [][][] unaryChild;

  void initTables(int size) {
    binaryScores = new double[numLabels][][];
    unaryScores = new double[numLabels][][];
    binaryRuleNum = new int[numLabels][][];
    binaryK = new int[numLabels][][];
    unaryChild = new int[numLabels][][];
    initTable(unaryScores, size);
    initTable(binaryScores, size);
    initTable(binaryRuleNum, size);
    initTable(binaryK, size);
    initTable(unaryChild, size);
  }
  
  void initTable(double[][][] table, int size) {
    for (int x = 0; x < numLabels; x++) {
      double [][] page = new double[size][];
      table[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new double[size - i];
        Arrays.fill(page[i], Double.NaN);
      }
    }
  }

  void initTable(int[][][] table, int size) {
    for (int x = 0; x < numLabels; x++) {
      int [][] page = new int[size][];
      table[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new int[size - i];
        Arrays.fill(page[i], -1);
      }
    }
  }

  public Tree<String> getBestParse(List<String> sentence) {
//    System.out.println("sentence = " + sentence);
    currentSentence = sentence;
    length = sentence.size();
    initTables(length);

    for (int x = 0; x < numLabels; x++) {
      String transformedLabel = indexer.get(x);

      double [][] labelTable = binaryScores[x];
      for (int j = 0; j < length; j++) {
        double s = lexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
//        if (s != Double.NEGATIVE_INFINITY) {
//          System.out.println(sentence.get(j) + " " + transformedLabel);
//        }
        labelTable[j][length-j-1] = s;
      }
    }

    for (int sum = length - 1; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double s, ruleScore, max;

        for (int x = 0; x < numLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          if (sum != length - 1) {
            int ruleNum = 0;
            for (BinaryRule rule : grammar.getBinaryRulesByParent(x)) {
              ruleScore = rule.getScore();
              assert length - j > i + 1;
              for (int k = i + 1; k < length - j; k++) {
                s = ruleScore;
                assert ruleScore <= 0;
                s += unaryScores[rule.getLeftChild()][i][length - k];
                s += unaryScores[rule.getRightChild()][k][j];
                if (s > max) {
                  max = s;
                  binaryRuleNum[x][i][j] = ruleNum;
                  binaryK[x][i][j] = k;
                }
              }
              ruleNum++;
            }
            assert max == Double.NEGATIVE_INFINITY || binaryRuleNum[x][i][j] != -1;
            binaryScores[x][i][j] = max;
          }
        }

        for (int x = 0; x < numLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : unaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            s = rule.getScore();
            s += binaryScores[child][i][j];
            if (s > max) {
              max = s;
              unaryChild[x][i][j] = rule.getChild();
            }
          }
          if (!selfLooped) {
            s = binaryScores[x][i][j];
            if (s > max) {
              max = s;
              unaryChild[x][i][j] = x;
            }
          }
          unaryScores[x][i][j] = max;
        }
      }
    }

//    for (int x = 0; x < numLabels; x++) {
//      System.out.println(x + ": " + indexer.get(x));
//      printArray(unaryScores[x]);
//      printArray(binaryScores[x]);
//    }

//    System.out.println("score = " + unaryScores[0][0][0]);

    Tree<String> ret;
    if (unaryScores[0][0][0] == Double.NEGATIVE_INFINITY) {
      ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
    } else {
      ret = unaryTree(0, 0, 0);
    }
//    System.out.println(Trees.PennTreeRenderer.render(ret));
    return TreeAnnotations.unAnnotateTree(ret);
  }

  Tree<String> unaryTree(int x, int i, int j) {
    int child = unaryChild[x][i][j];

    if (i + j == length - 1) {
      List<Tree<String>> word = Collections.singletonList(new Tree<String>(currentSentence.get(i)));
      if (child == x) {
        return new Tree<String>(indexer.get(x), word);
      } else {
        return new Tree<String>(indexer.get(x), Collections.singletonList(new Tree<String>(indexer.get(child), word)));
      }
    }

    if (child == x) {
      return binaryTree(x, i, j);
    } else {
      return new Tree<String>(indexer.get(x), Collections.singletonList(binaryTree(child, i, j)));
    }
  }

  Tree<String> binaryTree(int x, int i, int j) {
    int ruleNum = binaryRuleNum[x][i][j];
    assert ruleNum != -1 : binaryScores[x][i][j];
    int k = binaryK[x][i][j];
    BinaryRule rule = grammar.getBinaryRulesByParent(x).get(ruleNum);
    ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(unaryTree(rule.getLeftChild(), i, length - k));
    children.add(unaryTree(rule.getRightChild(), k, j));
    return new Tree<String>(indexer.get(x), children);
  }

  boolean TEST = false;
  void test() {
    String raw = "EARNINGS :";
    List<String> sentence = Arrays.asList(raw.split(" "));
    System.out.println(getBestParse(sentence));
  }

  void printArray(double[][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
  void printArray(double[][][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
}


/**
 * Class which contains code for annotating and binarizing trees for the
 * parser's use, and debinarizing and unannotating them for scoring.
// */
//public class MyAnnotator
//{
//  /**
//   * This performs lossless binarization. You'll need to define your own
//   * function to do more intelligent markovization.
//   *
//   * @param unAnnotatedTree
//   * @return
//   */
//  public static Tree<String> annotateTreeLosslessBinarization(Tree<String> unAnnotatedTree) {
//
//    return binarizeTree(unAnnotatedTree);
//  }
//
//  private static Tree<String> binarizeTree(Tree<String> tree) {
//    String label = tree.getLabel();
//    if (tree.isLeaf()) return new Tree<String>(label);
//    if (tree.getChildren().size() == 1) { return new Tree<String>(label, Collections.singletonList(binarizeTree(tree.getChildren().get(0)))); }
//    // otherwise, it's a binary-or-more local tree, so decompose it into a sequence of binary and unary trees.
//    String intermediateLabel = "@" + label + "->";
//    Tree<String> intermediateTree = binarizeTreeHelper(tree, 0, intermediateLabel);
//    return new Tree<String>(label, intermediateTree.getChildren());
//  }
//
//  private static Tree<String> binarizeTreeHelper(Tree<String> tree, int numChildrenGenerated, String intermediateLabel) {
//    Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
//    List<Tree<String>> children = new ArrayList<Tree<String>>();
//    children.add(binarizeTree(leftTree));
//    if (numChildrenGenerated < tree.getChildren().size() - 1) {
//      Tree<String> rightTree = binarizeTreeHelper(tree, numChildrenGenerated + 1, intermediateLabel + "_" + leftTree.getLabel());
//      children.add(rightTree);
//    }
//    return new Tree<String>(intermediateLabel, children);
//  }
//
//  public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {
//    // Remove intermediate nodes (labels beginning with "@"
//    // Remove all material on node labels which follow their base symbol (cuts anything after <,>,^,=,_ or ->)
//    // Examples: a node with label @NP->DT_JJ will be spliced out, and a node with label NP^S will be reduced to NP
//    Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>()
//    {
//      public boolean accept(String s) {
//        return s.startsWith("@");
//      }
//    });
//    Tree<String> unAnnotatedTree = (new Trees.LabelNormalizer()).transformTree(debinarizedTree);
//    return unAnnotatedTree;
//  }
//}



