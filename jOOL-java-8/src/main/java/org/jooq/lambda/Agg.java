/**
 * Copyright (c), Data Geekery GmbH, contact@datageekery.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jooq.lambda;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.collectingAndThen;
import static org.jooq.lambda.tuple.Tuple.tuple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

/**
 * A set of additional {@link Collector} implementations.
 * <p>
 * The class name isn't set in stone and will change.
 *
 * @author Lukas Eder
 */
public class Agg {

    /**
     * Get a {@link Collector} that filters data passed to downstream collector.
     */
    public static <T, A, R> Collector<T, ?, R> filter(Predicate<? super T> predicate, Collector<T, A, R> downstream) {
        return Collector.of(
            downstream.supplier(),
            (c, t) -> {
                if (predicate.test(t))
                    downstream.accumulator().accept(c, t);
            },
            downstream.combiner(),
            downstream.finisher()
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>FIRST</code> function.
     * <p>
     * Note that unlike in (Oracle) SQL, where the <code>FIRST</code> function
     * is an ordered set aggregate function that produces a set of results, this
     * collector just produces the first value in the order of stream traversal.
     * For matching behaviour to Oracle's [ aggregate function ] KEEP
     * (DENSE_RANK FIRST ORDER BY ... ), use {@link #maxAll(Comparator)} instead.
     */
    public static <T> Collector<T, ?, Optional<T>> first() {
        return Collectors.reducing((v1, v2) -> v1);
    }

    /**
     * Get a {@link Collector} that calculates the <code>LAST</code> function.
     * <p>
     * Note that unlike in (Oracle) SQL, where the <code>FIRST</code> function
     * is an ordered set aggregate function that produces a set of results, this
     * collector just produces the first value in the order of stream traversal.
     * For matching behaviour to Oracle's [ aggregate function ] KEEP
     * (DENSE_RANK LAST ORDER BY ... ), use {@link #minAll(Comparator)} instead.
     */
    public static <T> Collector<T, ?, Optional<T>> last() {
        return Collectors.reducing((v1, v2) -> v2);
    }

    /**
     * Get a {@link Collector} that takes the first <code>n</code> elements
     * from a collection.
     */
    public static <T> Collector<T, ?, Seq<T>> taking(long n) {
        return Collector.of(
            () -> new ArrayList<T>(),
            (l, v) -> {
                if (l.size() < n)
                    l.add(v);
            },
            (l1, l2) -> {
                l1.addAll(l2);
                return l1;
            },
            l -> Seq.seq(l)
        );
    }

    /**
     * Get a {@link Collector} that skip the first <code>n</code> elements of a
     * collection.
     */
    public static <T> Collector<T, ?, Seq<T>> dropping(long n) {
        class Accumulator {
            List<T> list = new ArrayList<>();
            long index;
        }

        return Collector.of(
            Accumulator::new,
            (a, v) -> {
                if (a.index >= n)
                    a.list.add(v);
                else
                    a.index++;
            },
            (a1, a2) -> {
                a1.list.addAll(a2.list);
                a1.index += a2.index;
                return a1;
            },
            a -> Seq.seq(a.list)
        );
    }


    /**
     * Get a {@link Collector} that calculates the <code>COUNT(*)</code>
     * function.
     */
    public static <T> Collector<T, ?, Long> count() {
        return Collectors.counting();
    }

    /**
     * Get a {@link Collector} that calculates the
     * <code>COUNT (DISTINCT *)</code> function.
     */
    public static <T> Collector<T, ?, Long> countDistinct() {
        return countDistinctBy(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the
     * <code>COUNT (DISTINCT expr)</code> function.
     */
    public static <T, U> Collector<T, ?, Long> countDistinctBy(Function<? super T, ? extends U> function) {
        return Collector.of(
            () -> new HashSet<U>(),
            (s, v) -> s.add(function.apply(v)),
            (s1, s2) -> {
                s1.addAll(s2);
                return s1;
            },
            s -> (long) s.size()
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>SUM()</code> for any
     * type of {@link Number}.
     */
    public static <T> Collector<T, ?, Optional<T>> sum() {
        return sum(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>SUM()</code> for any
     * type of {@link Number}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T, U> Collector<T, ?, Optional<U>> sum(Function<? super T, ? extends U> function) {
        return Collector.of(() -> (Sum<U>[]) new Sum[1],
            (s, v) -> {
                if (s[0] == null)
                    s[0] = Sum.create(function.apply(v));
                else
                    s[0].add(function.apply(v));
            },
            (s1, s2) -> {
                s1[0].add(s2[0]);
                return s1;
            },
            s -> s[0] == null ? Optional.empty() : Optional.of(s[0].result())
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>AVG()</code> for any
     * type of {@link Number}.
     */
    public static <T> Collector<T, ?, Optional<T>> avg() {
        return avg(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>AVG()</code> for any
     * type of {@link Number}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T, U> Collector<T, ?, Optional<U>> avg(Function<? super T, ? extends U> function) {
        return Collector.of(
            () -> (Sum<U>[]) new Sum[1],
            (s, v) -> {
                if (s[0] == null)
                    s[0] = Sum.create(function.apply(v));
                else
                    s[0].add(function.apply(v));
            },
            (s1, s2) -> {
                s1[0].add(s2[0]);
                return s1;
            },
            s -> s[0] == null ? Optional.empty() : Optional.of(s[0].avg())
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<T>> min() {
        return minBy(t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T> Collector<T, ?, Optional<T>> min(Comparator<? super T> comparator) {
        return minBy(t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<U>> min(Function<? super T, ? extends U> function) {
        return min(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T, U> Collector<T, ?, Optional<U>> min(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(minBy(function, comparator), t -> t.map(function));
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<T>> minBy(Function<? super T, ? extends U> function) {
        return minBy(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function.
     */
    public static <T, U> Collector<T, ?, Optional<T>> minBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return maxBy(function, comparator.reversed());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Seq<T>> minAll() {
        return minAllBy(t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T> Collector<T, ?, Seq<T>> minAll(Comparator<? super T> comparator) {
        return minAllBy(t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Seq<U>> minAll(Function<? super T, ? extends U> function) {
        return minAll(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T, U> Collector<T, ?, Seq<U>> minAll(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(minAllBy(function, comparator), t -> t.map(function));
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Seq<T>> minAllBy(Function<? super T, ? extends U> function) {
        return minAllBy(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MIN()</code> function, producing multiple results.
     */
    public static <T, U> Collector<T, ?, Seq<T>> minAllBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return maxAllBy(function, comparator.reversed());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<T>> max() {
        return maxBy(t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T> Collector<T, ?, Optional<T>> max(Comparator<? super T> comparator) {
        return maxBy(t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<U>> max(Function<? super T, ? extends U> function) {
        return max(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T, U> Collector<T, ?, Optional<U>> max(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(maxBy(function, comparator), t -> t.map(function));
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<T>> maxBy(Function<? super T, ? extends U> function) {
        return maxBy(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function.
     */
    public static <T, U> Collector<T, ?, Optional<T>> maxBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        class Accumulator {
            T t;
            U u;
        }

        return Collector.of(
            () -> new Accumulator(),
            (a, t) -> {
                U u = function.apply(t);

                if (a.u == null || comparator.compare(a.u, u) < 0) {
                    a.t = t;
                    a.u = u;
                }
            },
            (a1, a2) ->
                  a1.u == null
                ? a2
                : a2.u == null
                ? a1
                : comparator.compare(a1.u, a2.u) < 0
                ? a2
                : a1,
            a -> Optional.ofNullable(a.t)
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Seq<T>> maxAll() {
        return maxAllBy(t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T> Collector<T, ?, Seq<T>> maxAll(Comparator<? super T> comparator) {
        return maxAllBy(t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Seq<U>> maxAll(Function<? super T, ? extends U> function) {
        return maxAll(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T, U> Collector<T, ?, Seq<U>> maxAll(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(maxAllBy(function, comparator), t -> t.map(function));
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Seq<T>> maxAllBy(Function<? super T, ? extends U> function) {
        return maxAllBy(function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MAX()</code> function, producing multiple results.
     */
    public static <T, U> Collector<T, ?, Seq<T>> maxAllBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        class Accumulator {
            List<T> t = new ArrayList<>();
            U u;
            void set(T t, U u) {
                this.t.clear();
                this.t.add(t);
                this.u = u;
            }
        }

        return Collector.of(
            () -> new Accumulator(),
            (a, t) -> {
                U u = function.apply(t);
                if (a.u == null) {
                    a.set(t, u);
                }
                else {
                    int compare = comparator.compare(a.u, u);

                    if (compare < 0)
                        a.set(t, u);
                    else if (compare == 0)
                        a.t.add(t);
                }
            },
            (a1, a2) -> {
                if (a1.u == null)
                    return a2;
                if (a2.u == null)
                    return a1;

                int compare = comparator.compare(a1.u, a2.u);

                if (compare < 0)
                    return a1;
                else if (compare > 0)
                    return a2;

                a1.t.addAll(a2.t);
                return a1;
            },
            a -> Seq.seq(a.t)
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>ALL()</code> function.
     */
    public static Collector<Boolean, ?, Boolean> allMatch() {
        return allMatch(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>ALL()</code> function.
     */
    public static <T> Collector<T, ?, Boolean> allMatch(Predicate<? super T> predicate) {
        return Collector.of(
            () -> new Boolean[1],
            (a, t) -> {
                if (a[0] == null)
                    a[0] = predicate.test(t);
                else
                    a[0] = a[0] && predicate.test(t);
            },
            (a1, a2) -> {
                a1[0] = a1[0] && a2[0];
                return a1;
            },
            a -> a[0] == null || a[0]
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>ANY()</code> function.
     */
    public static Collector<Boolean, ?, Boolean> anyMatch() {
        return anyMatch(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>ANY()</code> function.
     */
    public static <T> Collector<T, ?, Boolean> anyMatch(Predicate<? super T> predicate) {
        return collectingAndThen(noneMatch(predicate), t -> !t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>NONE()</code> function.
     */
    public static Collector<Boolean, ?, Boolean> noneMatch() {
        return noneMatch(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>NONE()</code> function.
     */
    public static <T> Collector<T, ?, Boolean> noneMatch(Predicate<? super T> predicate) {
        return allMatch(predicate.negate());
    }

    /**
     * Get a {@link Collector} that calculates <code>BIT_AND()</code> for any
     * type of {@link Number}.
     */
    public static <T> Collector<T, ?, Optional<T>> bitAnd() {
        return bitAnd(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_AND()</code> for any
     * type of {@link Number}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T, U> Collector<T, ?, Optional<U>> bitAnd(Function<? super T, ? extends U> function) {
        return Collector.of(() -> (Sum<U>[]) new Sum[1],
            (s, v) -> {
                if (s[0] == null)
                    s[0] = Sum.create(function.apply(v));
                else
                    s[0].and(function.apply(v));
            },
            (s1, s2) -> {
                s1[0].and(s2[0]);
                return s1;
            },
            s -> s[0] == null ? Optional.empty() : Optional.of(s[0].result())
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_AND()</code> for any
     * type of {@link Number}.
     */
    public static <T, U> Collector<T, ?, Integer> bitAndInt(ToIntFunction<? super T> function) {
        return Collector.of(() -> new int[] { Integer.MAX_VALUE },
            (s, v) -> {
                s[0] = s[0] & function.applyAsInt(v);
            },
            (s1, s2) -> {
                s1[0] = s1[0] & s2[0];
                return s1;
            },
            s -> s[0]
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_AND()</code> for any
     * type of {@link Number}.
     */
    public static <T, U> Collector<T, ?, Long> bitAndLong(ToLongFunction<? super T> function) {
        return Collector.of(() -> new long[] { Long.MAX_VALUE },
            (s, v) -> {
                s[0] = s[0] & function.applyAsLong(v);
            },
            (s1, s2) -> {
                s1[0] = s1[0] & s2[0];
                return s1;
            },
            s -> s[0]
        );
    }

    /**
     * Get a {@link Collector} that calculates <code>BIT_OR()</code> for any
     * type of {@link Number}.
     */
    public static <T> Collector<T, ?, Optional<T>> bitOr() {
        return bitOr(t -> t);
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_OR()</code> for any
     * type of {@link Number}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T, U> Collector<T, ?, Optional<U>> bitOr(Function<? super T, ? extends U> function) {
        return Collector.of(() -> (Sum<U>[]) new Sum[1],
            (s, v) -> {
                if (s[0] == null)
                    s[0] = Sum.create(function.apply(v));
                else
                    s[0].or(function.apply(v));
            },
            (s1, s2) -> {
                s1[0].or(s2[0]);
                return s1;
            },
            s -> s[0] == null ? Optional.empty() : Optional.of(s[0].result())
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_OR()</code> for any
     * type of {@link Number}.
     */
    public static <T, U> Collector<T, ?, Integer> bitOrInt(ToIntFunction<? super T> function) {
        return Collector.of(() -> new int[1],
            (s, v) -> {
                s[0] = s[0] | function.applyAsInt(v);
            },
            (s1, s2) -> {
                s1[0] = s1[0] | s2[0];
                return s1;
            },
            s -> s[0]
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>BIT_OR()</code> for any
     * type of {@link Number}.
     */
    public static <T, U> Collector<T, ?, Long> bitOrLong(ToLongFunction<? super T> function) {
        return Collector.of(() -> new long[1],
            (s, v) -> {
                s[0] = s[0] | function.applyAsLong(v);
            },
            (s1, s2) -> {
                s1[0] = s1[0] | s2[0];
                return s1;
            },
            s -> s[0]
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>MODE()</code> function.
     */
    public static <T> Collector<T, ?, Optional<T>> mode() {
        return mode0(seq -> seq.maxBy(t -> t.v2).map(t -> t.v1));
    }

    /**
     * Get a {@link Collector} that calculates the <code>MODE()</code> function.
     */
    public static <T> Collector<T, ?, Seq<T>> modeAll() {
        return mode0(seq -> seq.maxAllBy(t -> t.v2).map(t -> t.v1));
    }

    private static <T, X> Collector<T, ?, X> mode0(Function<? super Seq<Tuple2<T, Long>>, ? extends X> transformer) {
        return Collector.of(
            () -> new LinkedHashMap<T, Long>(),
            (m, v) -> m.compute(v, (k1, v1) -> v1 == null ? 1L : v1 + 1L),
            (m1, m2) -> {
                m1.putAll(m2);
                return m1;
            },
            m -> Seq.seq(m).transform(transformer)
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>MODE()</code> function.
     */
    public static <T, U> Collector<T, ?, Optional<T>> modeBy(Function<? super T, ? extends U> function) {
        return Collectors.collectingAndThen(modeAllBy(function), s -> s.findFirst());
    }

    /**
     * Get a {@link Collector} that calculates the <code>MODE()</code> function.
     */
    public static <T, U> Collector<T, ?, Seq<T>> modeAllBy(Function<? super T, ? extends U> function) {
        return Collector.of(
            () -> new LinkedHashMap<U, List<T>>(),
            (m, t) -> m.compute(function.apply(t), (k, l) -> {
                List<T> result = l != null ? l : new ArrayList<>();
                result.add(t);
                return result;
            }),
            (m1, m2) -> {
                for (Entry<U, List<T>> e : m2.entrySet()) {
                    List<T> l = m1.get(e.getKey());

                    if (l == null)
                        m1.put(e.getKey(), e.getValue());
                    else
                        l.addAll(e.getValue());
                }

                return m1;
            },
            m -> Seq.seq(m).maxAllBy(t -> t.v2.size()).flatMap(t -> Seq.seq(t.v2))
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>RANK()</code> function given natural ordering.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<Long>> rank(T value) {
        return rankBy(value, t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>RANK()</code> function given a specific ordering.
     */
    public static <T> Collector<T, ?, Optional<Long>> rank(T value, Comparator<? super T> comparator) {
        return rankBy(value, t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>RANK()</code> function given natural ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<Long>> rankBy(U value, Function<? super T, ? extends U> function) {
        return rankBy(value, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>RANK()</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<Long>> rankBy(U value, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return Collector.of(
            () -> new long[] { -1L },
            (l, v) -> {
                if (l[0] == -1L)
                    l[0] = 0L;

                if (comparator.compare(value, function.apply(v)) > 0)
                    l[0] = l[0] + 1L;
            },
            (l1, l2) -> {
                l1[0] = (l1[0] == -1 ? 0L : l1[0]) +
                        (l2[0] == -1 ? 0L : l2[0]);

                return l1;
            },
            l -> l[0] == -1 ? Optional.empty() : Optional.of((long) l[0])
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>DENSE_RANK()</code> function given natural ordering.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<Long>> denseRank(T value) {
        return denseRankBy(value, t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>DENSE_RANK()</code> function given a specific ordering.
     */
    public static <T> Collector<T, ?, Optional<Long>> denseRank(T value, Comparator<? super T> comparator) {
        return denseRankBy(value, t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>DENSE_RANK()</code> function given natural ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<Long>> denseRankBy(U value, Function<? super T, ? extends U> function) {
        return denseRankBy(value, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>DENSE_RANK()</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<Long>> denseRankBy(U value, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return Collector.of(
            () -> (TreeSet<U>[]) new TreeSet[1],
            (l, v) -> {
                if (l[0] == null)
                    l[0] = new TreeSet<>(comparator);

                U u = function.apply(v);
                if (comparator.compare(value, u) > 0)
                    l[0].add(u);
            },
            (l1, l2) -> {
                if (l2[0] == null)
                    return l1;
                else if (l1[0] == null)
                    return l2;

                l1[0].addAll(l2[0]);
                return l1;
            },
            l -> l[0] == null ? Optional.empty() : Optional.of((long) l[0].size())
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENT_RANK()</code> function given natural ordering.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<Double>> percentRank(T value) {
        return percentRankBy(value, t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENT_RANK()</code> function given a specific ordering.
     */
    public static <T> Collector<T, ?, Optional<Double>> percentRank(T value, Comparator<? super T> comparator) {
        return percentRankBy(value, t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>PERCENT_RANK()</code> function given natural ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<Double>> percentRankBy(U value, Function<? super T, ? extends U> function) {
        return percentRankBy(value, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>PERCENT_RANK()</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<Double>> percentRankBy(U value, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(
            Tuple.collectors(
                rankBy(value, function, comparator),
                count()),
            t -> t.map((rank, count) -> rank.map(r -> (double) r / count))
        );
    }

    /**
     * Get a {@link Collector} that calculates the <code>MEDIAN()</code> function given natural ordering.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<T>> median() {
        return percentile(0.5);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MEDIAN()</code> function given a specific ordering.
     */
    public static <T> Collector<T, ?, Optional<T>> median(Comparator<? super T> comparator) {
        return percentile(0.5, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MEDIAN()</code> function given a specific ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<U>> median(Function<? super T, ? extends U> function) {
        return percentile(0.5, function);
    }

    /**
     * Get a {@link Collector} that calculates the <code>MEDIAN()</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<U>> median(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return percentile(0.5, function, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>MEDIAN()</code> function given natural ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<T>> medianBy(Function<? super T, ? extends U> function) {
        return percentileBy(0.5, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>MEDIAN()</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<T>> medianBy(Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return percentileBy(0.5, function, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENTILE_DISC(percentile)</code> function given natural ordering.
     */
    public static <T extends Comparable<? super T>> Collector<T, ?, Optional<T>> percentile(double percentile) {
        return percentile(percentile, t -> t, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENTILE_DISC(percentile)</code> function given a specific ordering.
     */
    public static <T> Collector<T, ?, Optional<T>> percentile(double percentile, Comparator<? super T> comparator) {
        return percentile(percentile, t -> t, comparator);
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENTILE_DISC(percentile)</code> function given a specific ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<U>> percentile(double percentile, Function<? super T, ? extends U> function) {
        return percentile(percentile, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the <code>PERCENTILE_DISC(percentile)</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<U>> percentile(double percentile, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        return collectingAndThen(percentileBy(percentile, function, comparator), t -> t.map(function));
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>PERCENTILE_DISC(percentile)</code> function given natural ordering.
     */
    public static <T, U extends Comparable<? super U>> Collector<T, ?, Optional<T>> percentileBy(double percentile, Function<? super T, ? extends U> function) {
        return percentileBy(percentile, function, naturalOrder());
    }

    /**
     * Get a {@link Collector} that calculates the derived <code>PERCENTILE_DISC(percentile)</code> function given a specific ordering.
     */
    public static <T, U> Collector<T, ?, Optional<T>> percentileBy(double percentile, Function<? super T, ? extends U> function, Comparator<? super U> comparator) {
        if (percentile < 0.0 || percentile > 1.0)
            throw new IllegalArgumentException("Percentile must be between 0.0 and 1.0");

        // At a later stage, we'll optimise this implementation in case that function is the identity function
        return Collector.of(
            () -> new ArrayList<Tuple2<T, U>>(),
            (l, v) -> l.add(tuple(v, function.apply(v))),
            (l1, l2) -> {
                l1.addAll(l2);
                return l1;
            },
            l -> {
                int size = l.size();

                if (size == 0)
                    return Optional.empty();
                else if (size == 1)
                    return Optional.of(l.get(0).v1);

                l.sort(Comparator.comparing(t -> t.v2, comparator));

                if (percentile == 0.0)
                    return Optional.of(l.get(0).v1);
                else if (percentile == 1.0)
                    return Optional.of(l.get(size - 1).v1);

                // x.5 should be rounded down
                return Optional.of(l.get((int) -Math.round(-(size * percentile + 0.5)) - 1).v1);
            }
        );
    }

    /**
     * Get a {@link Collector} that calculates the common prefix of a set of strings.
     */
    public static Collector<CharSequence, ?, String> commonPrefix() {
        return Collectors.collectingAndThen(
            Collectors.reducing((CharSequence s1, CharSequence s2) -> {
                if (s1 == null || s2 == null)
                    return "";

                int l = Math.min(s1.length(), s2.length());
                int i;
                for (i = 0; i < l && s1.charAt(i) == s2.charAt(i); i++);

                return s1.subSequence(0, i);
            }),
            s -> s.map(Objects::toString).orElse("")
        );
    }

    /**
     * Get a {@link Collector} that calculates the common suffix of a set of strings.
     */
    public static Collector<CharSequence, ?, String> commonSuffix() {
        return Collectors.collectingAndThen(
            Collectors.reducing((CharSequence s1, CharSequence s2) -> {
                if (s1 == null || s2 == null)
                    return "";

                int l1 = s1.length();
                int l2 = s2.length();
                int l = Math.min(l1, l2);
                int i;
                for (i = 0; i < l && s1.charAt(l1 - i - 1) == s2.charAt(l2 - i - 1); i++);

                return s1.subSequence(l1 - i, l1);
            }),
            s -> s.map(Objects::toString).orElse("")
        );
    }


    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/360
    /**
     * Calculate the variance of objects with mapping function from object to double value,
     * with given list, size and mapping function.
     *
     * <p><pre>
     *     This function is based on the definition of standard deviation:
     *     First, calculate the sum of value of all objects and the average value;
     *     Second, calculate the sum of square of difference between each objects and average;
     *     Third, use sum of variance divide size to obtain variance of all objects.
     * </pre>
     *
     * @param function mapping function from object to double value
     * @param l        a list containing all the objects
     * @param size     the size of the list in collector
     * @return the variance value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-04-25
     */
    private static <T> double getVariance(
            Function<? super T, Double> function,
            ArrayList<T> l, int size) {
        double sum = 0.0;
        double average;
        double sumVariance = 0.0;
        double variance;
        for (T o : l) {
            double temp = function.apply(o);
            sum += temp;
        }
        average = sum / size;
        for (T o : l) {
            double temp = Math.pow(function.apply(o) - average, 2);
            sumVariance += temp;
        }
        variance = sumVariance / size;
        return variance;
    }


    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/360
    /**
     * Calculate the variance of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function stddevBy():
     * The mapping function is a function mapping the objects to a double value, for instance:
     * {@code
     * Function<Integer, Double> mapping = e -> Double.valueOf(e);
     * }
     * The specific usage of stddevBy is like:
     * {@code
     * Seq.of(1, 1, 1, 1).collect(Agg.stddevBy(mapping));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(1), new Node(1), new Node(1), new Node(1)).collect(Agg.stddevBy(mapping1));
     * }
     * </pre>
     *
     * @param function mapping function from object to double value
     * @return the stddev value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-04-25
     */
    public static <T> Collector<T, ?, Optional<Double>> stddevBy(
            Function<? super T,
                    Double> function) {
        return Collector.of(
                (Supplier<ArrayList<T>>) ArrayList::new,
                ArrayList::add,
                (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                },
                l -> {
                    int size = l.size();
                    if (size == 0) {
                        return Optional.empty();
                    }
                    double variance = getVariance(function, l, size);
                    double stddev;


                    stddev = Math.sqrt(variance);

                    return Optional.of(stddev);
                }
        );
    }


    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/360
    /**
     * Calculate the variance of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function varianceBy():
     * The mapping function is a function mapping the objects to a double value, for instance:
     * {@code
     * Function<Integer, Double> mapping = e -> Double.valueOf(e);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(1, 1, 1, 1).collect(Agg.varianceBy(mapping));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(1), new Node(1), new Node(1), new Node(1)).collect(Agg.varianceBy(mapping1));
     * }
     * </pre>
     *
     * @param function mapping function from object to double value
     * @return the stddev value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-04-25
     */
    public static <T> Collector<T, ?, Optional<Double>> varianceBy(
            Function<? super T,
                    Double> function) {
        return Collector.of(
                (Supplier<ArrayList<T>>) ArrayList::new,
                ArrayList::add,
                (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                },
                l -> {
                    int size = l.size();
                    if (size == 0) {
                        return Optional.empty();
                    }
                    double variance = getVariance(function, l, size);
                    return Optional.of(variance);
                }
        );
    }













    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function Slope of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrSlopeBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrSlopeBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrSlopeBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the Slope value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrSlopeBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);

                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {
                    if (a.n == 0 || a.n==1) {
                        return Optional.empty();
                    }
                    if ((a.sumXX-a.sumX*a.sumX/a.n)==0.0){
//                        if (a.sumX==0){
//                            if (a.sumY==0){
//                                return Optional.of(0.0);
//                            }else {
//                                return Optional.of(Double.MAX_VALUE);
//                            }
//                        }
//                        return Optional.of(a.sumY/a.sumX);
                        return Optional.of(0.0);
                    }
                    return Optional.of((a.sumXY-a.sumX*a.sumY/a.n)/(a.sumXX-a.sumX*a.sumX/a.n));

                }
        );
    }




    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function Intercept of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrInterceptBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrInterceptBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrInterceptBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the Intercept value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrInterceptBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {
                    if (a.n == 0 || a.n==1)  {
                        return Optional.empty();
                    }
                    if ((a.sumXX-a.sumX*a.sumX/a.n)==0.0){
//                        return Optional.of(0.0);
                        return Optional.of(a.sumY/a.n);
                    }
                    return Optional.of(a.sumY/a.n-(a.sumXY-a.sumX*a.sumY/a.n)/(a.sumXX-a.sumX*a.sumX/a.n)*a.sumX/a.n);

                }
        );
    }


    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function R2 of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrR2By():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrR2By(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrR2By(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the R2 value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrR2By(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumYY = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;

            ArrayList<Double> listX = new ArrayList<>();
            ArrayList<Double> listY = new ArrayList<>();
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.listX.add(x);
                    a.listY.add(y);
                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumYY+=y*y;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.listX.addAll(a2.listX);
                    a1.listY.addAll(a2.listY);
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumYY+=a2.sumYY;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {

                    if (a.n == 0 || a.n==1) {
                        return Optional.empty();
                    }



                    Double slope = 0.0;
                    Double intercept = 0.0;
                    if ((a.sumXX-a.sumX*a.sumX/a.n)==0.0){
                        intercept = a.sumY/a.n;
                        slope = 0.0;
                    }else {
                        slope = ((a.sumXY-a.sumX*a.sumY/a.n)/(a.sumXX-a.sumX*a.sumX/a.n));
                        intercept = a.sumY/a.n-(a.sumXY-a.sumX*a.sumY/a.n)/(a.sumXX-a.sumX*a.sumX/a.n)*a.sumX/a.n;
                    }

                    Double avgY = a.sumY/a.n;
                    Double sstot = 0.0;
                    Double ssreg = 0.0;
                    for (int i = 0; i < a.listY.size(); i++){
                        sstot+=Math.pow((a.listY.get(i) -avgY),2);
                        ssreg+=Math.pow(slope*a.listX.get(i)+intercept-a.listY.get(i),2);
                    }
                    if (sstot==0.0){
                        return Optional.of(1.0);
                    }else {
                        return Optional.of(1-ssreg/sstot);
                    }

                }
        );
    }



    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function Count of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrCountBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrCountBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrCountBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the Count value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrCountBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.n++;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    return a1;
                },
                a -> {
                    return Optional.of(Double.valueOf(a.n));
                }
        );
    }



    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function AvgX of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrAvgXBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrAvgXBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrAvgXBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the AvgX value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrAvgXBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumX = 0.0;
            Double sumY = 0.0;
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.n++;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    return a1;
                },
                a -> {
                    if (a.n==0){
                        return Optional.empty();
                    }
                    return Optional.of(a.sumX/a.n);
                }
        );
    }



    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function AvgY of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrAvgYBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrAvgYBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrAvgYBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the AvgY value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrAvgYBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumX = 0.0;
            Double sumY = 0.0;
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.n++;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    return a1;
                },
                a -> {
                    if (a.n==0){
                        return Optional.empty();
                    }
                    return Optional.of(a.sumY/a.n);
                }
        );
    }






    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function SXX of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrSxxBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrSxxBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrSxxBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the SXX value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrSxxBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumYY = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;

            ArrayList<Double> listX = new ArrayList<>();
            ArrayList<Double> listY = new ArrayList<>();
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.listX.add(x);
                    a.listY.add(y);
                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumYY+=y*y;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.listX.addAll(a2.listX);
                    a1.listY.addAll(a2.listY);
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumYY+=a2.sumYY;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {

                    if (a.n == 0) {
                        return Optional.empty();
                    }

                    return Optional.of(a.sumXX-a.sumX*a.sumX/a.n);


                }
        );
    }











    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function SXY of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrSxyBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrSxyBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrSxyBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the SXY value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrSxyBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumYY = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;

            ArrayList<Double> listX = new ArrayList<>();
            ArrayList<Double> listY = new ArrayList<>();
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.listX.add(x);
                    a.listY.add(y);
                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumYY+=y*y;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.listX.addAll(a2.listX);
                    a1.listY.addAll(a2.listY);
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumYY+=a2.sumYY;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {

                    if (a.n == 0) {
                        return Optional.empty();
                    }

                    return Optional.of(a.sumXY-a.sumX*a.sumY/a.n);


                }
        );
    }





    //CS304 Issue link: https://github.com/jOOQ/jOOL/issues/151
    /**
     * Calculate the linear regression function SYY of the given object collectors,
     * based on the mapping function from object to double number.
     *
     * <p><pre> Usage of aggregation function regrSyyBy():
     * The mapping functions functionX and functionY are functions mapping the objects to double values X and Y, for instance:
     * {@code
     * FunctionX<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v1);
     * FunctionY<Tuple2<Integer,Integer>, Double> mapping = e -> Double.valueOf(e.v2);
     * }
     * The specific usage of varianceBy is like:
     * {@code
     * Seq.of(new Tuple2<Integer,Integer>(1,1),new Tuple2<Integer,Integer>(2,2)).collect(Agg.regrSyyBy(functionX,functionY));
     * }
     * Besides, self defined class is also allowed with mapping function:
     * {@code
     * Seq.of(new Node(-1,1),new Node(1,1)).collect(Agg.regrSyyBy(functionX,functionY));
     * }
     * </pre>
     *
     * @param functionX mapping function from object to double value representing x
     * @param functionY mapping function from object to double value representing y
     * @return the SYY value
     * @version 1.0
     * @author Jichen Lu
     * @date 2021-05-17
     */
    public static <T> Collector<T, ?, Optional<Double>> regrSyyBy(Function<? super T, ? extends Number> functionX,Function<? super T, ? extends Number> functionY) {
        class Accumulator{
            Integer n = 0;
            Double sumXY = 0.0;
            Double sumXX = 0.0;
            Double sumYY = 0.0;
            Double sumX = 0.0;
            Double sumY = 0.0;

            ArrayList<Double> listX = new ArrayList<>();
            ArrayList<Double> listY = new ArrayList<>();
        }

        return Collector.of(
                () -> new Accumulator(),
                (a,t) -> {
                    Double x = (Double) functionX.apply(t);
                    Double y = (Double) functionY.apply(t);
                    a.listX.add(x);
                    a.listY.add(y);
                    a.n++;
                    a.sumXY+=x*y;
                    a.sumXX+=x*x;
                    a.sumYY+=y*y;
                    a.sumX+=x;
                    a.sumY+=y;
                },
                (a1, a2) -> {
                    a1.n+=a2.n;
                    a1.listX.addAll(a2.listX);
                    a1.listY.addAll(a2.listY);
                    a1.sumX += a2.sumX;
                    a1.sumY+=a2.sumY;
                    a1.sumXX+=a2.sumXX;
                    a1.sumYY+=a2.sumYY;
                    a1.sumXY+=a2.sumXY;
                    return a1;
                },
                a -> {

                    if (a.n == 0) {
                        return Optional.empty();
                    }

                    return Optional.of(a.sumYY-a.sumY*a.sumY/a.n);


                }
        );
    }





}
