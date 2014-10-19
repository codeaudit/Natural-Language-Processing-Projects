package edu.berkeley.nlp.assignments.parsing.student;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.assignments.parsing.SimpleLexicon;
import edu.berkeley.nlp.assignments.parsing.ParserFactory;
import edu.berkeley.nlp.assignments.parsing.Parser;
import edu.berkeley.nlp.assignments.parsing.UnaryClosure;
import edu.berkeley.nlp.assignments.parsing.UnaryRule;
import edu.berkeley.nlp.assignments.parsing.BinaryRule;
import edu.berkeley.nlp.assignments.parsing.TreeAnnotations;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Counter;


public class CoarseToFineParserFactory implements ParserFactory {

  public Parser getParser(List<Tree<String>> trainTrees) {
     return new CoarseToFineParser(trainTrees);
  }
}

class CoarseToFineParser implements Parser {
  SimpleLexicon fineLexicon, coarseLexicon;
  Grammar fineGrammar, coarseGrammar;
  Indexer<String> fineIndexer, coarseIndexer;
  UnaryClosure fineUnaryClosure, coarseUnaryClosure;
  List<String> currentSentence;
  int numFineLabels, numCoarseLabels;
  int length;

  static double CELL_THRESH = -2;
  static double MIN_OCCURRENCES = 10d;
  final static int MAX_LENGTH = 40;

  CoarseToFineParser(List<Tree<String>> trainTrees) {
    String input;
    try {
      input = new BufferedReader(new FileReader("input.in")).readLine();
    } catch (Exception e) {
      input = "10";
    }
    System.out.println("input = " + input);
    MIN_OCCURRENCES = Double.parseDouble(input)*2;

    ArrayList<Tree<String>> fineTrees = new ArrayList<Tree<String>>();
    ArrayList<Tree<String>> coarseTrees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trainTrees) {
      Tree<String> newTree = FineAnnotator.annotateTree(tree);
      fineTrees.add(newTree);

      newTree = CoarseAnnotator.annotateTree(tree);
      coarseTrees.add(newTree);
    }
    assert fineTrees.size() > 0 : "No training trees";

    collapseTrees(fineTrees);

    fineLexicon = new SimpleLexicon(fineTrees);
    fineGrammar = Grammar.generativeGrammarFromTrees(fineTrees);
    fineIndexer = fineGrammar.getLabelIndexer();
    fineUnaryClosure = new UnaryClosure(fineIndexer, fineGrammar.getUnaryRules());
    numFineLabels = fineIndexer.size();

    coarseLexicon = new SimpleLexicon(coarseTrees);
    coarseGrammar = Grammar.generativeGrammarFromTrees(coarseTrees);
    coarseIndexer = coarseGrammar.getLabelIndexer();
    coarseUnaryClosure = new UnaryClosure(coarseIndexer, coarseGrammar.getUnaryRules());
    numCoarseLabels = coarseIndexer.size();

    assert coarseIndexer.get(0).equals("ROOT");
    assert fineIndexer.get(0).equals("ROOT");

    generateFineToCoarseMap();

    int fineTableSize = getFineIndex(numFineLabels - 1, MAX_LENGTH - 1, 0) + 1;
    fineBinaryScores = new double[fineTableSize];
    fineUnaryScores = new double[fineTableSize];
    binaryRuleNum = new int[fineTableSize];
    binaryK = new int[fineTableSize];
    unaryRuleBackpointers = new UnaryRule[fineTableSize];

