package edu.berkeley.nlp.assignments.assignspeech.student;

import edu.berkeley.nlp.assignments.assign1.solutions.KneserNeyLmFactory;
import edu.berkeley.nlp.assignments.assignspeech.AcousticModel;
import edu.berkeley.nlp.assignments.assignspeech.PronunciationDictionary;
import edu.berkeley.nlp.assignments.assignspeech.SpeechRecognizer;
import edu.berkeley.nlp.assignments.assignspeech.SubphoneWithContext;
import edu.berkeley.nlp.io.SentenceCollection;
import edu.berkeley.nlp.langmodel.EnglishWordIndexer;
import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.util.StringIndexer;

import java.lang.String;
import java.util.*;
import java.util.ArrayList;

public class SpeechRecognizerFactory {

  /**
   * @param acousticModel The pre-trained acoustic model
   *
   * @param dict A mapping from words to phonemes
   *
   * @param lmDataPath A path to language model data to train your language model on.
   * The implementation of the language model you use is up to you.
   * Note that this is a relatively small language model corpus, so training the language model
   * on the fly shouldn't be too slow.
   *
   * @return An implementation of the SpeechRecognizer interface
   */
  public static SpeechRecognizer getRecognizer(AcousticModel acousticModel, PronunciationDictionary dict, String lmDataPath) {

    return new Recognizer(acousticModel, dict, lmDataPath);
  }


  public static void main(String[] args) {

  }

}

class LexiconNode {
  HashMap<String, LexiconNode> children = new HashMap<String, LexiconNode>();
  String phoneme = "";
  LexiconNode prevNode;
  ArrayList<Integer> words;

  int level; // TODO: Remove

  LexiconNode(String phoneme, LexiconNode prevNode) {
    this.phoneme = phoneme;
    this.prevNode = prevNode;
    this.words = new ArrayList<Integer>();
  };

  LexiconNode(PronunciationDictionary dict) {
    this.level = 0;
    StringIndexer indexer = EnglishWordIndexer.getIndexer();

    ArrayList<String> skipped = new ArrayList<String>(); // TODO: Skip stuff

    for (String word : dict.getContainedWords()) {
      List<List<String>> pronunciations = dict.getPronunciations(word);

      LexiconNode node, nextNode;
      for (List<String> pronunciation : pronunciations) {
        node = this;
        for (String phoneme_ : pronunciation) {
          nextNode = node.children.get(phoneme_);
          if (nextNode == null) {
            nextNode = new LexiconNode(phoneme_, node);
            nextNode.level = node.level + 1;
            node.children.put(phoneme_, nextNode);
          }
          node = nextNode;
        }
        node.words.add(indexer.addAndGetIndex(word));
      }
    }

//    System.out.println("skipped = " + skipped);
  }

  public String toString() {
    LinkedList<String> nodes = new LinkedList<String>();
    LexiconNode curr = this;
    while (curr.prevNode != null) {
      nodes.addFirst(curr.phoneme);
      curr = curr.prevNode;
    }
    nodes.addLast("<<");

    curr = this;
    while (curr.words.isEmpty()) {
      curr = curr.children.values().iterator().next();
      nodes.addLast(curr.phoneme);
    }
    StringIndexer indexer = EnglishWordIndexer.getIndexer();
    return this.phoneme + ": " + nodes.toString() + " => " + indexer.get(curr.words.get(0));
  }
}


class Recognizer implements SpeechRecognizer {

  final LexiconNode lexicon;
  final PronunciationDictionary dict;
  final AcousticModel acousticModel;
  final static int BEAM_SIZE = 1500;
  final static double WORD_BONUS = Math.log(5);
  final static double SUBPHONE_BONUS = Math.log(1.1);
  final static double LM_BOOST = 3d;
  final static StringIndexer indexer = EnglishWordIndexer.getIndexer();
  static int[] ngram = new int[3];

