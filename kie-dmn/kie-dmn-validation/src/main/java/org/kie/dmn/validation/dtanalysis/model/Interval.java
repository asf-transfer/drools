/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.validation.dtanalysis.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.Range.RangeBoundary;
import org.kie.dmn.feel.runtime.impl.RangeImpl;
import org.kie.dmn.feel.runtime.impl.UndefinedValueComparable;
import org.kie.dmn.feel.util.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Interval {

    private static final Logger LOG = LoggerFactory.getLogger(Interval.class);

    public static final Comparable<?> POS_INF = new Comparable<Object>() {

        @Override
        public int compareTo(Object o) {
            return o == this ? 0 : +1;
        }

        @Override
        public String toString() {
            return "+Inf";
        }
    };

    public static final Comparable<?> NEG_INF = new Comparable<Object>() {

        @Override
        public int compareTo(Object o) {
            return o == this ? 0 : -1;
        }

        @Override
        public String toString() {
            return "-Inf";
        }
    };

    private final Bound<?> lowerBound;
    private final Bound<?> upperBound;
    private final int rule;
    private final int col;
    private final Range asRange;

    public Interval(RangeBoundary lowBoundary, Comparable<?> start, Comparable<?> end, RangeBoundary highBoundary, int rule, int col) {
        this.lowerBound = new Bound(start, lowBoundary, this);
        this.upperBound = new Bound(end, highBoundary, this);
        this.rule = rule;
        this.col = col;
        this.asRange = new RangeImpl(lowBoundary, nullIfInfinity(start), nullIfInfinity(end), highBoundary);
    }

    private Interval(Bound<?> lowerBound, Bound<?> upperBound) {
        this.lowerBound = new Bound(lowerBound.getValue(), lowerBound.getBoundaryType(), this);
        this.upperBound = new Bound(upperBound.getValue(), upperBound.getBoundaryType(), this);
        if (lowerBound.getParent() != null && upperBound.getParent() != null && lowerBound.getParent().rule == upperBound.getParent().rule && lowerBound.getParent().col == upperBound.getParent().col) {
            this.rule = lowerBound.getParent().rule;
            this.col = lowerBound.getParent().col;
        } else {
            this.rule = 0;
            this.col = 0;
        }
        this.asRange = new RangeImpl(lowerBound.getBoundaryType(), nullIfInfinity(lowerBound.getValue()), nullIfInfinity(upperBound.getValue()), upperBound.getBoundaryType());
    }

    public static Interval newFromBounds(Bound<?> lowerBound, Bound<?> upperBound) {
        return new Interval(lowerBound, upperBound);
    }

    private static Comparable<?> nullIfInfinity(Comparable<?> input) {
        if (input != POS_INF && input != NEG_INF) {
            return input;
        } else {
            return new UndefinedValueComparable();
        }
    }

    @Override
    public String toString() {
        return (lowerBound.getBoundaryType() == RangeBoundary.OPEN ? "(" : "[") +
               " " + Bound.boundValueToString(lowerBound.getValue()) +
               " .. " + Bound.boundValueToString(upperBound.getValue()) +
               " " + (upperBound.getBoundaryType() == RangeBoundary.OPEN ? ")" : "]");
    }

    public Bound<?> getLowerBound() {
        return lowerBound;
    }

    public Bound<?> getUpperBound() {
        return upperBound;
    }

    public int getRule() {
        return rule;
    }

    public int getCol() {
        return col;
    }

    @Generated("org.eclipse.jdt.internal.corext.codemanipulation")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((lowerBound == null) ? 0 : lowerBound.hashCode());
        result = prime * result + ((upperBound == null) ? 0 : upperBound.hashCode());
        return result;
    }

    @Generated("org.eclipse.jdt.internal.corext.codemanipulation")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Interval other = (Interval) obj;
        if (lowerBound == null) {
            if (other.lowerBound != null) {
                return false;
            }
        } else if (!lowerBound.equals(other.lowerBound)) {
            return false;
        }
        if (upperBound == null) {
            if (other.upperBound != null) {
                return false;
            }
        } else if (!upperBound.equals(other.upperBound)) {
            return false;
        }
        return true;
    }

    public boolean asRangeIncludes(Object param) {
        // Defaulting FEELDialect to FEEL
        Boolean result = this.asRange.includes(FEELDialect.FEEL, param);
        if (result != null) {
            return result;
        } else if (this.lowerBound.getValue() == NEG_INF &&
                   this.lowerBound.getBoundaryType() == RangeBoundary.CLOSED &&
                   this.upperBound.getValue() == POS_INF &&
                   this.upperBound.getBoundaryType() == RangeBoundary.CLOSED) {
            return true;
        } else {
            return false;
        }
    }

    public boolean includes(Interval o) {
        return this.lowerBound.compareTo((Bound) o.lowerBound) <= 0 && this.upperBound.compareTo((Bound) o.upperBound) >= 0;
    }

    public boolean leftAdjOrOverlap(Interval o) {
        boolean thisLeftLower = this.lowerBound.compareTo((Bound) o.lowerBound) <= 0;
        boolean oRightHigher = o.upperBound.compareTo((Bound) this.upperBound) >= 0;
        boolean chained = BoundValueComparator.compareValueDispatchingToInf(o.lowerBound, this.upperBound) < 0;
        boolean adj = Bound.adOrOver(o.lowerBound, this.upperBound);
        return thisLeftLower && oRightHigher && (chained || adj);
    }

    public static Range.RangeBoundary invertBoundary(Range.RangeBoundary b) {
        if (b == RangeBoundary.OPEN) {
            return RangeBoundary.CLOSED;
        } else if (b == RangeBoundary.CLOSED) {
            return RangeBoundary.OPEN;
        } else {
            throw new IllegalStateException("invertBoundary for: " + b);
        }
    }

    public static List<Interval> flatten(List<Interval> intervals) {
        List<Interval> results = new ArrayList<>();
        LOG.debug("intervals {}", intervals);
        List<Bound> bounds = intervals.stream().flatMap(i -> Stream.of(i.getLowerBound(), i.getUpperBound())).collect(Collectors.toList());
        Collections.sort(bounds);
        LOG.debug("bounds (sorted) {}", bounds);
        Deque<Bound> stack = new ArrayDeque<>();
        Interval candidate = null;
        for (Bound cur : bounds) {
            if (stack.isEmpty() && !cur.isLowerBound()) {
                throw new RuntimeException("Inconsistent sort of bounds.");
            }
            if (cur.isLowerBound()) {
                if (candidate == null) {
                    stack.push(cur);
                } else {
                    if (Bound.adOrOver(candidate.upperBound, cur)) {
                        stack.push(candidate.lowerBound);
                        candidate = null;
                    } else {
                        results.add(candidate);
                        stack.push(cur);
                    }
                }
            } else if (cur.isUpperBound()) {
                Bound pop = stack.pop();
                if (stack.isEmpty()) {
                    candidate = Interval.newFromBounds(pop, cur);
                }
            } else {
                throw new RuntimeException("Inconsistent value for bounds.");
            }
        }
        if (candidate != null) {
            results.add(candidate);
        }
        LOG.debug("results {}", results);
        return results;
    }

    public static List<Interval> invertOverDomain(Interval interval, Interval domain) {
        List<Interval> results = new ArrayList<>();
        if (!domain.lowerBound.equals(interval.lowerBound)) {
            Interval left = new Interval(domain.lowerBound.getBoundaryType(),
                                         domain.lowerBound.getValue(),
                                         interval.lowerBound.getValue(),
                                         invertBoundary(interval.lowerBound.getBoundaryType()),
                                         interval.rule,
                                         interval.col);
            results.add(left);
        }
        if (!domain.upperBound.equals(interval.upperBound)) {
            Interval right = new Interval(invertBoundary(interval.upperBound.getBoundaryType()),
                                          interval.upperBound.getValue(),
                                          domain.upperBound.getValue(),
                                          domain.upperBound.getBoundaryType(),
                                          interval.rule,
                                          interval.col);
            results.add(right);
        }
        LOG.debug("results {}", results);
        return results;
    }
    
    public static List<Interval> invertOverDomain(List<Interval> intervals, Interval domain) {
        List<Interval> results = new ArrayList<>();
        final List<Interval> is = flatten(intervals);
        Iterator<Interval> iterator = is.iterator();
        if (!iterator.hasNext()) {
            return Collections.singletonList(domain); // if none, inversion is the domain.
        }

        Interval firstInterval = iterator.next();
        if (!domain.lowerBound.equals(firstInterval.lowerBound)) {
            Interval left = new Interval(domain.lowerBound.getBoundaryType(),
                                         domain.lowerBound.getValue(),
                                         firstInterval.lowerBound.getValue(),
                                         invertBoundary(firstInterval.lowerBound.getBoundaryType()),
                                         firstInterval.rule,
                                         firstInterval.col);
            results.add(left);
        }
        Interval previousInterval = firstInterval;
        while (iterator.hasNext()) {
            Interval nextInterval = iterator.next();
            if ((!previousInterval.upperBound.getValue().equals(nextInterval.lowerBound.getValue()))
                    || (previousInterval.upperBound.getBoundaryType() == RangeBoundary.OPEN && nextInterval.lowerBound.getBoundaryType() == RangeBoundary.OPEN)) {
                Interval iNew = new Interval(invertBoundary(previousInterval.upperBound.getBoundaryType()),
                                              previousInterval.upperBound.getValue(),
                                              nextInterval.lowerBound.getValue(),
                                              invertBoundary(nextInterval.lowerBound.getBoundaryType()),
                                              previousInterval.rule,
                                              previousInterval.col);
                results.add(iNew);
            }
            previousInterval = nextInterval;
        }
        if (!domain.upperBound.equals(previousInterval.upperBound)) {
            Interval right = new Interval(invertBoundary(previousInterval.upperBound.getBoundaryType()),
                                          previousInterval.upperBound.getValue(),
                                          domain.upperBound.getValue(),
                                          domain.upperBound.getBoundaryType(),
                                          previousInterval.rule,
                                          previousInterval.col);
            results.add(right);
        }
        LOG.debug("results {}", results);
        return results;
    }

    public String asHumanFriendly(Domain domain) {
        if (lowerBound.getValue().equals(upperBound.getValue()) 
                && lowerBound.getBoundaryType() == RangeBoundary.CLOSED 
                && upperBound.getBoundaryType() == RangeBoundary.CLOSED) {
            return Bound.boundValueToString(lowerBound.getValue());
        } else if (domain.isDiscreteDomain()) {
            List<?> dValues = domain.getDiscreteValues();
            int posL = dValues.indexOf(lowerBound.getValue());
            if (posL < dValues.size() - 1
                && dValues.get(posL + 1).equals(upperBound.getValue())
                    && lowerBound.getBoundaryType() == RangeBoundary.CLOSED 
                    && upperBound.getBoundaryType() == RangeBoundary.OPEN) {
                return Bound.boundValueToString(lowerBound.getValue());
            } else if (posL == dValues.size() - 1
                       && lowerBound.getBoundaryType() == RangeBoundary.CLOSED 
                       && upperBound.getBoundaryType() == RangeBoundary.CLOSED) {
                return Bound.boundValueToString(lowerBound.getValue());
            } else {
                return this.toString();
            }
        } else if (this.equals(domain.getDomainMinMax())) {
            return "-";
        } else if (upperBound.equals(domain.getMax()) && upperBound.getBoundaryType() == RangeBoundary.CLOSED) {
            if (lowerBound.getBoundaryType() == RangeBoundary.CLOSED) {
                return ">=" + Bound.boundValueToString(lowerBound.getValue());
            } else if (lowerBound.getBoundaryType() == RangeBoundary.OPEN) {
                return ">" + Bound.boundValueToString(lowerBound.getValue());
            }
        } else if (lowerBound.equals(domain.getMin()) && lowerBound.getBoundaryType() == RangeBoundary.CLOSED) {
            if (upperBound.getBoundaryType() == RangeBoundary.CLOSED) {
                return "<=" + Bound.boundValueToString(upperBound.getValue());
            } else if (upperBound.getBoundaryType() == RangeBoundary.OPEN) {
                return "<" + Bound.boundValueToString(upperBound.getValue());
            }
        }
        return this.toString();
    }

    public static List<Interval> normalizeDiscrete(List<Interval> intervals, List<Object> discreteValues) {
        List<Interval> results = new ArrayList<>();
        for (Interval curInterval : intervals) {
            if (curInterval.lowerBound.getBoundaryType() == RangeBoundary.CLOSED && curInterval.upperBound.getBoundaryType() == RangeBoundary.OPEN) {
                int lowerIdx = discreteValues.indexOf(curInterval.lowerBound.getValue());
                int upperIdx = discreteValues.indexOf(curInterval.upperBound.getValue());
                if (upperIdx - lowerIdx >= 2 && lowerIdx >= 0 && upperIdx >= 0) {
                    Comparable<?> previousOfUpper = (Comparable<?>) discreteValues.get(upperIdx - 1);
                    Interval newInterval = new Interval(RangeBoundary.CLOSED, curInterval.lowerBound.getValue(), previousOfUpper, RangeBoundary.CLOSED, 0, 0);
                    results.add(newInterval);
                } else {
                    results.add(curInterval); // add as-is.
                }
            } else {
                results.add(curInterval); // add as-is.
            }
        }
        return results;
    }

    public boolean isSingularity() {
        return lowerBound.getBoundaryType() == RangeBoundary.CLOSED &&
               upperBound.getBoundaryType() == RangeBoundary.CLOSED &&
               BoundValueComparator.compareValueDispatchingToInf(lowerBound, upperBound) == 0;
    }

    public static boolean adjOrOverlap(List<Interval> intervalsA, List<Interval> intervalsB) {
        List<Interval> otherIntervals = new ArrayList<>(intervalsB);
        for (Interval i : intervalsA) {
            List<Interval> adjOrOverlapWithI = new ArrayList<>();
            for (Interval o : otherIntervals) {
                if (i.leftAdjOrOverlap(o) || o.leftAdjOrOverlap(i)) {
                    adjOrOverlapWithI.add(o);
                }
            }
            otherIntervals.removeAll(adjOrOverlapWithI);
        }
        return otherIntervals.isEmpty();
    }
}