    int coarseTableSize = getCoarseIndex(numCoarseLabels-1, MAX_LENGTH-1, 0) + 1;
    coarseBinaryOutside = new double[coarseTableSize];
    coarseUnaryOutside = new double[coarseTableSize];
    coarseBinaryScores = new double[coarseTableSize];
    coarseUnaryScores = new double[coarseTableSize];

  }

  int[] fineToCoarseMap;
  private void generateFineToCoarseMap() {
    fineToCoarseMap = new int[numFineLabels];
    for (int x = 0; x < numFineLabels; x++) {
      String fineLabel = fineIndexer.get(x);
      int index = fineLabel.indexOf('>') + 1;
      if (index == 0) {
        index = fineLabel.indexOf('^');
      } else {
        assert fineLabel.indexOf('^') == -1;
      }
      String coarseLabel = index == -1 ? fineLabel : fineLabel.substring(0, index);
      int coarseIndex = coarseIndexer.indexOf(coarseLabel);
      assert coarseIndex != -1 : fineLabel;
      fineToCoarseMap[x] = coarseIndex;
    }
  }

  private void collapseTrees(ArrayList<Tree<String>> trees) {
    Counter<String> counter = new Counter<String>();
    for (Tree<String> tree : trees) {
      tallyTree(counter, tree);
    }
    for (Tree<String> tree : trees) {
      collapseTree(counter, tree);
    }
  }

  private void tallyTree(Counter<String> counter, Tree<String> tree) {
    if (tree.isLeaf()) return;
    if (tree.isPreTerminal()) return;
    assert tree.getChildren().size() <= 2;
    counter.incrementCount(tree.getLabel(), 1.0);
    for (Tree<String> child : tree.getChildren()) {
      tallyTree(counter, child);
    }
  }

  private void collapseTree(Counter<String> counter, Tree<String> tree) {
    if (tree.isLeaf()) return;
    if (tree.isPreTerminal()) return;

    String label = tree.getLabel();
    if (counter.getCount(label) < MIN_OCCURRENCES) {
      int index = label.indexOf('_');
      int next = label.indexOf('_', index + 1);
      if (index != -1) {
        String newLabel = label.substring(0, index);
        if (next != -1) {
          newLabel += label.substring(next);
        }
        tree.setLabel(newLabel);
      }
    }

    for (Tree<String> child : tree.getChildren()) {
      collapseTree(counter, child);
    }
  }

  private int getCoarseIndex(int x, int i, int j) {
    int sum = i + j;
    int cell = sum * sum + sum + i;
    return cell * numCoarseLabels + x;
  }

  private int getFineIndex(int x, int i, int j) {
    int sum = i + j;
    int cell = sum * sum + sum + i;
    return cell * numFineLabels + x;
  }

  private int getCoarseBottomIndex(int x, int i, int j) {
    int sum = i + j;
    int minus = (sum * sum + sum) / 2 + j;
    return (area - minus) * numCoarseLabels + x;
  }

  private int getFineBottomIndex(int x, int i, int j) {
    int sum = i + j;
    int minus = (sum * sum + sum) / 2 + j;
    return (area - minus) * numFineLabels + x;
  }

  double [] coarseBinaryScores;
  double [] coarseUnaryScores;
  double [] coarseBinaryOutside;
  double [] coarseUnaryOutside;

  double [] fineBinaryScores;
  double [] fineUnaryScores;
  int [] binaryRuleNum;
  int [] binaryK;
  UnaryRule [] unaryRuleBackpointers;

  int area;
  public Tree<String> getBestParse(List<String> sentence) {
//    System.out.println("sentence = " + sentence);
    currentSentence = sentence;
    length = sentence.size();
    area = (length * length + length) / 2 - 1;

    for (int x = 0; x < numCoarseLabels; x++) {
      String transformedLabel = coarseIndexer.get(x);

      for (int j = 0; j < length; j++) {
        double s = coarseLexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
        coarseBinaryScores[getCoarseBottomIndex(x, j, length - j - 1)] = s;
      }
    }

    for (int sum = length - 1; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double score, ruleScore, max;

        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          if (sum != length - 1) {
            for (BinaryRule rule : coarseGrammar.getBinaryRulesByParent(x)) {
              ruleScore = rule.getScore();
              assert length - j > i + 1;
              for (int k = i + 1; k < length - j; k++) {
                score = ruleScore;
                assert ruleScore <= 0;
                score += coarseUnaryScores[getCoarseBottomIndex(rule.getLeftChild(), i, length - k)];
                if (score < max) continue;
                score += coarseUnaryScores[getCoarseBottomIndex(rule.getRightChild(), k, j)];
                if (score > max) {
                  max = score;
                }
              }
            }
            coarseBinaryScores[getCoarseBottomIndex(x, i, j)] = max;
          }
        }

        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            score = rule.getScore();
            score += coarseBinaryScores[getCoarseBottomIndex(child, i, j)];
            if (score > max) {
              max = score;
            }
          }
          if (!selfLooped) {
            score = coarseBinaryScores[getCoarseBottomIndex(x, i, j)];
            if (score > max) {
              max = score;
            }
          }
          coarseUnaryScores[getCoarseBottomIndex(x, i, j)] = max;
        }
      }
    }


    for (int x = 0; x < numCoarseLabels; x++) {
      coarseUnaryOutside[x] = Double.NEGATIVE_INFINITY;
      coarseBinaryOutside[x] = Double.NEGATIVE_INFINITY;
    }
    coarseUnaryOutside[0] = 0;
    for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByParent(0)) {
      coarseBinaryOutside[rule.getChild()] = rule.getScore();
    }

    for (int sum = 1; sum < length; sum++) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double score, ruleScore, max;

        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          for (BinaryRule rule: coarseGrammar.getBinaryRulesByRightChild(x)) {
            ruleScore = rule.getScore();
            int parent = rule.getParent();
            int left = rule.getLeftChild();
            for (int k = 0; k < i; k++) {
              score = ruleScore;
              score += coarseBinaryOutside[getCoarseIndex(parent, k, j)];
              if (score < max) continue;
              score += coarseUnaryScores[getCoarseBottomIndex(left, k, length - i)];
              max = Math.max(max, score);
            }
          }
          for (BinaryRule rule: coarseGrammar.getBinaryRulesByLeftChild(x)) {
            ruleScore = rule.getScore();
            int parent = rule.getParent();
            int right = rule.getRightChild();
            for (int k = j - 1; k >= 0; k--) {
              score = ruleScore;
              score += coarseBinaryOutside[getCoarseIndex(parent, i, k)];
              if (score < max) continue;
              score += coarseUnaryScores[getCoarseBottomIndex(right, length - j, k)];
              max = Math.max(max, score);
            }
          }
          coarseUnaryOutside[getCoarseIndex(x, i, j)] = max;
        }


        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByChild(x)) {
            int parent = rule.getParent();
            if (parent == x) selfLooped = true;
            score = rule.getScore();
            score += coarseUnaryOutside[getCoarseIndex(parent, i, j)];
            max = Math.max(max, score);
          }
          if (!selfLooped) {
            score = coarseUnaryOutside[getCoarseIndex(x, i, j)];
            max = Math.max(max, score);
          }
          coarseBinaryOutside[getCoarseIndex(x, i, j)] = max;
        }
      }
    }

    final double denominator = -coarseUnaryScores[area*numCoarseLabels];
    assert denominator != Double.POSITIVE_INFINITY;

    for (int x = 0; x < numFineLabels; x++) {
      String transformedLabel = fineIndexer.get(x);

      for (int j = 0; j < length; j++) {
        double s = fineLexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
        fineBinaryScores[getFineBottomIndex(x, j, length - j - 1)] = s;
      }
    }

    for (int sum = length - 1; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double score, ruleScore, max, coarseScore;

        for (int x = 0; x < numFineLabels; x++) {

          int coarseX = fineToCoarseMap[x];
          coarseScore = denominator;
          coarseScore += coarseBinaryOutside[getCoarseIndex(coarseX, i, j)];
          if (coarseScore < CELL_THRESH) {
            fineBinaryScores[getFineBottomIndex(x, i, j)] = Double.NEGATIVE_INFINITY;
            continue;
          }
          coarseScore += coarseBinaryScores[getCoarseBottomIndex(coarseX, i, j)];
          if (coarseScore < CELL_THRESH) {
            fineBinaryScores[getFineBottomIndex(x, i, j)] = Double.NEGATIVE_INFINITY;
            continue;
          }

          max = Double.NEGATIVE_INFINITY;
          if (sum != length - 1) {
            int ruleNum = 0;
            for (BinaryRule rule : fineGrammar.getBinaryRulesByParent(x)) {
              ruleScore = rule.getScore();
              assert length - j > i + 1;
              for (int k = i + 1; k < length - j; k++) {
                score = ruleScore;
                assert ruleScore <= 0;
                score += fineUnaryScores[getFineBottomIndex(rule.getLeftChild(), i, length - k)];
                if (score < max) continue;
                score += fineUnaryScores[getFineBottomIndex(rule.getRightChild(), k, j)];
                if (score > max) {
                  max = score;
                  binaryRuleNum[getFineBottomIndex(x, i, j)] = ruleNum;
                  binaryK[getFineBottomIndex(x, i, j)] = k;
                }
              }
              ruleNum++;
            }
            fineBinaryScores[getFineBottomIndex(x, i, j)] = max;
          }
        }

        for (int x = 0; x < numFineLabels; x++) {
          int coarseX = fineToCoarseMap[x];
          coarseScore = denominator;
          coarseScore += coarseBinaryOutside[getCoarseIndex(coarseX, i, j)];
          if (coarseScore <= CELL_THRESH) {
            fineUnaryScores[getFineBottomIndex(x, i, j)] = Double.NEGATIVE_INFINITY;
            continue;
          }
          coarseScore += coarseUnaryScores[getCoarseBottomIndex(coarseX, i, j)];
          if (coarseScore <= CELL_THRESH) {
            fineUnaryScores[getFineBottomIndex(x, i, j)] = Double.NEGATIVE_INFINITY;
            continue;
          }

          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : fineUnaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            score = rule.getScore();
            score += fineBinaryScores[getFineBottomIndex(child, i, j)];
            if (score > max) {
              max = score;
              unaryRuleBackpointers[getFineBottomIndex(x, i, j)] = rule;
            }
          }
          if (!selfLooped) {
            score = fineBinaryScores[getFineBottomIndex(x, i, j)];
            if (score > max) {
              max = score;
              unaryRuleBackpointers[getFineBottomIndex(x, i, j)] = null;
            }
          }
          fineUnaryScores[getFineBottomIndex(x, i, j)] = max;
        }
      }
    }

    Tree<String> ret;
    if (fineUnaryScores[area*numFineLabels] == Double.NEGATIVE_INFINITY) {
      if (CELL_THRESH == Double.NEGATIVE_INFINITY) {
        ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
      } else {
        double oldThresh = CELL_THRESH;
        CELL_THRESH = Double.NEGATIVE_INFINITY;
        ret = getBestParse(sentence);
        CELL_THRESH = oldThresh;
      }

    } else {
      ret = unaryTree(0, 0, 0);
    }
    return TreeAnnotations.unAnnotateTree(ret);
  }

  Tree<String> unaryTree(int x, int i, int j) {
    UnaryRule rule = unaryRuleBackpointers[getFineBottomIndex(x, i, j)];
    int child = rule == null ? x : rule.getChild();
    Tree<String> tree;

    if (i + j == length - 1) {
      List<Tree<String>> word = Collections.singletonList(new Tree<String>(currentSentence.get(i)));
      tree = new Tree<String>(fineIndexer.get(child), word);
    } else {
      tree = binaryTree(child, i, j);
    }

    if (child == x) return tree;

    List<Integer> path = fineUnaryClosure.getPath(rule);
    assert path.get(path.size() - 1) == child;
    for (int k = path.size() - 2; k >= 0; k--) {
      int tag = path.get(k);
      tree = new Tree<String>(fineIndexer.get(tag), Collections.singletonList(tree));
    }
    assert path.get(0) == x;
    return tree;
  }

  Tree<String> binaryTree(int x, int i, int j) {
    int ruleNum = binaryRuleNum[getFineBottomIndex(x, i, j)];
    int k = binaryK[getFineBottomIndex(x, i, j)];
    BinaryRule rule = fineGrammar.getBinaryRulesByParent(x).get(ruleNum);
    ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(unaryTree(rule.getLeftChild(), i, length - k));
    children.add(unaryTree(rule.getRightChild(), k, j));
    return new Tree<String>(fineIndexer.get(x), children);
  }

  void printArray(double[][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
  void printArray(double[][][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
}





