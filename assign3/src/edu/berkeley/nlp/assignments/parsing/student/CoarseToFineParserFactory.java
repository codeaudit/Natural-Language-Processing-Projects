package edu.berkeley.nlp.assignments.parsing.student;

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
import edu.berkeley.nlp.ling.Trees;
import edu.berkeley.nlp.util.Indexer;


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
  boolean TEST = false;

  CoarseToFineParser(List<Tree<String>> trainTrees) {
    ArrayList<Tree<String>> fineTrees = new ArrayList<Tree<String>>();
    ArrayList<Tree<String>> coarseTrees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trainTrees) {
      if (TEST && !Trees.PennTreeRenderer.render(tree).contains("Odds")) continue;
      Tree<String> newTree = FineAnnotator.annotateTree(tree);
      fineTrees.add(newTree);

      newTree = CoarseAnnotator.annotateTree(tree);
      coarseTrees.add(newTree);
      if (TEST) System.out.println(Trees.PennTreeRenderer.render(newTree));
    }
    assert fineTrees.size() > 0 : "No training trees";

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

    if (TEST) {
      test("Odds and Ends");
      System.exit(0);
    }
  }

  int[] fineToCoarseMap;
  void generateFineToCoarseMap() {
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

  double [][][] coarseBinaryScores;
  double [][][] coarseUnaryScores;
  double [][][] coarseBinaryOutside;
  double [][][] coarseUnaryOutside;

  double [][][] fineBinaryScores;
  double [][][] fineUnaryScores;
  int [][][] binaryRuleNum;
  int [][][] binaryK;
  UnaryRule [][][] unaryRuleBackpointers;

  void initTables() {
    coarseBinaryScores = new double[numCoarseLabels][][];
    coarseUnaryScores = new double[numCoarseLabels][][];
    coarseBinaryOutside = new double[numCoarseLabels][][];
    coarseUnaryOutside = new double[numCoarseLabels][][];
    initTable(coarseBinaryScores, numCoarseLabels);
    initTable(coarseUnaryScores, numCoarseLabels);
    initTable(coarseBinaryOutside, numCoarseLabels);
    initTable(coarseUnaryOutside, numCoarseLabels);

    fineBinaryScores = new double[numFineLabels][][];
    fineUnaryScores = new double[numFineLabels][][];
    binaryRuleNum = new int[numFineLabels][][];
    binaryK = new int[numFineLabels][][];
    unaryRuleBackpointers = new UnaryRule[numFineLabels][][];
    initTable(fineUnaryScores, numFineLabels);
    initTable(fineBinaryScores, numFineLabels);
    initTable(binaryRuleNum, numFineLabels);
    initTable(binaryK, numFineLabels);
    initTable(unaryRuleBackpointers, numFineLabels);
  }

  void initTable(double[][][] table, int size) {
    for (int x = 0; x < size; x++) {
      double [][] page = new double[length][];
      table[x] = page;
      for (int i = 0; i < length; i++) {
        page[i] = new double[length - i];
        Arrays.fill(page[i], Double.NaN);
      }
    }
  }

  void initTable(int[][][] table, int size) {
    for (int x = 0; x < size; x++) {
      int [][] page = new int[length][];
      table[x] = page;
      for (int i = 0; i < length; i++) {
        page[i] = new int[length - i];
        Arrays.fill(page[i], -1);
      }
    }
  }

  void initTable(UnaryRule[][][] table, int size) {
    for (int x = 0; x < size; x++) {
      UnaryRule [][] page = new UnaryRule[length][];
      table[x] = page;
      for (int i = 0; i < length; i++) {
        page[i] = new UnaryRule[length - i];
      }
    }
  }

  public Tree<String> getBestParse(List<String> sentence) {
//    System.out.println("sentence = " + sentence);
    currentSentence = sentence;
    length = sentence.size();
    initTables();

    for (int x = 0; x < numCoarseLabels; x++) {
      String transformedLabel = coarseIndexer.get(x);

      double [][] labelTable = coarseBinaryScores[x];
      for (int j = 0; j < length; j++) {
        double s = coarseLexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
        labelTable[j][length-j-1] = s;
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
                score += coarseUnaryScores[rule.getLeftChild()][i][length - k];
                score += coarseUnaryScores[rule.getRightChild()][k][j];
                if (score > max) {
                  max = score;
                }
              }
            }
            coarseBinaryScores[x][i][j] = max;
          }
        }

        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            score = rule.getScore();
            score += coarseBinaryScores[child][i][j];
            if (score > max) {
              max = score;
            }
          }
          if (!selfLooped) {
            score = coarseBinaryScores[x][i][j];
            if (score > max) {
              max = score;
            }
          }
          coarseUnaryScores[x][i][j] = max;
        }
      }
    }


    // remove this and make it all -infinity during init, NaN during debugging
    for (int x = 0; x < numCoarseLabels; x++) {
      coarseUnaryOutside[x][0][0] = Double.NEGATIVE_INFINITY;
      coarseBinaryOutside[x][0][0] = Double.NEGATIVE_INFINITY;
    }
    coarseUnaryOutside[0][0][0] = 0;
    for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByParent(0)) {
      coarseBinaryOutside[rule.getChild()][0][0] = rule.getScore();
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
              score += coarseUnaryScores[left][k][length - i];
              score += coarseBinaryOutside[parent][k][j];
              max = Math.max(max, score);
            }
          }
          for (BinaryRule rule: coarseGrammar.getBinaryRulesByLeftChild(x)) {
            ruleScore = rule.getScore();
            int parent = rule.getParent();
            int right = rule.getRightChild();
            for (int k = j - 1; k >= 0; k--) {
              score = ruleScore;
              score += coarseUnaryScores[right][length - j][k];
              score += coarseBinaryOutside[parent][i][k];
              max = Math.max(max, score);
            }
          }
          coarseUnaryOutside[x][i][j] = max;
        }


        for (int x = 0; x < numCoarseLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : coarseUnaryClosure.getClosedUnaryRulesByChild(x)) {
            int parent = rule.getParent();
            if (parent == x) selfLooped = true;
            score = rule.getScore();
            score += coarseUnaryOutside[parent][i][j];
            max = Math.max(max, score);
          }
          if (!selfLooped) {
            score = coarseUnaryOutside[x][i][j];
            max = Math.max(max, score);
          }
          coarseBinaryOutside[x][i][j] = max;
        }
      }
    }

