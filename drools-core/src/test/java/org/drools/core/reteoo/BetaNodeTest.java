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

import java.util.Collections;

import org.drools.core.common.EmptyBetaConstraints;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.reteoo.builder.BuildContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BetaNodeTest {

    @Test
    public void testEqualsObject() {
        InternalRuleBase kBase = RuleBaseFactory.newRuleBase();

        BuildContext buildContext = new BuildContext( kBase, Collections.emptyList() );

        final LeftTupleSource ts = new MockTupleSource( 1, buildContext );
        final ObjectSource    os    = new MockObjectSource( 2, buildContext );
        RightInputAdapterNode right = new JoinRightAdapterNode(5, os, buildContext);
        
        final BetaNode j1 = new JoinNode( 1,
                                          ts,
                                          right,
                                          EmptyBetaConstraints.getInstance(),
                                          buildContext );
        final BetaNode j2 = new JoinNode( 2,
                                          ts,
                                          right,
                                          EmptyBetaConstraints.getInstance(),
                                          buildContext );
        final BetaNode n1 = new NotNode( 3,
                                         ts,
                                         right,
                                         EmptyBetaConstraints.getInstance(),
                                         buildContext );
        final BetaNode n2 = new NotNode( 4,
                                         ts,
                                         right,
                                         EmptyBetaConstraints.getInstance(),
                                         buildContext );

        assertThat(j1).isEqualTo(j1);
        assertThat(j2).isEqualTo(j2);
        assertThat(j2).isEqualTo(j1);
        assertThat(n1).isEqualTo(n1);
        assertThat(n2).isEqualTo(n2);
        assertThat(n2).isEqualTo(n1);

        assertThat(j1.equals(n1)).isFalse();
        assertThat(j1.equals(n2)).isFalse();
        assertThat(n1.equals(j1)).isFalse();
        assertThat(n1.equals(j2)).isFalse();
    }

}
