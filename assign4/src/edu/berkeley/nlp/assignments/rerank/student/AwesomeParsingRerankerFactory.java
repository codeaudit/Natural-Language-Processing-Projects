package edu.berkeley.nlp.assignments.rerank.student;

import edu.berkeley.nlp.assignments.rerank.KbestList;
import edu.berkeley.nlp.assignments.rerank.ParsingReranker;
import edu.berkeley.nlp.assignments.rerank.ParsingRerankerFactory;
import edu.berkeley.nlp.assignments.rerank.SurfaceHeadFinder;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.AnchoredTree;
import edu.berkeley.nlp.ling.Constituent;
import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.math.LBFGSMinimizer;

import java.util.*;


public class AwesomeParsingRerankerFactory implements ParsingRerankerFactory {

  public ParsingReranker trainParserReranker(Iterable<Pair<KbestList,Tree<String>>> kbestListsAndGoldTrees) {
     return new PerceptronReranker(kbestListsAndGoldTrees);
  }
}

abstract class Reranker implements ParsingReranker {

  boolean addFeaturesToIndexer = true;
  static Indexer<String> featureIndexer = new Indexer<String>();

  public abstract Tree<String> getBestParse(List<String> sentence, KbestList kbestList);

  public ArrayList<Integer> extractFeatures(KbestList kbestList, int idx) {
    Tree<String> tree = kbestList.getKbestTrees().get(idx);
    // Converts the tree
    // (see below)
    AnchoredTree<String> anchoredTree = AnchoredTree.fromTree(tree);
    // If you just want to iterate over labeled spans, use the constituent list
    Collection<Constituent<String>> constituents = tree.toConstituentList();
    // You can fire features on parts of speech or words
    List<String> poss = tree.getPreTerminalYield();
    List<String> words = tree.getYield();
    // Allows you to find heads of spans of preterminals. Use this to fire dependency-based features
    // like those discussed in Charniak and Johnson
    SurfaceHeadFinder shf = new SurfaceHeadFinder();

    // FEATURE COMPUTATION
    ArrayList<Integer> feats = new ArrayList<Integer>();
    // Fires a feature based on the position in the k-best list. This should allow the model to learn that
    // high-up trees
    addFeature("Posn=" + idx, feats, featureIndexer, addFeaturesToIndexer);

    for (AnchoredTree<String> subtree : anchoredTree.toSubTreeList()) {
      if (!subtree.isPreTerminal() && !subtree.isLeaf()) {
        // Fires a feature based on the identity of a nonterminal rule production. This allows the model to learn features
        // roughly equivalent to those in an unbinarized coarse grammar.
        String rule = "Rule=" + subtree.getLabel() + " ->";
        for (AnchoredTree<String> child : subtree.getChildren()) {
          rule += " " + child.getLabel();
        }
        addFeature(rule, feats, featureIndexer, addFeaturesToIndexer);
      }
    }
    // Add your own features here!

    return feats;

//    int[] featsArr = new int[feats.size()];
//    for (int i = 0; i < feats.size(); i++) {
//      featsArr[i] = feats.get(i).intValue();
//    }
//    return featsArr;
  }

  private void addFeature(String feat, List<Integer> feats, Indexer<String> featureIndexer, boolean addNew) {
    if (addNew || featureIndexer.contains(feat)) {
      feats.add(featureIndexer.addAndGetIndex(feat));
    }
  }

  boolean isTreeInList(Tree<String> goldTree, List<Tree<String>> list) {
    for (Tree<String> tree : list) {
      if (tree.hashCode() == goldTree.hashCode()) return true;
    }
    return false;
  }
}

class MaximumEntropyReranker extends Reranker {

  final static double REGULARIZATION_CONSTANT = 1.0d;

  MaximumEntropyReranker(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {

  }

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList) {
    return null;
  }

  class LogLikelihood implements DifferentiableFunction {

    List<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees;

    public int dimension() {
      return MaximumEntropyReranker.featureIndexer.size();
    }

    public double valueAt(double[] x) {
      double value = 0d;
      for (double x_i : x) {
        value += x_i * x_i;
      }
      value *= REGULARIZATION_CONSTANT;

      for (Pair<KbestList, Tree<String>> pair : kbestListsAndGoldTrees) {
        KbestList kbestList = pair.getFirst();
        Tree<String> goldTree = pair.getSecond();
        if (!isTreeInList(goldTree, kbestList.getKbestTrees())) continue;

        for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {

        }
      }

      return 0d;
    }

    public double[] derivativeAt(double[] x) {
      return null;
    }
  }
}

class PerceptronReranker extends Reranker {

  final static int NUM_ITERATIONS = 5;
  ArrayList<Integer> weights;

  PerceptronReranker(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {
    weights = new ArrayList<Integer>();

    for (int iter_number = 0; iter_number < NUM_ITERATIONS; iter_number++) {
      System.out.println("iter_number = " + iter_number);
      for (Pair<KbestList, Tree<String>> pair : kbestListsAndGoldTrees) {

        KbestList kbestList = pair.getFirst();
        Tree<String> goldTree = pair.getSecond();

        if (!isTreeInList(goldTree, kbestList.getKbestTrees())) continue; // TODO: F1 dist as gold

        List<Integer> predictedFeats = Collections.EMPTY_LIST;
        List<Integer> goldFeats = Collections.EMPTY_LIST;
        int predictedScore = Integer.MIN_VALUE;
        for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {
          ArrayList<Integer> features = extractFeatures(kbestList, index);
          if (kbestList.getKbestTrees().get(index).hashCode() == goldTree.hashCode()) {
            goldFeats = features;
          }

          int score = 0;
          for (Integer featIdx : features) {
            while (featIdx >= weights.size()) weights.add(0);
            score += weights.get(featIdx);
          }
          if (score >= predictedScore) {
            predictedFeats = features;
          }
        }

        for (Integer featIdx : predictedFeats) {
          weights.set(featIdx, weights.get(featIdx) - 1);
        }
        for (Integer featIdx : goldFeats) {
          weights.set(featIdx, weights.get(featIdx) + 1);
        }
      }
    }

    addFeaturesToIndexer = false;
  }

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList) {
    int bestIndex = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {
      ArrayList<Integer> features = extractFeatures(kbestList, index);

      int score = 0;
      for (Integer featIdx :features) {
        score += weights.get(featIdx);
      }
      if (score > bestScore) bestIndex = index;
    }

    return kbestList.getKbestTrees().get(bestIndex);
  }
}

