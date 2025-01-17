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
package org.kie.dmn.feel.util;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.chrono.ChronoPeriod;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.lang.types.impl.ComparablePeriod;
import org.kie.dmn.feel.runtime.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.dmn.feel.util.DateTimeEvalHelper.valuedt;
import static org.kie.dmn.feel.util.DateTimeEvalHelper.valuet;
import static org.kie.dmn.feel.util.NumberEvalHelper.getBigDecimalOrNull;

public class BooleanEvalHelper {
    public static final Logger LOG = LoggerFactory.getLogger( BooleanEvalHelper.class );

    public static Boolean getBooleanOrNull(Object value) {
        if (!(value instanceof Boolean)) {
            return null;
        }
        return (Boolean) value;
    }

    /**
     * Compares left and right operands using the given predicate and returns TRUE/FALSE accordingly
     *
     * @param left
     * @param right
     * @param op
     * @return
     */
    public static Boolean compare(Object left, Object right, FEELDialect feelDialect, BiPredicate<Comparable, Comparable> op) {
        if ( left == null || right == null ) {
            return getBooleanOrDialectDefault(null, feelDialect);
        }
        if (left instanceof ChronoPeriod && right instanceof ChronoPeriod) {
            // periods have special compare semantics in FEEL as it ignores "days". Only months and years are compared
            Long l = ComparablePeriod.toTotalMonths((ChronoPeriod) left);
            Long r = ComparablePeriod.toTotalMonths((ChronoPeriod) right);
            return op.test( l, r );
        }
        if (left instanceof TemporalAccessor && right instanceof TemporalAccessor) {
            // Handle specific cases when both time / datetime
            TemporalAccessor l = (TemporalAccessor) left;
            TemporalAccessor r = (TemporalAccessor) right;
            if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.TIME) {
                return op.test(valuet(l), valuet(r));
            } else if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.DATE_TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.DATE_TIME) {
                return op.test(valuedt(l, r.query(TemporalQueries.zone())), valuedt(r, l.query(TemporalQueries.zone())));
            }
        }
        if (left instanceof Number && right instanceof Number) {
            // Handle specific cases when both are Number, converting both to BigDecimal
            BigDecimal l = getBigDecimalOrNull(left);
            BigDecimal r = getBigDecimalOrNull(right);
            return op.test(l, r);
        }
        // last fallback:
        if ((left instanceof String && right instanceof String) ||
                (left instanceof Boolean && right instanceof Boolean) ||
                (left instanceof Comparable && left.getClass().isAssignableFrom(right.getClass()))) {
            Comparable<?> l = (Comparable<?>) left;
            Comparable<?> r = (Comparable<?>) right;
            return op.test(l, r);
        }
        return getBooleanOrDialectDefault(null, feelDialect);
    }

    /**
     * Compares left and right for equality applying FEEL semantics to specific data types
     *
     * @param left
     * @param right
     * @return
     */
    public static Boolean isEqual(Object left, Object right, FEELDialect feelDialect) {
        if ( left == null || right == null ) {
            return left == right;
        }

        // spec defines that "a=[a]", i.e., singleton collections should be treated as the single element
        // and vice-versa
        if( left instanceof Collection && !(right instanceof Collection) && ((Collection)left).size() == 1 ) {
            left = ((Collection)left).toArray()[0];
        } else if( right instanceof Collection && !(left instanceof Collection) && ((Collection)right).size()==1 ) {
            right = ((Collection) right).toArray()[0];
        }

        if( left instanceof Range && right instanceof Range ) {
            return isEqual( (Range)left, (Range) right );
        } else if( left instanceof Iterable && right instanceof Iterable ) {
            return isEqual( (Iterable)left, (Iterable) right );
        } else if( left instanceof Map && right instanceof Map ) {
            return isEqual( (Map)left, (Map) right );
        } else if (left instanceof ChronoPeriod && right instanceof ChronoPeriod) {
            // periods have special compare semantics in FEEL as it ignores "days". Only months and years are compared
            Long l = ComparablePeriod.toTotalMonths((ChronoPeriod) left);
            Long r = ComparablePeriod.toTotalMonths((ChronoPeriod) right);
            return isEqual(l, r, feelDialect);
        } else if (left instanceof TemporalAccessor && right instanceof TemporalAccessor) {
            // Handle specific cases when both time / datetime
            TemporalAccessor l = (TemporalAccessor) left;
            TemporalAccessor r = (TemporalAccessor) right;
            if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.TIME) {
                return isEqual(DateTimeEvalHelper.valuet(l), DateTimeEvalHelper.valuet(r), feelDialect);
            } else if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.DATE_TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.DATE_TIME) {
                return isEqual(DateTimeEvalHelper.valuedt(l, r.query(TemporalQueries.zone())), DateTimeEvalHelper.valuedt(r, l.query(TemporalQueries.zone())), feelDialect);
            } // fallback; continue:
        }
        return compare( left, right, feelDialect, (l, r) -> l.compareTo( r ) == 0  );
    }

    /**
     * DMNv1.2 Table 48: Specific semantics of equality
     * DMNv1.3 Table 71: Semantic of date and time functions
     */
    public static Boolean isEqualDateTimeInSemanticD(TemporalAccessor left, TemporalAccessor right) {
        boolean result = true;
        Optional<Integer> lY = Optional.ofNullable(left.isSupported(ChronoField.YEAR) ? left.get(ChronoField.YEAR) : null);
        Optional<Integer> rY = Optional.ofNullable(right.isSupported(ChronoField.YEAR) ? right.get(ChronoField.YEAR) : null);
        result &= lY.equals(rY);
        Optional<Integer> lM = Optional.ofNullable(left.isSupported(ChronoField.MONTH_OF_YEAR) ? left.get(ChronoField.MONTH_OF_YEAR) : null);
        Optional<Integer> rM = Optional.ofNullable(right.isSupported(ChronoField.MONTH_OF_YEAR) ? right.get(ChronoField.MONTH_OF_YEAR) : null);
        result &= lM.equals(rM);
        Optional<Integer> lD = Optional.ofNullable(left.isSupported(ChronoField.DAY_OF_MONTH) ? left.get(ChronoField.DAY_OF_MONTH) : null);
        Optional<Integer> rD = Optional.ofNullable(right.isSupported(ChronoField.DAY_OF_MONTH) ? right.get(ChronoField.DAY_OF_MONTH) : null);
        result &= lD.equals(rD);
        result &= isEqualTimeInSemanticD(left, right);
        return result;
    }

    /**
     * DMNv1.2 Table 48: Specific semantics of equality
     * DMNv1.3 Table 71: Semantic of date and time functions
     */
    public static Boolean isEqualTimeInSemanticD(TemporalAccessor left, TemporalAccessor right) {
        boolean result = true;
        Optional<Integer> lH = Optional.ofNullable(left.isSupported(ChronoField.HOUR_OF_DAY) ? left.get(ChronoField.HOUR_OF_DAY) : null);
        Optional<Integer> rH = Optional.ofNullable(right.isSupported(ChronoField.HOUR_OF_DAY) ? right.get(ChronoField.HOUR_OF_DAY) : null);
        result &= lH.equals(rH);
        Optional<Integer> lM = Optional.ofNullable(left.isSupported(ChronoField.MINUTE_OF_HOUR) ? left.get(ChronoField.MINUTE_OF_HOUR) : null);
        Optional<Integer> rM = Optional.ofNullable(right.isSupported(ChronoField.MINUTE_OF_HOUR) ? right.get(ChronoField.MINUTE_OF_HOUR) : null);
        result &= lM.equals(rM);
        Optional<Integer> lS = Optional.ofNullable(left.isSupported(ChronoField.SECOND_OF_MINUTE) ? left.get(ChronoField.SECOND_OF_MINUTE) : null);
        Optional<Integer> rS = Optional.ofNullable(right.isSupported(ChronoField.SECOND_OF_MINUTE) ? right.get(ChronoField.SECOND_OF_MINUTE) : null);
        result &= lS.equals(rS);
        Optional<ZoneId> lTZ = Optional.ofNullable(left.query(TemporalQueries.zone()));
        Optional<ZoneId> rTZ = Optional.ofNullable(right.query(TemporalQueries.zone()));
        result &= lTZ.equals(rTZ);
        return result;
    }

    /**
     * This method consider if the <code>value</code> object is a <code>String</code>
     * In that case, return the {@link String#equals(Object)} result
     * Otherwise, default to the {@link #isEqual(Object, Object, FEELDialect)}
     * @param value
     * @param itemFromList
     * @return
     */
    public static boolean isEqualsStringCompare(Object value, Object itemFromList) {
        if (value instanceof String) {
            return value.equals(itemFromList);
        } else {
            // Defaulting FEELDialect to FEEL
            Boolean dmnEqual = isEqual(value, itemFromList, FEELDialect.FEEL);
            return dmnEqual != null && dmnEqual;
        }
    }

    /**
     * Return the original object or, depending on the FEELDialect, a default value
     * @param rawReturn
     * @param feelDialect
     * @return
     */
    public static Boolean getBooleanOrDialectDefault(Object rawReturn, FEELDialect feelDialect) {
        if (feelDialect.equals(FEELDialect.BFEEL)) {
            if (rawReturn instanceof Boolean bool) {
                return bool;
            } else {
                return false;
            }
        } else {
            return (Boolean) rawReturn;
        }
    }

    /**
     * Return <code>TRUE</code> if it is the original object or, depending on the FEELDialect, a default value
     * @param rawReturn
     * @param feelDialect
     * @return
     */
    public static Boolean getTrueOrDialectDefault(Object rawReturn, FEELDialect feelDialect) {
        if (rawReturn instanceof Boolean bool && bool) {
            return bool;
        } else {
            return getBooleanOrDialectDefault(null, feelDialect);
        }
    }

    /**
     * Return <code>TRUE</code> if it is the original object or, depending on the FEELDialect, a default value
     * @param rawReturn
     * @param feelDialect
     * @return
     */
    public static Boolean getFalseOrDialectDefault(Object rawReturn, FEELDialect feelDialect) {
        if (rawReturn instanceof Boolean bool && (!bool)) {
            return bool;
        } else {
            return getBooleanOrDialectDefault(null, feelDialect);
        }
    }

    static Boolean isEqual(Range left, Range right) {
        return left.equals( right );
    }

    static Boolean isEqual(Iterable left, Iterable right) {
        Iterator li = left.iterator();
        Iterator ri = right.iterator();
        while( li.hasNext() && ri.hasNext() ) {
            Object l = li.next();
            Object r = ri.next();
            if ( !isEqualObject(l, r ) ) return false;
        }
        return li.hasNext() == ri.hasNext();
    }

    static Boolean isEqual(Map<?,?> left, Map<?,?> right) {
        if( left.size() != right.size() ) {
            return false;
        }
        for( Map.Entry le : left.entrySet() ) {
            Object l = le.getValue();
            Object r = right.get( le.getKey() );
            if ( !isEqualObject( l, r ) ) return false;
        }
        return true;
    }

    static Boolean isEqualObject(Object l, Object r) {
        if( l instanceof Iterable && r instanceof Iterable && !isEqual( (Iterable) l, (Iterable) r ) ) {
            return false;
        } else if( l instanceof Map && r instanceof Map && !isEqual( (Map) l, (Map) r ) ) {
            return false;
        } else if( l != null && r != null && !l.equals( r ) ) {
            return false;
        } else if( ( l == null || r == null ) && l != r ) {
            return false;
        }
        return true;
    }

}
