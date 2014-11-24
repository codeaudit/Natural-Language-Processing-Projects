package edu.berkeley.nlp.assignments.align.student;

import edu.berkeley.nlp.mt.Alignment;
import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.mt.WordAligner;
import edu.berkeley.nlp.mt.WordAlignerFactory;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HmmAlignerFactory implements WordAlignerFactory
{

	public WordAligner newAligner(Iterable<SentencePair> trainingData) {

		 return new IntersectedHmmAligner(trainingData);
	}
}

class IntersectedHmmAligner implements WordAligner {

	static int MAX_ITERATIONS = 20;

	Indexer<String> englishIndexer = new Indexer<String>();
	Indexer<String> frenchIndexer = new Indexer<String>();
	ArrayList<IndexedPair> indexedPairs = new ArrayList<IndexedPair>();

	HmmAligner forwardAligner;

	IntersectedHmmAligner(Iterable<SentencePair> trainingData) {
		processTrainingData(trainingData);

		forwardAligner = new HmmAligner(false);
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {
		return forwardAligner.alignSentencePair(sentencePair);
	}

	private void processTrainingData(Iterable<SentencePair> trainingData) {
		int nullIndex = englishIndexer.addAndGetIndex("<NULL>");
		assert nullIndex == 0;
		nullIndex = frenchIndexer.addAndGetIndex("<NULL>");
		assert nullIndex == 0;

		for (SentencePair sentencePair : trainingData) {

			List<String> frenchWords, englishWords;

			frenchWords = sentencePair.getEnglishWords();
			englishWords = sentencePair.getFrenchWords();

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

	class HmmAligner implements WordAligner{

		boolean reverse;
		Counter<Integer> probabilities;
		double[] transitions;
		final int MAX_TRANSITION = 10;

		HmmAligner(boolean reverse) {
			this.reverse = reverse;

			probabilities = new Counter<Integer>();
			double uniform = 1.0/englishIndexer.size();

			transitions = new double[MAX_TRANSITION * 2 + 1];
			Arrays.fill(transitions, 0.01);
			transitions[1 + MAX_TRANSITION] = 0.4;
			transitions[0 + MAX_TRANSITION] += 0.2;
			transitions[2 + MAX_TRANSITION] += 0.2;

			int iterationNumber = 0;
			while (iterationNumber < MAX_ITERATIONS) {
				System.out.print("\riterationNumber = " + iterationNumber);
			}
		}

		public Alignment alignSentencePair(SentencePair sentencePair) {
			Alignment alignment = new Alignment();

			return alignment;
		}
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
