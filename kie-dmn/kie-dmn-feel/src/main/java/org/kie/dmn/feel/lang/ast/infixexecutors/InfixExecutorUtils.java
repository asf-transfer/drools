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
package org.kie.dmn.feel.lang.ast.infixexecutors;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.function.BinaryOperator;

import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.BooleanEvalHelper;
import org.kie.dmn.feel.util.Msg;
import org.kie.dmn.feel.util.NumberEvalHelper;

public class InfixExecutorUtils {

    /**
     * Implements the ternary logic OR operation
     *
     * @deprecated this variant do not allow short-circuit of the operator
     */
    @Deprecated
    public static Object or(Object left, Object right, EvaluationContext ctx) {
        Boolean l = BooleanEvalHelper.getBooleanOrDialectDefault(left, ctx.getFEELDialect());
        Boolean r = BooleanEvalHelper.getBooleanOrDialectDefault(right, ctx.getFEELDialect());
        // have to check for all nulls first to avoid NPE
        if ((l == null && r == null) || (l == null && r == false) || (r == null && l == false)) {
            return null;
        } else if (l == null || r == null) {
            return true;
        }
        return l || r;
    }

    /**
     * Implements the ternary logic AND operation
     *
     * @deprecated this variant do not allow short-circuit of the operator
     */
    @Deprecated
    public static Object and(Object left, Object right, EvaluationContext ctx) {
        Boolean l = BooleanEvalHelper.getBooleanOrDialectDefault(left, ctx.getFEELDialect());
        Boolean r = BooleanEvalHelper.getBooleanOrDialectDefault(right, ctx.getFEELDialect());
        // have to check for all nulls first to avoid NPE
        if ((l == null && r == null) || (l == null && r == true) || (r == null && l == true)) {
            return null;
        } else if (l == null || r == null) {
            return false;
        }
        return l && r;
    }

    static Object math(Object left, Object right, EvaluationContext ctx, BinaryOperator<BigDecimal> op) {
        BigDecimal l = left instanceof String ? null : NumberEvalHelper.getBigDecimalOrNull(left);
        BigDecimal r = right instanceof String ? null : NumberEvalHelper.getBigDecimalOrNull(right);
        if (l == null || r == null) {
            return null;
        }
        try {
            return op.apply(l, r);
        } catch (ArithmeticException e) {
            // happens in cases like division by 0
            ctx.notifyEvt(() -> new InvalidParametersEvent(FEELEvent.Severity.ERROR, Msg.createMessage(Msg.GENERAL_ARITHMETIC_EXCEPTION, e.getMessage())));
            return null;
        }
    }

    static LocalDate addLocalDateAndDuration(LocalDate left, Duration right) {
        LocalDateTime leftLDT = LocalDateTime.of(left, LocalTime.MIDNIGHT);
        LocalDateTime evaluated = leftLDT.plus(right);
        return LocalDate.of(evaluated.getYear(), evaluated.getMonth(), evaluated.getDayOfMonth());
    }

    static Object subtractTemporals(final Temporal left, final Temporal right, final EvaluationContext ctx) {
        // Based on the Table 57 in the spec, if it is only date, convert to date and time.
        final Temporal leftTemporal = getTemporalForSubtraction(left);
        final Temporal rightTemporal = getTemporalForSubtraction(right);

        if (isAllowedTemporalSubtractionBasedOnSpec(leftTemporal, rightTemporal, ctx)) {
            return Duration.between(rightTemporal, leftTemporal);
        } else {
            return null;
        }
    }

    static Temporal getTemporalForSubtraction(final Temporal temporal) {
        if (temporal instanceof LocalDate) {
            return ZonedDateTime.of((LocalDate) temporal, LocalTime.MIDNIGHT, ZoneOffset.UTC);
        } else {
            return temporal;
        }
    }

    /**
     * Checks if the multiplication is supported by the DMN specification based on the objects specified as parameters.
     *
     * @param left  Left parameter of the subtraction expression.
     * @param right Right parameter of the subtraction expression.
     * @param ctx   Context that is used to notify about not allowed set of parameters.
     * @return True, if the parameters are valid for multiplication based on the DMN specification.
     * False, when multiplication is not defined for the specified set of parameters in the DMN spec, or is forbidden: <br>
     * - Multiplication of two durations e is not allowed in the specification.
     */
    static boolean isAllowedMultiplicationBasedOnSpec(final Object left, final Object right, final EvaluationContext ctx) {
        if (left instanceof TemporalAmount && right instanceof TemporalAmount) {
            ctx.notifyEvt(() -> new InvalidParametersEvent(FEELEvent.Severity.ERROR, Msg.createMessage(Msg.INVALID_PARAMETERS_FOR_OPERATION, "multiplication",
                    left.getClass().getName(),
                    right.getClass().getName())));
            return false;
        }
        return true;
    }

    /**
     * Checks if the subtraction is supported by the DMN specification based on the temporals specified as parameters.
     *
     * @param leftTemporal  Left temporal parameter of the subtraction expression.
     * @param rightTemporal Right temporal parameter of the subtraction expression.
     * @param ctx           Context that is used to notify about not allowed set of parameters.
     * @return True, if the temporal parameters are valid for subtraction based on the DMN specification.
     * False, when subtraction is not defined for the specified set of parameters in the DMN spec, or is forbidden: <br>
     * - Subtraction of a datetime with timezone and a datetime without a timezone is not defined in the specification.
     * - Subtraction of a time and a datetime is not defined in the specification.
     */
    static boolean isAllowedTemporalSubtractionBasedOnSpec(final Temporal leftTemporal, final Temporal rightTemporal, final EvaluationContext ctx) {
        // Both datetimes have a timezone or both timezones don't have it. Cannot combine timezoned datetime and datetime without a timezone.
        if ((leftTemporal instanceof ZonedDateTime || leftTemporal instanceof OffsetDateTime)
                && (rightTemporal instanceof LocalDateTime)) {
            ctx.notifyEvt(() -> new InvalidParametersEvent(FEELEvent.Severity.ERROR, Msg.createMessage(Msg.DATE_AND_TIME_TIMEZONE_NEEDED, "first", leftTemporal, "second", rightTemporal)));
            return false;
        } else if ((rightTemporal instanceof ZonedDateTime || rightTemporal instanceof OffsetDateTime)
                && (leftTemporal instanceof LocalDateTime)) {
            ctx.notifyEvt(() -> new InvalidParametersEvent(FEELEvent.Severity.ERROR, Msg.createMessage(Msg.DATE_AND_TIME_TIMEZONE_NEEDED, "second", rightTemporal, "first", leftTemporal)));
            return false;
        }

        // Cannot combine time and date (or datetime) based on the DMN specification.
        if ((!leftTemporal.isSupported(ChronoUnit.DAYS) && rightTemporal.isSupported(ChronoUnit.DAYS))
                || (!rightTemporal.isSupported(ChronoUnit.DAYS) && leftTemporal.isSupported(ChronoUnit.DAYS))) {
            ctx.notifyEvt(() -> new InvalidParametersEvent(FEELEvent.Severity.ERROR, Msg.OPERATION_IS_UNDEFINED_FOR_PARAMETERS.getMask()));
            return false;
        }
        return true;
    }

}
