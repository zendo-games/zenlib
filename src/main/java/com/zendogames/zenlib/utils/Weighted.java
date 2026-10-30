package com.zendogames.zenlib.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Items with weights for a random pick: {@link #pick()} rolls {@code [0, total]} and returns the
 * first item whose cumulative weight reaches the roll, so an item weighing 70 of a total 100 comes
 * up 70% of the time. Weights are any positive floats and need not sum to 1 or 100. Built once
 * (a static field, a constructor), read many times; nothing allocates in {@code pick}.
 */
public final class Weighted<T> {

    private final Array<T> items = new Array<>();
    private final FloatArray weights = new FloatArray();
    private float total;

    public Weighted<T> add(T item, float weight) {
        if (weight <= 0) throw new GdxRuntimeException("weight must be positive: %s for %s".formatted(weight, item));
        items.add(item);
        weights.add(weight);
        total += weight;
        return this;
    }

    public T pick() {
        return pick(MathUtils.random(total));
    }

    /** The item at {@code roll} along the cumulative weights, {@code 0 <= roll <= total()}. */
    public T pick(float roll) {
        if (items.isEmpty()) throw new GdxRuntimeException("pick from an empty Weighted");
        if (roll < 0 || roll > total) throw new GdxRuntimeException("roll %s outside [0, %s]".formatted(roll, total));
        var cumulative = 0f;
        for (int i = 0; i < items.size; i++) {
            cumulative += weights.get(i);
            if (roll <= cumulative) return items.get(i);
        }
        return items.peek(); // float rounding left the roll a hair past the last cumulative sum
    }

    public float total()       { return total; }
    public int   size()        { return items.size; }
    public T     get(int i)    { return items.get(i); }
    public float weight(int i) { return weights.get(i); }
}
