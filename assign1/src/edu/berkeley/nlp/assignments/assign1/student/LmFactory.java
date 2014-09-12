package edu.berkeley.nlp.assignments.assign1.student;

import java.lang.*;
import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import java.lang.StringBuffer;
import java.lang.System;
import java.util.*;

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

        System.out.println("p( a | a a ) = " + Math.exp(model.getNgramLogProbability(ngram, 0, 3)));
        System.out.println("p( a | a ) = " + Math.exp(model.getNgramLogProbability(ngram, 0, 2)));
        System.out.println("p( a ) = " + Math.exp(model.getNgramLogProbability(ngram, 0, 1)));
        System.out.println("p( s | a a ) = " + Math.exp(model.getNgramLogProbability(ngram, 1, 4)));
        System.out.println("p( b | a a ) = " + Math.exp(model.getNgramLogProbability(ngram, 4, 7)));
        System.out.println("p( e | a a ) = " + Math.exp(model.getNgramLogProbability(ngram, 7, 10)));
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

    int[] unigrams = new int[500000];
    // Fertility refers to N1+(*, ngram)
    int[] unigramFertility = new int[500000];
    // This one is N1+(ngram, *)
    int[] unigramPostFertility = new int[500000];
    // This one is \Sum_w(N1+(*, key, w))
    int[] sumFertility = new int[500000];

    BigramCounter bigrams = new BigramCounter();
    TrigramCounter trigrams = new TrigramCounter();

    public LanguageModel(Iterable<List<String>> sentenceCollection) {
        int sent = 0;
        StringIndexer indexer = EnglishWordIndexer.getIndexer();
        Arrays.fill(unigrams, 0);
        Arrays.fill(unigramFertility, 0);
        Arrays.fill(unigramPostFertility, 0);
        Arrays.fill(sumFertility, 0);
        System.out.println("Building LanguageModel...");
        for (List<String> sentence : sentenceCollection) {
            sent++;
            if (sent % 1000000 == 0) System.out.println("On sentence " + sent);
            List<String> stoppedSentence = new ArrayList<String>(sentence);
            stoppedSentence.add(0, START);
            stoppedSentence.add(STOP);
            int curr = 0, prev = 0, prev2;
            int value;
            Integer value2;
            Integer fertility;
            for (int i = 0; i < stoppedSentence.size(); i++) {
                String word = stoppedSentence.get(i);
                prev2 = prev;
                prev = curr;
                curr = indexer.addAndGetIndex(word);
                assert(curr < 1<<21);
                long keyPC = ((long)prev << 21) + curr;
                if (i >= 2) {
                    long key3 = ((long)prev2 << 21) + prev;
                    key3 <<= 21; key3 += curr;
                    if (!trigrams.containsKey(key3)) {
                        long key2P = ((long)prev2 << 21) + prev;
                        bigrams.incrementPostFertility(key2P);
                        bigrams.incrementFertility(keyPC);
                        // expandArray(sumFertility, prev);
                        sumFertility[prev] += 1;
                    }
                    trigrams.increment(key3);
                }
                if (i >= 1) {
                    if (bigrams.getCount(keyPC) == 0) { // TODO: Change to lget
                        // expandArray(unigramFertility, curr);
                        unigramFertility[curr] += 1;
                        // expandArray(unigramPostFertility, prev);
                        unigramPostFertility[prev] += 1;
                    }
                    bigrams.incrementCount(keyPC);
                }
                // expandArray(unigrams, curr);
                unigrams[curr] += 1;
            }
        }
    }

    static int max(int[] values) {
        int max = Integer.MIN_VALUE;
        for(int value : values) {
            if(value > max)
                max = value;
        }
        return max;
    }

    void expandArray(int[] array, int index) {

    }

    public double getNgramLogProbability(int[] ngram, int from, int to) {
        final double D = 0.5d;
        int order = to - from;
        int word3 = ngram[to-1];
        double pUnigram = (double)unigramFertility[word3];
        pUnigram /= (double)(bigrams.bigramTypeCount);

        if (order == 1) {
            if (pUnigram == 0) throw new Error(logProbDump(Double.NEGATIVE_INFINITY, Arrays.copyOfRange(ngram, from, to)));
            double ret = Math.log(pUnigram);
            assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : ret;
            return ret;
        }


        int word2 = ngram[to-2];
        long key = ((long)word2 << 21) + word3;
        int fertility = bigrams.getFertility(key);
        double pBigram = fertility == 0 ? 0 : fertility - D;
        pBigram += D * unigramPostFertility[word2] * pUnigram;
        fertility = sumFertility[word2];
        if (fertility == 0) {
            assert pBigram == 0 : pBigram;
        } else {
            pBigram /= (double)fertility;
        }

        if (pBigram == 0) pBigram = ZERO;
        if (order == 2) {
            double ret = Math.log(pBigram);
            assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : ret;
            return ret;
        }


        int word1 = ngram[to-3];
        key = ((long)word1 << 21) + word2; key <<= 21; key += word3;
        int count = trigrams.get(key);
        double pTrigram = count == 0 ? 0 : (double)(count) - D;

        key = 0; key += word1; key <<= 21; key += word2;
        int fertility2 = bigrams.getPostFertility(key);
        pTrigram += fertility2 == 0 ? 0 : D * fertility2 * pBigram;

        int denominator = bigrams.getCount(key);
        if (denominator == 0) {
            // TODO: Backoff to actual bigram and not fertility bigram
            return Math.log(pBigram);
        } else {
            pTrigram /= (double)denominator;
        }

        if (pTrigram == 0) return LOG_ZERO;
        double ret = Math.log(pTrigram);
        assert !(Double.isNaN(ret) || Double.isInfinite(ret)) && ret <= 0 : logProbDump(
                ret, Arrays.copyOfRange(ngram, from, to)
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
            wordCounts.add(unigrams[ngram[i]]);
        }
        out.append(" ");
        out.append(words.toString());
        out.append(" with counts ");
        out.append(wordCounts.toString());
        out.append("\n");
        return out.toString();
    }

    public long getCount(int[] ngram) {
        int val = 0;
        if (ngram.length > 3) return 0;
        if (ngram.length == 3) {
            long key = ((long)ngram[0] << 21) + ngram[1]; key <<= 21; key += ngram[2];
            val = trigrams.get(key);
        }
        if (ngram.length == 2) {
            long key = ((long)ngram[0] << 21) + ngram[1];
            val = bigrams.getCount(key);
        }
        if (ngram.length == 1) {
            val = unigrams[ngram[0]];
        }
        return val == 0 ? 0 : val;
    }
}

