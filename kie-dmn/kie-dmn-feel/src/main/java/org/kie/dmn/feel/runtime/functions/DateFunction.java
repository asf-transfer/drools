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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Pattern;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELDateFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public class DateFunction
        extends BaseFEELFunction implements FEELDateFunction {

    public static final DateFunction INSTANCE = new DateFunction();

    public static final Pattern BEGIN_YEAR = Pattern.compile("^-?(([1-9]\\d\\d\\d+)|(0\\d\\d\\d))-"); // FEEL spec, "specified by XML Schema Part 2 Datatypes", hence: yearFrag ::= '-'? (([1-9] digit digit digit+)) | ('0' digit digit digit))
    public static final DateTimeFormatter FEEL_DATE;
    static {
        FEEL_DATE = new DateTimeFormatterBuilder().appendValue(YEAR, 4, 9, SignStyle.NORMAL)
                                                  .appendLiteral('-')
                                                  .appendValue(MONTH_OF_YEAR, 2)
                                                  .appendLiteral('-')
                                                  .appendValue(DAY_OF_MONTH, 2)
                                                  .toFormatter()
                                                  .withResolverStyle(ResolverStyle.STRICT);
    }


    protected DateFunction() {
        super(FEELConversionFunctionNames.DATE);
    }

    public FEELFnResult<TemporalAccessor> invoke(@ParameterName( "from" ) String val) {
        if ( val == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        if (!BEGIN_YEAR.matcher(val).find()) { // please notice the regex strictly requires the beginning, so we can use find.
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "year not compliant with XML Schema Part 2 Datatypes"));
        }

        try {
            return FEELFnResult.ofResult(LocalDate.from(FEEL_DATE.parse(val)));
        } catch (DateTimeException e) {
            return manageDateTimeException(e, val);
        }
    }

    public FEELFnResult<TemporalAccessor> invoke(@ParameterName( "year" ) Number year, @ParameterName( "month" ) Number month, @ParameterName( "day" ) Number day) {
        if ( year == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "year", "cannot be null"));
        }
        if ( month == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "month", "cannot be null"));
        }
        if ( day == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "day", "cannot be null"));
        }

        try {
            return FEELFnResult.ofResult( LocalDate.of( year.intValue(), month.intValue(), day.intValue() ) );
        } catch (DateTimeException e) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "input parameters date-parsing exception", e));
        }
    }

    public FEELFnResult<TemporalAccessor> invoke(@ParameterName( "from" ) TemporalAccessor date) {
        if ( date == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }

        try {
            return FEELFnResult.ofResult( LocalDate.from( date ) );
        } catch (DateTimeException e) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "date-parsing exception", e));
        }
    }

    protected FEELFnResult<TemporalAccessor> manageDateTimeException(DateTimeException e, String val) {
        return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "date", e));
    }
}
