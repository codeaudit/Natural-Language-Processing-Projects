package edu.berkeley.nlp.assignments.align.student;

import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.mt.Alignment;
import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.mt.WordAligner;
import edu.berkeley.nlp.mt.WordAlignerFactory;
import edu.berkeley.nlp.mt.decoder.Decoder;
import edu.berkeley.nlp.mt.decoder.DecoderFactory;
import edu.berkeley.nlp.mt.decoder.DistortionModel;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Model1AlignerFactory implements WordAlignerFactory
{

	public WordAligner newAligner(Iterable<SentencePair> trainingData) {

		 return new Model1Aligner(trainingData);
	}

}

class IndexedPair {
	int[] frenchWords;
	int[] englishWords;
	IndexedPair(int[] frenchWords, int[] englishWords) {
		this.frenchWords = frenchWords;
		this.englishWords = englishWords;
	}

	int[] getFrenchWords(boolean reverse) {
		return reverse ? englishWords : frenchWords;
	}

	int[] getEnglishWords(boolean reverse) {
		return reverse ? frenchWords : englishWords;
	}
}

class Model1Aligner implements WordAligner {

	NonIntersectedModel1Aligner aligner, reversedAligner;
	public Model1Aligner(Iterable<SentencePair> trainingData) {
		aligner = new NonIntersectedModel1Aligner(trainingData, false);
		reversedAligner = new NonIntersectedModel1Aligner(trainingData, true);
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {
		Alignment alignment = new Alignment();

		Alignment forwardAlignment = aligner.alignSentencePair(sentencePair);
		Alignment reverseAlignment = reversedAligner
						.alignSentencePair(sentencePair.getReversedCopy())
						.getReverseCopy();

		for (Pair<Integer, Integer> pair : forwardAlignment.getSureAlignments()) {
			if (reverseAlignment.containsSureAlignment(pair.getFirst(), pair.getSecond())) {
				alignment.addAlignment(pair.getFirst(), pair.getSecond(), true);
			}
		}

		return alignment;
	}
}

class NonIntersectedModel1Aligner implements WordAligner {

	static int MAX_ITERATIONS = 25;

	Indexer<String> englishIndexer = new Indexer<String>();
	Indexer<String> frenchIndexer = new Indexer<String>();
	ArrayList<IndexedPair> indexedPairs = new ArrayList<IndexedPair>();

	double[] alignProb = new double[256];

	Counter<Integer> probabilities;

	NonIntersectedModel1Aligner(Iterable<SentencePair> trainingData, boolean reverse) {
		processTrainingData(trainingData, reverse);

		assert englishIndexer.size() < 1 << 16;
		assert frenchIndexer.size() < 1 << 16;

		probabilities = new Counter<Integer>();
		double uniform = 1d/englishIndexer.size();

		int iterationNumber = 0;
		while (iterationNumber < MAX_ITERATIONS) {
			System.out.print("\riterationNumber = " + iterationNumber);

			Counter<Integer> newProbabilities = new Counter<Integer>();
			double[] englishProbSums = new double[englishIndexer.size()];
			for (IndexedPair sentencePair : indexedPairs) {

				for (int frenchWord : sentencePair.frenchWords) {

					int englishIndex = 0;
					double totalProb = 0;
					for (int englishWord : sentencePair.englishWords) {
						double prob;
						if (iterationNumber == 0) {
							prob = uniform;
						} else {
							prob = probabilities.getCount(getKey(englishWord, frenchWord));
						}
						alignProb[englishIndex] = prob;
						totalProb += prob;
						englishIndex++;
					}

					englishIndex = 0;
					for (int englishWord : sentencePair.englishWords) {
						double prob = alignProb[englishIndex] / totalProb;
						newProbabilities.incrementCount(getKey(englishWord, frenchWord), prob);
						englishProbSums[englishWord] += prob;
						englishIndex++;
					}
				}
			}

			for (Integer key : newProbabilities.keySet()) {
				double probSum = englishProbSums[getEnglish(key)];
				double newCount = newProbabilities.getCount(key) / probSum;
				newProbabilities.setCount(key, newCount);
			}
			probabilities = newProbabilities;
			iterationNumber++;
		}
	}

	private void processTrainingData(Iterable<SentencePair> trainingData) {
		processTrainingData(trainingData, false);
	}

	private void processTrainingData(Iterable<SentencePair> trainingData, boolean reverse) {
		int nullIndex = englishIndexer.addAndGetIndex("<NULL>");
		assert nullIndex == 0;

		for (SentencePair sentencePair : trainingData) {

			List<String> frenchWords, englishWords;
			if (!reverse) {
				frenchWords = sentencePair.getFrenchWords();
				englishWords = sentencePair.getEnglishWords();
			} else {
				frenchWords = sentencePair.getEnglishWords();
				englishWords = sentencePair.getFrenchWords();
			}

			int[] indexedFrench = new int[frenchWords.size()];
			int[] indexedEnglish = new int[englishWords.size() + 1];

			int index = 0;
			for (String frenchWord : frenchWords) {
				indexedFrench[index] = frenchIndexer.addAndGetIndex(frenchWord);
				index++;
			}
			index = 1;
			assert indexedEnglish[0] == 0;
			for (String englishWord : englishWords) {
				indexedEnglish[index] = englishIndexer.addAndGetIndex(englishWord);
				index++;
			}
			indexedPairs.add(new IndexedPair(indexedFrench, indexedEnglish));
		}
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {

		Alignment alignment = new Alignment();
		int frenchPos = 0;
		for (String frenchWord : sentencePair.getFrenchWords()) {
			int indexedFrench = frenchIndexer.indexOf(frenchWord);
			if (indexedFrench == -1) continue;

			int englishPos = 0;
			double bestScore = probabilities.getCount(getKey(0, indexedFrench));
			int bestPos = -1;
			for (String englishWord : sentencePair.getEnglishWords()) {
				int indexedEnglish = englishIndexer.indexOf(englishWord);
				if (indexedEnglish == -1) continue;

				double prob = probabilities.getCount(getKey(indexedEnglish, indexedFrench));
				if (prob > bestScore) {
					bestScore = prob;
					bestPos = englishPos;
				}
				englishPos++;
			}
			if (bestPos != -1) {
				alignment.addAlignment(bestPos, frenchPos, true);
			}
			frenchPos++;
		}

		return alignment;
	}

	Integer getKey(int englishIndex, int frenchIndex) {
		assert frenchIndex < 1 << 16;
		assert englishIndex < 1 << 16;
		assert frenchIndex < frenchIndexer.size();
		assert englishIndex < englishIndexer.size();
		int key = (frenchIndex << 16) + englishIndex;
		assert getEnglish(key) == englishIndex;
		assert getFrench(key) == frenchIndex;

		return key;
	}

	int getEnglish(int key) {
		return key & ((1 << 16) - 1);
	}

	int getFrench(int key) {
		return key >>> 16;
	}
}
