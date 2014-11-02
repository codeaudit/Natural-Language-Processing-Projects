package edu.berkeley.nlp.assignments.rerank.student;

import edu.berkeley.nlp.assignments.rerank.KbestList;
import edu.berkeley.nlp.assignments.rerank.ParsingReranker;
import edu.berkeley.nlp.assignments.rerank.ParsingRerankerFactory;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.math.LBFGSMinimizer;

import java.util.ArrayList;


public class AwesomeParsingRerankerFactory implements ParsingRerankerFactory {

  public ParsingReranker trainParserReranker(Iterable<Pair<KbestList,Tree<String>>> kbestListsAndGoldTrees) {
     return new PerceptronReranker(kbestListsAndGoldTrees);
  }
}

abstract class Reranker implements ParsingReranker {

  final int NUM_ITERATIONS = 5;
  final int KBEST_K = 10;
  final int NUM_FEATURES = 100;
  boolean addFeaturesToIndexer = true;
  Indexer<String> featureIndexer = new Indexer<String>();

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList);

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
    List<Integer> feats = new ArrayList<Integer>();
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

  MaximumEntropyReranker(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {

  }
}

class PerceptronReranker extends Reranker {

  int[] weights;
  final static SimpleFeatureExtractor featureExtractor = new SimpleFeatureExtractor();

  PerceptronReranker(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {
    weights = new int[NUM_FEATURES];
    Arrays.fill(weights, 0);

    for (int iter_number = 0; iter_number < NUM_ITERATIONS; iter_number++) {
      for (Pair<KbestList, Tree<String>> pair : kbestListsAndGoldTrees) {

        KbestList kbestList = pair.getFirst();
        Tree<String> goldTree = pair.getSecond();

        if (!isTreeInList(goldTree, kbestList)) continue; // TODO

        assert kbestList.getKbestTrees().size() == KBEST_K;

        ArrayList<Integer> predictedFeats;
        ArrayList<Integer> goldFeats;
        int predictedScore = Integer.MIN_VALUE;
        for (int index = 0; index < KBEST_K; k++) {
          ArrayList<Integer> features = extractFeatures(kbestList, index);
          if (kbestList.getKbestTrees().get(index).hashCode() == goldTree.hashCode()) {
            goldFeats = features;
          }

          int score = 0;
          for (Integer featIdx : features) {
            score += weights[featIdx];
          }
          if (score >= predictedScore) {
            predictedFeats = features;
          }
        }

        for (Integer featIdx : predictedFeats) {
          weights[featIdx] -= 1;
        }
        for (Integer featIdx : goldFeats) {
          weights[featIdx] += 1;
        }
      }
    }

    addFeaturesToIndexer = false;
  }

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList) {
    assert kbestList.getKbestTrees().size() == KBEST_K;
    int bestIndex;
    int bestScore = Integer.MIN_VALUE;
    for (int index = 0; index < KBEST_K; k++) {
      ArrayList<Integer> features = extractFeatures(kbestList, index);

      int score = 0;
      for (Integer featIdx :features) {
        score += weights[featIdx];
      }
      if (score > bestScore) bestIndex = index;
    }

    return kbestList.get(bestIndex);
  }
}