// Following classes are based on Carrot Search Labs HPPC OpenHashMap
// and some methods (such as rehash) are directly copied.
abstract class Counter {
    public final static int DEFAULT_CAPACITY = 16;
    public final static int MIN_CAPACITY = 4;
    public final static float loadFactor = 0.75f;

    public final static long EMPTY = 1 << 63;
    public long[] keys;

    protected int nextCapacity(int current) {
        assert current > 0 && Long.bitCount(current) == 1
                : "Capacity must be a power of two.";
        assert ((current << 1) > 0)
                : "Maximum capacity exceeded (" + (0x80000000 >>> 1) + ").";

        if (current < MIN_CAPACITY / 2) current = MIN_CAPACITY / 2;
        return current << 1;
    }

    static int rehash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int)k;
    }

    abstract protected String valuesAsString(int index);

    public static String keyToString(long key) {
        final long mask = (1 << 21) - 1;
        long R = key & mask; key >>= 21;
        long M = key & mask; key >>= 21;
        return (key & mask) + "_" + M + "_" + R;
    }

    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == EMPTY) continue;
            if (!first) buffer.append(", \n");
            long key = keys[i];
            buffer.append(keyToString(key));
            buffer.append(" => ");
            buffer.append(valuesAsString(i));
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }
}

class TrigramCounter extends Counter {

    public short[] values;
    public int assigned;
    private int resizeThreshold;
    private int lastSlot;

    public TrigramCounter() {
        allocateBuffers(DEFAULT_CAPACITY);
    }

    public final void increment(long key) {
        if (lastSlot >= 0) {
            values[lastSlot] += 1;
            return;
        }

        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                values[slot] += 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        keys[slot] = key;
        values[slot] = 1;
        return;
    }

