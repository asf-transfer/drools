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

import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.runtime.Range;

import static org.assertj.core.api.Assertions.assertThat;

class RangeImplTest {

    @Test
    void isWithUndefined() {
        RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, null, null, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isFalse();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, 10, null, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isFalse();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, null, 10, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isFalse();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, null, new UndefinedValueComparable(), Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isTrue();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, new UndefinedValueComparable(), null, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isTrue();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, 10, new UndefinedValueComparable(), Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isTrue();
        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, new UndefinedValueComparable(), 10, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.isWithUndefined()).isTrue();
    }

    @Test
    void getLowBoundary() {
        final Range.RangeBoundary lowBoundary = Range.RangeBoundary.CLOSED;
        final RangeImpl rangeImpl = new RangeImpl(lowBoundary, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.getLowBoundary()).isEqualTo(lowBoundary);
    }

    @Test
    void getLowEndPoint() {
        final Integer lowEndPoint = 1;
        final RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, lowEndPoint, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.getLowEndPoint()).isEqualTo(lowEndPoint);
    }

    @Test
    void getHighEndPoint() {
        final Integer highEndPoint = 15;
        final RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 1, highEndPoint, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.getHighEndPoint()).isEqualTo(highEndPoint);
    }

    @Test
    void getHighBoundary() {
        final Range.RangeBoundary highBoundary = Range.RangeBoundary.CLOSED;
        final RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, highBoundary);
        assertThat(rangeImpl.getHighBoundary()).isEqualTo(highBoundary);
    }

    @Test
    void includes() {
        RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, -15)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 5)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 10)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 12)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 15)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 156)).isFalse();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 10)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 12)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 15)).isFalse();

        rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 10)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 12)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 15)).isTrue();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 10)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 12)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 15)).isTrue();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, new UndefinedValueComparable(), 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, -1456)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 20)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, null)).isNull();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, 15, new UndefinedValueComparable(), Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, -1456)).isFalse();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 20)).isTrue();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, null)).isNull();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, null, new UndefinedValueComparable(), Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, -1456)).isNull();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 20)).isNull();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, null)).isNull();

        rangeImpl = new RangeImpl(Range.RangeBoundary.CLOSED, new UndefinedValueComparable(), null, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl.includes(FEELDialect.FEEL, -1456)).isNull();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, 20)).isNull();
        assertThat(rangeImpl.includes(FEELDialect.FEEL, null)).isNull();
    }

    @Test
    void equals() {
        RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl).isEqualTo(rangeImpl);

        RangeImpl rangeImpl2 = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl2).isEqualTo(rangeImpl);

        rangeImpl2 = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).isNotEqualTo(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl2).isNotEqualTo(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).isNotEqualTo(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 12, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).isNotEqualTo(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 12, 17, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).isNotEqualTo(rangeImpl);

        rangeImpl = new RangeImpl();
        assertThat(rangeImpl).isEqualTo(rangeImpl);
    }

    @Test
    void hashCodeTest() {
        final RangeImpl rangeImpl = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl.hashCode()).isEqualTo(rangeImpl.hashCode());

        RangeImpl rangeImpl2 = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl2.hashCode()).isEqualTo(rangeImpl.hashCode());

        rangeImpl2 = new RangeImpl(Range.RangeBoundary.OPEN, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).doesNotHaveSameHashCodeAs(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.OPEN);
        assertThat(rangeImpl2).doesNotHaveSameHashCodeAs(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 10, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).doesNotHaveSameHashCodeAs(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 12, 15, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).doesNotHaveSameHashCodeAs(rangeImpl);
        rangeImpl2 = new RangeImpl(Range.RangeBoundary.CLOSED, 12, 17, Range.RangeBoundary.CLOSED);
        assertThat(rangeImpl2).doesNotHaveSameHashCodeAs(rangeImpl);
    }
}