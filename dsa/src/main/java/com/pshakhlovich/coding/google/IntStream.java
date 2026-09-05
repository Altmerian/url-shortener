package com.pshakhlovich.coding.google;

interface IntStream {
    /**
     * Returns true if the stream has more elements, false otherwise.
     */
    boolean hasNext();

    /**
     * Returns the next element in the stream.
     * Unordered positive distinct integers are returned.
     * If there are no more elements, throws NoSuchElementException.
     */
    int next();
}
