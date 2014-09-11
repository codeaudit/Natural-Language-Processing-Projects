package edu.berkeley.nlp.assignments.assign1.student;

import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import java.lang.StringBuffer;
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

    BigramHashMap bigrams = new BigramHashMap();
    LongIntOpenHashMap trigrams = new LongIntOpenHashMap();

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
                if (i >= 2) {
                    long key = 0;
                    key += prev2;
                    key <<= 21;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    if (!trigrams.containsKey(key)) {
                        long key2 = 0;
                        key2 += prev;
                        key2 <<= 21;
                        key2 += curr;
                        bigrams.incrementFertility(key2);

                        // expandArray(sumFertility, prev);
                        sumFertility[prev] += 1;

                        key2 = 0; key2 += prev2; key2 <<= 21; key2 += prev;
                        bigrams.incrementPostFertility(key2);
                    }
                    trigrams.putOrAdd(key, 1, 1);
                }
                if (i >= 1) {
                    long key = 0;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    if (bigrams.getCount(key) == 0) { // TODO: Change to lget
                        // expandArray(unigramFertility, curr);
                        unigramFertility[curr] += 1;
                        // expandArray(unigramPostFertility, prev);
                        unigramPostFertility[prev] += 1;
                    }
                    bigrams.incrementCount(key);
                }
                // expandArray(unigrams, curr);
                unigrams[curr] += 1;
            }
        }
        System.out.println(bigrams.allocated.length);
        System.out.println(trigrams.allocated.length);
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
        final double D = 0.5;
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
        long key = 0; key += word2; key <<= 21; key += word3;
        int fert = bigrams.getFertility(key);
        double pBigram = fert == 0 ? 0 : fert - D;
        pBigram += D * unigramPostFertility[word2] * pUnigram;
        int fertility = sumFertility[word2];
        if (fertility == 0) {
            assert pBigram == 0 : pBigram;
        } else {
            pBigram /= (double)fertility;
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
            val = bigrams.getCount(key);
        }
        if (ngram.length == 1) {
            Integer v = unigrams[ngram[0]];
            val = v == null ? 0 : v.intValue();
        }
        return val == 0 ? 0 : val;
    }
}


// THE FOLLOWING CLASS WAS COPIED FROM CARROT SEARCH LABS HPPC PACKAGE
// AND MODIFIED FROM ITS ORIGINAL FORM FOR THIS APPLICATION.
class LongIntOpenHashMap {
    /**
     * Default capacity.
     */
    public final static int DEFAULT_CAPACITY = 16;

    /**
     * Minimum capacity for the map.
     */
    public final static int MIN_CAPACITY = 4;

    /**
     * Default load factor.
     */
    public final static float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Hash-indexed array holding all keys.
     *
     * @see #values
     */
    public long [] keys;

    /**
     * Hash-indexed array holding all values associated to the keys
     * stored in {@link #keys}.
     *
     * @see #keys
     */
    public int [] values;

    /**
     * Information if an entry (slot) in the {@link #values} table is allocated
     * or empty.
     *
     * @see #assigned
     */
    public boolean [] allocated;

    /**
     * Cached number of assigned slots in {@link #allocated}.
     */
    public int assigned;

    /**
     * The load factor for this map (fraction of allocated slots
     * before the buffers must be rehashed or reallocated).
     */
    public final float loadFactor;

    /**
     * Cached capacity threshold at which we must resize the buffers.
     */
    private int resizeThreshold;

    /**
     * The most recent slot accessed in {@link #containsKey} (required for
     * {@link #lget}).
     *
     * @see #containsKey
     * @see #lget
     */
    private int lastSlot;

    /**
     * Creates a hash map with the default capacity of {@value #DEFAULT_CAPACITY},
     * load factor of {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     */
    public LongIntOpenHashMap()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a hash map with the given initial capacity, default load factor of
     * {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     */
    public LongIntOpenHashMap(int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a hash map with the given initial capacity,
     * load factor.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     *
     * @param loadFactor The load factor (greater than zero and smaller than 1).
     */
    public LongIntOpenHashMap(int initialCapacity, float loadFactor)
    {
        initialCapacity = Math.max(initialCapacity, MIN_CAPACITY);

        assert initialCapacity == DEFAULT_CAPACITY
                : "Initial capacity not supported";
        assert loadFactor > 0 && loadFactor <= 1
                : "Load factor must be between (0, 1].";

        this.loadFactor = loadFactor;
        allocateBuffers(roundCapacity(initialCapacity));
    }

    public int put(long key, int value)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                final int oldValue = values[slot];
                values[slot] = value;
                return oldValue;
            }

            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        values[slot] = value;
        return ((int) 0);
    }

    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. An equivalent
     * of the following code:
     * <pre>
     * if (map.containsKey(key))
     *    map.lset(map.lget() + additionValue);
     * else
     *    map.put(key, putValue);
     * </pre>
     *
     * @param key The key of the value to adjust.
     * @param putValue The value to put if <code>key</code> does not exist.
     * @param additionValue The value to add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
    public final int putOrAdd(long key, int putValue, int additionValue)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return values[slot] += additionValue;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        int v = values[slot] = putValue;

