/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.dmn.feel.runtime;

import org.junit.runners.Parameterized;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;

public class FEELExtendedFunctionsTest
        extends BaseFEELTest {

    @Parameterized.Parameters(name = "{index}: {0} ({1}) = {2}")
    public static Collection<Object[]> data() {
        final Object[][] cases = new Object[][] {
                { "string(\"Happy %.0fth birthday, Mr %s!\", 38, \"Doe\")", "Happy 38th birthday, Mr Doe!", null},
                { "all( true, true, true )", Boolean.TRUE , null},
                { "all([ true, true, true ])", Boolean.TRUE , null},
                { "all( true, true, false )", Boolean.FALSE , null},
                { "all([ false ])", Boolean.FALSE , null},
                { "any( false, true, false )", Boolean.TRUE , null},
                { "any([ false, true, false ])", Boolean.TRUE , null},
                { "any( false )", Boolean.FALSE , null},
                { "any([ false, false, false ])", Boolean.FALSE , null},
                { "now()", ZonedDateTime.class , null},
                { "today()", LocalDate.class, null },
                { "abs( -10 )", new BigDecimal( "10" ), null },
                { "abs( 20 )", new BigDecimal( "20" ), null },
                { "round( 5.235, 2 )", new BigDecimal( "5.24" ), null },
                { "round( 5.345, 2 )", new BigDecimal( "5.34" ), null },
                { "round( 5.234 )", new BigDecimal( "5" ), null },
                { "roundUp( 1.344, 2 )", new BigDecimal( "1.35" ), null },
                { "roundDown( 1.349, 2 )", new BigDecimal( "1.34" ), null },
                { "integer( 1.5 )", new BigDecimal( "1" ), null },
                { "integer( -1.5 )", new BigDecimal( "-1" ), null },
                { "module( 4, 3 )", new BigDecimal( "1" ), null },
                { "percent( 23 )", new BigDecimal( "0.23" ), null },
                { "power( 2, 3 )", new BigDecimal( "8" ), null },
                { "power( 2, -3 )", new BigDecimal( "0.125" ), null },
                { "product( [ 2, 3, 4 ] )", new BigDecimal( "24" ), null },
                { "product( 2, 3, 4 )", new BigDecimal( "24" ), null },
                { "product( 2 )", new BigDecimal( "2" ), null },
                { "product( [] )", new BigDecimal( "0" ), null },
                { "day( \"2017-07-02\" )", new BigDecimal( "2" ), null },
                { "day( \"2017-07-02T14:40:23\" )", new BigDecimal( "2" ), null },
                { "day( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "2" ), null },
                { "day( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "2" ), null },
                { "day( date( \"2017-07-02\" ) )", new BigDecimal( "2" ), null },
                { "day( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "2" ), null },
                { "month( \"2017-07-02\" )", new BigDecimal( "7" ), null },
                { "month( \"2017-07-02T14:40:23\" )", new BigDecimal( "7" ), null },
                { "month( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "7" ), null },
                { "month( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "7" ), null },
                { "month( date( \"2017-07-02\" ) )", new BigDecimal( "7" ), null },
                { "month( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "7" ), null },
                { "year( \"2017-07-02\" )", new BigDecimal( "2017" ), null },
                { "year( \"2017-07-02T14:40:23\" )", new BigDecimal( "2017" ), null },
                { "year( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "2017" ), null },
                { "year( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "2017" ), null },
                { "year( date( \"2017-07-02\" ) )", new BigDecimal( "2017" ), null },
                { "year( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "2017" ), null },
                { "hour( \"2017-07-02T14:40:23\" )", new BigDecimal( "14" ), null },
                { "hour( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "14" ), null },
                { "hour( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "14" ), null },
                { "hour( \"14:40:23\" )", new BigDecimal( "14" ), null },
                { "hour( \"14:40:23+05:00\" )", new BigDecimal( "14" ), null },
                { "hour( time( \"14:40:23\" ) )", new BigDecimal( "14" ), null },
                { "hour( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "14" ), null },
                { "minute( \"2017-07-02T14:40:23\" )", new BigDecimal( "40" ), null },
                { "minute( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "40" ), null },
                { "minute( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "40" ), null },
                { "minute( \"14:40:23\" )", new BigDecimal( "40" ), null },
                { "minute( \"14:40:23+05:00\" )", new BigDecimal( "40" ), null },
                { "minute( time( \"14:40:23\" ) )", new BigDecimal( "40" ), null },
                { "minute( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "40" ), null },
                { "second( \"2017-07-02T14:40:23\" )", new BigDecimal( "23" ), null },
                { "second( \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "23" ), null },
                { "second( \"2017-07-02T14:40:23Z\" )", new BigDecimal( "23" ), null },
                { "second( \"14:40:23\" )", new BigDecimal( "23" ), null },
                { "second( \"14:40:23+05:00\" )", new BigDecimal( "23" ), null },
                { "second( time( \"14:40:23\" ) )", new BigDecimal( "23" ), null },
                { "second( date and time( \"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "23" ), null },
                { "yearDiff( date and time( \"2015-07-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "2" ), null },
                { "yearDiff( date and time( \"2015-06-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "2" ), null },
                { "yearDiff( date and time( \"2015-08-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "1" ), null },
                { "yearDiff( \"2015-07-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "2" ), null },
                { "yearDiff( \"2015-06-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "2" ), null },
                { "yearDiff( \"2015-08-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "1" ), null },
                { "dayAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), 2 )", ZonedDateTime.of( 2015, 7, 4, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "dayAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), -2 )", ZonedDateTime.of( 2015, 6, 30, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "dayAdd( \"2015-07-02T14:40:23-06:00\", 2 )", ZonedDateTime.of( 2015, 7, 4, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "dayAdd( \"2015-07-02T14:40:23-06:00\", -2 )", ZonedDateTime.of( 2015, 6, 30, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "dayAdd( date( \"2015-07-02\" ), 2 )", LocalDate.of( 2015, 7, 4 ), null },
                { "dayAdd( date( \"2015-07-02\" ), -2 )", LocalDate.of( 2015, 6, 30 ), null },
                { "dayAdd( \"2015-07-02\", 2 )", LocalDate.of( 2015, 7, 4 ), null },
                { "dayAdd( \"2015-07-02\", -2 )", LocalDate.of( 2015, 6, 30 ), null },
                { "dayDiff( date and time( \"2015-07-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "731" ), null },
                { "dayDiff( date and time( \"2015-06-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "761" ), null },
                { "dayDiff( date and time( \"2015-08-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "700" ), null },
                { "dayDiff( \"2015-07-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "731" ), null },
                { "dayDiff( \"2015-06-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "761" ), null },
                { "dayDiff( \"2015-08-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "700" ), null },
                { "dateTime(\"2016-07-29T05:48:23\")", LocalDateTime.of( 2016, 7, 29, 5, 48, 23, 0 ) , null},
                { "dateTime( 2016, 7, 29, 5, 48, 23 )", LocalDateTime.of( 2016, 7, 29, 5, 48, 23, 0 ) , null},
                { "dateTime(\"2016-07-29T05:48:23Z\")", ZonedDateTime.of(2016, 7, 29, 5, 48, 23, 0, ZoneId.of("Z").normalized()) , null},
                { "dateTime( 2016, 7, 29, 5, 48, 23, -5 )", ZonedDateTime.of(2016, 7, 29, 5, 48, 23, 0, ZoneOffset.ofHours( -5 ) ) , null},
                { "dateTime(\"2016-07-29T05:48:23.765-05:00\")", DateTimeFormatter.ISO_DATE_TIME.parse( "2016-07-29T05:48:23.765-05:00", ZonedDateTime::from ) , null},
                { "dateTime(date(\"2016-07-29\"), time(\"05:48:23.765-05:00\") )", DateTimeFormatter.ISO_DATE_TIME.parse( "2016-07-29T05:48:23.765-05:00", ZonedDateTime::from ) , null},
                { "hourDiff( date and time( \"2015-07-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "17544" ), null },
                { "hourDiff( date and time( \"2015-06-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "18264" ), null },
                { "hourDiff( date and time( \"2015-08-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "16800" ), null },
                { "hourDiff( \"2015-07-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "17544" ), null },
                { "hourDiff( \"2015-06-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "18264" ), null },
                { "hourDiff( \"2015-08-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "16800" ), null },
                { "minutesDiff( date and time( \"2015-07-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "1052640" ), null },
                { "minutesDiff( date and time( \"2015-06-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "1095840" ), null },
                { "minutesDiff( date and time( \"2015-08-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "1008000" ), null },
                { "minutesDiff( \"2015-07-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "1052640" ), null },
                { "minutesDiff( \"2015-06-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "1095840" ), null },
                { "minutesDiff( \"2015-08-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "1008000" ), null },
                { "monthAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), 2 )", ZonedDateTime.of( 2015, 9, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "monthAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), -2 )", ZonedDateTime.of( 2015, 5, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "monthAdd( \"2015-07-02T14:40:23-06:00\", 2 )", ZonedDateTime.of( 2015, 9, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "monthAdd( \"2015-07-02T14:40:23-06:00\", -2 )", ZonedDateTime.of( 2015, 5, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "monthAdd( date( \"2015-07-02\" ), 2 )", LocalDate.of( 2015, 9, 2 ), null },
                { "monthAdd( date( \"2015-07-02\" ), -2 )", LocalDate.of( 2015, 5, 2 ), null },
                { "monthAdd( \"2015-07-02\", 2 )", LocalDate.of( 2015, 9, 2 ), null },
                { "monthAdd( \"2015-07-02\", -2 )", LocalDate.of( 2015, 5, 2 ), null },
                { "monthDiff( date and time( \"2015-07-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "24" ), null },
                { "monthDiff( date and time( \"2015-06-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "25" ), null },
                { "monthDiff( date and time( \"2015-08-02T14:40:23-06:00\" ), date and time(\"2017-07-02T14:40:23-06:00\") )", new BigDecimal( "23" ), null },
                { "monthDiff( \"2015-07-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "24" ), null },
                { "monthDiff( \"2015-06-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "25" ), null },
                { "monthDiff( \"2015-08-02T14:40:23-06:00\", \"2017-07-02T14:40:23-06:00\" )", new BigDecimal( "23" ), null },
                { "weekday( \"2016-02-09\" )", new BigDecimal( "2" ), null },
                { "weekday( \"2016-02-09T14:40:23\" )", new BigDecimal( "2" ), null },
                { "weekday( \"2016-02-09T14:40:23-06:00\" )", new BigDecimal( "2" ), null },
                { "weekday( \"2016-02-09T14:40:23Z\" )", new BigDecimal( "2" ), null },
                { "weekday( date( \"2016-02-09\" ) )", new BigDecimal( "2" ), null },
                { "weekday( date and time( \"2016-02-09T14:40:23-06:00\") )", new BigDecimal( "2" ), null },
                { "yearAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), 2 )", ZonedDateTime.of( 2017, 7, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "yearAdd( date and time( \"2015-07-02T14:40:23-06:00\" ), -2 )", ZonedDateTime.of( 2013, 7, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "yearAdd( \"2015-07-02T14:40:23-06:00\", 2 )", ZonedDateTime.of( 2017, 7, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "yearAdd( \"2015-07-02T14:40:23-06:00\", -2 )", ZonedDateTime.of( 2013, 7, 2, 14, 40, 23, 0, ZoneOffset.ofHours( -6 ) ), null },
                { "yearAdd( date( \"2015-07-02\" ), 2 )", LocalDate.of( 2017, 7, 2 ), null },
                { "yearAdd( date( \"2015-07-02\" ), -2 )", LocalDate.of( 2013, 7, 2 ), null },
                { "yearAdd( \"2015-07-02\", 2 )", LocalDate.of( 2017, 7, 2 ), null },
                { "yearAdd( \"2015-07-02\", -2 )", LocalDate.of( 2013, 7, 2 ), null },

        };
        return Arrays.asList( cases );
    }
}
