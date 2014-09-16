package edu.berkeley.nlp.assignments.assignspeech.student;

import edu.berkeley.nlp.assignments.assignspeech.AcousticModel;
import edu.berkeley.nlp.assignments.assignspeech.PronunciationDictionary;
import edu.berkeley.nlp.assignments.assignspeech.SpeechRecognizer;


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
}

class Recognizer implements SpeechRecognizer {

    public Recognizer(AcousticModel acousticModel, PronunciationDictionary dict, String lmDataPath) {

    }

    /**
     * Decode a sequence of MFCCs and produce a sequence of lowercased words.
     *
     * @param acousticFeatures The sequence of MFCCs for this sentence with silences filtered out
     * @return The recognized sequence of words
     */
    public List<String> recognize(List<float[]> acousticFeatures) {

    }
}
