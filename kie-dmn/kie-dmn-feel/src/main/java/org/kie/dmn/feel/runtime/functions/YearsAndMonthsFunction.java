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
package org.kie.dmn.feel.runtime.functions;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.lang.types.impl.ComparablePeriod;
import org.kie.dmn.feel.runtime.FEELDurationFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class YearsAndMonthsFunction
        extends BaseFEELFunction implements FEELDurationFunction {

    public static final YearsAndMonthsFunction INSTANCE = new YearsAndMonthsFunction();

    private YearsAndMonthsFunction() {
        super(FEELConversionFunctionNames.YEARS_AND_MONTHS_DURATION);
    }

    public FEELFnResult<TemporalAmount> invoke(@ParameterName("from") Temporal from, @ParameterName("to") Temporal to) {
        if ( from == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        if ( to == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "to", "cannot be null"));
        }
        final LocalDate fromDate = getLocalDateFromTemporal(from);
        if (fromDate == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "is of type not suitable for years and months function"));
        }
        final LocalDate toDate = getLocalDateFromTemporal(to);
        if (toDate == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "to", "is of type not suitable for years and months function"));
        }

        return FEELFnResult.ofResult(new ComparablePeriod(Period.between(fromDate, toDate).withDays(0)));
    }

    private LocalDate getLocalDateFromTemporal(final Temporal temporal) {
        if (temporal instanceof LocalDate) {
            return (LocalDate) temporal;
        } else if (temporal instanceof Year) {
            return getLocalDateFromYear((Year) temporal);
        } else if (temporal instanceof YearMonth) {
            return getLocalDateFromYearAndMonth((YearMonth) temporal);
        } else {
            try {
                return LocalDate.from(temporal);
            } catch (DateTimeException ex) {
                return null;
            }
        }
    }

    private LocalDate getLocalDateFromYear(final Year year) {
        return LocalDate.of(year.getValue(), 1, 1);
    }

    private LocalDate getLocalDateFromYearAndMonth(final YearMonth yearMonth) {
        return LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), 1);
    }
}
