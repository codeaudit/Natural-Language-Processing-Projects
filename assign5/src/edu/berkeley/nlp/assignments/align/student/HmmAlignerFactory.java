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
	static double NULL_PROBABILITY = 0.2;
	static int NULL_INDEX = 0;
	static int MAX_FRENCH_LENGTH = 256;
	static double[] TEMP_ARRAY = new double[MAX_FRENCH_LENGTH];
//	static double[] START_STATE_ALPHAS = {1.0};

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
		final int TRANSITION_SIZE = MAX_TRANSITION * 2 + 1;
		final double NULL_PROBABILITY = 0.2;

		HmmAligner(boolean reverse) {
			this.reverse = reverse;

			probabilities = new Counter<Integer>();
			double uniform = 1.0/englishIndexer.size();

			transitions = new double[TRANSITION_SIZE];
			Arrays.fill(transitions, 0.01);
			transitions[1 + MAX_TRANSITION] = 0.3;
			transitions[0 + MAX_TRANSITION] += 0.15;
			transitions[2 + MAX_TRANSITION] += 0.15;

			int iterationNumber = 0;
			while (iterationNumber < MAX_ITERATIONS) {
				System.out.print("\riterationNumber = " + iterationNumber);

				Counter<Integer> newProbabilities = new Counter<Integer>();
				double[] newTransitions = new double[TRANSITION_SIZE];
				double[][][] transitionMatrices = new double[MAX_FRENCH_LENGTH][][];

				for (IndexedPair indexedPair : indexedPairs) {
					int[] frenchWords = indexedPair.getFrenchWords(reverse);
					int[] englishWords = indexedPair.getEnglishWords(reverse);
					int frenchLength = frenchWords.length;
					int englishLength = englishWords.length;
					final int NULL_OFFSET = englishLength;

					double[][] transitionMatrix;
					if (transitionMatrices[frenchLength] == null) {
						transitionMatrix = getTransitionMatrix(frenchLength);
						transitionMatrices[frenchLength] = transitionMatrix;
					} else {
						transitionMatrix = transitionMatrices[frenchLength];
					}

					double[][] alphas = new double[frenchLength][englishLength * 2];
					for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
						double[] currAlphas = alphas[frenchIndex];
						if (frenchIndex == 0) {
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								currAlphas[englishIndex] = probabilities.getCount(
												getKey(englishWords[englishIndex], frenchWords[frenchIndex]));
								currAlphas[englishIndex + NULL_OFFSET] = probabilities.getCount(
												getKey(englishWords[englishIndex], NULL_INDEX));
							}
						} else {
							double[] prevAlphas = alphas[frenchIndex - 1];
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								for (int prevEngIdx = 0; prevEngIdx < englishIndex; prevEngIdx++) {
									double transitionProbability = transitionMatrix[prevEngIdx][englishIndex];
									double prevAlpha = prevAlphas[prevEngIdx] + prevAlphas[prevEngIdx + NULL_OFFSET];
									currAlphas[englishIndex] += prevAlpha * transitionProbability;
								}
								double prevAlpha = prevAlphas[englishIndex] + prevAlphas[englishIndex + NULL_OFFSET];
								currAlphas[englishIndex + NULL_OFFSET] = prevAlpha * NULL_PROBABILITY;
							}
						}
					}

					double[][] betas = new double[frenchLength][englishLength];
					for (int frenchIndex = frenchLength - 1; frenchIndex >= 0; frenchIndex--) {
						double[] currBetas = betas[frenchIndex];
						if (frenchIndex == frenchLength - 1) {
							Arrays.fill(currBetas, 1);
						} else {
							double[] prevBetas = betas[frenchIndex + 1];
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
									double transitionProbability = transitionMatrix[englishIndex][prevEngIdx];
									currBetas[englishIndex] += prevBetas[prevEngIdx] * transitionProbability;
								}
								currBetas[englishIndex] += prevBetas[englishIndex] * NULL_PROBABILITY;
							}
						}
					}

					double totalProb;
					for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
						double[] alphaBetas = TEMP_ARRAY;
						double nullProb = 0;
						totalProb = 0;
						for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
							alphaBetas[englishIndex] = alphas[frenchIndex][englishIndex] * betas[frenchIndex][englishIndex];

						}
					}
				}
			}
		}

		// double[from][to]
		double[][] getTransitionMatrix(int length) {
			double[][] matrix = new double[length][length];
			for (int from = 0; from < length; from++) {
				double[] fromArr = matrix[from];
				double sum = 0;
				for (int to = 0; to < length; to++) {
					int dist = to - from;
					double prob;
					if (dist >= MAX_TRANSITION) {
						prob = transitions[MAX_TRANSITION + MAX_TRANSITION];
					} else if (dist <= -MAX_TRANSITION) {
						prob = transitions[0];
					} else {
						prob = transitions[dist + MAX_TRANSITION];
					}
					fromArr[to] = prob;
					sum += prob;
				}
				for (int to = 0; to < length; to++) {
					fromArr[to] /= sum;
				}
			}
			return matrix;
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
