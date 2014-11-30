package edu.berkeley.nlp.assignments.align.student;

import com.sun.org.apache.xpath.internal.SourceTree;
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

	static int MAX_ITERATIONS = 2;
	static double NULL_PROBABILITY = 0.25;
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

			frenchWords = sentencePair.getFrenchWords();
			englishWords = sentencePair.getEnglishWords();

			int[] indexedFrench = new int[frenchWords.size()];
			int[] indexedEnglish = new int[englishWords.size()];

			int index = 0;
			for (String frenchWord : frenchWords) {
				indexedFrench[index] = frenchIndexer.addAndGetIndex(frenchWord);
				index++;
			}
			index = 0;
			for (String englishWord : englishWords) {
				indexedEnglish[index] = englishIndexer.addAndGetIndex(englishWord);
				index++;
			}
			indexedPairs.add(new IndexedPair(indexedFrench, indexedEnglish));
		}
	}

	class HmmAligner implements WordAligner {

		boolean reverse;
		Counter<Integer> probabilities;
		double[] transitions;
		final int MAX_TRANSITION = 10;
		final int TRANSITION_SIZE = MAX_TRANSITION * 2 + 1;
		final double NULL_ALPHABETA_MULTIPLIER = 0.25;
		double[] englishProbSums = new double[englishIndexer.size()];
		int iterationNumber = 0;
		double uniform = 1.0 / englishIndexer.size();

		HmmAligner(boolean reverse) {
			this.reverse = reverse;

			probabilities = new Counter<Integer>();
			transitions = getInitialTransitions();

			while (iterationNumber < MAX_ITERATIONS) {
//				System.out.print("\riterationNumber = " + iterationNumber);

				Counter<Integer> newProbabilities = new Counter<Integer>();
				double[] newTransitions = new double[TRANSITION_SIZE];
				double[][][] transitionMatrices = new double[MAX_FRENCH_LENGTH][][];

				if (iterationNumber != 0) Arrays.fill(englishProbSums, 0);

				int sentenceCount = 0;
				for (IndexedPair indexedPair : indexedPairs) {
					sentenceCount++;
					System.out.print("\rIteration " + iterationNumber + ": " + sentenceCount + "/" + indexedPairs.size());
					int[] frenchWords = indexedPair.getFrenchWords(reverse);
					int[] englishWords = indexedPair.getEnglishWords(reverse);
					int frenchLength = frenchWords.length;
					int englishLength = englishWords.length;
					final int NULL_OFFSET = englishLength;

					double[][] transitionMatrix;
					if (transitionMatrices[englishLength] == null) {
						transitionMatrix = getTransitionMatrix(englishLength);
						transitionMatrices[englishLength] = transitionMatrix;
					} else {
						transitionMatrix = transitionMatrices[englishLength];
					}

					double[][] alphas = getAlphas(englishWords, frenchWords, transitionMatrix);
					double[][] betas = getBetas(englishWords, frenchWords, transitionMatrix);

					// Update Emissions
					double totalProb, lastTotal = -1;
					for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
						double[] alphaBetas = TEMP_ARRAY;
						totalProb = 0;
						double nullProb = 0;
						for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
							double beta = betas[frenchIndex][englishIndex];
							double alphaBeta = alphas[frenchIndex][englishIndex] * beta;
							alphaBetas[englishIndex] = alphaBeta;
							totalProb += alphaBeta;
							nullProb += alphas[frenchIndex][englishIndex + NULL_OFFSET] * beta;
						}
						totalProb += nullProb;
						for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
							int key = getKey(englishWords[englishIndex], frenchWords[frenchIndex]);
							double prob = alphaBetas[englishIndex] / totalProb;
							assert prob > 0;
							newProbabilities.incrementCount(key, prob);
							englishProbSums[englishWords[englishIndex]] += prob;
						}
						int key = getKey(NULL_INDEX, frenchWords[frenchIndex]);
						assert nullProb > 0;
						assert !Double.isInfinite(totalProb);
						newProbabilities.incrementCount(key, nullProb / totalProb);
						englishProbSums[NULL_INDEX] += nullProb / totalProb;

						assert lastTotal == -1 || Math.abs(totalProb - lastTotal) < 0.000001
										: totalProb + " != " + lastTotal;
						lastTotal = totalProb; // TODO: assert normalized
						System.out.println("englishProbSums = " + Arrays.toString(englishProbSums));
					}

					// Update Transitions
					double[][] wordTransitionMatrix = new double[englishLength][englishLength];
					double matrixSum = 0;
					for (int frenchIndex = 1; frenchIndex < frenchLength; frenchIndex++) {
						for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
							Arrays.fill(wordTransitionMatrix[prevEngIdx], 0);
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								double alpha = alphas[frenchIndex - 1][prevEngIdx];
								double beta = betas[frenchIndex][englishIndex];
								double transition = transitionMatrix[prevEngIdx][englishIndex];
								double emission;
								if (iterationNumber == 0) {
									emission = uniform;
								} else {
									emission = probabilities.getCount(
													getKey(englishWords[englishIndex], frenchWords[frenchIndex]));
								}
								double probability = alpha * beta * transition * emission;
								wordTransitionMatrix[prevEngIdx][englishIndex] = probability;
								matrixSum += probability;
							}
						}
						for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								newTransitions[englishIndex - prevEngIdx + MAX_TRANSITION] +=
												wordTransitionMatrix[prevEngIdx][englishIndex] / matrixSum;
							}
						}
					}
				}
				normalizeProbabilities(newProbabilities);
				probabilities = newProbabilities;
				iterationNumber++;
			}
			TEMP_ARRAY = null;
			System.out.println("\nTraining complete!");
			System.out.println("probabilities = " + probabilities);
		}

		public Alignment alignSentencePair(SentencePair sentencePair) {
			Alignment alignment = new Alignment();
			List<String> englishStrings = sentencePair.getEnglishWords();
			List<String> frenchStrings = sentencePair.getFrenchWords();
			int englishLength = englishStrings.size();
			int frenchLength = frenchStrings.size();
			int[] englishWords = new int[englishLength];
			int[] frenchWords = new int[frenchLength];

			int index = 0;
			for (String englishString : englishStrings) {
				englishWords[index] = englishIndexer.indexOf(englishString);
				index++;
			}
			index = 0;
			for (String frenchString : frenchStrings) {
				frenchWords[index] = frenchIndexer.indexOf(frenchString);
				index++;
			}

			double[][] transitionMatrix = getTransitionMatrix(englishLength);
			double[][] alphas = getAlphas(englishWords, frenchWords, transitionMatrix);
			double[][] betas = getBetas(englishWords, frenchWords, transitionMatrix);

			for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
				double bestScore = 0;
				for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
					bestScore += alphas[frenchIndex][englishIndex + englishLength] * betas[frenchIndex][englishIndex];
				}
				bestScore *= NULL_ALPHABETA_MULTIPLIER;
				int bestIndex = -1;
				for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
					double score = alphas[frenchIndex][englishIndex] * betas[frenchIndex][englishIndex];
					if (score > bestScore) {
						bestScore = score;
						bestIndex = englishIndex;
					}
				}
				if (bestIndex != -1) {
					alignment.addAlignment(bestIndex, frenchIndex, true);
				}
			}
			return alignment;
		}

		double[][] getAlphas(int[] englishWords, int[] frenchWords, double[][] transitionMatrix) {
			int frenchLength = frenchWords.length;
			int englishLength = englishWords.length;
			final int NULL_OFFSET = englishLength;

			double[][] alphas = new double[frenchLength][englishLength * 2];
			for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
				double[] currAlphas = alphas[frenchIndex];
				if (frenchIndex == 0) {
					for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
						if (iterationNumber == 0) {
							currAlphas[englishIndex] = uniform;
							currAlphas[englishIndex + NULL_OFFSET] = uniform;
						} else {
							double prob = probabilities.getCount(
											getKey(englishWords[englishIndex], frenchWords[frenchIndex]));
							assert !Double.isInfinite(prob) : probabilities;
							currAlphas[englishIndex] = prob;
							prob = probabilities.getCount(
											getKey(NULL_INDEX, frenchWords[frenchIndex]));
							currAlphas[englishIndex + NULL_OFFSET] = prob;
							assert prob > 0 :  "P(" + frenchIndexer.get(frenchWords[frenchIndex])
											+ "|" + englishIndexer.get(englishWords[englishIndex]) + ") = 0";
						}
					}
				} else {
					double[] prevAlphas = alphas[frenchIndex - 1];
					for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
						for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
							double transitionProbability = transitionMatrix[prevEngIdx][englishIndex];
							double prevAlpha = prevAlphas[prevEngIdx] + prevAlphas[prevEngIdx + NULL_OFFSET];
							double emission;
							if (iterationNumber == 0) {
								emission = uniform;
							} else {
								emission = probabilities.getCount(
												getKey(englishWords[englishIndex], frenchWords[frenchIndex])
								);
							}
							double prob = prevAlpha * transitionProbability * emission;
							assert !Double.isNaN(prob) && !Double.isInfinite(prob): prevAlpha;
							currAlphas[englishIndex] += prob;
						}
						double prevAlpha = prevAlphas[englishIndex] + prevAlphas[englishIndex + NULL_OFFSET];
						double emission;
						if (iterationNumber == 0) {
							emission = uniform;
						} else {
							emission = probabilities.getCount(
											getKey(NULL_INDEX, frenchWords[frenchIndex])
							);
						}
						double prob = prevAlpha * NULL_PROBABILITY * emission;
						assert !Double.isNaN(prob) && !Double.isInfinite(prob) : prevAlpha;
						currAlphas[englishIndex + NULL_OFFSET] = prob;
					}
				}
				assert max(currAlphas) > 0 : Arrays.toString(currAlphas) + frenchIndex;
			}
			return alphas;
		}

		double[][] getBetas(int[] englishWords, int[] frenchWords, double[][] transitionMatrix) {
			int frenchLength = frenchWords.length;
			int englishLength = englishWords.length;

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
							double emission;
							if (iterationNumber == 0) {
								emission = uniform;
							} else {
								emission = probabilities.getCount(
												getKey(englishWords[prevEngIdx], frenchWords[frenchIndex + 1])
								);
							}
							currBetas[englishIndex] += prevBetas[prevEngIdx] * transitionProbability * emission;
						}
						double emission = iterationNumber == 0 ? uniform : probabilities.getCount(
										getKey(NULL_INDEX, frenchWords[frenchIndex + 1])
						);
						currBetas[englishIndex] += prevBetas[englishIndex] * NULL_PROBABILITY * emission;
					}
				}
				assert max(currBetas) > 0 : Arrays.toString(currBetas);
			}
			return betas;
		}


		void normalizeProbabilities(Counter<Integer> probabilities) {
			for (Integer key : probabilities.keySet()) {
				double probSum = englishProbSums[getEnglish(key)];
				double count = probabilities.getCount(key);
				assert probSum != 0 : Arrays.toString(englishProbSums) + getEnglish(key);
				assert !Double.isInfinite(count);
				double newCount = count / probSum;
				probabilities.setCount(key, newCount);
			}
		}

		double[] getInitialTransitions() {
			double[] transitions = new double[TRANSITION_SIZE];
			Arrays.fill(transitions, 0.0);
			transitions[-2 + MAX_TRANSITION] += 1.0/15;
			transitions[-1 + MAX_TRANSITION] += 2.0/15;
			transitions[0 + MAX_TRANSITION] += 3.0/15;
			transitions[1 + MAX_TRANSITION] = 4.0/15;
			transitions[2 + MAX_TRANSITION] += 5.0/15;
			return transitions;
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
				sum /= 1 - NULL_PROBABILITY;
				for (int to = 0; to < length; to++) {
					fromArr[to] /= sum;
				}
			}
			return matrix;
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

	double max(double[] arr) {
		double m = Double.NEGATIVE_INFINITY;
		for (double elem : arr) {
			m = Math.max(m, elem);
		}
		return m;
	}

	List<String> getWords(int[] sentence, Indexer indexer) {
		ArrayList<String> list = new ArrayList<String>();
		for (int index : sentence) {
			list.add((String)indexer.get(index));
		}
		return list;
	}
}
