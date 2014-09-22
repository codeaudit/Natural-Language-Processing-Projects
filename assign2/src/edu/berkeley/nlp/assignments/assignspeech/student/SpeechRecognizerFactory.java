package edu.berkeley.nlp.assignments.assignspeech.student;

import edu.berkeley.nlp.assignments.assignspeech.AcousticModel;
import edu.berkeley.nlp.assignments.assignspeech.PronunciationDictionary;
import edu.berkeley.nlp.assignments.assignspeech.SpeechRecognizer;
import edu.berkeley.nlp.assignments.assignspeech.SubphoneWithContext;
import edu.berkeley.nlp.langmodel.EnglishWordIndexer;
import edu.berkeley.nlp.util.StringIndexer;

import java.util.*;
import java.util.ArrayList;
import java.util.PriorityQueue;

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
  String prevPhoneme = "";
  int word = -1;

  int level;

  LexiconNode(String phoneme, String prevPhoneme) {
    this.phoneme = phoneme;
    this.prevPhoneme = prevPhoneme;
  };

  LexiconNode(PronunciationDictionary dict) {
    this.level = 0;
    StringIndexer indexer = EnglishWordIndexer.getIndexer();
    for (String word : dict.getContainedWords()) {
      List<List<String>> pronunciations = dict.getPronunciations(word);

      LexiconNode node, nextNode;
      for (List<String> pronunciation : pronunciations) {
        node = this;
        for (String phoneme : pronunciation) {
          nextNode = node.children.get(phoneme);
          if (nextNode == null) {
            nextNode = new LexiconNode(phoneme, node.phoneme);
            nextNode.level = node.level + 1;
            node.children.put(phoneme, nextNode);
          }
          node = nextNode;
        }
        assert node.word == -1 : indexer.get(node.word) + ", " + word + "\n"
                + dict.getPronunciations(indexer.get(node.word)) + ";\n"
                + dict.getPronunciations(word) + ";\n"
                + node.level;
        node.word = indexer.addAndGetIndex(word);
      }
    }
  }
}


class Recognizer implements SpeechRecognizer {

  final LexiconNode lexicon;
  final PronunciationDictionary dict;
  final AcousticModel acousticModel;
  final static int BEAM_SIZE = 500;

  // static const String[] allPhonemes = ["M", "AH", "L", "P", "CH", "EH", "N", "IY", "R", "EY", "IH", "NG", "HH", "G", "T", "Z", "Y", "UW", "D", "SH", "V", "ER", "B", "S", "K", "UH", "OY", "F", "AY", "W", "OW", "AE", "JH", "AA", "TH", "AO", "AW", "DH", "ZH"];

  public Recognizer(AcousticModel acousticModel, PronunciationDictionary dict, String lmDataPath) {
    lexicon = new LexiconNode(dict);
    this.dict = dict;
    this.acousticModel = acousticModel;

  }

  class State implements Comparable {
    int prevWord = -1;
    State prevState;
    LexiconNode phoneme;
    int subphone = 1;

    State(State prevState, int prevWord) {
      this.prevState = prevState;
      this.prevWord = prevWord;
    }

    double probability;


    public int compareTo(Object o) {
      double diff = this.probability - ((State)o).probability;
      if (diff == 0) return 0;
      if (diff < 0) return -1;
      return 1;
    }
  }

  /**
   * Decode a sequence of MFCCs and produce a sequence of lowercased words.
   *
   * @param acousticFeatures The sequence of MFCCs for this sentence with silences filtered out
   * @return The recognized sequence of words
   */
  public List<String> recognize(List<float[]> acousticFeatures) {

    ArrayList<PriorityQueue<State>> beams = new ArrayList<PriorityQueue<State>>(); 

    PriorityQueue<State> prevBeam, nextBeam = new PriorityQueue<State>(BEAM_SIZE);
    for (float[] features : acousticFeatures) {
      prevBeam = nextBeam;
      nextBeam = new PriorityQueue<State>(BEAM_SIZE);

      // Recombine states in prevBeam

      for (State state : prevBeam) {

        State newState = new State(state, state.prevWord);
        newState.phoneme = state.phoneme;
        newState.subphone = state.subphone;
        SubphoneWithContext subphone = new SubphoneWithContext(
                state.phoneme.phoneme, state.subphone,
                state.subphone == 1 ? state.phoneme.prevPhoneme : "",
                state.subphone == 3 ? "" : ""); // FIXMEEEE
        newState.probability = state.probability + acousticModel.getLogProbability(subphone, features);
        nextBeam.add(newState); if (nextBeam.size() == BEAM_SIZE) nextBeam.poll();

        if (state.subphone == 3) {
          for (Map.Entry<String, LexiconNode> entry : state.phoneme.children.entrySet()) {
            newState = new State(state, state.prevWord);
            newState.phoneme = entry.getValue();
            subphone = new SubphoneWithContext(newState.phoneme.phoneme, 1, state.phoneme.phoneme, "");
            newState.probability = state.probability + acousticModel.getLogProbability(subphone, features);
            nextBeam.add(newState); if (nextBeam.size() == BEAM_SIZE) nextBeam.poll();
          }

          if (state.phoneme.word != -1) {
            for (Map.Entry<String, LexiconNode> entry : lexicon.children.entrySet()) {
              newState = new State(state, state.phoneme.word);
              newState.phoneme = entry.getValue();
              subphone = new SubphoneWithContext(newState.phoneme.phoneme, 1, "", "");
              newState.probability = state.probability + acousticModel.getLogProbability(subphone, features);
              nextBeam.add(newState); if (nextBeam.size() == BEAM_SIZE) nextBeam.poll();
            }
          }
        } else {
          newState = new State(state, state.prevWord);
          newState.phoneme = state.phoneme;
          newState.subphone = state.subphone + 1;
          subphone = new SubphoneWithContext(newState.phoneme.phoneme, newState.subphone, "", ""); // TODO
          newState.probability = state.probability + acousticModel.getLogProbability(subphone, features);
          nextBeam.add(newState); if (nextBeam.size() == BEAM_SIZE) nextBeam.poll();
        }
      }
    }

    State best = nextBeam.poll();
    assert best != null;
    while (nextBeam.peek() != null) best = nextBeam.poll();

    ArrayList<Integer> words = new ArrayList<Integer>();
    int word = best.prevWord;
    while (best.prevState != null) {
      if (best.prevWord != word) {
        word = best.prevWord;
        words.add(word);
      }
      best = best.prevState;
    }

    StringIndexer indexer = EnglishWordIndexer.getIndexer();
    ArrayList<String> ret = new ArrayList<String>();
    for (int i = words.size() - 1; i <= 0; i--) {
      ret.add(indexer.get(words.get(i)));
    }
    return ret;
  }
}
