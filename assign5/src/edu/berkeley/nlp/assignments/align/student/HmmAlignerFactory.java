package edu.berkeley.nlp.assignments.align.student;

import com.sun.org.apache.xpath.internal.SourceTree;
import edu.berkeley.nlp.mt.Alignment;
import edu.berkeley.nlp.mt.SentencePair;
import edu.berkeley.nlp.mt.WordAligner;
import edu.berkeley.nlp.mt.WordAlignerFactory;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HmmAlignerFactory implements WordAlignerFactory
{

	public WordAligner newAligner(Iterable<SentencePair> trainingData) {

		 return new IntersectedHmmAligner(trainingData);
	}

	public static void main(String[] args) {
		List<SentencePair> trainingData = new ArrayList<SentencePair>();
		List<String> english1 = new ArrayList<String>();
		english1.add("A");
		List<String> french1 = new ArrayList<String>();
		french1.add("X");
		trainingData.add(new SentencePair(0, "", english1, french1));
		List<String> english2 = new ArrayList<String>();
		english2.add("A");
		List<String> french2 = new ArrayList<String>();
		french2.add("X");
		french2.add("Y");
		trainingData.add(new SentencePair(1, "", english2, french2));

		WordAligner aligner = new IntersectedHmmAligner(trainingData);
	}
}

class IntersectedHmmAligner implements WordAligner {

	static int MAX_ITERATIONS = 10;
	static double NULL_PROBABILITY = 0.2;
	final double NULL_ALPHABETA_MULTIPLIER = 0.5;
	final int MAX_TRANSITION = 10;
	static int MAX_FRENCH_LENGTH = 30;

	double[] getInitialTransitions() {
		double[] transitions = new double[TRANSITION_SIZE];
		Arrays.fill(transitions, 0.01);
		transitions[-1 + MAX_TRANSITION] += 0.1;
		transitions[0 + MAX_TRANSITION] += 0.5;
		transitions[1 + MAX_TRANSITION] += 2;
		transitions[2 + MAX_TRANSITION] += 0.5;
		transitions[3 + MAX_TRANSITION] += 0.1;
		return transitions;
	}

	final int TRANSITION_SIZE = MAX_TRANSITION * 2 + 1;
	static int MAX_ENGLISH_LENGTH = 256;
	static int NULL_INDEX = 0;
	static double[] TEMP_ARRAY = new double[MAX_ENGLISH_LENGTH];
	static double A_SMALL_NUMBER = 0.000001;
//	static double[] START_STATE_ALPHAS = {1.0};

	Indexer<String> englishIndexer = new Indexer<String>();
	Indexer<String> frenchIndexer = new Indexer<String>();
	ArrayList<IndexedPair> indexedPairs = new ArrayList<IndexedPair>();

	HmmAligner forwardAligner;
	HmmAligner reversedAligner;

	IntersectedHmmAligner(Iterable<SentencePair> trainingData) {
//		processTrainingData(trainingData);
//
//		forwardAligner = new HmmAligner(false);
//		reversedAligner = new HmmAligner(true);
//		TEMP_ARRAY = null;

		NonIntersectedModel1Aligner forwardModel1 = new NonIntersectedModel1Aligner(trainingData, false);
		NonIntersectedModel1Aligner reverseModel1 = new NonIntersectedModel1Aligner(trainingData, true);

		forwardAligner = new HmmAligner(forwardModel1, false);
		reversedAligner = new HmmAligner(reverseModel1, true);
	}

