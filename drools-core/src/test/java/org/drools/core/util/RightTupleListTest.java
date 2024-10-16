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
package org.drools.core.util;

import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.MockLeftTupleSink;
import org.drools.core.reteoo.MockObjectSink;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.test.model.Cheese;
import org.drools.core.util.index.TupleList;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class RightTupleListTest {
    @Test
    public void testEmptyIterator() {
        final TupleList map = new TupleList();
        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        final InternalFactHandle h1 = new DefaultFactHandle( 1,
                                                             stilton1 );

        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("1"), Collections.emptyList());
        assertThat(map.getFirst(new LeftTuple(h1, new MockLeftTupleSink(0, bctx), true ))).isNull();
    }
}
