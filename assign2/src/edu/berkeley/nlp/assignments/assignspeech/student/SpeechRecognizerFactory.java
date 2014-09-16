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

     return null; // TODO Construct a speech recognizer implementation here
  }
}