	public Alignment alignSentencePair(SentencePair sentencePair) {
		Alignment alignment = new Alignment();
		Alignment forwardAlignment =  forwardAligner.alignSentencePair(sentencePair);
		Alignment reverseAlignment = reversedAligner
						.alignSentencePair(sentencePair.getReversedCopy())
						.getReverseCopy();

		for (Pair<Integer, Integer> pair : forwardAlignment.getSureAlignments()) {
			if (reverseAlignment.containsSureAlignment(pair.getFirst(), pair.getSecond())) {
				alignment.addAlignment(pair.getFirst(), pair.getSecond(), true);
			}
		}

		return alignment;
//		return forwardAligner.alignSentencePair(sentencePair);
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
		Indexer<String> frenchIndexer, englishIndexer;
		double[] englishProbSums;
		int iterationNumber = 0;
		double uniform;

		HmmAligner(boolean reverse) {
			this.reverse = reverse;
			setIndexers();

			englishProbSums = new double[englishIndexer.size()];
			uniform = 1.0 / englishIndexer.size();

			probabilities = new Counter<Integer>();
			transitions = getInitialTransitions();

			while (iterationNumber < MAX_ITERATIONS) {
				System.out.print("\riterationNumber = " + (iterationNumber + 1));

				Counter<Integer> newProbabilities = new Counter<Integer>();
				double[] newTransitions = new double[TRANSITION_SIZE];
				double[][][] transitionMatrices = new double[MAX_ENGLISH_LENGTH][][];

				if (iterationNumber != 0) Arrays.fill(englishProbSums, 0);

				int sentenceCount = 0;
				for (IndexedPair indexedPair : indexedPairs) {
					sentenceCount++;
//					System.out.print("\rIteration " + (iterationNumber + 1) + ": " + sentenceCount + "/" + indexedPairs.size());
					int[] frenchWords = indexedPair.getFrenchWords(reverse);
					int[] englishWords = indexedPair.getEnglishWords(reverse);
					int frenchLength = frenchWords.length;
					int englishLength = englishWords.length;
					final int NULL_OFFSET = englishLength;
					if (frenchLength > MAX_FRENCH_LENGTH) continue;

					double[][] transitionMatrix;
					if (transitionMatrices[englishLength] == null) {
						transitionMatrix = getTransitionMatrix(englishLength);
						transitionMatrices[englishLength] = transitionMatrix;
					} else {
						transitionMatrix = transitionMatrices[englishLength];
					}

					double[][] alphas = getAlphas(englishWords, frenchWords, transitionMatrix);
					double[][] betas = getBetas(englishWords, frenchWords, transitionMatrix);
//					print("alphas", alphas);
//					print("betas", betas);

					// Update Emissions
					double totalProb, lastTotal = -1;
					for (int frenchIndex = 0; frenchIndex < frenchLength; frenchIndex++) {
						double[] alphaBetas = TEMP_ARRAY;
						assert alphaBetas != null;
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
							newProbabilities.incrementCount(key, prob);
							englishProbSums[englishWords[englishIndex]] += prob;
						}
						int key = getKey(NULL_INDEX, frenchWords[frenchIndex]);
						assert !Double.isInfinite(totalProb);
						newProbabilities.incrementCount(key, nullProb / totalProb);
						englishProbSums[NULL_INDEX] += nullProb / totalProb;

						assert lastTotal == -1 || Math.abs(totalProb - lastTotal) < A_SMALL_NUMBER
										: totalProb + " != " + lastTotal;
						lastTotal = totalProb;
					}

					// Update Transitions
					for (int frenchIndex = 1; frenchIndex < frenchLength; frenchIndex++) {
						double matrixSum = 0;
						double[][] wordTransitionMatrix = new double[englishLength][englishLength];
						for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
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
//						assert Math.abs(matrixSum - lastTotal) < A_SMALL_NUMBER
//										: "transitionMatrixSum " + matrixSum + " != " + lastTotal;
//						print("wordTransitionMatrix = ", wordTransitionMatrix);
						for (int prevEngIdx = 0; prevEngIdx < englishLength; prevEngIdx++) {
							for (int englishIndex = 0; englishIndex < englishLength; englishIndex++) {
								int index = englishIndex - prevEngIdx + MAX_TRANSITION;
								if (index < 0 || index >= TRANSITION_SIZE) continue;
								newTransitions[index] +=
												wordTransitionMatrix[prevEngIdx][englishIndex] / matrixSum;
							}
						}
					}
				}
				transitions = newTransitions;
				normalizeProbabilities(newProbabilities);
				probabilities = newProbabilities;
				iterationNumber++;
//				print(probabilities);
//				print("transitions = ", transitions);
			}
			System.out.println("\nTraining complete!");
		}