    private void expandAndRehash() {
        final long[] oldKeys = this.keys;
        final short[] oldValues = this.values;

        assert assigned >= resizeThreshold;
        allocateBuffers(nextCapacity(keys.length));

        final int mask = keys.length - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                final long key = oldKeys[i];
                final short value = oldValues[i];

                int slot = rehash(key) & mask;
                while (keys[slot] != EMPTY) {
                    if (((key) == (keys[slot]))) break;
                    slot = (slot + 1) & mask;
                }

                keys[slot] = key;
                values[slot] = value;
            }
        }

        lastSlot = -1;
    }

    private void allocateBuffers(int capacity) {
        this.keys = new long[capacity];
        Arrays.fill(this.keys, EMPTY);
        this.values = new short[capacity];

        this.resizeThreshold = (int) (capacity * loadFactor);
    }

    public int get(long key) {
        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                return values[slot] & 0xffff;
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }

    public boolean containsKey(long key) {
        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                lastSlot = slot;
                return true;
            }
            slot = (slot + 1) & mask;
        }
        lastSlot = -1;
        return false;
    }

    protected String valuesAsString(int i) {
        return Integer.toString(values[i] & 0xffff);
    }

}

class BigramCounter extends Counter {

    public short[] counts;
    public short[] fertilities;
    public short[] postFertilities;

    public int bigramTypeCount;
    public int assigned;

    private int resizeThreshold;

    public BigramCounter() {
        allocateBuffers(DEFAULT_CAPACITY);
    }

    public final void incrementCount(long key) {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                if (counts[slot] == 0) bigramTypeCount++;
                counts[slot]++;
                return;
            }
            slot = (slot + 1) & mask;
        }

        bigramTypeCount++;
        assigned++;
        keys[slot] = key;
        counts[slot] = 1;
        fertilities[slot] = 0;
        postFertilities[slot] = 0;
    }

    public final void incrementFertility(long key) {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                fertilities[slot] += 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        keys[slot] = key;
        counts[slot] = 0;
        fertilities[slot] = 1;
        postFertilities[slot] = 0;
    }

    public final void incrementPostFertility(long key) {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                postFertilities[slot] += 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        keys[slot] = key;
        counts[slot] = 0;
        fertilities[slot] = 0;
        postFertilities[slot] = 1;
    }

    private void expandAndRehash() {
        final long[] oldKeys = this.keys;
        final short[] oldCounts = this.counts;
        final short[] oldFertilities = this.fertilities;
        final short[] oldPostFertilities = this.postFertilities;

        assert assigned >= resizeThreshold;
        allocateBuffers(nextCapacity(keys.length));

        final int mask = keys.length - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                final long key = oldKeys[i];
                final short count = oldCounts[i];
                final short fertility = oldFertilities[i];
                final short postFertility = oldPostFertilities[i];

                int slot = rehash(key) & mask;
                while (keys[slot] != EMPTY) {
                    if (((key) == (keys[slot]))) break;
                    slot = (slot + 1) & mask;
                }

                keys[slot] = key;
                counts[slot] = count;
                fertilities[slot] = fertility;
                postFertilities[slot] = postFertility;
            }
        }
    }

    private void allocateBuffers(int capacity) {
        this.keys = new long[capacity];
        Arrays.fill(this.keys, EMPTY);
        this.counts = new short[capacity];
        this.fertilities = new short[capacity];
        this.postFertilities = new short[capacity];

        this.resizeThreshold = (int) (capacity * loadFactor);
    }

    public int getCount(long key) {
        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                return counts[slot] & 0xffff;
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }
    public int getFertility(long key) {
        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                return fertilities[slot] & 0xffff;
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }
    public int getPostFertility(long key) {
        final int mask = keys.length - 1;
        int slot = rehash(key) & mask;
        while (keys[slot] != EMPTY) {
            if (((key) == (keys[slot]))) {
                return postFertilities[slot] & 0xffff;
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }

    protected String valuesAsString(int i) {
        return (counts[i] & 0xffff) + ", "
                + (fertilities[i] & 0xffff) + ", "
                + (postFertilities[i] & 0xffff);
    }
}
