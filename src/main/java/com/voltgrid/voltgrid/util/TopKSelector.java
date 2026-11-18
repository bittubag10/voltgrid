package com.voltgrid.voltgrid.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class TopKSelector {

    private TopKSelector() {
    }

    /**
     * Returns the k smallest items (by the comparator), sorted ascending.
     * Uses a bounded max-heap of size k, so it runs in O(n log k) instead of O(n log n).
     */
    public static <T> List<T> topK(Collection<T> items, int k, Comparator<? super T> comparator) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }

        // Max-heap: the worst of the current best-k sits on top, ready to be dropped
        PriorityQueue<T> heap = new PriorityQueue<>(k + 1, comparator.reversed());

        for (T item : items) {
            heap.offer(item);
            if (heap.size() > k) {
                heap.poll();
            }
        }

        List<T> result = new ArrayList<>(heap);
        result.sort(comparator);
        return result;
    }
}