//    for (int x = 0; x < numCoarseLabels; x++) {
//      System.out.println(x + ": " + coarseIndexer.get(x));
//      printArray(coarseUnaryOutside[x]);
//      printArray(coarseBinaryOutside[x]);
//    }


    for (int x = 0; x < numFineLabels; x++) {
      String transformedLabel = fineIndexer.get(x);

      double [][] labelTable = fineBinaryScores[x];
      for (int j = 0; j < length; j++) {
        double s = fineLexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
        labelTable[j][length-j-1] = s;
      }
    }

    for (int sum = length - 1; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double score, ruleScore, max;

        for (int x = 0; x < numFineLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          if (sum != length - 1) {
            int ruleNum = 0;
            for (BinaryRule rule : fineGrammar.getBinaryRulesByParent(x)) {
              ruleScore = rule.getScore();
              assert length - j > i + 1;
              for (int k = i + 1; k < length - j; k++) {
                score = ruleScore;
                assert ruleScore <= 0;
                score += fineUnaryScores[rule.getLeftChild()][i][length - k];
                score += fineUnaryScores[rule.getRightChild()][k][j];
                if (score > max) {
                  max = score;
                  binaryRuleNum[x][i][j] = ruleNum;
                  binaryK[x][i][j] = k;
                }
              }
              ruleNum++;
            }
            assert max == Double.NEGATIVE_INFINITY || binaryRuleNum[x][i][j] != -1;
            fineBinaryScores[x][i][j] = max;
          }
        }

        for (int x = 0; x < numFineLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : fineUnaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            score = rule.getScore();
            score += fineBinaryScores[child][i][j];
            if (score > max) {
              max = score;
              unaryRuleBackpointers[x][i][j] = rule;
            }
          }
          if (!selfLooped) {
            score = fineBinaryScores[x][i][j];
            if (score > max) {
              max = score;
              unaryRuleBackpointers[x][i][j] = null;
            }
          }
          fineUnaryScores[x][i][j] = max;
        }
      }
    }

//    for (int x = 0; x < numFineLabels; x++) {
//      System.out.println(x + ": " + fineIndexer.get(x));
//      printArray(fineUnaryScores[x]);
//      printArray(fineBinaryScores[x]);
//    }

//    System.out.println("score = " + fineUnaryScores[0][0][0]);

    Tree<String> ret;
    if (fineUnaryScores[0][0][0] == Double.NEGATIVE_INFINITY) {
      ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
    } else {
      ret = unaryTree(0, 0, 0);
    }
//    System.out.println(Trees.PennTreeRenderer.render(ret));
    return TreeAnnotations.unAnnotateTree(ret);
  }

  Tree<String> unaryTree(int x, int i, int j) {
    UnaryRule rule = unaryRuleBackpointers[x][i][j];
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
    int ruleNum = binaryRuleNum[x][i][j];
    assert ruleNum != -1 : fineBinaryScores[x][i][j];
    int k = binaryK[x][i][j];
    BinaryRule rule = fineGrammar.getBinaryRulesByParent(x).get(ruleNum);
    ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(unaryTree(rule.getLeftChild(), i, length - k));
    children.add(unaryTree(rule.getRightChild(), k, j));
    return new Tree<String>(fineIndexer.get(x), children);
  }

  void test(String raw) {
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





