package com.pshakhlovich.coding.google;


import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

public class UnionStream implements IntStream {

    // Contract guarantees strictly positive values, so -1 is safely outside the domain.
    private static final int EMPTY = -1;

    private final IntStream s1;
    private final IntStream s2;

    private final Set<Integer> seen = new HashSet<>();

    // Look-ahead for phase 2: the next unseen element of s2, already pulled out of s2.
    // Invariant: non-empty only after s1 is exhausted.
    private int buffered = EMPTY;

    public UnionStream(IntStream s1, IntStream s2) {
        this.s1 = s1;
        this.s2 = s2;
    }

    @Override
    public boolean hasNext() {
        // Phase 1 still running, or phase 2 already has an element parked.
        if (s1.hasNext() || buffered != EMPTY) {
            return true;
        }
        // Phase 2: advance s2 until an unseen element appears, then park it.
        while (s2.hasNext()) {
            int candidate = s2.next();
            if (!seen.contains(candidate)) {
                buffered = candidate;
                return true;
            }
        }
        return false;
    }

    @Override
    public int next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        // hasNext() returned true, so exactly one of the two holds:
        if (buffered != EMPTY) {           // (a) phase 2, element already parked
            int result = buffered;
            buffered = EMPTY;
            return result;
        }
        int result = s1.next();            // (b) phase 1, s1 still has elements
        seen.add(result);
        return result;
    }


}
