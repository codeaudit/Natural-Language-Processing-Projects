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

    LongIntOpenHashMap trigrams = new LongIntOpenHashMap();
    LongIntOpenHashMap bigrams = new LongIntOpenHashMap();
    HashMap<Integer, Integer> unigrams = new HashMap<Integer, Integer>();
    // Fertility refers to N1+(*, ngram)
    HashMap<Integer, Integer> unigramFertility = new HashMap<Integer, Integer>();
    LongIntOpenHashMap bigramFertility = new LongIntOpenHashMap();
    // This one is N1+(ngram, *)
    HashMap<Integer, Integer> unigramPostFertility = new HashMap<Integer, Integer>();
    LongIntOpenHashMap bigramPostFertility = new LongIntOpenHashMap();
    // This one is \Sum_w(N1+(*, key, w))
    HashMap<Integer, Integer> sumFertility = new HashMap<Integer, Integer>();

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
                        bigramFertility.putOrAdd(key2, 1, 1);

                        fertility = sumFertility.get(new Integer(prev));
                        sumFertility.put(new Integer(prev), fertility == null ? new Integer(1) : new Integer(fertility + 1));

                        key2 = 0; key2 += prev2; key2 <<= 21; key2 += prev;
                        bigramPostFertility.putOrAdd(key2, 1, 1);
                    }
                    trigrams.putOrAdd(key, 1, 1);
                }
                if (i >= 1) {
                    long key = 0;
                    key += prev;
                    key <<= 21;
                    key += curr;
                    if (!bigrams.containsKey(key)) {
                        fertility = unigramFertility.get(new Integer(curr));
                        unigramFertility.put(new Integer(curr), fertility == null ? new Integer(1) : new Integer(fertility + 1));
                        fertility = unigramPostFertility.get(new Integer(prev));
                        unigramPostFertility.put(new Integer(prev), fertility == null ? new Integer(1) : new Integer(fertility + 1));
                    }
                    bigrams.putOrAdd(key, 1, 1);
                }
                value2 = unigrams.get(new Integer(curr));
                unigrams.put(new Integer(curr), value2 == null ? new Integer(1) : new Integer(value2 + 1));
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
        int fert = bigramFertility.get(key);
        double pBigram = fert == 0 ? 0 : fert - D;
        fertility = unigramPostFertility.get(new Integer(word2));
        pBigram += fertility == null ? 0 : D * fertility.doubleValue() * pUnigram;
        fertility = sumFertility.get(new Integer(word2));
        if (fertility == null) {
            assert pBigram == 0 : pBigram;
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
        int count = trigrams.get(key);
        double pTrigram = count == 0 ? 0 : (double)(count) - D;

        key = 0; key += word1; key <<= 21; key += word2;
        int fertility2 = bigramPostFertility.get(key);
        pTrigram += fertility2 == 0 ? 0 : D * fertility2 * pBigram;

        int denominator = bigrams.get(key);
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
            val = bigrams.get(key);
        }
        if (ngram.length == 1) {
            Integer v = unigrams.get(new Integer(ngram[0]));
            val = v == null ? 0 : v.intValue();
        }
        return val == 0 ? 0 : val;
    }
}

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
