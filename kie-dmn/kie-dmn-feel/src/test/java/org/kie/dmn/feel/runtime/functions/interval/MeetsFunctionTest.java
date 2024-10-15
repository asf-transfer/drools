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
package org.kie.dmn.feel.runtime.functions.interval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.runtime.functions.FunctionTestUtil;
import org.kie.dmn.feel.runtime.impl.RangeImpl;

class MeetsFunctionTest {

    private MeetsFunction meetsFunction;

    @BeforeEach
    void setUp() {
        meetsFunction = MeetsFunction.INSTANCE;
    }

    @Test
    void invokeParamIsNull() {
        FunctionTestUtil.assertResultError(meetsFunction.invoke(null, new RangeImpl()), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(meetsFunction.invoke(new RangeImpl(), null), InvalidParametersEvent.class);
    }

    @Test
    void invokeParamsCantBeCompared() {
        FunctionTestUtil.assertResultError( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.CLOSED,  1, 2, Range.RangeBoundary.CLOSED, false, false ) ), InvalidParametersEvent.class );
    }

    @Test
    void invokeParamRangeAndRange() {
        FunctionTestUtil.assertResult( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ) ),
                Boolean.FALSE );
        FunctionTestUtil.assertResult( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.CLOSED, "c", "k", Range.RangeBoundary.CLOSED, false, false ) ),
                Boolean.FALSE );
        FunctionTestUtil.assertResult( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.CLOSED, "f", "k", Range.RangeBoundary.CLOSED, false, false ) ),
                Boolean.TRUE );
        FunctionTestUtil.assertResult( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.OPEN, "g", "k", Range.RangeBoundary.CLOSED, false, false ) ),
                Boolean.FALSE );
        FunctionTestUtil.assertResult( meetsFunction.invoke(
                new RangeImpl( Range.RangeBoundary.CLOSED, "f", "k", Range.RangeBoundary.CLOSED, false, false ),
                new RangeImpl( Range.RangeBoundary.CLOSED, "a", "f", Range.RangeBoundary.CLOSED, false, false ) ),
                Boolean.FALSE );
    }

}