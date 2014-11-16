package edu.berkeley.nlp.assignments.align.student;

import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.mt.Alignment;
import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.mt.WordAligner;
import edu.berkeley.nlp.mt.WordAlignerFactory;
import edu.berkeley.nlp.mt.decoder.Decoder;
import edu.berkeley.nlp.mt.decoder.DecoderFactory;
import edu.berkeley.nlp.mt.decoder.DistortionModel;
import edu.berkeley.nlp.mt.phrasetable.PhraseTable;
import edu.berkeley.nlp.util.Counter;

public class HeuristicAlignerFactory implements WordAlignerFactory
{

	public WordAligner newAligner(Iterable<SentencePair> trainingData) {

		 return new HeuristicAligner(trainingData);
	}

}

class HeuristicAligner implements WordAligner {

	Counter<String> frenchCounter = new Counter<String>();
	Counter<String> englishCounter = new Counter<String>();
	Counter<String> pairCounter = new Counter<String>();

	HeuristicAligner(Iterable<SentencePair> trainingData) {
		for (SentencePair sentencePair : trainingData) {
			for (String englishWord : sentencePair.getEnglishWords()) {
				englishCounter.incrementCount(englishWord, 1);
				for (String frenchWord : sentencePair.getFrenchWords()) {
					pairCounter.incrementCount(englishWord + "^" + frenchWord, 1);
				}
			}
			for (String frenchWord : sentencePair.getFrenchWords()) {
				frenchCounter.incrementCount(frenchWord, 1);
			}
		}
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {

		Alignment alignment = new Alignment();

		int frenchPos = 0;
		for (String frenchWord : sentencePair.getFrenchWords()) {
			int englishPos = 0;
			int bestPos = 0;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (String englishWord : sentencePair.getEnglishWords()) {
				double score = pairCounter.getCount(englishWord + "^" + frenchWord);
				score /= (englishCounter.getCount(englishWord) + englishCounter.getCount(frenchWord));
				if (score > bestScore) {
					bestPos = englishPos;
					bestScore = score;
				}
				englishPos++;
			}
			alignment.addAlignment(bestPos, frenchPos, true);
			frenchPos++;
		}
		return alignment;
	}
}
