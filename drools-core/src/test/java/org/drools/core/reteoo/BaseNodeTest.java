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
package org.drools.core.reteoo;

import org.drools.base.common.RuleBasePartitionId;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.BitMask;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseNodeTest {

    @Test
    public void testBaseNode() {
        MockBaseNode node = new MockBaseNode( 10 );
        assertThat(node.getId()).isEqualTo(10);

        node = new MockBaseNode( 155 );
        assertThat(node.getId()).isEqualTo(155);
    }

    class MockBaseNode extends BaseNode {
        private static final long serialVersionUID = 510l;

        public MockBaseNode() {
        }

        public MockBaseNode(final int id) {
            super( id, RuleBasePartitionId.MAIN_PARTITION );
        }

        public void ruleAttached() {
        }

        public void doAttach( BuildContext context ) {

        }

        public void updateNewNode(final InternalWorkingMemory workingMemory,
                                  final PropagationContext context) {
        }

        protected boolean doRemove(final RuleRemovalContext context,
                                   final ReteooBuilder builder) {
            return true;
        }

        @Override
        public BaseNode getParent() {
            return null;
        }

        public boolean isInUse() {
            return true;
        }

        @Override
        public ObjectTypeNode getObjectTypeNode() {
            return null;
        }

        @Override
        public void networkUpdated(UpdateContext updateContext) {           
        }

        public int getType() {
            return 0;
        }

        @Override
        public BitMask getDeclaredMask() {
            return null;
        }

        @Override
        public BitMask getInferredMask() {
            return null;
        }
    }
}
