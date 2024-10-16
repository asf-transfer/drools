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

import org.drools.base.InitialFact;
import org.drools.base.base.ClassObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.core.common.BaseNode;
import org.drools.core.reteoo.*;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.builder.KieModule;
import org.kie.internal.conf.ParallelExecutionOption;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class NodesPartitioningTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public NodesPartitioningTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void test2Partitions() {
        String drl = ruleA(1) + ruleB(2) + ruleC(2) + ruleD(1) +
                     ruleD(2) + ruleC(1) + ruleA(2) + ruleB(1);
        checkDrl( drl );
    }

    @Test
    public void testPartitioningWithSharedNodes() {
        StringBuilder sb = new StringBuilder( 400 );
        for (int i = 1; i < 4; i++) {
            sb.append( getRule( i ) );
        }
        for (int i = 1; i < 4; i++) {
            sb.append( getNotRule( i ) );
        }
        checkDrl( sb.toString() );
    }

    private void checkDrl(String drl) {
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ParallelExecutionOption.FULLY_PARALLEL);
        Rete rete = kbase.getRete();
        for (EntryPointNode entryPointNode : rete.getEntryPointNodes().values()) {
            traverse( entryPointNode );
        }
    }

    private void traverse(BaseNode node ) {
        checkNode(node);
        NetworkNode[] sinks = node.getSinks();
        if (sinks != null) {
            for (NetworkNode sink : sinks) {
                if (sink instanceof RightInputAdapterNode<?>) {
                    traverse(((RightInputAdapterNode)sink).getBetaNode());
                } else if (sink instanceof BaseNode) {
                    traverse((BaseNode)sink);
                }
            }
        }
    }

    private void checkNode(NetworkNode node) {
        if (node instanceof EntryPointNode) {
            assertThat(node.getPartitionId()).isSameAs(RuleBasePartitionId.MAIN_PARTITION);
        } else if (node instanceof ObjectTypeNode) {
            assertThat(node.getPartitionId()).isSameAs(RuleBasePartitionId.MAIN_PARTITION);
            checkPartitionedSinks((ObjectTypeNode) node);
        } else if (node instanceof RightInputAdapterNode){
            assertThat(node.getPartitionId()).isSameAs(((RightInputAdapterNode)node).getBetaNode().getPartitionId());
        } if (node instanceof ObjectSource ) {
            BaseNode source = ( (ObjectSource) node ).getParent();
            if ( !(source instanceof ObjectTypeNode) ) {
                assertThat(node.getPartitionId()).isSameAs(source.getPartitionId());
            }
        } else if (node instanceof BetaNode ) {
            ObjectSource rightInput = ( (BetaNode) node ).getRightInput().getParent();
            if ( !(rightInput instanceof ObjectTypeNode) ) {
                assertThat(node.getPartitionId()).isSameAs(rightInput.getPartitionId());
            }
            LeftTupleSource leftInput = ( (BetaNode) node ).getLeftTupleSource();
            assertThat(node.getPartitionId()).isSameAs(leftInput.getPartitionId());
        } else if (node instanceof TerminalNode ) {
            LeftTupleSource leftInput = ( (TerminalNode) node ).getLeftTupleSource();
            assertThat(node.getPartitionId()).isSameAs(leftInput.getPartitionId());
        }
    }

    private void checkPartitionedSinks(ObjectTypeNode otn) {
        if ( InitialFact.class.isAssignableFrom( ( (ClassObjectType) otn.getObjectType() ).getClassType() ) ) {
            return;
        }
        ObjectSinkPropagator sinkPropagator = otn.getObjectSinkPropagator();
        ObjectSinkPropagator[] propagators = sinkPropagator instanceof CompositePartitionAwareObjectSinkAdapter ?
                                             ((CompositePartitionAwareObjectSinkAdapter) sinkPropagator).getPartitionedPropagators() :
                                             new ObjectSinkPropagator[] { sinkPropagator };
        for (int i = 0; i < propagators.length; i++) {
            for (ObjectSink sink : propagators[i].getSinks()) {
                assertThat(sink.getPartitionId().getId() % propagators.length).as(sink + " on " + sink.getPartitionId() + " is expcted to be on propagator " + i).isEqualTo(i);
            }
        }
    }

    private String ruleA(int i) {
        return "rule Ra" + i + " when\n" +
               "    $i : Integer( this == " + i + " )\n" +
               "    $s : String( length == $i )\n" +
               "    Integer( this == $s.length )\n" +
               "then end\n";
    }

    private String ruleB(int i) {
        return "rule Rb" + i + " when\n" +
               "    $i : Integer( this == " + i + " )\n" +
               "    $s : String( this == $i.toString )\n" +
               "    Integer( this == $s.length )\n" +
               "then end\n";
    }

    private String ruleC(int i) {
        return "rule Rc" + i + " when\n" +
               "    $i : Integer( this == " + i + " )\n" +
               "    $s : String( length == $i )\n" +
               "    Integer( this == $i+1 )\n" +
               "then end\n";
    }

    private String ruleD(int i) {
        return "rule Rd" + i + " when\n" +
               "    $i : Integer( this == " + i + " )\n" +
               "    $s : String( length == $i )\n" +
               "then end\n";
    }

    private String getRule(int i) {
        return  "rule R" + i + " when\n" +
                "    $i : Integer( this == " + i + " )" +
                "    String( this == $i.toString )\n" +
                "then end\n";
    }

    private String getNotRule(int i) {
        return  "rule Rnot" + i + " when\n" +
                "    String( this == \"" + i + "\" )\n" +
                "    not Integer( this == " + i + " )" +
                "then end\n";
    }

    public static class Account {
        private final int number;
        private final String uuid;
        private final Customer owner;

        public Account( int number, String uuid, Customer owner ) {
            this.number = number;
            this.uuid = uuid;
            this.owner = owner;
        }

        public int getNumber() {
            return number;
        }

        public String getUuid() {
            return uuid;
        }

        public Customer getOwner() {
            return owner;
        }
    }

    public static class Customer {
        private final String uuid;

        public Customer( String uuid ) {
            this.uuid = uuid;
        }

        public String getUuid() {
            return uuid;
        }
    }

    @Test
    public void testChangePartitionOfAlphaSourceOfAlpha() {
        // DROOLS-1487
        String drl =
                "import " + Account.class.getCanonicalName() + ";\n" +
                "import " + Customer.class.getCanonicalName() + ";\n" +
                "rule \"customerDoesNotHaveSpecifiedAccount_2\"\n" +
                "when\n" +
                "    $account : Account (number == 1, uuid == \"customerDoesNotHaveSpecifiedAccount\")\n" +
                "    Customer (uuid == \"customerDoesNotHaveSpecifiedAccount\")\n" +
                "then\n" +
                "end\n" +
                "\n" +
                "rule \"customerDoesNotHaveSpecifiedAccount_1\"\n" +
                "when\n" +
                "    $account : Account (number == 2, uuid == \"customerDoesNotHaveSpecifiedAccount\")\n" +
                "    Customer (uuid == \"customerDoesNotHaveSpecifiedAccount\")\n" +
                "then\n" +
                "end";

        checkDrl( drl );
    }
}
