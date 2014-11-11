package edu.berkeley.nlp.assignments.rerank.student;

import edu.berkeley.nlp.assignments.rerank.KbestList;
import edu.berkeley.nlp.assignments.rerank.ParsingReranker;
import edu.berkeley.nlp.assignments.rerank.ParsingRerankerFactory;
import edu.berkeley.nlp.assignments.rerank.SurfaceHeadFinder;
import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.AnchoredTree;
import edu.berkeley.nlp.ling.Constituent;
import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.math.LBFGSMinimizer;

import java.text.NumberFormat;
import java.util.*;


public class AwesomeParsingRerankerFactory implements ParsingRerankerFactory {

  public ParsingReranker trainParserReranker(Iterable<Pair<KbestList,Tree<String>>> kbestListsAndGoldTrees) {
     return new MaximumEntropyReranker(kbestListsAndGoldTrees);
  }
}

abstract class Reranker implements ParsingReranker {

  boolean addFeaturesToIndexer = true;
  boolean isTreeGold = true;
  static Indexer<String> featureIndexer = new Indexer<String>();
  static Dictionary dictionary;

  public abstract Tree<String> getBestParse(List<String> sentence, KbestList kbestList);

  public int[] extractFeatures(KbestList kbestList, int idx) {
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

    boolean goldOnly = isTreeGold && addFeaturesToIndexer;
    ArrayList<Integer> feats = new ArrayList<Integer>();
    addFeature("Posn=" + idx, feats, addFeaturesToIndexer);

    for (AnchoredTree<String> subtree : anchoredTree.toSubTreeList()) {
      String label = subtree.getLabel();
      int startIndex = subtree.getStartIdx();
      int endIndex = subtree.getEndIdx();
      if (!subtree.isPreTerminal() && !subtree.isLeaf()) {
        String rule = "Rule=" + label + " ->";
        int numChildren = 0;
        for (AnchoredTree<String> child : subtree.getChildren()) {
          rule += " " + child.getLabel();
        }
        addFeature(rule, feats, addFeaturesToIndexer);
//        addFeature("RuleNumChildren=" + label + numChildren, feats, addFeaturesToIndexer);

        if (!label.equals("S") && !label.equals("ROOT")) {
          String ruleLen = "RuleLen=" + label + " " + subtree.getSpanLength();
          addFeature(ruleLen, feats, addFeaturesToIndexer);

          String ruleWordBegin = "RuleWordBegin=" + label + " "
                  + dictionary.get(words.get(startIndex));
          addFeature(ruleWordBegin, feats, addFeaturesToIndexer);

          String ruleWordEnd = "RuleWordEnd=" + label + " "
                  + dictionary.get(words.get(endIndex - 1));
          addFeature(ruleWordEnd, feats, addFeaturesToIndexer);

          String ruleTagBegin = "RuleTagBegin=" + label + " " + poss.get(startIndex);
          addFeature(ruleTagBegin, feats, addFeaturesToIndexer);

          String ruleTagEnd = "RuleTagEnd=" + label + " " + poss.get(endIndex - 1);
          addFeature(ruleTagEnd, feats, addFeaturesToIndexer);

          String wordBefore = startIndex == 0 ? "" : dictionary.get(words.get(startIndex - 1));
          String ruleWordBefore = "RuleWordBefore=" + label + " " + wordBefore;
          addFeature(ruleWordBefore, feats, addFeaturesToIndexer);

          String wordAfter = endIndex == words.size() ? "" : dictionary.get(words.get(endIndex));
          String ruleWordAfter = "RuleWordAfter=" + label + " " + wordAfter;
          addFeature(ruleWordAfter, feats, addFeaturesToIndexer);

          String tagBefore = startIndex == 0 ? "" : dictionary.get(poss.get(startIndex - 1));
          String ruleTagBefore = "RuleTagBefore=" + label + " " + tagBefore;
          addFeature(ruleTagBefore, feats, addFeaturesToIndexer);

          String tagAfter = endIndex == words.size() ? "" : dictionary.get(poss.get(endIndex));
          String ruleTagAfter = "RuleTagAfter=" + label + " " + tagAfter;
          addFeature(ruleTagAfter, feats, addFeaturesToIndexer);
        }
      }
    }

    long score = Math.round(-kbestList.getScores()[idx]);
    addFeature("Score=" + Long.toString(score), feats, addFeaturesToIndexer);

    int[] featsArr = new int[feats.size()];
    for (int i = 0; i < feats.size(); i++) {
      featsArr[i] = feats.get(i);
    }
    return featsArr;
  }

