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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.kie.dmn.feel.lang.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateTimeEvalHelper {
    public static final Logger LOG = LoggerFactory.getLogger( DateTimeEvalHelper.class );

    public static ZonedDateTime coerceDateTime(final LocalDate value) {
        return ZonedDateTime.of(value, LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
    }
    
    public static String toParsableString(TemporalAccessor temporalAccessor) {
        int hour = temporalAccessor.get(ChronoField.HOUR_OF_DAY);
        int minute = temporalAccessor.get(ChronoField.MINUTE_OF_HOUR);
        int second = temporalAccessor.get(ChronoField.SECOND_OF_MINUTE);
        ZoneId query = temporalAccessor.query(TemporalQueries.zoneId());
        return String.format("%02d:%02d:%02d@%s", hour, minute, second, query.getId());
    }

    /**
     * DMNv1.2 10.3.2.3.6 date-time, valuedt(date and time), for use in this {@link BooleanEvalHelper#compare(Object, Object, EvaluationContext, BiPredicate)}
     * DMNv1.3 also used for equality DMN13-35
     */
     static long valuedt(TemporalAccessor datetime, ZoneId otherTimezoneOffset) {
        ZoneId alternativeTZ = Optional.ofNullable(otherTimezoneOffset).orElse(ZoneOffset.UTC);
        if (datetime instanceof LocalDateTime) {
            return ((LocalDateTime) datetime).atZone(alternativeTZ).toEpochSecond();
        } else if (datetime instanceof ZonedDateTime) {
            return ((ZonedDateTime) datetime).toEpochSecond();
        } else if (datetime instanceof OffsetDateTime) {
            return ((OffsetDateTime) datetime).toEpochSecond();
        } else {
            throw new RuntimeException("valuedt() for " + datetime + " but is not a FEEL date and time " + datetime.getClass());
        }
    }

    /**
     * DMNv1.2 10.3.2.3.4 time, valuet(time), for use in this {@link BooleanEvalHelper#compare(Object, Object, EvaluationContext, BiPredicate)}
     * DMNv1.3 also used for equality DMN13-35
     */
     static long valuet(TemporalAccessor time) {
        long result = 0;
        result += time.get(ChronoField.HOUR_OF_DAY) * (60 * 60);
        result += time.get(ChronoField.MINUTE_OF_HOUR) * (60);
        result += time.get(ChronoField.SECOND_OF_MINUTE);
        return result;
    }

}
