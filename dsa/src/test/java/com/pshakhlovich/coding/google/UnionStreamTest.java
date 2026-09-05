package com.pshakhlovich.coding.google;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class UnionStreamTest {

    /**
     * Array-backed IntStream honouring the contract: hasNext/next, NoSuchElementException at end.
     */
    private static IntStream of(int... values) {
        return new IntStream() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < values.length;
            }

            @Override
            public int next() {
                if (i >= values.length) throw new NoSuchElementException();
                return values[i++];
            }
        };
    }

    /**
     * Drains via hasNext()/next() and returns a sorted list — order is unspecified.
     */
    private static List<Integer> drain(UnionStream u) {
        List<Integer> out = new ArrayList<>();
        while (u.hasNext()) out.add(u.next());
        out.sort(null);
        return out;
    }

    /**
     * Drains using next() only, relying on the exception to stop.
     */
    private static List<Integer> drainNextOnly(UnionStream u) {
        List<Integer> out = new ArrayList<>();
        try {
            while (true) out.add(u.next());
        } catch (NoSuchElementException expected) {
            // end of stream
        }
        out.sort(null);
        return out;
    }

    @Test
    void bothEmpty() {
        UnionStream u = new UnionStream(of(), of());
        assertFalse(u.hasNext());
        assertThrows(NoSuchElementException.class, u::next);
    }

    @Test
    void firstEmpty() {
        assertEquals(List.of(1, 2, 3), drain(new UnionStream(of(), of(3, 1, 2))));
    }

    @Test
    void secondEmpty() {
        assertEquals(List.of(1, 2, 3), drain(new UnionStream(of(2, 3, 1), of())));
    }

    @Test
    void disjoint() {
        assertEquals(List.of(1, 2, 3, 4), drain(new UnionStream(of(1, 2), of(3, 4))));
    }

    @Test
    void partialOverlap() {
        assertEquals(List.of(1, 2, 3, 4, 5), drain(new UnionStream(of(1, 3, 5), of(4, 3, 2, 1))));
    }

    @Test
    void fullOverlap_secondContributesNothing() {
        // Every element of s2 is a duplicate: hasNext() must report false after s1 is drained.
        UnionStream u = new UnionStream(of(1, 2), of(2, 1));
        assertEquals(List.of(1, 2), drain(u));
        assertFalse(u.hasNext());
    }

    @Test
    void trailingDuplicatesInSecond() {
        // The crux of the follow-up: s2 still has elements, but none are emittable.
        UnionStream u = new UnionStream(of(1, 2), of(3, 1, 2));
        assertEquals(1, u.next());
        assertEquals(2, u.next());
        assertTrue(u.hasNext());
        assertEquals(3, u.next());
        assertFalse(u.hasNext());             // s2 has [1, 2] left, both seen
        assertThrows(NoSuchElementException.class, u::next);
    }

    @Test
    void nextOnly_noHasNextCalls() {
        // Catches the classic double-consume bug in next().
        assertEquals(List.of(1, 2), drainNextOnly(new UnionStream(of(1), of(2))));
        assertEquals(List.of(1, 2), drainNextOnly(new UnionStream(of(1), of(2, 1))));
        assertEquals(List.of(1, 2, 3, 4), drainNextOnly(new UnionStream(of(1), of(2, 3, 4))));
    }

    @Test
    void hasNextIsIdempotent() {
        UnionStream u = new UnionStream(of(1), of(1, 7));
        assertEquals(1, u.next());
        // Repeated hasNext() must not consume or lose the buffered element.
        assertTrue(u.hasNext());
        assertTrue(u.hasNext());
        assertTrue(u.hasNext());
        assertEquals(7, u.next());
        assertFalse(u.hasNext());
        assertFalse(u.hasNext());
    }

    @Test
    void exhaustedStreamStaysExhausted() {
        UnionStream u = new UnionStream(of(1), of(2));
        drain(u);
        assertFalse(u.hasNext());
        assertThrows(NoSuchElementException.class, u::next);
        assertThrows(NoSuchElementException.class, u::next);
    }
}