  private void addFeature(String feat, List<Integer> feats, boolean addNew) {
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

  int goldTreePosition(Tree<String> goldTree, List<Tree<String>> list) {
    double bestF1 = Double.NEGATIVE_INFINITY;
    int bestIndex = -1;

    int index = 0;
    for (Tree<String> tree : list) {
      EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> evaluator =
              new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(
                      Collections.singleton("ROOT"), new HashSet<String>()
              );
      double f1 = evaluator.evaluateF1(goldTree, tree);
      if (f1 > bestF1) {
        bestF1 = f1;
        bestIndex = index;
      }
      index++;
    }

    return bestIndex;
  }

}

class TreeWrapper {
  int position = -1; // TODO: delete?
  int[] features;
}

class BestList {
  ArrayList<TreeWrapper> trees = new ArrayList<TreeWrapper>();
  TreeWrapper goldTree;
}

class Dictionary {
  HashMap<String, Integer> counter;
  final static int DISCARD_THRESHOLD = 100;

  Dictionary(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {
    counter = new HashMap<String, Integer>();

    for (Pair<KbestList, Tree<String>> pair : kbestListsAndGoldTrees) {
      List<Tree<String>> kbestList = pair.getFirst().getKbestTrees();
      Tree<String> goldTree = pair.getSecond();

      addTree(goldTree);
      for (Tree<String> tree : kbestList) {
        addTree(tree);
      }
    }

    for (String key : new ArrayList<String>(counter.keySet())) {
      if (counter.get(key) < DISCARD_THRESHOLD) counter.remove(key);
    }
  }

  private void addTree(Tree<String> tree) {
    for (String node : tree.getYield()) {
      node = node.toLowerCase();
      while (!node.isEmpty()) {
        Integer count = counter.get(node);
        if (count == null) count = 0;
        counter.put(node, count + 1);
        node = node.substring(1);
      }
    }
  }

  public String get(String key) {
    key = key.toLowerCase();
    while (!counter.containsKey(key)) {
      if (key.length() == 1) return "";
      key = key.substring(1);
    }
    return key;
  }
}

class MaximumEntropyReranker extends Reranker {

  final static double REGULARIZATION_CONSTANT = 0.01;
  final static double OPTIMIZATION_TOLERANCE = 0.001;
  ArrayList<BestList> trainingData;
  double[] weights;

  MaximumEntropyReranker(Iterable<Pair<KbestList, Tree<String>>> kbestListsAndGoldTrees) {

    dictionary = new Dictionary(kbestListsAndGoldTrees);

    System.out.println("Calculating features");
    trainingData = new ArrayList<BestList>();
    for (Pair<KbestList, Tree<String>> pair : kbestListsAndGoldTrees) {
      KbestList kbestList = pair.getFirst();
      Tree<String> goldTree = pair.getSecond();

      if (!isTreeInList(goldTree, kbestList.getKbestTrees())) {
        goldTree = kbestList.getKbestTrees().get(goldTreePosition(goldTree, kbestList.getKbestTrees()));
      }

      BestList bestList = new BestList();
      for (int pos = 0; pos < kbestList.getKbestTrees().size(); pos++) {
        TreeWrapper treeWrapper = new TreeWrapper();

        treeWrapper.features = extractFeatures(kbestList, pos);
        treeWrapper.position = pos;
        bestList.trees.add(treeWrapper);

        if (kbestList.getKbestTrees().get(pos).hashCode() == goldTree.hashCode()) {
          TreeWrapper goldTreeWrapper = new TreeWrapper();
          goldTreeWrapper.features = treeWrapper.features;
          goldTreeWrapper.position = pos;

          bestList.goldTree = goldTreeWrapper;
        }
      }

      trainingData.add(bestList);
      if (trainingData.size() % 1000 == 0) {
        System.out.println(Integer.toString(trainingData.size()) + " lists processed");
      }
    }


    LBFGSMinimizer minimizer = new LBFGSMinimizer();
    LogLikelihood function = new LogLikelihood(trainingData);
    weights = new double[function.dimension()];
    Arrays.fill(weights, 0);

    System.out.println("Minimizing with dimension " + NumberFormat.getInstance().format(function.dimension()));
    weights = minimizer.minimize(function, weights, OPTIMIZATION_TOLERANCE);
    System.out.println("Parsing test set");

    addFeaturesToIndexer = false;
  }

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList) {
    Tree<String> bestTree = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {
      int[] feats = extractFeatures(kbestList, index);
      double score = 0;
      for (int feat : feats) {
        score += weights[feat];
      }
      if (score > bestScore) {
        bestScore = score;
        bestTree = kbestList.getKbestTrees().get(index);
      }
    }
    return bestTree;
  }

