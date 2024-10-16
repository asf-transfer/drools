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
package org.drools.mvel.integrationtests;

import java.util.Collection;

import org.drools.base.base.ClassObjectType;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.ReteDumper;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class NodePositionInPathTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public NodePositionInPathTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void test() {
        String drl =
                "rule R1 when\n" +
                "    Integer()\n" +
                "    exists( Integer() and Integer() )\n" +
                "    String()\n" +
                "then\n" +
                "end\n" +
                "rule R2 when\n" +
                "    Integer()\n" +
                "    exists( Integer() and String() )\n" +
                "then\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        
        ReteDumper.dumpRete( ( (InternalRuleBase) kbase ));

        Rete rete = ( (InternalRuleBase) kbase ).getRete();
        LeftInputAdapterNode liaNode = null;
        for ( ObjectTypeNode otn : rete.getObjectTypeNodes() ) {
            Class<?> otnType = ( (ClassObjectType) otn.getObjectType() ).getClassType();
            if ( Integer.class == otnType ) {
                liaNode = (LeftInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0];
            }
        }

        assertThat(liaNode.getPathIndex()).isEqualTo(0);

        LeftTupleSink[] liaSinks = liaNode.getSinkPropagator().getSinks();
        BetaNode join1 = (BetaNode)liaSinks[0];
        assertThat(join1.getPathIndex()).isEqualTo(1);

        ExistsNode ex1 = (ExistsNode)liaSinks[1];
        assertThat(ex1.getPathIndex()).isEqualTo(1);
        BetaNode join2 = (BetaNode)ex1.getSinkPropagator().getSinks()[0];
        assertThat(join2.getPathIndex()).isEqualTo(2);
        RuleTerminalNode rtn1 = (RuleTerminalNode)join2.getSinkPropagator().getSinks()[0];
        assertThat(rtn1.getPathIndex()).isEqualTo(3);

        ExistsNode ex2 = (ExistsNode)liaSinks[2];
        assertThat(ex2.getPathIndex()).isEqualTo(1);
        RuleTerminalNode rtn2 = (RuleTerminalNode)ex2.getSinkPropagator().getSinks()[0];
        assertThat(rtn2.getPathIndex()).isEqualTo(2);

        BetaNode join3 = (BetaNode) join1.getSinkPropagator().getSinks()[0];
        assertThat(join3.getPathIndex()).isEqualTo(2);
        TupleToObjectNode ria1 = (TupleToObjectNode) join3.getSinkPropagator().getSinks()[0];
        assertThat(ria1.getPathIndex()).isEqualTo(3);

        BetaNode join4 = (BetaNode) join1.getSinkPropagator().getSinks()[1];
        assertThat(join4.getPathIndex()).isEqualTo(2);
        TupleToObjectNode ria2 = (TupleToObjectNode) join4.getSinkPropagator().getSinks()[0];
        assertThat(ria2.getPathIndex()).isEqualTo(3);

        LeftTupleNode[] rtn1PathNodes = rtn1.getPathNodes();
        assertThat(rtn1PathNodes.length).isEqualTo(4);
        checkNodePosition(rtn1PathNodes, liaNode);
        checkNodePosition(rtn1PathNodes, ex1);
        checkNodePosition(rtn1PathNodes, join2);
        checkNodePosition(rtn1PathNodes, rtn1);

        LeftTupleNode[] rtn2PathNodes = rtn2.getPathNodes();
        assertThat(rtn2PathNodes.length).isEqualTo(3);
        checkNodePosition(rtn2PathNodes, liaNode);
        checkNodePosition(rtn2PathNodes, ex2);
        checkNodePosition(rtn2PathNodes, rtn2);

        LeftTupleNode[] ria1PathNodes = ria1.getPathNodes();
        assertThat(ria1PathNodes.length).isEqualTo(4);
        checkNodePosition(ria1PathNodes, liaNode);
        checkNodePosition(ria1PathNodes, join1);
        checkNodePosition(ria1PathNodes, join3);
        checkNodePosition(ria1PathNodes, ria1);

        LeftTupleNode[] ria2PathNodes = ria2.getPathNodes();
        assertThat(ria2PathNodes.length).isEqualTo(4);
        checkNodePosition(ria2PathNodes, liaNode);
        checkNodePosition(ria2PathNodes, join1);
        checkNodePosition(ria2PathNodes, join4);
        checkNodePosition(ria2PathNodes, ria2);
    }

    private void checkNodePosition(LeftTupleNode[] pathNodes, LeftTupleNode node) {
        assertThat(pathNodes[node.getPathIndex()]).isEqualTo(node);
    }
}