        return v;
    }


    /**
     * Expand the internal storage buffers (capacity) or rehash current
     * keys and values if there are a lot of deleted slots.
     */
    private void expandAndRehash()
    {
        final long [] oldKeys = this.keys;
        final int [] oldValues = this.values;
        final boolean [] oldStates = this.allocated;

        assert assigned >= resizeThreshold;
        allocateBuffers(nextCapacity(keys.length));

        /*
         * Rehash all assigned slots from the old hash table. Deleted
         * slots are discarded.
         */
        final int mask = allocated.length - 1;
        for (int i = 0; i < oldStates.length; i++)
        {
            if (oldStates[i])
            {
                final long key = oldKeys[i];
                final int value = oldValues[i];

                /*  */
                /*  */

                int slot = rehash(key) & mask;
                while (allocated[slot])
                {
                    if (((key) == (keys[slot])))
                    {
                        break;
                    }
                    slot = (slot + 1) & mask;
                }

                allocated[slot] = true;
                keys[slot] = key;
                values[slot] = value;
            }
        }

        /*
         * The number of assigned items does not change, the number of deleted
         * items is zero since we have resized.
         */
        lastSlot = -1;
    }

    /**
     * Allocate internal buffers for a given capacity.
     *
     * @param capacity New capacity (must be a power of two).
     */
    private void allocateBuffers(int capacity)
    {
        this.keys = new long [capacity];
        this.values = new int [capacity];
        this.allocated = new boolean [capacity];

        this.resizeThreshold = (int) (capacity * loadFactor);
    }

    public int remove(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;

        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                assigned--;
                int v = values[slot];
                shiftConflictingKeys(slot);
                return v;
            }
            slot = (slot + 1) & mask;
        }

        return ((int) 0);
    }

    /**
     * Shift all the slot-conflicting keys allocated to (and including) <code>slot</code>.
     */
    protected final void shiftConflictingKeys(int slotCurr)
    {
        // Copied nearly verbatim from fastutil's impl.
        final int mask = allocated.length - 1;
        int slotPrev, slotOther;
        while (true)
        {
            slotCurr = ((slotPrev = slotCurr) + 1) & mask;

            while (allocated[slotCurr])
            {
                slotOther = rehash(keys[slotCurr]) & mask;
                if (slotPrev <= slotCurr)
                {
                    // we're on the right of the original slot.
                    if (slotPrev >= slotOther || slotOther > slotCurr)
                        break;
                }
                else
                {
                    // we've wrapped around.
                    if (slotPrev >= slotOther && slotOther > slotCurr)
                        break;
                }
                slotCurr = (slotCurr + 1) & mask;
            }

            if (!allocated[slotCurr])
                break;

            // Shift key/value pair.
            keys[slotPrev] = keys[slotCurr];
            values[slotPrev] = values[slotCurr];
        }

        allocated[slotPrev] = false;

        /*  */
        /*  */
    }

    public int get(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return values[slot];
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }

    public int lget()
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        return values[lastSlot];
    }

    public int lset(int key)
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        final int previous = values[lastSlot];
        values[lastSlot] = key;
        return previous;
    }

    public boolean containsKey(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                lastSlot = slot;
                return true;
            }
            slot = (slot + 1) & mask;
        }
        lastSlot = -1;
        return false;
    }

    /**
     * Round the capacity to the next allowed value.
     */
    protected int roundCapacity(int requestedCapacity)
    {
        // Maximum positive integer that is a power of two.
        if (requestedCapacity > (0x80000000 >>> 1))
            return (0x80000000 >>> 1);

        return Math.max(MIN_CAPACITY, requestedCapacity);
    }

    /**
     * Return the next possible capacity, counting from the current buffers'
     * size.
     */
    protected int nextCapacity(int current)
    {
        assert current > 0 && Long.bitCount(current) == 1
                : "Capacity must be a power of two.";
        assert ((current << 1) > 0)
                : "Maximum capacity exceeded (" + (0x80000000 >>> 1) + ").";

        if (current < MIN_CAPACITY / 2) current = MIN_CAPACITY / 2;
        return current << 1;
    }

    public void clear()
    {
        assigned = 0;

        // States are always cleared.
        Arrays.fill(allocated, false);

    }

    public int size()
    {
        return assigned;
    }

    static int rehash(long k)
    {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int)k;
    }

    public String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (int i = 0; i < allocated.length; i++)
        {
            if (!allocated[i]) continue;
            if (!first) buffer.append(", ");
            buffer.append(keys[i]);
            buffer.append("=>");
            buffer.append(values[i]);
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }
}

