package edu.berkeley.nlp.assignments.assign1.student;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.langmodel.LanguageModelFactory;
import edu.berkeley.nlp.langmodel.NgramLanguageModel;

public class LmFactory implements LanguageModelFactory {

    public NgramLanguageModel newLanguageModel(Iterable<List<String>> trainingData) {
        return new LanguageModel(trainingData);
    }
}

class LanguageModel implements NgramLanguageModel {

    static final String STOP = NgramLanguageModel.STOP;
    static final String START = NgramLanguageModel.START;

    public int getOrder() {
        return 3;
    }

    HashMap<Long, Integer> trigrams = new HashMap<Long, Integer>();
    HashMap<Long, Integer> bigrams = new HashMap<Long, Integer>();
    HashMap<Integer, Integer> unigrams = new HashMap<Integer, Integer>();
    // Fertility refers to N1+(*, ngram)
    HashMap<Integer, Integer> unigramFertility = new HashMap<Integer, Integer>();
    HashMap<Long, Integer> bigramFertility = new HashMap<Long, Integer>();
    // This one is N1+(ngram, *)
    HashMap<Integer, Integer> unigramPostFertility = new HashMap<Integer, Integer>();
    HashMap<Long, Integer> bigramPostFertility = new HashMap<Long, Integer>();
    // This one is \Sum_w(N1+(*, key, w))
    HashMap<Integer, Integer> sumFertility = new HashMap<Integer, Integer>();

    public LanguageModel(Iterable<List<String>> sentenceCollection) {
        System.out.println("Building LanguageModel...");
        int sent = 0;
        StringIndexer indexer = EnglishWordIndexer.getIndexer();
        for (List<String> sentence : sentenceCollection) {
            sent++;
            if (sent % 1000000 == 0) System.out.println("On sentence " + sent);
            List<String> stoppedSentence = new ArrayList<String>(sentence);
            stoppedSentence.add(0, START);
            stoppedSentence.add(STOP);
            int curr, prev, prev2;
            for (int i = 0; i < stoppedSentence.size(); i++) {
                String word = stoppedSentence[i];
                prev2 = prev;
                prev = curr;
                curr = indexer.addAndGetIndex(word);
                assert(curr < 1<<21);
                if (i >= 2) {
                    long key = 0;
                    key += prev2;
                    key <<= 21;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    Integer value = trigrams.get(key);
                    if (value == null) {
                        value = 1;
                        long key2 = 0;
                        key2 += prev;
                        key2 <<= 21;
                        key2 += curr;
                        Integer fertility = bigramFertility.get(key2);
                        bigramFertility.put(key2, fertility == null ? 1 : fertility + 1);

                        fertility = sumFertility.get(prev);
                        sumFertility.put(prev, fertility == null ? 1 : fertility + 1);

                        key2 = 0; key2 += prev2; key2 <<= 21; key2 += prev;
                        fertility = bigramPostFertility.get(key2);
                        bigramPostFertility.put(key2, fertility == null ? 1 : fertility + 1);
                    } else {
                        value += 1;
                    }
                    trigrams.put(key, value);
                }
                if (i >= 1) {
                    long key = 0;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    Integer value = bigrams.get(key);
                    if (value == null) {
                        value = 1;
                        Integer fertility = unigramFertility.get(curr);
                        unigramFertility.put(curr, fertility == null ? 1 : fertility + 1);
                        fertility = unigramPostFertility.get(prev);
                        unigramPostFertility.put(prev, fertility == null ? 1 : fertility + 1);
                    } else {
                        value += 1;
                    }
                    bigrams.put(key, value);
                }
                Integer value = unigrams.get(curr);
                unigrams.put(curr, value == null ? 1 : value + 1);
            }
        }
    }

    public double getNgramLogProbability(int[] ngram, int from, int to) {
        double D = 0.75;
        int order = to - from;
        to -= 1;
        int word3 = ngram[to];
        double pUnigram = (double)(unigramFertility.get(word3)) / (double)(bigrams.size());

        if (order == 1) return Math.log(pUnigram);

        int word2 = ngram[to-1];
        long key = 0; key += word2; key <<= 21; key += word3;
        double pBigram = bigramFertility.get(key) - D;// TODO: null & max omitted
        pBigram += D * unigramPostFertility.get(word2) * pUnigram;
        pBigram /= sumFertility.get(word2);

        if (order == 2) return Math.log(pBigram);

        int word1 = ngram[to-2];
        key = 0; key += word1; key <<= 21; key += word2; key <<= 21; key += word3;
        Integer count = trigrams.get(key);
        double pTrigram = count == null ? 0 : (double)(count) - D;
        key = 0; key += word1; key <<= 21; key += word2;
        pTrigram += D * bigramPostFertility.get(key) * pBigram;
        pTrigram /= (double)(bigrams.get(key));

        return Math.log(pTrigram);;
    }

    public long getCount(int[] ngram) {
        Integer val;
        if (ngram.length > 3) return 0;
        if (ngram.length == 3) {
            long key = 0;
            key += ngram[0];
            key <<= 21;
            key += ngram[1];
            key <<= 21;
            key += ngram[2];
            val = trigrams.get(key);
        }
        if (ngram.length == 2) {
            long key = 0;
            key += ngram[0];
            key <<= 21;
            key += ngram[1];
            val = bigrams.get(key);
        }
        if (ngram.length == 1) {
            val = unigrams.get(ngram[0]);
        }
        return val == null ? 0 : val;
    }
}
