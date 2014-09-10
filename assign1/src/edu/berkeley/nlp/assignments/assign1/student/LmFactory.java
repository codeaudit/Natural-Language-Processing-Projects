package edu.berkeley.nlp.assignments.assign1.student;

import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import java.lang.StringBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import edu.berkeley.nlp.langmodel.LanguageModelFactory;
import edu.berkeley.nlp.langmodel.NgramLanguageModel;
import edu.berkeley.nlp.langmodel.EnglishWordIndexer;
import edu.berkeley.nlp.util.StringIndexer;

public class LmFactory implements LanguageModelFactory {

    public NgramLanguageModel newLanguageModel(Iterable<List<String>> trainingData) {
        return new LanguageModel(trainingData);
    }

    public static void main(String[] args) {
        List<String> sentence = new ArrayList<String>();
        sentence.add("a");
        sentence.add("a");
        sentence.add("a");
        sentence.add("b");
        List<List<String>> data = new ArrayList<List<String>>();
        data.add(sentence);
        NgramLanguageModel model = new LanguageModel(data);
        StringIndexer indexer = EnglishWordIndexer.getIndexer();
        int a = indexer.addAndGetIndex("a");
        int[] ngram = {
                a, a, a,
                indexer.addAndGetIndex(NgramLanguageModel.START),
                a, a,
                indexer.addAndGetIndex("b"),
                a, a,
                indexer.addAndGetIndex(NgramLanguageModel.STOP)
        };
        System.out.print("p( a | a a ) = ");
        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 0, 3)));
        System.out.print("p( a | a ) = ");
        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 0, 2)));
        System.out.print("p( a ) = ");
        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 0, 1)));

//        System.out.print("p( s | a a ) = ");
//        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 1, 4)));
        System.out.print("p( b | a a ) = ");
        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 4, 7)));
        System.out.print("p( e | a a ) = ");
        System.out.println(Math.exp(model.getNgramLogProbability(ngram, 7, 10)));
    }
}

class LanguageModel implements NgramLanguageModel {

    static final String STOP = NgramLanguageModel.STOP;
    static final String START = NgramLanguageModel.START;

    static final double ZERO = 0.000001;
    static final double LOG_ZERO = Math.log(ZERO);

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
    // This one is \Sum_w(count(bigram, w))
    HashMap<Long, Integer> sumCounts = new HashMap<Long, Integer>();

    public LanguageModel(Iterable<List<String>> sentenceCollection) {
        System.out.println("Building LanguageModel...");
        int sent = 0;
        StringIndexer indexer = EnglishWordIndexer.getIndexer();
        for (List<String> sentence : sentenceCollection) {
            sent++;
            if (sent % 250000 == 0) System.out.println("On sentence " + sent);
            List<String> stoppedSentence = new ArrayList<String>(sentence);
            stoppedSentence.add(0, START);
            stoppedSentence.add(STOP);
            int curr = 0, prev = 0, prev2;
            Integer value;
            for (int i = 0; i < stoppedSentence.size(); i++) {
                String word = stoppedSentence.get(i);
                prev2 = prev;
                prev = curr;
                curr = indexer.addAndGetIndex(word);
                assert(curr < 1<<21);
                if (i >= 2) {
                    long key = 0;
                    key += prev2;
                    key <<= 21;
                    key += prev;
                    value = sumCounts.get(new Long(key));
                    sumCounts.put(new Long(key), value == null ? new Integer(1) : new Integer(value + 1));
                    key <<= 21;
                    key += curr;
                    value = trigrams.get(new Long(key));
                    if (value == null) {
                        value = 1;
                        long key2 = 0;
                        key2 += prev;
                        key2 <<= 21;
                        key2 += curr;
                        Integer fertility = bigramFertility.get(new Long(key2));
                        bigramFertility.put(new Long(key2), fertility == null ? new Integer(1) : new Integer(fertility + 1));

                        fertility = sumFertility.get(new Integer(prev));
                        sumFertility.put(new Integer(prev), fertility == null ? new Integer(1) : new Integer(fertility + 1));

                        key2 = 0; key2 += prev2; key2 <<= 21; key2 += prev;
                        fertility = bigramPostFertility.get(new Long(key2));
                        bigramPostFertility.put(new Long(key2), fertility == null ? new Integer(1) : new Integer(fertility + 1));
                    } else {
                        value += 1;
                    }
                    trigrams.put(new Long(key), new Integer(value));
                }
                if (i >= 1) {
                    long key = 0;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    value = bigrams.get(new Long(key));
                    if (value == null) {
                        value = 1;
                        Integer fertility = unigramFertility.get(new Integer(curr));
                        unigramFertility.put(new Integer(curr), fertility == null ? new Integer(1) : new Integer(fertility + 1));
                        fertility = unigramPostFertility.get(new Integer(prev));
                        unigramPostFertility.put(new Integer(prev), fertility == null ? new Integer(1) : new Integer(fertility + 1));
                    } else {
                        value += 1;
                    }
                    bigrams.put(new Long(key), new Integer(value));
                }
                value = unigrams.get(new Integer(curr));
                unigrams.put(new Integer(curr), value == null ? new Integer(1) : new Integer(value + 1));
            }
        }
    }