		HmmAligner(NonIntersectedModel1Aligner model1Aligner, boolean reverse) {
			iterationNumber = -1;
			this.reverse = reverse;
			frenchIndexer = model1Aligner.frenchIndexer;
			englishIndexer = model1Aligner.englishIndexer;
			probabilities = model1Aligner.probabilities;
//			print(probabilities);
			transitions = getInitialTransitions();
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
							currAlphas[englishIndex + NULL_OFFSET] = uniform * NULL_PROBABILITY;
						} else {
							double prob = probabilities.getCount(
											getKey(englishWords[englishIndex], frenchWords[frenchIndex]));
							assert !Double.isInfinite(prob) : probabilities;
							currAlphas[englishIndex] = prob;
							prob = probabilities.getCount(
											getKey(NULL_INDEX, frenchWords[frenchIndex]));
							currAlphas[englishIndex + NULL_OFFSET] = prob;
							assert prob > 0 || NULL_PROBABILITY == 0 :
											"P(" + frenchIndexer.get(frenchWords[frenchIndex]) + "|NULL) = 0";
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
				assert max(currAlphas) > 0 : "Empty alphas at french index " + frenchIndex;
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
				if (count == 0) continue;
				assert probSum != 0 : Arrays.toString(englishProbSums) + getEnglish(key) + "-" + count;
				assert !Double.isInfinite(count);
				double newCount = count / probSum;
				probabilities.setCount(key, newCount);
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
				sum /= 1 - NULL_PROBABILITY;
				for (int to = 0; to < length; to++) {
					fromArr[to] /= sum;
				}
			}
			return matrix;
		}

		void setIndexers() {
			if (!reverse) {
				frenchIndexer = IntersectedHmmAligner.this.frenchIndexer;
				englishIndexer = IntersectedHmmAligner.this.englishIndexer;
			} else {
				frenchIndexer = IntersectedHmmAligner.this.englishIndexer;
				englishIndexer = IntersectedHmmAligner.this.frenchIndexer;
			}
		}

		void print(Counter<Integer> probabilities) {
			System.out.println("probabilities = ");
			for (int englishWord = 0; englishWord < englishIndexer.size(); englishWord++) {
				String englishString = englishIndexer.get(englishWord);
				System.out.print(englishString + " > ");
				for (int frenchWord = 0; frenchWord < frenchIndexer.size(); frenchWord++) {
					String frenchString = frenchIndexer.get(frenchWord);
					int key = getKey(englishWord, frenchWord);
					if (!probabilities.containsKey(key)) continue;
					System.out.print(frenchString + ":");
					double prob = probabilities.getCount(key);
					if (prob == 0) {
						System.out.print("0,   ");
					} else {
						System.out.printf("%.2f, ", prob);
					}
				}
				System.out.print('\n');
			}
		}
	}

	Integer getKey(int englishIndex, int frenchIndex) {
		assert frenchIndex < 1 << 16;
		assert englishIndex < 1 << 16;
//		assert frenchIndex < frenchIndexer.size();
//		assert englishIndex < englishIndexer.size();
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

	void print(String prefix, double[] arr) {
		System.out.print(prefix + '[');
		for (double elem : arr) {
			System.out.printf("%.4f, ", elem);
		}
		System.out.println(']');
	}

	void print(String prefix, double[][] arr) {
		System.out.print(prefix + "[\n");
		for (double[] subarr : arr) {
			print("  ", subarr);
		}
		System.out.println(']');
	}
}