  class LogLikelihood implements DifferentiableFunction {

    ArrayList<BestList> trainingData;

    public int dimension() {
      return MaximumEntropyReranker.featureIndexer.size();
    }

    public double valueAt(double[] x) {
      double value = 0d;
      for (double x_i : x) {
        value += x_i * x_i;
      }
      value *= REGULARIZATION_CONSTANT;

      for (BestList bestList : trainingData) {
        ArrayList<TreeWrapper> trees = bestList.trees;
        TreeWrapper goldTree = bestList.goldTree;

        for (int feat : goldTree.features) {
          value -= x[feat];
        }

        double sum = 0;
        for (TreeWrapper tree : trees) {
          double dotProduct = 0;
          for (int feat : tree.features) {
            dotProduct += x[feat];
          }
          sum += Math.exp(dotProduct);
        }
        value += Math.log(sum);
      }

      return value;
    }

    public double[] derivativeAt(double[] x) {
      int dim = x.length;
      double[] derivative = new double[dim];
      Arrays.fill(derivative, 0);

      for (int i = 0; i < dim; i++) {
        derivative[i] = 2 * REGULARIZATION_CONSTANT * x[i];
      }

      for (BestList bestList : trainingData) {

        for (int feat : bestList.goldTree.features) {
          derivative[feat] -= 1;
        }

        double[] num = new double[trainingData.size()];
        double denom = 0;
        for (TreeWrapper tree : bestList.trees) {
          double exp = 0;
          for (int feat : tree.features) {
            exp += x[feat];
          }
          exp = Math.exp(exp);
          denom += exp;
          assert denom < Double.MAX_VALUE;

          num[tree.position] = exp;
        }

        for (TreeWrapper tree : bestList.trees) {
          for (int feat : tree.features) {
            assert denom >= num[tree.position];
            derivative[feat] += num[tree.position] / denom; // TODO: Reorder this
          }
        }
      }

      return derivative;
    }

    LogLikelihood(ArrayList<BestList> trainingData) {
      this.trainingData = trainingData;
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

        if (!isTreeInList(goldTree, kbestList.getKbestTrees())) continue;

        int[] predictedFeats = null;
        int[] goldFeats = null;
        int predictedScore = Integer.MIN_VALUE;
        for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {
          int[] features = extractFeatures(kbestList, index);
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

        if (predictedFeats != null ) {
          assert goldFeats != null;
          for (int featIdx : predictedFeats) {
            weights.set(featIdx, weights.get(featIdx) - 1);
          }
        }
        if (goldFeats != null) {
          for (int featIdx : goldFeats) {
            weights.set(featIdx, weights.get(featIdx) + 1);
          }
        }
      }
    }

    addFeaturesToIndexer = false;
  }

  public Tree<String> getBestParse(List<String> sentence, KbestList kbestList) {
    int bestIndex = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int index = 0; index < kbestList.getKbestTrees().size(); index++) {
      int[] features = extractFeatures(kbestList, index);

      int score = 0;
      for (int featIdx :features) {
        score += weights.get(featIdx);
      }
      if (score > bestScore) bestIndex = index;
    }

    return kbestList.getKbestTrees().get(bestIndex);
  }
}