class BigramHashMap {
    /**
     * Default capacity.
     */
    public final static int DEFAULT_CAPACITY = 16;

    /**
     * Minimum capacity for the map.
     */
    public final static int MIN_CAPACITY = 4;

    /**
     * Default load factor.
     */
    public final static float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Hash-indexed array holding all keys.
     *
     * @see #values
     */
    public long [] keys;

    /**
     * Hash-indexed array holding all values associated to the keys
     * stored in {@link #keys}.
     *
     * @see #keys
     */
    public int [] counts;
    public int [] fertilities;
    public int [] postFertilities;

    public int bigramTypeCount;

    /**
     * Information if an entry (slot) in the {@link #values} table is allocated
     * or empty.
     *
     * @see #assigned
     */
    public boolean [] allocated;

    /**
     * Cached number of assigned slots in {@link #allocated}.
     */
    public int assigned;

    /**
     * The load factor for this map (fraction of allocated slots
     * before the buffers must be rehashed or reallocated).
     */
    public final float loadFactor;

    /**
     * Cached capacity threshold at which we must resize the buffers.
     */
    private int resizeThreshold;

    /**
     * The most recent slot accessed in {@link #containsKey} (required for
     * {@link #lget}).
     *
     * @see #containsKey
     * @see #lget
     */
    private int lastSlot;

    /**
     * Creates a hash map with the default capacity of {@value #DEFAULT_CAPACITY},
     * load factor of {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     */
    public BigramHashMap()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a hash map with the given initial capacity, default load factor of
     * {@value #DEFAULT_LOAD_FACTOR}.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     */
    public BigramHashMap(int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a hash map with the given initial capacity,
     * load factor.
     *
     * <p>See class notes about hash distribution importance.</p>
     *
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     *
     * @param loadFactor The load factor (greater than zero and smaller than 1).
     */
    public BigramHashMap(int initialCapacity, float loadFactor)
    {
        initialCapacity = Math.max(initialCapacity, MIN_CAPACITY);

        assert initialCapacity == DEFAULT_CAPACITY
                : "Initial capacity not supported";
        assert loadFactor > 0 && loadFactor <= 1
                : "Load factor must be between (0, 1].";

        this.loadFactor = loadFactor;
        allocateBuffers(roundCapacity(initialCapacity));
    }

    public final void incrementCount(long key)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                final int v = counts[slot];
                if (v == 0) bigramTypeCount++;
                counts[slot] = v + 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        bigramTypeCount++;
        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        counts[slot] = 1;
        fertilities[slot] = 0;
        postFertilities[slot] = 0;
    }

    public final void incrementFertility(long key)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                fertilities[slot] += 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        counts[slot] = 0;
        fertilities[slot] = 1;
        postFertilities[slot] = 0;
    }

