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
package org.kie.dmn.feel.runtime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.types.impl.ComparablePeriod;
import org.kie.dmn.feel.runtime.impl.RangeImpl;

public class BFEELTest extends BaseFEELTest {

    /**
     * WARNING: do not use as JUNit's @Parameters name the index {1} within this test class, as this would result in invalid character in the XML surefire-report
     * Original error was: An invalid XML character (Unicode: 0x8) was found in the value of attribute "name" and element is "testcase".
     */
    @ParameterizedTest
    @MethodSource("data")
    protected void instanceTest(String expression, Object result, FEELEvent.Severity severity, BaseFEELTest.FEEL_TARGET testFEELTarget, Boolean useExtendedProfile, FEELDialect feelDialect) {
        try {
            expression(expression, result, severity, testFEELTarget, useExtendedProfile, feelDialect);
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }

    private static Collection<Object[]> data() {
        final Object[][] cases = new Object[][] {
                {"\"a\" = 1", false, null},
                {"\"a\" != 1", true, null},
                {"\"a\" < 1", false, null},
                {"\"a\" <= null", false, null},
                {"\"a\" > 1", false, null},
                {"null >= 1", false, null},
                {"not(\"a\")", false, null},
                {"true and \"x\"", false, null},
                {"false or \"x\"", false, null},
                {"\"a\" in [1..100]", false, null},
                {"null between 1 and 100", false, FEELEvent.Severity.ERROR},

                {"matches(\"bad pattern\",\"[0-9\")", false, null},
                {"before(date(\"2021-01-01\"), null)", false, null},
                {"all(true,\"x\",true)", false, null},
                {"any(null)", false, null},

                {"decimal(\"a\", 0)", BigDecimal.ZERO, FEELEvent.Severity.ERROR},
                {"round up(\"5.5\", 0)", BigDecimal.ZERO, FEELEvent.Severity.ERROR},
                {"string length(22)", BigDecimal.ZERO, FEELEvent.Severity.ERROR},
                {"day of year(\"a\")", BigDecimal.ZERO, FEELEvent.Severity.ERROR},
                {"count([1,null,3])", BigDecimal.valueOf(3), null},
                {"sum([1, null, 3])", BigDecimal.valueOf(4), null},
                {"sum([1, \"1\" ,3])", BigDecimal.valueOf(4), null},
                {"sum([])", BigDecimal.ZERO, null},
                {"mean([\"a\"])", BigDecimal.ZERO, null},
                {"mean([1, \"a\", 3])", BigDecimal.valueOf(2), null},

                {"lower case(12)", "", FEELEvent.Severity.ERROR},
                {"string(null)", "", null},
                {"day of week(\"a\")", "", FEELEvent.Severity.ERROR},
                {"substring(\"a\", \"z\")", "", FEELEvent.Severity.ERROR},

                {"time(\"a\")", OffsetTime.of(0, 0, 0, 0, ZoneOffset.ofHoursMinutes(0, 0)), null},
                {"date(null)", LocalDate.of(1970, 1, 1), null},

                {"duration(\"a\")", ComparablePeriod.parse("P0M" ) , null},
                {"years and months duration(null, null)", ComparablePeriod.parse("P0M" ) , null},

                {"split(\"abc\", 22)", Collections.emptyList(), FEELEvent.Severity.ERROR},
                {"mode([null,null,null, 1, 1, 2])", List.of(BigDecimal.ONE), null},

                {"range(\"[x]\")", new RangeImpl(Range.RangeBoundary.OPEN, BigDecimal.ZERO, BigDecimal.ZERO, Range.RangeBoundary.OPEN), null},


        };
        return addAdditionalParametersForBothProfiles(cases, FEELDialect.BFEEL);
    }
}
