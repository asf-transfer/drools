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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.regex.Pattern;

import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELTimeFunction;
import org.kie.dmn.feel.runtime.custom.ZoneTime;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class TimeFunction
        extends BaseFEELFunction implements FEELTimeFunction {

    public static final TimeFunction INSTANCE = new TimeFunction();

    public static final DateTimeFormatter FEEL_TIME;

    private static final String timePatternString = "[0-9]{2}[:]{1}[0-9]{2}[:]{1}[0-9]{2}";
    private static final Pattern timePattern = Pattern.compile(timePatternString);

    static {
        FEEL_TIME = new DateTimeFormatterBuilder().parseCaseInsensitive()
                                                  .append(DateTimeFormatter.ISO_LOCAL_TIME)
                                                  .optionalStart()
                                                  .appendLiteral("@")
                                                  .appendZoneRegionId()
                                                  .optionalEnd()
                                                  .optionalStart()
                                                  .appendOffsetId()
                                                  .optionalEnd()
                                                  .toFormatter()
                                                  .withResolverStyle(ResolverStyle.STRICT);
    }

    public static boolean timeStringWithSeconds(String val) {
        return timePattern.matcher(val).find();
    }

    private static final BigDecimal NANO_MULT = BigDecimal.valueOf( 1000000000 );


    protected TimeFunction() {
        super(FEELConversionFunctionNames.TIME);
    }

    public FEELFnResult<TemporalAccessor> invoke(@ParameterName("from") String val) {
        if ( val == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        try {
            TemporalAccessor parsed = FEEL_TIME.parse(val);

            if (parsed.query(TemporalQueries.offset()) != null) {
                // it is an offset-zoned time, so I can know for certain an OffsetTime
                OffsetTime asOffSetTime = parsed.query(OffsetTime::from);
                return FEELFnResult.ofResult(asOffSetTime);
            } else if (parsed.query(TemporalQueries.zone()) == null) {
                // if it does not contain any zone information at all, then I know for certain is a local time.
                LocalTime asLocalTime = parsed.query(LocalTime::from);
                return FEELFnResult.ofResult(asLocalTime);
            } else if (parsed.query(TemporalQueries.zone()) != null) {
                boolean hasSeconds = timeStringWithSeconds(val);
                LocalTime asLocalTime = parsed.query(LocalTime::from);
                ZoneId zoneId = parsed.query(TemporalQueries.zone());
                ZoneTime zoneTime = ZoneTime.of(asLocalTime, zoneId, hasSeconds);
                return FEELFnResult.ofResult(zoneTime);
            }
            return FEELFnResult.ofResult(parsed);
        } catch (DateTimeException e) {
            return manageDateTimeException(e, val);
        }
    }

    public FEELFnResult<TemporalAccessor> invoke(
            @ParameterName("hour") Number hour, @ParameterName("minute") Number minute,
            @ParameterName("second") Number seconds) {
        return invoke( hour, minute, seconds, null );
    }

    public FEELFnResult<TemporalAccessor> invoke(
            @ParameterName("hour") Number hour, @ParameterName("minute") Number minute,
            @ParameterName("second") Number seconds, @ParameterName("offset") Duration offset) {
        if ( hour == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "hour", "cannot be null"));
        }
        if ( minute == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "minute", "cannot be null"));
        }
        if ( seconds == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "seconds", "cannot be null"));
        }
        
        try {
            int nanosecs = 0;
            if( seconds instanceof BigDecimal ) {
                BigDecimal secs = (BigDecimal) seconds;
                nanosecs = secs.subtract( secs.setScale( 0, RoundingMode.DOWN ) ).multiply( NANO_MULT ).intValue();
            }

            if ( offset == null ) {
                return FEELFnResult.ofResult( LocalTime.of( hour.intValue(), minute.intValue(), seconds.intValue(),
                                                            nanosecs ) );
            } else {
                return FEELFnResult.ofResult( OffsetTime.of( hour.intValue(), minute.intValue(), seconds.intValue(),
                                                             nanosecs,
                                              ZoneOffset.ofTotalSeconds( (int) offset.getSeconds() ) ) );
            }
        } catch (DateTimeException e) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "time-parsing exception", e));
        }
    }

    public FEELFnResult<TemporalAccessor> invoke(@ParameterName("from") TemporalAccessor date) {
        if ( date == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        
        try {
            // If the temporal accessor type doesn't support time, try to parse it as a date with UTC midnight.
            if (!date.isSupported(ChronoField.HOUR_OF_DAY)) {
                return BuiltInFunctions.getFunction( DateAndTimeFunction.class ).invoke( date, OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC) )
                        .cata( overrideLeft -> FEELFnResult.ofError(new InvalidParametersEvent(FEELEvent.Severity.ERROR, "from", "time-parsing exception")),
                                this::invoke
                        );
            } else if( date.query( TemporalQueries.offset() ) == null ) {
                return FEELFnResult.ofResult( LocalTime.from( date ) );
            } else {
                ZoneId zone = date.query(TemporalQueries.zoneId());
                if (!(zone instanceof ZoneOffset)) {
                    // TZ is a ZoneRegion, so do NOT normalize (although the result will be unreversible, but will keep what was supplied originally).
                    // Unfortunately java.time.Parsed is a package-private class, hence will need to re-parse in order to have it instantiated. 
                    return invoke(date.query(TemporalQueries.localTime()) + "@" + zone);
                } else {
                    return FEELFnResult.ofResult(OffsetTime.from(date));
                }
            }
        } catch (DateTimeException e) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "time-parsing exception", e));
        }
    }

    protected FEELFnResult<TemporalAccessor> manageDateTimeException(DateTimeException e, String val) {
        return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", e));
    }

}
