package edu.berkeley.nlp.assignments.align.student;

import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.mt.Alignment;
import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.mt.WordAligner;
import edu.berkeley.nlp.mt.WordAlignerFactory;
import edu.berkeley.nlp.mt.decoder.Decoder;
import edu.berkeley.nlp.mt.decoder.DecoderFactory;
import edu.berkeley.nlp.mt.decoder.DistortionModel;
import edu.berkeley.nlp.util.Indexer;

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
}

class Model1Aligner implements WordAligner {

	static int MAX_ITERATIONS = 100;

	Indexer<String> englishIndexer = new Indexer<String>();
	Indexer<String> frenchIndexer = new Indexer<String>();
	ArrayList<IndexedPair> indexedPairs = new ArrayList<IndexedPair>();

	static double[] alignProb = new double[64];

	double[][] probabilities;

	Model1Aligner(Iterable<SentencePair> trainingData) {
		int nullIndex = englishIndexer.addAndGetIndex("<NULL>");
		assert nullIndex == 0;
		for (SentencePair sentencePair : trainingData) {
			List<String> frenchWords = sentencePair.getFrenchWords();
			int[] indexedFrench = new int[frenchWords.size()];
			List<String> englishWords = sentencePair.getEnglishWords();
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

		probabilities = new double[englishIndexer.size()][frenchIndexer.size()];
		double uniform = 1d/englishIndexer.size();
		for (double[] english : probabilities) {
			Arrays.fill(english, uniform);
		}

		int iterationNumber = 0;
		while (iterationNumber < MAX_ITERATIONS) {
			System.out.print("\riterationNumber = " + iterationNumber);

			double[][] newProbabilities = new double[englishIndexer.size()][frenchIndexer.size()];
			double[] englishProbSums = new double[englishIndexer.size()];
			for (IndexedPair sentencePair : indexedPairs) {

				int frenchIndex = 0;
				for (int frenchWord : sentencePair.frenchWords) {

					int englishIndex = 0;
					double totalProb = 0;
					for (int englishWord : sentencePair.englishWords) {
						double prob = probabilities[englishWord][frenchWord];
						alignProb[englishIndex] = prob;
						totalProb += prob;
						englishIndex++;
					}

					englishIndex = 0;
					for (int englishWord : sentencePair.englishWords) {
						double prob = alignProb[englishIndex] / totalProb;
						newProbabilities[englishWord][frenchWord] += prob;
						englishProbSums[englishWord] += prob;
						englishIndex++;
					}

					frenchIndex++;
				}
			}

			int englishIndex = 0;
			for (double[] englishProbs : newProbabilities) {
				assert sumArray(englishProbs) == englishProbSums[englishIndex];
				for (int i = 0; i < englishProbs.length; i++) {
					englishProbs[i] /= englishProbSums[englishIndex];
				}
				englishIndex++;
			}

			iterationNumber++;
		}
	}

	double sumArray(double[] array) {
		double sum = 0;
		for (double elem : array) {
			sum += elem;
		}
		return sum;
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {

		Alignment alignment = new Alignment();
		int frenchPos = 0;
		for (String frenchWord : sentencePair.getFrenchWords()) {
			int indexedFrench = frenchIndexer.indexOf(frenchWord);
			if (indexedFrench == -1) continue;

			int englishPos = 0;
			double bestScore = probabilities[0][indexedFrench];
			int bestPos = -1;
			for (String englishWord : sentencePair.getEnglishWords()) {
				int indexedEnglish = englishIndexer.indexOf(englishWord);
				if (indexedEnglish == -1) continue;

				double prob = probabilities[indexedEnglish][indexedFrench];
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
}