  NgramLanguageModel lm;

  public Recognizer(AcousticModel acousticModel, PronunciationDictionary dict, String lmDataPath) {
    lexicon = new LexiconNode(dict);
    this.dict = dict;
    this.acousticModel = acousticModel;
    Iterable<List<String>> sents = SentenceCollection.Reader.readSentenceCollection(lmDataPath);
    lm = KneserNeyLmFactory.newLanguageModel(sents, false);
    System.out.println("-------------------------------");
  }

  class State implements Comparable {
    int prevWord = -1;
    State prevState;
    LexiconNode lexiconNode;
    SubphoneWithContext subphone;
    double probability;

    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof State)) return false;
      State other = (State)obj;
      return other.subphone.equals(this.subphone)
              && other.prevWord == this.prevWord
              && other.lexiconNode == this.lexiconNode;
    }

    public int hashCode() {
      return this.subphone.hashCode() ^ (int)((long)this.prevWord * 0xff51afd7ed558ccdL) ^ this.lexiconNode.hashCode();
    }

    State(State prevState, int prevWord) {
      this.prevState = prevState;
      this.prevWord = prevWord;
    }

    public int compareTo(Object o) {
      double diff = this.probability - ((State)o).probability;
      if (diff == 0) return 0;
      if (diff < 0) return -1;
      return 1;
    }

    State(LexiconNode entry) {
      this.lexiconNode = entry;
      this.subphone = new SubphoneWithContext(entry.phoneme, 1, "", "");
      probability = 0;
    }

    double acousticsProbability(float[] point) {
      if (!acousticModel.contains(this.subphone)) {
        if (subphone.getSubphonePosn() != 2 && subphone.getBackContext() == "" && subphone.getForwardContext() == "") {
          return acousticModel.getLogProbability(new SubphoneWithContext(subphone.getPhoneme(), 2, "", ""), point);
        }
        // System.out.println("WARNING: acoustic " + this.subphone + " unseen " + this.lexiconNode);
        return Double.NEGATIVE_INFINITY;
      }
      return acousticModel.getLogProbability(this.subphone, point);
    }

    State selfLoop(float[] point) {
      State newState = new State(this, this.prevWord);
      newState.lexiconNode = this.lexiconNode;
      newState.subphone = this.subphone;
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }

    State newWord(float[] point, int word, LexiconNode nextNode) {
      assert this.subphone.getSubphonePosn() == 3;
      State newState = new State(this, word);
      newState.lexiconNode = nextNode;
      newState.subphone = new SubphoneWithContext(nextNode.phoneme, 1, "", "");
      newState.probability = this.probability
              + newState.acousticsProbability(point)
              + WORD_BONUS;
      ngram[0] = this.prevWord;
      ngram[1] = word;
      double lmProb;
      if (prevWord != -1) {
        lmProb = lm.getNgramLogProbability(ngram, 0, 2);
      } else {
        lmProb = lm.getNgramLogProbability(ngram, 1, 2);
      }
      assert lmProb > -50: "lmProb: " + lmProb + " [" + (ngram[0] == -1 ? "" : (indexer.get(ngram[0]) + ", ")) + indexer.get(ngram[1]) + "]";
      newState.probability += lmProb * LM_BOOST;
      return newState;
    }

    State trans1_2(float[] point) {
      assert this.subphone.getSubphonePosn() == 1;
      State newState = new State(this, this.prevWord);
      newState.lexiconNode = this.lexiconNode;
      newState.subphone = new SubphoneWithContext(
              newState.lexiconNode.phoneme,
              this.subphone.getSubphonePosn() + 1,
              "", "");
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }

    State trans2_3(float[] point, LexiconNode nextNode) {
      assert this.subphone.getSubphonePosn() == 2;
      State newState = new State(this, this.prevWord);
      newState.lexiconNode = nextNode == null ? this.lexiconNode : nextNode;
      newState.subphone = new SubphoneWithContext(this.lexiconNode.phoneme, 3, "", nextNode == null ? "" : nextNode.phoneme);
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }

    State trans3_1(float[] point) {
      assert this.subphone.getSubphonePosn() == 3;
      State newState = new State(this, this.prevWord);
      newState.lexiconNode = this.lexiconNode;
      newState.subphone = new SubphoneWithContext(this.lexiconNode.phoneme, 1, this.lexiconNode.prevNode.phoneme, "");
      newState.probability = this.probability + newState.acousticsProbability(point) + SUBPHONE_BONUS;
      return newState;
    }
  }

  class Beam implements Iterable<State> {
    PriorityQueue<State> queue;
//    HashSet<State> states;
    int size;

    Beam(int size) {
      queue = new PriorityQueue<State>(size);
      this.size = size;
    }

    void relax(State state) {

//      if (states.contains(state)) {
//        State oldState = states
//      }

      queue.add(state);
      if (queue.size() == size) queue.poll();
    }

    State poll() {
      return queue.poll();
    }

    State max() {
      return Collections.max(queue);
    }

    public Iterator<State> iterator() {
      return queue.iterator();
    }
  }

  /**
   * Decode a sequence of MFCCs and produce a sequence of lowercased words.
   *
   * @param acousticFeatures The sequence of MFCCs for this sentence with silences filtered out
   * @return The recognized sequence of words
   */
  public List<String> recognize(List<float[]> acousticFeatures) {

    Beam prevBeam, nextBeam = new Beam(BEAM_SIZE);

    for (Map.Entry<String, LexiconNode> entry : lexicon.children.entrySet()) {
      nextBeam.relax(new State(entry.getValue()));
    }

    int index = 0;
    for (float[] features : acousticFeatures) {
      index++;
      prevBeam = nextBeam;

      if (index % 10 == 9) {
        System.out.println(getPrediction(nextBeam));
      }

      int diff = acousticFeatures.size() - index;
      if (diff < 10) {
        nextBeam = new Beam(BEAM_SIZE * 2);
      } else {
        nextBeam = new Beam(BEAM_SIZE);
      }

      for (State state : prevBeam) {

        nextBeam.relax(state.selfLoop(features));

        switch (state.subphone.getSubphonePosn()) {
          case 3:
            if (state.subphone.getForwardContext() != "") {
              nextBeam.relax(state.trans3_1(features));
            } else {
              assert state.lexiconNode.words.size() != 0;
              for (int word : state.lexiconNode.words) {
                for (Map.Entry<String, LexiconNode> entry : lexicon.children.entrySet()) {
                  nextBeam.relax(state.newWord(features, word, entry.getValue()));
                }
              }
            }
            break;
          case 2:
            for (Map.Entry<String, LexiconNode> entry : state.lexiconNode.children.entrySet()) {
              nextBeam.relax(state.trans2_3(features, entry.getValue()));
            }
            if (state.lexiconNode.words.size() != 0) {
              nextBeam.relax(state.trans2_3(features, null));
            }
            break;
          case 1:
            nextBeam.relax(state.trans1_2(features));
            break;
          default: assert false;
        }
      }
    }

    return getPrediction(nextBeam);
  }

  static List<String> getPrediction(Beam beam) {
    State best = beam.max();
    ArrayList<Integer> words = new ArrayList<Integer>();
    int word = best.prevWord;
    System.out.println(best.lexiconNode);
    if (word == -1) {
      return new ArrayList<String>();
    }
    words.add(word);
    while (best.prevWord != -1) {
      if (best.prevWord != word) {
        word = best.prevWord;
        words.add(word);
      }
      best = best.prevState;
    }

    ArrayList<String> ret = new ArrayList<String>();
    for (int i = words.size() - 1; i >= 0; i--) {
      ret.add(indexer.get(words.get(i)));
    }
    return ret;
  }
}