    public final void incrementPostFertility(long key)
    {
        if (assigned >= resizeThreshold)
            expandAndRehash();

        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                postFertilities[slot] += 1;
                return;
            }
            slot = (slot + 1) & mask;
        }

        assigned++;
        allocated[slot] = true;
        keys[slot] = key;
        counts[slot] = 0;
        fertilities[slot] = 0;
        postFertilities[slot] = 1;
    }


    /**
     * Expand the internal storage buffers (capacity) or rehash current
     * keys and values if there are a lot of deleted slots.
     */
    private void expandAndRehash()
    {
        final long [] oldKeys = this.keys;
        final int [] oldCounts = this.counts;
        final int [] oldFertilities = this.fertilities;
        final int [] oldPostFertilities = this.postFertilities;
        final boolean [] oldStates = this.allocated;

        assert assigned >= resizeThreshold;
        allocateBuffers(nextCapacity(keys.length));

        /*
         * Rehash all assigned slots from the old hash table. Deleted
         * slots are discarded.
         */
        final int mask = allocated.length - 1;
        for (int i = 0; i < oldStates.length; i++)
        {
            if (oldStates[i])
            {
                final long key = oldKeys[i];
                final int count = oldCounts[i];
                final int fertility = oldFertilities[i];
                final int postFertility = oldPostFertilities[i];

                int slot = rehash(key) & mask;
                while (allocated[slot])
                {
                    if (((key) == (keys[slot])))
                    {
                        break;
                    }
                    slot = (slot + 1) & mask;
                }

                allocated[slot] = true;
                keys[slot] = key;
                counts[slot] = count;
                fertilities[slot] = fertility;
                postFertilities[slot] = postFertility;
            }
        }

        /*
         * The number of assigned items does not change, the number of deleted
         * items is zero since we have resized.
         */
        lastSlot = -1;
    }

    /**
     * Allocate internal buffers for a given capacity.
     *
     * @param capacity New capacity (must be a power of two).
     */
    private void allocateBuffers(int capacity)
    {
        this.keys = new long [capacity];
        this.counts = new int [capacity];
        this.fertilities = new int [capacity];
        this.postFertilities = new int [capacity];
        this.allocated = new boolean [capacity];

        this.resizeThreshold = (int) (capacity * loadFactor);
    }

    protected final void shiftConflictingKeys(int slotCurr)
    {
        // Copied nearly verbatim from fastutil's impl.
        final int mask = allocated.length - 1;
        int slotPrev, slotOther;
        while (true)
        {
            slotCurr = ((slotPrev = slotCurr) + 1) & mask;

            while (allocated[slotCurr])
            {
                slotOther = rehash(keys[slotCurr]) & mask;
                if (slotPrev <= slotCurr)
                {
                    // we're on the right of the original slot.
                    if (slotPrev >= slotOther || slotOther > slotCurr)
                        break;
                }
                else
                {
                    // we've wrapped around.
                    if (slotPrev >= slotOther && slotOther > slotCurr)
                        break;
                }
                slotCurr = (slotCurr + 1) & mask;
            }

            if (!allocated[slotCurr])
                break;

            // Shift key/value pair.
            keys[slotPrev] = keys[slotCurr];
            counts[slotPrev] = counts[slotCurr];
            fertilities[slotPrev] = fertilities[slotCurr];
            postFertilities[slotPrev] = postFertilities[slotCurr];
        }

        allocated[slotPrev] = false;

    }

    public int getCount(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return counts[slot];
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }
    public int getFertility(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return fertilities[slot];
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }
    public int getPostFertility(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                return postFertilities[slot];
            }

            slot = (slot + 1) & mask;
        }
        return ((int) 0);
    }

    public int lget()
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        return counts[lastSlot];
    }

    public int lset(int key)
    {
        assert lastSlot >= 0 : "Call containsKey() first.";
        assert allocated[lastSlot] : "Last call to exists did not have any associated value.";

        final int previous = counts[lastSlot];
        counts[lastSlot] = key;
        return previous;
    }

    public boolean containsKey(long key)
    {
        final int mask = allocated.length - 1;
        int slot = rehash(key) & mask;
        while (allocated[slot])
        {
            if (((key) == (keys[slot])))
            {
                lastSlot = slot;
                return true;
            }
            slot = (slot + 1) & mask;
        }
        lastSlot = -1;
        return false;
    }

    /**
     * Round the capacity to the next allowed value.
     */
    protected int roundCapacity(int requestedCapacity)
    {
        // Maximum positive integer that is a power of two.
        if (requestedCapacity > (0x80000000 >>> 1))
            return (0x80000000 >>> 1);

        return Math.max(MIN_CAPACITY, requestedCapacity);
    }

    /**
     * Return the next possible capacity, counting from the current buffers'
     * size.
     */
    protected int nextCapacity(int current)
    {
        assert current > 0 && Long.bitCount(current) == 1
                : "Capacity must be a power of two.";
        assert ((current << 1) > 0)
                : "Maximum capacity exceeded (" + (0x80000000 >>> 1) + ").";

        if (current < MIN_CAPACITY / 2) current = MIN_CAPACITY / 2;
        return current << 1;
    }

    public int size()
    {
        return assigned;
    }

    static int rehash(long k)
    {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int)k;
    }

    public String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (int i = 0; i < allocated.length; i++)
        {
            if (!allocated[i]) continue;
            if (!first) buffer.append(",\n");
            buffer.append(keys[i]);
            buffer.append("=>");
            buffer.append(counts[i]);
            buffer.append(", ");
            buffer.append(fertilities[i]);
            buffer.append(", ");
            buffer.append(postFertilities[i]);
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }
}
