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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufOutputMarshallerTest {

    @Test
    public void testOrderFacts() throws Exception {
        List<InternalFactHandle> list = new ArrayList<InternalFactHandle>();
        List<Integer> ids = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 30, 31, 32, -2147483640,  7, 8, 9, 10, 11, 12, 13,14, 15, 28,  17, 18, 19, 20, 21, 22, 23, 24,  25, 26, 27);
        for(Integer i : ids) {
            list.add(new DefaultFactHandle(i.intValue(), i));
        }
        InternalFactHandle first = ProtobufOutputMarshaller.orderFacts(list)[0];
        assertThat(first.getId()).isEqualTo(-2147483640);        
    }
}