    public double getNgramLogProbability(int[] ngram, int from, int to) {
        double D = 0.5;
        int order = to - from;
        int word3 = ngram[to-1];
        Integer fertility = unigramFertility.get(new Integer(word3));
        double pUnigram = fertility == null ? 0 : fertility.doubleValue();
        pUnigram /= (double)(bigrams.size());

        if (order == 1) {
            if (pUnigram == 0) throw new Error(logProbDump(Double.NEGATIVE_INFINITY, Arrays.copyOfRange(ngram, from, to)));
            double ret = Math.log(pUnigram);
            assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : ret;
            return ret;
        }


        int word2 = ngram[to-2];
        long key = 0; key += word2; key <<= 21; key += word3;
        fertility = bigramFertility.get(new Long(key));
        double pBigram = fertility == null ? 0 : (fertility.doubleValue() - D);
        fertility = unigramPostFertility.get(new Integer(word2));
        pBigram += fertility == null ? 0 : D * fertility.doubleValue() * pUnigram;
        fertility = sumFertility.get(new Integer(word2));
        if (fertility == null) {
            assert(pBigram == 0);
        } else {
            pBigram /= fertility.doubleValue();
        }

        if (pBigram == 0) pBigram = ZERO;
        if (order == 2) {
//            if (pBigram == 0) throw new Error(
//                    logProbDump(Double.NEGATIVE_INFINITY, Arrays.copyOfRange(ngram, from, to))
//            );
            double ret = Math.log(pBigram);
            assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : ret;
            return ret;
        }


        int word1 = ngram[to-3];
        key = 0; key += word1; key <<= 21; key += word2; key <<= 21; key += word3;
        Integer count = trigrams.get(new Long(key));
        double pTrigram = count == null ? 0 : (double)(count) - D;

        key = 0; key += word1; key <<= 21; key += word2;
        fertility = bigramPostFertility.get(new Long(key));
        pTrigram += fertility == null ? 0 : D * fertility.doubleValue() * pBigram;

        Integer denominator = sumCounts.get(new Long(key));
        if (denominator == null) {
            // TODO: Backoff to actual bigram and not fertility bigram
            return Math.log(pBigram);
        } else {
            pTrigram /= denominator.doubleValue();
        }

        if (pTrigram == 0) return LOG_ZERO;
        double ret = Math.log(pTrigram);
        assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : logProbDump(
                ret,
                Arrays.copyOfRange(ngram, from, to)
        );
        return ret;
    }

    String logProbDump(double logProb, int[] ngram) {
        StringBuffer out = new StringBuffer();
        out.append("\nGot log probability of " + logProb + "\nfor ngram ");
        out.append(Arrays.toString(ngram));

        ArrayList<String> words = new ArrayList<String>();
        ArrayList<Integer> wordCounts = new ArrayList<Integer>();
        StringIndexer indexer = EnglishWordIndexer.getIndexer();
        String word;
        for (int i = 0; i < ngram.length; i++) {
            word = indexer.get(ngram[i]);
            words.add(word);
            wordCounts.add(unigrams.get(new Integer(ngram[i])));
        }
        out.append(" ");
        out.append(words.toString());
        out.append(" with counts ");
        out.append(wordCounts.toString());
        out.append("\n");
        return out.toString();
    }

    public long getCount(int[] ngram) {
        Integer val = 0;
        if (ngram.length > 3) return 0;
        if (ngram.length == 3) {
            long key = 0;
            key += ngram[0];
            key <<= 21;
            key += ngram[1];
            key <<= 21;
            key += ngram[2];
            val = trigrams.get(new Long(key));
        }
        if (ngram.length == 2) {
            long key = 0;
            key += ngram[0];
            key <<= 21;
            key += ngram[1];
            val = bigrams.get(new Long(key));
        }
        if (ngram.length == 1) {
            val = unigrams.get(new Integer(ngram[0]));
        }
        return val == null ? 0 : val;
    }
}
