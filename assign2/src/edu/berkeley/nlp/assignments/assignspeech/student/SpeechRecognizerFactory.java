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

import java.lang.Double;
import java.lang.Integer;
import java.lang.String;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;

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

  LexiconNode(String phoneme, LexiconNode prevNode) {
    this.phoneme = phoneme;
    this.prevNode = prevNode;
    this.words = new ArrayList<Integer>();
  };

  LexiconNode(PronunciationDictionary dict, AcousticModel acousticModel) {
    StringIndexer indexer = EnglishWordIndexer.getIndexer();

    ArrayList<String> skipped = new ArrayList<String>(); // TODO: Skip stuff

    int index = 0;
    for (String word : dict.getContainedWords()) {
      List<List<String>> pronunciations = dict.getPronunciations(word);

      LexiconNode node, nextNode;
      SubphoneWithContext subphone;
      int p = 0;
      outer:
      for (List<String> pronunciation : pronunciations) {

        String prev = "";
        for (String phoneme : pronunciation) {
          if (!prev.equals("")) {
            subphone = new SubphoneWithContext(prev, 3, "", phoneme);
            if (!acousticModel.contains(subphone)) continue outer;
          }
          subphone = new SubphoneWithContext(phoneme, 1, prev, "");
          if (!acousticModel.contains(subphone)) continue outer;
          subphone = new SubphoneWithContext(phoneme, 2, "", "");
          if (!acousticModel.contains(subphone)) continue outer;
          prev = phoneme;
        }
        subphone = new SubphoneWithContext(prev, 3, "", "");
        if (!acousticModel.contains(subphone)) continue;

        p++;
        node = this;
        for (String phoneme : pronunciation) {
          nextNode = node.children.get(phoneme);
          if (nextNode == null) {
            nextNode = new LexiconNode(phoneme, node);
            node.children.put(phoneme, nextNode);
          }
          node = nextNode;
        }
        node.words.add(indexer.addAndGetIndex(word));
      }
      if (p > 0) index++;
    }

    System.out.println("Words in Lexicon: " + index);
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
  final static int BEAM_SIZE = 2048;
  final static double WORD_BONUS = Math.log(1.15);
  final static double WIP_MULTIPLIER = 20d;
  final static double LM_BOOST = 7d;
  final static StringIndexer indexer = EnglishWordIndexer.getIndexer();
  static int[] ngram = new int[3];

  NgramLanguageModel lm;
  int START_SYMBOL;
  State MIN_STATE = new State(Double.NEGATIVE_INFINITY);
  State MAX_STATE = new State(Double.POSITIVE_INFINITY);

  public Recognizer(AcousticModel acousticModel, PronunciationDictionary dict, String lmDataPath) {
    lexicon = new LexiconNode(dict, acousticModel);
    this.dict = dict;
    this.acousticModel = acousticModel;
    Iterable<List<String>> sents = SentenceCollection.Reader.readSentenceCollection(lmDataPath);
    lm = KneserNeyLmFactory.newLanguageModel(sents, false);
    START_SYMBOL = indexer.indexOf("<s>");
    System.out.println("-------------------------------");
  }

  class State implements Comparable {
    int hashCache;
    boolean hashCacheNull = true;

    int prevWord = -1;
    int prevPrevWord = -1;
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
      if (!hashCacheNull) {
        return hashCache;
      } else {
        hashCacheNull = false;
        this.hashCache = this.subphone.hashCode() ^ (int)((long)this.prevWord * 0xff51afd7ed558ccdL) ^ this.lexiconNode.hashCode();
        return this.hashCache;
      }
    }

    State(double probability) {
      this.probability = probability;
    }
    State(State state) {
      this.prevState = state;
      this.prevWord = state.prevWord;
      this.prevPrevWord = state.prevPrevWord;
    }
    State(State prevState, int prevWord) {
      this.prevState = prevState;
      this.prevWord = prevWord;
      this.prevPrevWord = prevState.prevWord;
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
//        if (subphone.getSubphonePosn() != 2 && subphone.getBackContext() == "" && subphone.getForwardContext() == "") {
//          return acousticModel.getLogProbability(new SubphoneWithContext(subphone.getPhoneme(), 2, "", ""), point);
//        }
        System.out.println("WARNING: acoustic " + this.subphone + " unseen " + this.lexiconNode);
        return Double.NEGATIVE_INFINITY;
      }
      return acousticModel.getLogProbability(this.subphone, point);
    }

    State selfLoop(float[] point) {
      State newState = new State(this);
      newState.lexiconNode = this.lexiconNode;
      newState.subphone = this.subphone;
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }

    State newWord(float[] point, int word, LexiconNode nextNode, double lmProb) {
      assert this.subphone.getSubphonePosn() == 3;
      State newState = new State(this, word);
      newState.lexiconNode = nextNode;
      newState.subphone = new SubphoneWithContext(nextNode.phoneme, 1, "", "");
      newState.probability = this.probability + newState.acousticsProbability(point) + lmProb;
      return newState;
    }

    State trans1_2(float[] point) { // TODO: Smear LM
      assert this.subphone.getSubphonePosn() == 1;
      State newState = new State(this);
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
      State newState = new State(this);
      newState.lexiconNode = nextNode == null ? this.lexiconNode : nextNode;
      newState.subphone = new SubphoneWithContext(this.lexiconNode.phoneme, 3, "", nextNode == null ? "" : nextNode.phoneme);
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }

    State trans3_1(float[] point) {
      assert this.subphone.getSubphonePosn() == 3;
      State newState = new State(this);
      newState.lexiconNode = this.lexiconNode;
      newState.subphone = new SubphoneWithContext(this.lexiconNode.phoneme, 1, this.lexiconNode.prevNode.phoneme, "");
      newState.probability = this.probability + newState.acousticsProbability(point);
      return newState;
    }
  }

  class Beam {
    State[] heap;
    int size;
    int heapMaxSize;
    HashMap<State, Integer> hash;

    Beam(int maxSize) {
      heapMaxSize = maxSize;
      heap = new State[heapMaxSize];
      size = 0;
      hash = new HashMap<State, Integer>((int)(heapMaxSize * 1.5f));
    }

    void relax(State state) {
      Integer copyIndex = hash.get(state);
      if (copyIndex != null) {
        assert heap[copyIndex].equals(state) : copyIndex + ":" + hash.get(state)
                + "_" + heap[copyIndex].hashCode() + ":" + state.hashCode();
        if (heap[copyIndex].probability >= state.probability) return;
        heap[copyIndex].probability = state.probability;
        bubbleDown(copyIndex);
      } else {
        if (size == heapMaxSize - 1) {
          hash.remove(heap[1]);
          heap[1] = heap[size];
          hash.put(heap[1], new Integer(1));
          heap[size] = MAX_STATE;
          bubbleDown(1);
        } else {
          size++;
        }
        int pos = size;
        for (; pos > 1 && state.probability < heap[pos/2].probability; pos = pos/2) {
          heap[pos] = heap[pos/2];
          hash.put(heap[pos], new Integer(pos));
        }
        heap[pos] = state;
        hash.put(state, new Integer(pos));
      }
    }

    private void bubbleDown(int i) {
      State tmp = heap[i];
      hash.remove(tmp);

      while (i * 2 <= size) {
        int child = i * 2;
        if (child != size && heap[child].probability > heap[child+1].probability) {
          child++;
        }
        if (tmp.probability > heap[child].probability) {
          heap[i] = heap[child];
          hash.put(heap[i], new Integer(i));
        } else {
          break;
        }
        i = child;
      }
      heap[i] = tmp;
      hash.put(tmp, new Integer(i));
    }

    State max() {
      State max = MIN_STATE;
      for ( int i = 1; i <= size; i++) {
        if ( heap[i].probability > max.probability) {
          max = heap[i];
        }
      }
      return max;
    }

    double minProb() {
      if (size == 0) return Double.NEGATIVE_INFINITY;
      return heap[1].probability;
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

      int PRINT_EVERY = 2;
      if (index % PRINT_EVERY == PRINT_EVERY - 1) {
        System.out.println(getPrediction(nextBeam));
      }

      int diff = acousticFeatures.size() - index;
      if (diff < 10) {
        nextBeam = new Beam(BEAM_SIZE * 2);
      } else {
        nextBeam = new Beam(BEAM_SIZE);
      }

      int stateIndex = 0;
      for (State state : prevBeam.heap) {
        if (stateIndex++ == 0) continue;
        if (stateIndex > prevBeam.size) break;

        nextBeam.relax(state.selfLoop(features));

        switch (state.subphone.getSubphonePosn()) {
          case 3:
            if (state.subphone.getForwardContext() != "") {
              nextBeam.relax(state.trans3_1(features));
            } else {
              assert state.lexiconNode.words.size() != 0;
              for (int word : state.lexiconNode.words) {
                ngram[0] = state.prevPrevWord;
                ngram[1] = state.prevWord;
                ngram[2] = word;
                double lmProb;
                if (ngram[0] != -1) {
                  lmProb = lm.getNgramLogProbability(ngram, 0, 3);
                } else if (ngram[1] != -1) {
                  ngram[0] = START_SYMBOL;
                  lmProb = lm.getNgramLogProbability(ngram, 0, 3);
                } else {
                  ngram[1] = START_SYMBOL;
                  lmProb = lm.getNgramLogProbability(ngram, 1, 3);
                }
//                if (lmProb > -2) {
//                  System.out.println("lmProb: " + lmProb + " ["
//                          + (ngram[0] == -1 ? "" : (indexer.get(ngram[0]) + ", "))
//                          + (ngram[1] == -1 ? "" : (indexer.get(ngram[1]) + ", "))
//                          + indexer.get(ngram[2]) + "]");
//                }
                lmProb = (lmProb * LM_BOOST) + (WORD_BONUS * Math.max(WIP_MULTIPLIER / (diff + 0.1d), 1d));

                for (LexiconNode nextNode : lexicon.children.values()) {
                  nextBeam.relax(state.newWord(features, word, nextNode, lmProb));
                }
              }
            }
            break;
          case 2:
            for (LexiconNode nextNode : state.lexiconNode.children.values()) {
              nextBeam.relax(state.trans2_3(features, nextNode));
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

  List<String> getPrediction(Beam beam) {
    State best = beam.max();
    ArrayList<Integer> words = new ArrayList<Integer>();
    ArrayList<String> ret = new ArrayList<String>();

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

    for (int i = words.size() - 1; i >= 0; i--) {
      ret.add(indexer.get(words.get(i)));
    }
    return ret;
  }
}
