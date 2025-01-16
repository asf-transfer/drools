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
package org.drools.serialization.protobuf;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.drools.core.common.InternalFactHandle;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.consequence.InternalMatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// DROOLS-4866
public class MarshalledInternalMatchSortTest {
    private static final String TEST_DATA =
            "ExcChFV:[623, 564, 565]\n" +
            "ExcChFV:[623, 564, 565]\n" +
            "ExcChFV:[623, null, 565]\n" +
            "ExcCh:[579, 565, 564]\n" +
            "ExcCh:[579, null, 564]\n" +
            "ExcCh:[623, 564, 565]\n" +
            "ExcCh:[623, 564, 565]\n" +
            "ExcCh:[623, 564, 565]\n" +
            "ExcCh:[623, null, 565]\n" +
            "ExcChV:[648, 564, 565]\n" +
            "ExcOS:[558, null, null, 534]\n" +
            "ExcOS:[558, null, null, 534]\n" +
            "ExlCo:[551, null, 534]\n" +
            "ExlCo:[551, null, 534]\n" +
            "ExcChHo:[555, 534]\n" +
            "ExcChHo:[555, 534]\n" +
            "StoreAc:[562, 534]\n" +
            "StoreAc:[562, 534]\n" +
            "ExcCh:[579, 565, 564]\n" +
            "ExcCh:[579, 564, 564]\n" +
            "ExcCh:[579, 564, 564]\n" +
            "ExcRe:[651, null, 565]\n" +
            "ExlCr:[630, 565, 565]\n" +
            "ExlCr:[630, 564, 565]\n" +
            "ExlCr:[630, 564, 565]\n" +
            "ExcSe:[639, 565, 565]\n" +
            "ExcSe:[639, 564, 565]\n" +
            "ExcSe:[639, 564, 565]\n" +
            "ExcSe:[639, null, 565]\n" +
            "ExlChV:[648, 565, 565]\n" +
            "ExlChV:[648, 564, 565]\n" +
            "ExlChV:[648, 564, 565]";

    @Test
    public void test() throws IOException {
        List<InternalMatch> as = Lists.newArrayList();
        for (String text : TEST_DATA.split( "\\n" )) {
            ActivationEntry line = parseLine(text);
            InternalMatch a = createActivation(line);
            as.add(a);
        }
        as.sort( ProtobufOutputMarshaller.ActivationsSorter.INSTANCE );
        assertThat(as.get(0).toString()).isEqualTo("ActivationEntry{ruleName='ExcCh', ids=[579, null, 564]}");
    }

    private static class ActivationEntry {
        private final String ruleName;
        private final List<Long> ids;

        private ActivationEntry( String ruleName, List<Long> ids ) {
            this.ruleName = ruleName;
            this.ids = ids;
        }

        @Override
        public String toString() {
            return "ActivationEntry{" +
                    "ruleName='" + ruleName + '\'' +
                    ", ids=" + ids +
                    '}';
        }
    }

    private InternalMatch createActivation(final ActivationEntry line) {
        InternalMatch a = mock(InternalMatch.class);
        RuleImpl rule = mock(RuleImpl.class);
        when(rule.getName()).thenReturn(line.ruleName);
        when(a.getRule()).thenReturn(rule);
        when(a.toString()).thenReturn(line.toString());

        TupleImpl tuple1 = null, tuple2 = null, tuple3 = null, tuple4;
        if (line.ids.size() >= 1) {
            tuple1 = mockTuple(line.ids.get(0));
            when(a.getTuple()).thenReturn(tuple1);
        }

        if (line.ids.size() >= 2) {
            tuple2 = mockTuple(line.ids.get(1));
            when(tuple1.getParent()).thenReturn(tuple2);
        }

        if (line.ids.size() >= 3) {
            tuple3 = mockTuple(line.ids.get(2));
            when(tuple2.getParent()).thenReturn(tuple3);
        }

        if (line.ids.size() >= 4) {
            tuple4 = mockTuple(line.ids.get(3));
            when(tuple3.getParent()).thenReturn(tuple4);
        }
        assertThat(line.ids.size() < 5).isTrue();
        return a;

    }

    private TupleImpl mockTuple(final Long handleId) {
        TupleImpl t = mock(TupleImpl.class);
        if (handleId == null) {
            return t;
        }
        InternalFactHandle h = mock(InternalFactHandle.class);
        when(h.getId()).thenReturn(handleId);
        when(t.getFactHandle()).thenReturn(h);
        return t;
    }

    private ActivationEntry parseLine(final String text) {
        String ruleName = StringUtils.substringBefore(text, ":").trim();
        String idsText = StringUtils.substringAfter(text, ":").trim();
        idsText = idsText.replaceAll("[\\[\\]]", "");
        List<Long> ids = Stream.of(idsText.split( "," )).map(String::trim).map(this::parseLong).collect( Collectors.toList());
        return new ActivationEntry( ruleName, ids );
    }

    private Long parseLong(final String value) {
        if ("null".equals(value)) {
            return null;
        } else {
            return Long.parseLong(value);
        }

    }
}
