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
package org.kie.dmn.feel.runtime.impl;

import java.util.function.BiPredicate;

import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.util.BooleanEvalHelper;

import static org.kie.dmn.feel.lang.ast.UnaryTestNode.UnaryOperator.GT;
import static org.kie.dmn.feel.lang.ast.UnaryTestNode.UnaryOperator.GTE;
import static org.kie.dmn.feel.lang.ast.UnaryTestNode.UnaryOperator.LT;
import static org.kie.dmn.feel.lang.ast.UnaryTestNode.UnaryOperator.LTE;

public class RangeImpl
        implements Range {

    private RangeBoundary lowBoundary;
    private RangeBoundary highBoundary;
    private Comparable    lowEndPoint;
    private Comparable    highEndPoint;
    private boolean withUndefined = false;

    public RangeImpl() {
    }

    public RangeImpl(RangeBoundary lowBoundary, Comparable lowEndPoint, Comparable highEndPoint, RangeBoundary highBoundary) {
        this.lowBoundary = lowBoundary;
        this.highBoundary = highBoundary;
        this.lowEndPoint = lowEndPoint;
        this.highEndPoint = highEndPoint;
        withUndefined = lowEndPoint instanceof UndefinedValueComparable || highEndPoint instanceof UndefinedValueComparable;
    }

    @Override
    public RangeBoundary getLowBoundary() {
        return lowBoundary;
    }

    @Override
    public Comparable getLowEndPoint() {
        return lowEndPoint;
    }

    @Override
    public Comparable getHighEndPoint() {
        return highEndPoint;
    }

    @Override
    public RangeBoundary getHighBoundary() {
        return highBoundary;
    }

    @Override
    public Boolean includes(FEELDialect feelDialect, Object param) {
        if (param == null) {
            return null;
        }
        if (lowEndPoint == null || lowEndPoint instanceof UndefinedValueComparable) {
            if (highEndPoint == null || highEndPoint instanceof UndefinedValueComparable) {
                return null;
            } else if (lowEndPoint != null) { // it means it is UndefinedValueComparable
                return negInfRangeIncludes(feelDialect, param);
            } else {
                return false;
            }
        } else {
            if (highEndPoint instanceof UndefinedValueComparable) {
                return posInfRangeIncludes(feelDialect, param);
            } else if (highEndPoint != null) {
                return finiteRangeIncludes(feelDialect, param);
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean isWithUndefined() {
        return withUndefined;
    }

    private Boolean finiteRangeIncludes(FEELDialect feelDialect, Object param) {
        // Defaulting FEELDialect to FEEL
        if (lowBoundary == RangeBoundary.OPEN && highBoundary == RangeBoundary.OPEN) {
            return bothOrThrow(compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) < 0) , compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) > 0), param);
        } else if (lowBoundary == RangeBoundary.OPEN && highBoundary == RangeBoundary.CLOSED) {
            return bothOrThrow(compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) < 0) , compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) >= 0), param);
        } else if (lowBoundary == RangeBoundary.CLOSED && highBoundary == RangeBoundary.OPEN) {
            return bothOrThrow(compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) <= 0) , compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) > 0), param);
        } else if (lowBoundary == RangeBoundary.CLOSED && highBoundary == RangeBoundary.CLOSED) {
            return bothOrThrow(compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) <= 0) , compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) >= 0), param);
        }
        throw new RuntimeException("unknown boundary combination");
    }

    private Boolean posInfRangeIncludes(FEELDialect feelDialect, Object param) {
        if (lowBoundary == RangeBoundary.OPEN) {
            return compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) < 0);
        } else {
            return compare(feelDialect, lowEndPoint, param, (l, r) -> l.compareTo(r) <= 0);
        }
    }

    private Boolean negInfRangeIncludes(FEELDialect feelDialect, Object param) {
        if (highBoundary == RangeBoundary.OPEN) {
            return compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) > 0);
        } else {
            return compare(feelDialect, highEndPoint, param,  (l, r) -> l.compareTo(r) >= 0);
        }
    }
    
    private Boolean bothOrThrow(Boolean left, Boolean right, Object param) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Range.include("+classOf(param)+") not comparable with "+classOf(lowEndPoint)+", "+classOf(highEndPoint));
        }
        return left && right;
    }
    
    private static String classOf(Object p) {
        return p != null ? p.getClass().toString() : "null";
    }
    
    private static Boolean compare(FEELDialect feelDialect, Comparable left, Object right, BiPredicate<Comparable, Comparable> op) {
        if (left.getClass().isAssignableFrom(right.getClass())) { // short path
                return op.test(left, (Comparable) right);
        }
        return BooleanEvalHelper.compare(left, right, feelDialect, op); // defer to full DMN/FEEL semantic
    }

    @Override
    public boolean equals(Object o) {
        if ( this == o ) return true;
        if ( !(o instanceof RangeImpl) ) return false;

        RangeImpl range = (RangeImpl) o;

        if ( lowBoundary != range.lowBoundary ) return false;
        if ( highBoundary != range.highBoundary ) return false;
        if ( lowEndPoint != null ? !lowEndPoint.equals( range.lowEndPoint ) : range.lowEndPoint != null ) return false;
        return highEndPoint != null ? highEndPoint.equals( range.highEndPoint ) : range.highEndPoint == null;

    }

    @Override
    public int hashCode() {
        int result = lowBoundary != null ? lowBoundary.hashCode() : 0;
        result = 31 * result + (highBoundary != null ? highBoundary.hashCode() : 0);
        result = 31 * result + (lowEndPoint != null ? lowEndPoint.hashCode() : 0);
        result = 31 * result + (highEndPoint != null ? highEndPoint.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return withUndefined ? withUndefinedtoString() : withoutUndefinedtoString();
    }

    private String withoutUndefinedtoString() {
        return (lowBoundary == RangeBoundary.OPEN ? "(" : "[") +
                " " + lowEndPoint +
                " .. " + highEndPoint +
                " " + ( highBoundary == RangeBoundary.OPEN ? ")" : "]" );
    }

    private String withUndefinedtoString() {
        StringBuilder sb = new StringBuilder("( ");
        if (lowEndPoint instanceof UndefinedValueComparable) {
            if (highBoundary == RangeBoundary.OPEN) {
                sb.append(LT.symbol);
            } else {
                sb.append(LTE.symbol);
            }
            sb.append(" ");
            sb.append(highEndPoint);
        } else if (highEndPoint instanceof UndefinedValueComparable) {
            if (lowBoundary == RangeBoundary.OPEN) {
                sb.append(GT.symbol);
            } else {
                sb.append(GTE.symbol);
            }
            sb.append(" ");
            sb.append(lowEndPoint);
        }
        sb.append(" )");
        return sb.toString();
    }
}
