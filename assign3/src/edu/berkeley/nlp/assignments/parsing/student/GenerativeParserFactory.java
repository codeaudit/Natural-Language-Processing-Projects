package edu.berkeley.nlp.assignments.parsing.student;

import java.lang.ArrayIndexOutOfBoundsException;
import java.util.*;
import java.util.HashSet;

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
  int numLabels;

  GenerativeParser(List<Tree<String>> trainTrees) {
    ArrayList<Tree<String>> trees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trainTrees) {
      if (Trees.PennTreeRenderer.render(tree).contains("Vice")) {
        trees.add(TreeAnnotations.annotateTreeLosslessBinarization(tree));
      }
    }
    System.out.println(trees.size());
    lexicon = new SimpleLexicon(trees);
    grammar = Grammar.generativeGrammarFromTrees(trees);
    indexer = grammar.getLabelIndexer();
    numLabels = indexer.size();

    if (TEST) {
      test();
      System.exit(0);
    }
  }

  double [][][] score;

  void initTable(int size) {
    score = new double[numLabels][][];
    for (int x = 0; x < numLabels; x++) {
      double [][] page = new double[size][];
      score[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new double[size - i];
        Arrays.fill(page[i], Double.NaN);
      }
    }
  }

  public Tree<String> getBestParse(List<String> sentence) {
    int length = sentence.size();
    initTable(length);
    System.out.println("length = " + length);

    for (int x = 0; x < numLabels; x++) {
      String transformedLabel = indexer.get(x);

//      int index = -1;
//      for (String delim : new String[] { "=", "<", ">", "^", "_", "->" }) {
//        final int currIndex = transformedLabel.indexOf(delim);
//        index = index < 0 ? currIndex : (currIndex < 0 ? index : Math.min(currIndex, index));
//      }
//      int start = 0;
//      if (transformedLabel.indexOf("@") == 0) {
//        start = 1;
//      }
//      transformedLabel = new String(transformedLabel.substring(start, index < 0 ? transformedLabel.length() : index));

      double [][] labelTable = score[x];
      for (int j = 0; j < length; j++) {
        double s = lexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
//        if (s != Double.NEGATIVE_INFINITY) {
//          System.out.println(sentence.get(j) + " " + transformedLabel);
//        }
        labelTable[j][length-j-1] = s;
      }
    }

    for (int sum = length - 2; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        for (int x = 0; x < numLabels; x++) {
          double max = Double.NEGATIVE_INFINITY;
          for (BinaryRule rule : grammar.getBinaryRulesByParent(x)) {
            double ruleScore = rule.getScore();
            assert length - j > i + 1;
            for (int k = i + 1; k < length-j; k++) {
              double s = ruleScore;
              s += score[rule.getLeftChild()][i][length-k];
              s += score[rule.getRightChild()][k][j];
              if (s > max) {
                max = s;
              }
            }
          }
          score[x][i][j] = max;
        }
      }
    }

    for (int x = 0; x < numLabels; x++) {
      double s = score[x][0][0];
      if (true || s != Double.NEGATIVE_INFINITY) {
        System.out.println("score[" + indexer.get(x) + "][0][0] = " + s);
        printArray(score[x]);
      }
    }

    return new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
  }

  boolean TEST = true;
  void test() {
    String raw = "Vice President";
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





