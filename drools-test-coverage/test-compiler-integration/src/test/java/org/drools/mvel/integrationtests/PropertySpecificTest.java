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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.base.reteoo.PropertySpecificUtil;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.base.base.ObjectType;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.EmptyBitMask;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message.Level;
import org.kie.api.definition.type.FactType;
import org.kie.api.definition.type.Modifies;
import org.kie.api.definition.type.PropertyReactive;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.*;
import static org.drools.base.reteoo.PropertySpecificUtil.calculateNegativeMask;
import static org.drools.base.reteoo.PropertySpecificUtil.calculatePositiveMask;

@RunWith(Parameterized.class)
public class PropertySpecificTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public PropertySpecificTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    public static List<String> getSettableProperties(InternalWorkingMemory workingMemory, ObjectTypeNode objectTypeNode) {
        return getSettableProperties(workingMemory.getKnowledgeBase(), objectTypeNode);
    }

    public static List<String> getSettableProperties(InternalRuleBase kBase, ObjectTypeNode objectTypeNode) {
        return PropertySpecificUtil.getAccessibleProperties( kBase, getNodeClass( objectTypeNode ) );
    }

    public static Class<?> getNodeClass( ObjectTypeNode objectTypeNode ) {
        ObjectType objectType = objectTypeNode.getObjectType();
        return objectType instanceof ClassObjectType ? ((ClassObjectType)objectType).getClassType() : null;
    }

    @Test
    public void testRTNodeEmptyLHS() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "rule r1\n" +
                        "when\n" +
                        "then\n" +
                        "end\n";
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "InitialFactImpl" );
        assertThat(otn).isNotNull();

        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(rtNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
    }   
    
    @Test
    public void testRTNodeNoConstraintsNoPropertySpecific() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person()\n" +
                      "then\n" +
                      "end\n";
        
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);

        ObjectTypeNode otn = getObjectTypeNode(kbase, "Person" );
        assertThat(otn).isNotNull();

        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(rtNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
    }   
    
    @Test
    public void testRTNodeWithConstraintsNoPropertySpecific() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person( name == 'bobba')\n" +
                      "then\n" +
                      "end\n";
        
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "Person" );
        assertThat(otn).isNotNull();

        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(alphaNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        
        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) alphaNode.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(rtNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
    }  
    
    @Test
    public void testBetaNodeNoConstraintsNoPropertySpecific() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "import " + Cheese.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person()\n" +
                      "   Cheese()\n" +
                      "then\n" +
                      "end\n";
        
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "Cheese" );
        assertThat(otn).isNotNull();

        BetaNode betaNode = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
    }    
    
    @Test
    public void testBetaNodeWithConstraintsNoPropertySpecific() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "import " + Cheese.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person()\n" +
                      "   Cheese( type == 'brie' )\n" +
                      "then\n" +
                      "end\n";
        
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "Cheese" );
        assertThat(otn).isNotNull();

        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(alphaNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        BetaNode betaNode = ((RightInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
    }  
    
    @Test
    public void testInitialFactBetaNodeWithRightInputAdapter() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "import " + Cheese.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   exists(eval(1==1))\n" +
                      "then\n" +
                      "end\n";
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "InitialFactImpl" );
        assertThat(otn).isNotNull();
        
        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        BetaNode betaNode = ( BetaNode ) liaNode.getSinkPropagator().getSinks()[1];

        assertThat(betaNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
    }  
    
    @Test
    public void testPersonFactBetaNodeWithRightInputAdapter() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "import " + Cheese.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person()\n" + 
                      "   exists(eval(1==1))\n" +
                      "then\n" +
                      "end\n";

        // assumption is this test was intended to be for the case
        // property reactivity is NOT enabled by default.
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        final KieSession ksession = kbase.newKieSession();
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "Person" );
        assertThat(otn).isNotNull();
        
        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        BetaNode betaNode = ( BetaNode ) liaNode.getSinkPropagator().getSinks()[1];

        assertThat(betaNode.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getInferredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
    }    
    
    @Test
    public void testSharedAlphanodeWithBetaNodeConstraintsNoPropertySpecific() {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + Person.class.getCanonicalName() + "\n" +
                      "import " + Cheese.class.getCanonicalName() + "\n" +
                      "rule r1\n" +
                      "when\n" +
                      "   Person()\n" +
                      "   Cheese( type == 'brie', price == 1.5 )\n" +
                      "then\n" +
                      "end\n"+
                      "rule r2\n" +
                      "when\n" +
                      "   Person()\n" +
                      "   Cheese( type == 'brie', price == 2.5 )\n" +
                      "then\n" +
                      "end\n";
        
        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "Cheese" );
        assertThat(otn).isNotNull();

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(alphaNode1.getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        
        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(AllSetBitMask.get());
        
        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(AllSetBitMask.get());
    }       
    

    private KieBase getKnowledgeBase(String... rules) {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "global java.util.List list;\n" +
                "declare A\n" +
                "    @propertyReactive\n" +
                "    a : int\n" +
                "    b : int\n" +
                "    c : int\n" +
                "    s : String\n" +
                "    i : int\n" +
                "    j : int\n" +
                "    k : int\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    a : int\n" +
                "    b : int\n" +
                "    c : int\n" +
                "    s : String\n" +
                "    i : int\n" +
                "    j : int\n" +
                "    k : int\n" +
                "end\n" +
                "declare C\n" +
                "    @propertyReactive\n" +
                "end\n" +
                "declare D\n" +
                "    @propertyReactive\n" +
                "end\n";

        int i = 0;
        for ( String str : rules ) {
            rule += "rule r" + (i++) + "\n" +
                    "when\n" +
                    str +
                    "then\n" +
                    "end\n";
        }
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();
        return kbase;
    }
    
    @Test
    public void testRtnNoConstraintsNoWatches() {
        String rule1 = "A()";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(rtNode.getInferredMask()).isEqualTo(EmptyBitMask.get());
    }   
    
    @Test
    public void testRtnNoConstraintsWithWatches() {
        String rule1 = "A() @watch(a)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otn.getObjectSinkPropagator().getSinks()[0];
        
        List<String> sp = getSettableProperties(wm, otn);
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(rtNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));        
    }       
    
    @Test
    public void testRtnWithConstraintsNoWatches() {
        String rule1 = "A( a == 10 )";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);
        
        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        
        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) alphaNode.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(EmptyBitMask.get()); // rtn declares nothing
        assertThat(rtNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp)); // rtn infers from alpha 
    }  
    
    @Test
    public void testRtnWithConstraintsWithWatches() {
        String rule1 = "A( a == 10 ) @watch(b)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);
        
        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));
        
        LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) alphaNode.getObjectSinkPropagator().getSinks()[0];
        
        RuleTerminalNode rtNode = ( RuleTerminalNode ) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(rtNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(rtNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));         
    }      
    
    @Test
    public void testRtnSharedAlphaNoWatches() {
        String rule1 = "A( a == 10, b == 15 )";
        String rule2 = "A( a == 10, i == 20 )";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "i"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));  
        
        LeftInputAdapterNode liaNode1 = ( LeftInputAdapterNode ) alphaNode1_1.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtNode1 = ( RuleTerminalNode ) liaNode1.getSinkPropagator().getSinks()[0];

        assertThat(rtNode1.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(rtNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));
        
        
        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i"), sp));
        
        LeftInputAdapterNode liaNode2 = ( LeftInputAdapterNode ) alphaNode1_2.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtNode2 = ( RuleTerminalNode ) liaNode2.getSinkPropagator().getSinks()[0];

        assertThat(rtNode2.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(rtNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i"), sp));
        
        // test rule removal        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i"), sp));

        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i"), sp));

        assertThat(rtNode2.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(rtNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i"), sp));
        
        // have to rebuild to remove r1
        kbase = getKnowledgeBase(rule1, rule2);
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        otn = getObjectTypeNode(kbase, "A" );
        
        alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));   
        
        alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));   
        
        liaNode1 = ( LeftInputAdapterNode ) alphaNode1_1.getObjectSinkPropagator().getSinks()[0];
        rtNode1 = ( RuleTerminalNode ) liaNode1.getSinkPropagator().getSinks()[0];
        assertThat(rtNode1.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(rtNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));         
    }      
    
    @Test
    public void testRtnSharedAlphaWithWatches() {
        String rule1 = "A( a == 10, b == 15 ) @watch(c, !a)";
        String rule2 = "A( a == 10, i == 20 ) @watch(s, !i)";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c", "s", "i"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));  
        
        LeftInputAdapterNode liaNode1 = ( LeftInputAdapterNode ) alphaNode1_1.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtNode1 = ( RuleTerminalNode ) liaNode1.getSinkPropagator().getSinks()[0];

        assertThat(rtNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(rtNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(rtNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));  
        
        LeftInputAdapterNode liaNode2 = ( LeftInputAdapterNode ) alphaNode1_2.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtNode2 = ( RuleTerminalNode ) liaNode2.getSinkPropagator().getSinks()[0];

        assertThat(rtNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(rtNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s"), sp));
        assertThat(rtNode2.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!i"), sp));

        // test rule removal        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));

        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));

        assertThat(rtNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(rtNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s"), sp));
        assertThat(rtNode2.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!i"), sp));

        // have to rebuild to remove r1
        kbase = getKnowledgeBase(rule1, rule2);
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        otn = getObjectTypeNode(kbase, "A" );
        
        alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));   
        
        alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));   
        
        liaNode1 = ( LeftInputAdapterNode ) alphaNode1_1.getObjectSinkPropagator().getSinks()[0];
        rtNode1 = ( RuleTerminalNode ) liaNode1.getSinkPropagator().getSinks()[0];
        assertThat(rtNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(rtNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(rtNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));
    }

    @Test
    public void testBetaNoConstraintsNoWatches() {
        String rule1 = "B() A()";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        BetaNode betaNode = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());

        assertThat(betaNode.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNode.getInferredMask()).isEqualTo(EmptyBitMask.get());
    }     
    
    @Test
    public void testBetaNoConstraintsWithWatches() {
        String rule1 = "B() @watch(a) A() @watch(a)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();
        List<String> sp = getSettableProperties(wm, otn);
        
        BetaNode betaNode = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));

        assertThat(betaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(betaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
    }  
    
    @Test
    public void testBetaWithConstraintsNoWatches() {
        String rule1 = "$b : B(a == 15) A( a == 10, b == $b.b )";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);
        
        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));
        
        BetaNode betaNode = ((RightInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp)); // beta declares nothing
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp)); // beta infers from alpha
        
        otn = getObjectTypeNode(kbase, "B" );
        alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));

        assertThat(betaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));
    }    
    
    @Test
    public void testBetaWithConstraintsWithWatches() {
        String rule1 = "$b : B( a == 15) @watch(c) A( a == 10, b == $b.b ) @watch(s)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);
        
        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "s"), sp));
        
        BetaNode betaNode =((RightInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "s"), sp));
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "s"), sp));
        assertThat(betaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        otn = getObjectTypeNode(kbase, "B" );
        alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        assertThat(betaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));
    }

    @Test
    public void testBetaWithConstraintsWithNegativeWatches() {
        String rule1 = "$b : B( a == 15) @watch(c, !a) A( a == 10, b == $b.b ) @watch(s, !a, !b)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);

        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "s"), sp));

        BetaNode betaNode = ((RightInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "s"), sp));
        assertThat(betaNode.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(betaNode.getRightInput().getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a", "!b"), sp));

        otn = getObjectTypeNode(kbase, "B" );
        alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        assertThat(betaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));
    }

    @Test
    public void testBetaSharedAlphaNoWatches() {
        String rule1 = "$b : B( a == 15) @watch(c, !a) A( a == 10, s == 15, b == $b.b  )";
        String rule2 = "$b : B( a == 15) @watch(j, !i) A( a == 10, i == 20, b == $b.b  )";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "s", "i"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s", "b"), sp));  
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s", "b"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b"), sp));
        
        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "j"), sp));
        assertThat(betaNode2.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!i"), sp));

        // test rule removal        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b"), sp));

        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b"), sp));

        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        // have to rebuild to remove r1
        kbase = getKnowledgeBase(rule1, rule2);
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        otn = getObjectTypeNode(kbase, "A" );
        
        alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s", "b"), sp));   
        
        alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s", "b"), sp));   
        
        betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "s", "b"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "j"), sp));
        assertThat(betaNode2.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!i"), sp));
    }
    
    @Test
    public void testBetaSharedAlphaWithWatches() {
        String rule1 = "$b : B( a == 15) @watch(c, !a) A( a == 10, b == 15, b == $b.b  ) @watch(c, !b)";
        String rule2 = "$b : B( a == 15) @watch(j) A( a == 10, i == 20, b == $b.b ) @watch(s, !a)";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c", "s", "i"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));  
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "c"), sp));
        assertThat(betaNode1.getRightInput().getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!b"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "s"), sp));  
        

        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "s"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i", "b", "s"), sp));
        assertThat(betaNode2.getRightInput().getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        assertThat(betaNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));
        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "j"), sp));
        assertThat(betaNode2.getNegativeMask()).isEqualTo(EmptyBitMask.get());

        // test rule removal        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "s"), sp));

        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "s"), sp));

        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "s"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i", "b", "s"), sp));
        assertThat(betaNode2.getRightInput().getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "j"), sp));
        assertThat(betaNode2.getNegativeMask()).isEqualTo(EmptyBitMask.get());

        // have to rebuild to remove r1
        kbase = getKnowledgeBase(rule1, rule2);
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        otn = getObjectTypeNode(kbase, "A" );
        
        alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));   
        
        alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));   
        
        betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "c"), sp));
        assertThat(betaNode1.getRightInput().getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!b"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "c"), sp));
        assertThat(betaNode1.getNegativeMask()).isEqualTo(calculateNegativeMask(otn.getObjectType(), list("!a"), sp));
    }
    
    @Test
    public void testComplexBetaSharedAlphaWithWatches() {
        String rule1 = "$b : B( b == 15) @watch(i) A( a == 10, b == 15 ) @watch(c)";
        String rule2 = "$b : B( b == 15) @watch(j) A( a == 10, i == 20 ) @watch(s)";
        String rule3 = "$b : B( c == 15) @watch(k) A( a == 10, i == 20, b == 10 ) @watch(j)";
        KieBase kbase = getKnowledgeBase(rule1, rule2, rule3);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c", "s", "i", "j"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));  
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "i"), sp));
        
        // second share
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "i", "s", "j"), sp));  
        

        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[1]).getBetaNode();
        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
        
        // third share        
        AlphaNode alphaNode1_4 = ( AlphaNode ) alphaNode1_2.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_4.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_4.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "i", "j"), sp));          
        

        BetaNode betaNode3 = ((RightInputAdapterNode)alphaNode1_4.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode3.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode3.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "j"), sp));

        assertThat(betaNode3.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("k"), sp));
        assertThat(betaNode3.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c", "k"), sp));
    }   
    
    @Test
    public void testComplexBetaSharedAlphaWithWatchesRemoveR1() {
        String rule1 = "$b : B( b == 15) @watch(i) A( a == 10, b == 15 ) @watch(c)";
        String rule2 = "$b : B( b == 15) @watch(j) A( a == 10, i == 20 ) @watch(s)";
        String rule3 = "$b : B( c == 15) @watch(k) A( a == 10, i == 20, b == 10 ) @watch(j)";
        KieBase kbase = getKnowledgeBase(rule1, rule2, rule3);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "s", "j"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "s", "j"), sp));
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[1]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));

        // second split, third alpha
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1_1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "i", "j"), sp));

        BetaNode betaNode3 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode3.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode3.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "j"), sp));

        assertThat(betaNode3.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("k"), sp));
        assertThat(betaNode3.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c", "k"), sp));
    }       
    
    @Test
    public void testComplexBetaSharedAlphaWithWatchesRemoveR2() {
        String rule1 = "$b : B( b == 15) @watch(i) A( a == 10, b == 15 ) @watch(c)";
        String rule2 = "$b : B( b == 15) @watch(j) A( a == 10, i == 20 ) @watch(s)";
        String rule3 = "$b : B( c == 15) @watch(k) A( a == 10, i == 20, b == 10 ) @watch(j)";
        KieBase kbase = getKnowledgeBase(rule1, rule2, rule3);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c", "i", "j"), sp));
                
        // first split
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));  
        
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "i"), sp));
        
        // fist share, second alpha
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "j"), sp));  
        
        AlphaNode alphaNode1_3 = ( AlphaNode ) alphaNode1_2.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_3.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_3.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "j"), sp));         
        

        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_3.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "b", "j"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("k"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c", "k"), sp));
           
    }         
    
    @Test
    public void testComplexBetaSharedAlphaWithWatchesRemoveR3() {
        String rule1 = "$b : B( b == 15) @watch(i) A( a == 10, b == 15 ) @watch(c)";
        String rule2 = "$b : B( b == 15) @watch(j) A( a == 10, i == 20 ) @watch(s)";
        String rule3 = "$b : B( c == 15) @watch(k) A( a == 10, i == 20, b == 10 ) @watch(j)";
        KieBase kbase = getKnowledgeBase(rule1, rule2, rule3);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        kbase.removeRule( "org.drools.mvel.integrationtests", "r2" );
        
        ObjectTypeNode otn = getObjectTypeNode(kbase, "A" );
        assertThat(otn).isNotNull();

        List<String> sp = getSettableProperties(wm, otn);        

        AlphaNode alphaNode1 = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c", "i", "s"), sp));
                
        // first share
        AlphaNode alphaNode1_1 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode1_1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(alphaNode1_1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));
        
        // first split
        BetaNode betaNode1 = ((RightInputAdapterNode)alphaNode1_1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(betaNode1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        assertThat(betaNode1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(betaNode1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "i"), sp));
        
        // second split
        AlphaNode alphaNode1_2 = ( AlphaNode ) alphaNode1.getObjectSinkPropagator().getSinks()[1];
        assertThat(alphaNode1_2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("i"), sp));
        assertThat(alphaNode1_2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));
        

        BetaNode betaNode2 = ((RightInputAdapterNode)alphaNode1_2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNode2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("s"), sp));
        assertThat(betaNode2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "i", "s"), sp));

        assertThat(betaNode2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("j"), sp));
        assertThat(betaNode2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b", "j"), sp));
    }    

    @Test
    public void testPropertySpecificSimplified() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "dialect \"mvel\"\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $b : B(s != $s) @watch( ! s, on )\n" +
                "then\n" +
                "    modify($b) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $b : B(on == false)\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "s", "y" );
        ksession.insert( factA );

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        factTypeB.set( factB, "on", false );
        factTypeB.set( factB, "s", "x" );
        ksession.insert( factB );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);

        assertThat(factTypeB.get(factB, "on")).isEqualTo(true);
        assertThat(factTypeB.get(factB, "s")).isEqualTo("y");
        ksession.dispose();
    }

    @Test
    public void testWatchNothing() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "dialect \"mvel\"\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $b : B(s != $s) @watch( !* )\n" +
                "then\n" +
                "    modify($b) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $b : B(on == false)\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "s", "y" );
        ksession.insert(factA);

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        factTypeB.set( factB, "on", false );
        factTypeB.set( factB, "s", "x" );
        ksession.insert(factB);

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);

        assertThat(factTypeB.get(factB, "on")).isEqualTo(true);
        assertThat(factTypeB.get(factB, "s")).isEqualTo("y");
        ksession.dispose();
    }

    @Test
    public void testWrongPropertyNameInWatchAnnotation() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "dialect \"mvel\"\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $b : B(s != $s) @watch( !s1, on )\n" +
                "then\n" +
                "    modify($b) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $b : B(on == false)\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "end\n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, rule);
        assertThat(kieBuilder.getResults().hasMessages(Level.ERROR)).isTrue();
    }

    @Test
    public void testDuplicatePropertyNamesInWatchAnnotation() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "dialect \"mvel\"\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $b : B(s != $s) @watch( s, !s )\n" +
                "then\n" +
                "    modify($b) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $b : B(on == false)\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "end\n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, rule);
        assertThat(kieBuilder.getResults().hasMessages(Level.ERROR)).isTrue();
    }

    @Test
    public void testWrongUasgeOfWatchAnnotationOnNonPropertySpecificClass() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "dialect \"mvel\"\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "declare B\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $b : B(s != $s) @watch( !s, on )\n" +
                "then\n" +
                "    modify($b) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $b : B(on == false)\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "end\n";

        // The compilation of the above ^ would turn error under the assumption Property Reactivity is NOT enabled by default. 

        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 false,
                                                                 rule);
        assertThat(kieBuilder.getResults().hasMessages(Level.ERROR)).isTrue();
    }

    @Test
    public void testPropertySpecificJavaBean() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.C.class.getCanonicalName() + "\n" +
                "declare A\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    A($s : s)\n" +
                "    $c : C(s != $s)\n" +
                "then\n" +
                "    modify($c) { setS($s) }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $c : C(on == false)\n" +
                "then\n" +
                "    modify($c) { turnOn() }\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "s", "y" );
        ksession.insert( factA );

        C c = new C();
        c.setOn(false);
        c.setS("x");
        ksession.insert( c );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);

        assertThat(c.isOn()).isEqualTo(true);
        assertThat(c.getS()).isEqualTo("y");
        ksession.dispose();
    }

    @Test(timeout = 5000)
    public void testPropertySpecificOnAlphaNode() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.C.class.getCanonicalName() + "\n" +
                "rule R1\n" +
                "when\n" +
                "    $c : C(s == \"test\")\n" +
                "then\n" +
                "    modify($c) { setOn(true) }\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        C c = new C();
        c.setOn(false);
        c.setS("test");
        ksession.insert( c );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        assertThat(c.isOn()).isEqualTo(true);
        ksession.dispose();
    }

    @Test(timeout = 5000)
    public void testPropertySpecificWithUpdate() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.C.class.getCanonicalName() + "\n" +
                "rule R1\n" +
                "when\n" +
                "    $c : C(s == \"test\")\n" +
                "then\n" +
                "   $c.setOn(true);\n" +
                "   update($c);\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        C c = new C();
        c.setOn(false);
        c.setS("test");
        ksession.insert( c );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        assertThat(c.isOn()).isEqualTo(true);
        ksession.dispose();
    }

    @Test
    public void testInfiniteLoop() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.C.class.getCanonicalName() + "\n" +
                "global java.util.concurrent.atomic.AtomicInteger counter\n" +
                "rule R1\n" +
                "when\n" +
                "    $c : C(s == \"test\") @watch( on )\n" +
                "then\n" +
                "    modify($c) { turnOn() }\n" +
                "    if (counter.incrementAndGet() > 10) throw new RuntimeException();\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        AtomicInteger counter = new AtomicInteger(0);
        ksession.setGlobal( "counter", counter );

        C c = new C();
        c.setOn(false);
        c.setS("test");
        ksession.insert( c );

        assertThatThrownBy(() -> ksession.fireAllRules())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception executing consequence for rule \"R1\"");
    }

    @Test
    public void testClassReactive() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "global java.util.concurrent.atomic.AtomicInteger counter\n" +
                "declare B\n" +
                "    @classReactive\n" +
                "    on : boolean\n" +
                "    s : String\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    $b : B(s == \"test\")\n" +
                "then\n" +
                "    modify($b) { setOn(true) }\n" +
                "    if (counter.incrementAndGet() > 10) throw new RuntimeException();\n" +
                "end\n";

        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALWAYS");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        KieSession ksession = kbase.newKieSession();

        AtomicInteger counter = new AtomicInteger(0);
        ksession.setGlobal( "counter", counter );

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        factTypeB.set( factB, "s", "test" );
        factTypeB.set( factB, "on", false );
        ksession.insert( factB );

        assertThatThrownBy(() -> ksession.fireAllRules())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception executing consequence for rule \"R1\"");
    }

    @Test(timeout = 5000)
    public void testSharedWatchAnnotation() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "declare A\n" +
                "    @propertyReactive\n" +
                "    a : int\n" +
                "    b : int\n" +
                "    s : String\n" +
                "    i : int\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    s : String\n" +
                "    i : int\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    $a : A(a == 0) @watch( i )\n" +
                "    $b : B(i == $a.i) @watch( s )\n" +
                "then\n" +
                "    modify($a) { setS(\"end\") }\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "    $a : A(a == 0) @watch( b )\n" +
                "then\n" +
                "    modify($a) { setI(1) }\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "a", 0 );
        factTypeA.set( factA, "b", 0 );
        factTypeA.set( factA, "i", 0 );
        factTypeA.set( factA, "s", "start" );
        ksession.insert( factA );

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        factTypeB.set( factB, "i", 1 );
        factTypeB.set( factB, "s", "start" );
        ksession.insert( factB );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);
        assertThat(factTypeA.get(factA, "s")).isEqualTo("end");
    }

    @PropertyReactive
    public static class C {
        private boolean on;
        private String s;

        public boolean isOn() {
            return on;
        }

        public void setOn(boolean on) {
            this.on = on;
        }

        @Modifies( { "on" } )
        public void turnOn() {
            setOn(true);
        }

        public String getS() {
            return s;
        }

        public void setS(String s) {
            this.s = s;
        }
    }

    @Test
    public void testBetaNodePropagation() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.Hero.class.getCanonicalName() + "\n" +
                "import " + PropertySpecificTest.MoveCommand.class.getCanonicalName() + "\n" +
                "rule \"Move\" when\n" +
                "   $mc : MoveCommand( move == 1 )" +
                "   $h  : Hero( canMove == true )" +
                "then\n" +
                "   modify( $h ) { setPosition($h.getPosition() + 1) };\n" +
                "   retract ( $mc );\n" +
                "   System.out.println( \"Move: \" + $h + \" : \" + $mc );" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        Hero hero = new Hero();
        hero.setPosition(0);
        hero.setCanMove(true);
        ksession.insert(hero);
        ksession.fireAllRules();

        MoveCommand moveCommand = new MoveCommand();
        moveCommand.setMove(1);
        ksession.insert(moveCommand);
        ksession.fireAllRules();

        moveCommand = moveCommand = new MoveCommand();
        moveCommand.setMove(1);
        ksession.insert(moveCommand);
        ksession.fireAllRules();

        assertThat(hero.getPosition()).isEqualTo(2);
    }

    @Test(timeout = 5000)
    public void testPropSpecOnPatternWithThis() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "declare A\n" +
                "    @propertyReactive\n" +
                "    i : int\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    a : A\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    $b : B();\n" +
                "    $a : A(this == $b.a);\n" +
                "then\n" +
                "    modify($b) { setA(null) };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "i", 1 );
        ksession.insert(factA);

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        factTypeB.set( factB, "a", factA );
        ksession.insert( factB );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
    }

    @Test
    public void testPropSpecOnBetaNode() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "declare A\n" +
                "    @propertyReactive\n" +
                "    i : int\n" +
                "end\n" +
                "declare B\n" +
                "    @propertyReactive\n" +
                "    i : int\n" +
                "    j : int\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    $a : A()\n" +
                "    $b : B($i : i < 4, j < 2, j == $a.i)\n" +
                "then\n" +
                "    modify($b) { setI($i+1) };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        FactType typeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        FactType typeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );

        Object a1 = typeA.newInstance();
        typeA.set( a1, "i", 1 );
        ksession.insert( a1 );

        Object a2 = typeA.newInstance();
        typeA.set( a2, "i", 2 );
        ksession.insert( a2 );

        Object b1 = typeB.newInstance();
        typeB.set( b1, "i", 1 );
        typeB.set( b1, "j", 1 );
        ksession.insert( b1 );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(3);
    }

    @Test(timeout = 5000)
    public void testConfig() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "declare A\n" +
                "    i : int\n" +
                "    j : int\n" +
                "end\n" +
                "rule R1\n" +
                "when\n" +
                "    $a : A(i == 1)\n" +
                "then\n" +
                "    modify($a) { setJ(2) };\n" +
                "end\n";

        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALWAYS");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        KieSession ksession = kbase.newKieSession();

        FactType typeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object a = typeA.newInstance();
        typeA.set( a, "i", 1 );
        ksession.insert( a );

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
    }

    @Test
    public void testEmptyBetaConstraint() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.Hero.class.getCanonicalName() + "\n" +
                "import " + PropertySpecificTest.Cell.class.getCanonicalName() + "\n" +
                "import " + PropertySpecificTest.Init.class.getCanonicalName() + "\n" +
                "import " + PropertySpecificTest.CompositeImageName.class.getCanonicalName() + "\n" +
                "declare CompositeImageName\n" +
                "   @propertyReactive\n" +
                "end\n" +
                "rule \"Show First Cell\" when\n" +
                "   Init()\n" +
                "   $c : Cell( row == 0, col == 0 )\n" +
                "then\n" +
                "   modify( $c ) { hidden = false };\n" +
                "end\n" +
                "\n" +
                "rule \"Paint Empty Hero\" when\n" +
                "   $c : Cell()\n" +
                "   $cin : CompositeImageName( cell == $c )\n" +
                "   not Hero( row == $c.row, col == $c.col  )\n" +
                "then\n" +
                "   modify( $cin ) { hero = \"\" };\n" +
                "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Init());

        Cell cell = new Cell();
        cell.setRow(0);
        cell.setCol(0);
        cell.hidden = true;
        ksession.insert(cell);

        Hero hero = new Hero();
        hero.setRow(1);
        hero.setCol(1);
        ksession.insert(hero);

        CompositeImageName cin = new CompositeImageName();
        cin.setHero("hero");
        cin.setCell(cell);
        ksession.insert(cin);

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);
    }

    @Test (timeout = 5000)
    public void testNoConstraint() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.Cell.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "   $c : Cell()\n" +
                "then\n" +
                "   modify( $c ) { hidden = true };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Cell());

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
    }

    @Test(timeout = 5000)
    public void testNodeSharing() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import " + PropertySpecificTest.Cell.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "   $c : Cell()\n" +
                "then\n" +
                "   modify( $c ) { hidden = true };\n" +
                "   System.out.println( \"R1\");\n" +
                "end\n" +
                "rule R2 when\n" +
                "   $c : Cell(hidden == true)\n" +
                "then\n" +
                "   System.out.println( \"R2\");\n" +
                "end\n" +
                "rule R3 when\n" +
                "   $c : Cell(hidden == true, row == 0)\n" +
                "then\n" +
                "   modify( $c ) { setCol(1) };\n" +
                "   System.out.println( \"R3\");\n" +
                "end\n" +
                "rule R4 when\n" +
                "   $c : Cell(hidden == true, col == 1)\n" +
                "then\n" +
                "   modify( $c ) { setRow(1) };\n" +
                "   System.out.println( \"R4\");\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Cell());

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(4);
    }

    @PropertyReactive
    public static class Init { }

    @PropertyReactive
    public static class Hero {
        private boolean canMove;
        private int position;
        private int col;
        private int row;

        public boolean isCanMove() {
            return canMove;
        }

        public void setCanMove(boolean canMove) {
            this.canMove = canMove;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public int getCol() {
            return col;
        }

        public void setCol(int col) {
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        @Override
        public String toString() {
            return "Hero{" + "position=" + position + '}';
        }
    }

    @PropertyReactive
    public static class MoveCommand {
        private int move;

        public int getMove() {
            return move;
        }

        public void setMove(int move) {
            this.move = move;
        }

        @Override
        public String toString() {
            return "MoveCommand{" + "move=" + move + '}';
        }
    }

    @PropertyReactive
    public static class Cell {
        private int col;
        private int row;
        public boolean hidden;

        public int getCol() {
            return col;
        }

        public void setCol(int col) {
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }
    }

    public static class CompositeImageName {
        private Cell cell;
        public String hero;

        public Cell getCell() {
            return cell;
        }

        public void setCell(Cell cell) {
            this.cell = cell;
        }

        public String getHero() {
            return hero;
        }

        public void setHero(String hero) {
            this.hero = hero;
        }
    }    

    <T> List<T> list(T... items) {
        return Arrays.asList(items);
    }

    public ObjectTypeNode getObjectTypeNode(KieBase kbase, String nodeName) {
        List<ObjectTypeNode> nodes = ((InternalRuleBase)kbase).getRete().getObjectTypeNodes();
        for ( ObjectTypeNode n : nodes ) {
            if (((ClassObjectType) n.getObjectType()).getClassType().getSimpleName().equals( nodeName ) ) {
                return n;
            }
        }
        return null;
    }
    
    @Test(timeout = 5000)
    public void testNoConstraint2() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + PropertySpecificTest.Order.class.getCanonicalName() + "\n" +
                      "import " + PropertySpecificTest.OrderItem.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "   $o : Order()\n" +
                "   $i : OrderItem( orderId == $o.id, quantity > 2 )\n" +
                "then\n" +
                "   modify( $o ) { setDiscounted( true ) };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        Order order1 = new Order("1");
        OrderItem orderItem11 = new OrderItem("1", 1, 1.1);
        OrderItem orderItem12 = new OrderItem("1", 2, 1.2);
        OrderItem orderItem13 = new OrderItem("1", 3, 1.3);
        order1.setItems(list(orderItem11, orderItem12, orderItem13));
        ksession.insert(order1);
        ksession.insert(orderItem11);
        ksession.insert(orderItem12);
        ksession.insert(orderItem13);

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        assertThat(order1.isDiscounted()).isTrue();
    }

    @Test(timeout = 5000)
    public void testFrom() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + PropertySpecificTest.Order.class.getCanonicalName() + "\n" +
                      "import " + PropertySpecificTest.OrderItem.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "   $o : Order()\n" +
                "   $i : OrderItem( $price : price, quantity > 1 ) from $o.items\n" +
                "then\n" +
                "   modify( $o ) { setDiscounted( true ) };\n" +
                "   modify( $i ) { setPrice( $price - 0.1 ) };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        Order order1 = new Order("1");
        OrderItem orderItem11 = new OrderItem("1", 1, 1.1);
        OrderItem orderItem12 = new OrderItem("1", 2, 1.2);
        OrderItem orderItem13 = new OrderItem("1", 3, 1.3);
        order1.setItems(list(orderItem11, orderItem12, orderItem13));
        ksession.insert(order1);
        ksession.insert(orderItem11);
        ksession.insert(orderItem12);
        ksession.insert(orderItem13);

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);
        assertThat(orderItem11.getPrice()).isCloseTo(1.1, within(0.005));
        assertThat(orderItem12.getPrice()).isCloseTo(1.1, within(0.005));
        assertThat(orderItem13.getPrice()).isCloseTo(1.2, within(0.005));
        assertThat(order1.isDiscounted()).isTrue();
    }

    @Test(timeout = 5000)
    public void testAccumulate() throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                      "import " + PropertySpecificTest.Order.class.getCanonicalName() + "\n" +
                      "import " + PropertySpecificTest.OrderItem.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "   $o : Order()\n" +
                "   $i : Number( doubleValue > 5 ) from accumulate( OrderItem( orderId == $o.id, $value : value ),\n" +
                "                                                   sum( $value ) )\n" +
                "then\n" +
                "   modify( $o ) { setDiscounted( true ) };\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        Order order1 = new Order("1");
        OrderItem orderItem11 = new OrderItem("1", 1, 1.1);
        OrderItem orderItem12 = new OrderItem("1", 2, 1.2);
        OrderItem orderItem13 = new OrderItem("1", 3, 1.3);
        order1.setItems(list(orderItem11, orderItem12, orderItem13));
        ksession.insert(order1);
        ksession.insert(orderItem11);
        ksession.insert(orderItem12);
        ksession.insert(orderItem13);

        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        assertThat(order1.isDiscounted()).isTrue();
    }

    @PropertyReactive
    public static class Order {
        private String id;
        private List<OrderItem> items;
        private boolean discounted;

        public Order(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public List<OrderItem> getItems() {
            return items;
        }

        public void setItems(List<OrderItem> items) {
            this.items = items;
        }

        public boolean isDiscounted() {
            return discounted;
        }
        public void setDiscounted(boolean discounted) {
            this.discounted = discounted;
        }
    }

    @PropertyReactive
    public static class OrderItem {
        private String orderId;
        private int quantity;
        private double price;

        public OrderItem(String orderId, int quantity, double price) {
            this.orderId = orderId;
            this.quantity = quantity;
            this.price = price;
        }

        public String getOrderId() {
            return orderId;
        }
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public int getQuantity() {
            return quantity;
        }
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getPrice() {
            return price;
        }
        public void setPrice(double price) {
            this.price = price;
        }

        public double getValue() {
            return price * quantity;
        }
    }

    @Test
    public void testBetaWithWatchAfterBeta() {
        String rule1 = "$b : B(a == 15) @watch(k) C() A(i == $b.j) @watch(b, c)";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otnA = getObjectTypeNode(kbase, "A" );
        ObjectTypeNode otnC = getObjectTypeNode(kbase, "C" );
        List<String> sp = getSettableProperties(wm, otnA);

        BetaNode betaNodeA = ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNodeA.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("i", "b", "c"), sp));
        assertThat(betaNodeA.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("i", "b", "c"), sp));
        assertThat(betaNodeA.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeA.getInferredMask()).isEqualTo(AllSetBitMask.get());

        BetaNode betaNodeC = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNodeC.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnC.getObjectType(), list("j", "k"), sp));
        assertThat(betaNodeC.getInferredMask()).isEqualTo(calculatePositiveMask(otnC.getObjectType(), list("a", "j", "k"), sp));
    }

    @Test
    public void testBetaAfterBetaWithWatch() {
        String rule1 = "$b : B(a == 15) @watch(k) A(i == $b.j) @watch(b, c) C()";
        KieBase kbase = getKnowledgeBase(rule1);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otnA = getObjectTypeNode(kbase, "A" );
        ObjectTypeNode otnC = getObjectTypeNode(kbase, "C" );
        List<String> sp = getSettableProperties(wm, otnA);

        BetaNode betaNodeA =  ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNodeA.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("i", "b", "c"), sp));
        assertThat(betaNodeA.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("i", "b", "c"), sp));
        assertThat(betaNodeA.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("j", "k"), sp));
        assertThat(betaNodeA.getInferredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("a", "j", "k"), sp));

        BetaNode betaNodeC =  ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        assertThat(betaNodeC.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeC.getInferredMask()).isEqualTo(AllSetBitMask.get());
    }

    @Test
    public void test2DifferentAlphaWatchBeforeSameBeta() {
        String rule1 = "B(a == 15) @watch(b) C()";
        String rule2 = "B(a == 15) @watch(c) C()";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otn = getObjectTypeNode(kbase, "B" );
        List<String> sp = getSettableProperties(wm, otn);

        AlphaNode alphaNode = ( AlphaNode ) otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b", "c"), sp));

        ObjectTypeNode otnC = getObjectTypeNode(kbase, "C" );
        BetaNode betaNodeC1 = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        BetaNode betaNodeC2 = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[1]).getBetaNode();

        LeftInputAdapterNode lia1 = (LeftInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[0];
        assertThat(lia1.getSinkPropagator().getSinks()[0]).isSameAs(betaNodeC1);
        LeftInputAdapterNode lia2 = (LeftInputAdapterNode)alphaNode.getObjectSinkPropagator().getSinks()[1];
        assertThat(lia2.getSinkPropagator().getSinks()[0]).isSameAs(betaNodeC2);

        assertThat(betaNodeC1.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC1.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("b"), sp));
        assertThat(betaNodeC1.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "b"), sp));

        assertThat(betaNodeC2.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC2.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(betaNodeC2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "c"), sp));

        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "c"), sp));

        assertThat(lia2.getSinkPropagator().getSinks().length).isEqualTo(1);
        BetaNode betaNodeC = ( BetaNode ) lia2.getSinkPropagator().getSinks()[0];

        assertThat(betaNodeC2.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC2.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("c"), sp));
        assertThat(betaNodeC2.getInferredMask()).isEqualTo(calculatePositiveMask(otn.getObjectType(), list("a", "c"), sp));
    }

    @Test
    public void testSameBetasWith2RTNSinks() {
        String rule1 = "B(a == 15) C() A()";
        String rule2 = "B(a == 15) C() A() @watch(b, c)";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otnA = getObjectTypeNode(kbase, "A" );
        ObjectTypeNode otnC = getObjectTypeNode(kbase, "C");
        List<String> sp = getSettableProperties(wm, otnA);

        BetaNode betaNodeC = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        BetaNode betaNodeA1 = ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        BetaNode betaNodeA2 = ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[1]).getBetaNode();
        assertThat(betaNodeA1).isSameAs(betaNodeC.getSinkPropagator().getSinks()[0]);
        assertThat(betaNodeA2).isSameAs(betaNodeC.getSinkPropagator().getSinks()[1]);
        assertThat(betaNodeC).isSameAs(betaNodeA1.getLeftTupleSource());
        assertThat(betaNodeC).isSameAs(betaNodeA2.getLeftTupleSource());

        assertThat(betaNodeC.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getInferredMask()).isEqualTo(calculatePositiveMask(otnA.getObjectType(), list("a"), sp));

        assertThat(betaNodeA1.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeA1.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeA1.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeA1.getInferredMask()).isEqualTo(AllSetBitMask.get());

        assertThat(betaNodeA2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otnC.getObjectType(), list("b", "c"), sp));
        assertThat(betaNodeA2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otnC.getObjectType(), list("b", "c"), sp));
        assertThat(betaNodeA2.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeA2.getInferredMask()).isEqualTo(AllSetBitMask.get());

        kbase.removeRule( "org.drools.mvel.integrationtests", "r0" );
        assertThat(betaNodeC.getSinkPropagator().getSinks().length).isEqualTo(1);
    }

    @Test
    public void testBetaWith2BetaSinks() {
        String rule1 = "B(a == 15) @watch(b) A() @watch(i) C()";
        String rule2 = "B(a == 15) @watch(c) A() @watch(j) D()";
        KieBase kbase = getKnowledgeBase(rule1, rule2);
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode otnB = getObjectTypeNode(kbase, "B" );
        List<String> sp = getSettableProperties(wm, otnB);

        AlphaNode alphaNode = ( AlphaNode ) otnB.getObjectSinkPropagator().getSinks()[0];
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a", "b", "c"), sp));

        ObjectTypeNode otnA = getObjectTypeNode(kbase, "A" );
        BetaNode betaNodeA1 = ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        BetaNode betaNodeA2 = ((RightInputAdapterNode)otnA.getObjectSinkPropagator().getSinks()[1]).getBetaNode();

        assertThat(betaNodeA1.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("i"), sp));
        assertThat(betaNodeA1.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("i"), sp));
        assertThat(betaNodeA1.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("b"), sp));
        assertThat(betaNodeA1.getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a", "b"), sp));

        assertThat(betaNodeA2.getRightInput().getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("j"), sp));
        assertThat(betaNodeA2.getRightInput().getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("j"), sp));
        assertThat(betaNodeA2.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("c"), sp));
        assertThat(betaNodeA2.getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a", "c"), sp));

        ObjectTypeNode otnC = getObjectTypeNode(kbase, "C" );
        BetaNode betaNodeC = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNodeC.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeC.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeC.getInferredMask()).isEqualTo(AllSetBitMask.get());

        ObjectTypeNode otnD = getObjectTypeNode(kbase, "D" );
        BetaNode betaNodeD = ((RightInputAdapterNode)otnC.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        assertThat(betaNodeD.getRightInput().getDeclaredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeD.getRightInput().getInferredMask()).isEqualTo(EmptyBitMask.get());
        assertThat(betaNodeD.getDeclaredMask()).isEqualTo(AllSetBitMask.get());
        assertThat(betaNodeD.getInferredMask()).isEqualTo(AllSetBitMask.get());

        kbase.removeRule( "org.drools.mvel.integrationtests", "r1" );
        assertThat(alphaNode.getDeclaredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a"), sp));
        assertThat(alphaNode.getInferredMask()).isEqualTo(calculatePositiveMask(otnB.getObjectType(), list("a", "b"), sp));
    }

    @Test(timeout = 5000)
    public void testBetaWith2RTNSinksExecNoLoop() throws Exception {
        testBetaWith2RTNSinksExec(false);
    }

    @Test
    public void testBetaWith2RTNSinksExecInfiniteLoop() throws Exception {
        assertThatThrownBy(() -> testBetaWith2RTNSinksExec(true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception executing consequence for rule \"R1\"");
    }

    private void testBetaWith2RTNSinksExec(boolean addInfiniteLoopWatch) throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "global java.util.concurrent.atomic.AtomicInteger counter\n" +
                "declare A\n" +
                "   @propertyReactive\n" +
                "   x : int\n" +
                "end\n" +
                "declare B\n" +
                "   @propertyReactive\n" +
                "end\n" +
                "declare C\n" +
                "   @propertyReactive\n" +
                "   y : int\n" +
                "end\n" +
                "rule R1 when\n" +
                "   A ( x == 1 )\n" +
                "   B ( )\n" +
                (addInfiniteLoopWatch ? "   $c : C ( ) @watch(y)\n" : "   $c : C ( )\n") +
                "then " +
                "   modify( $c ) { setY( 2 ) };\n" +
                "   if (counter.incrementAndGet() > 10) throw new RuntimeException();\n" +
                "end;\n" +
                "rule R2 when\n" +
                "   A ( x == 1 )\n" +
                "   B ( )\n" +
                "   C ( ) @watch(y)\n" +
                "then end;\n" +
                "rule InitA when\n" +
                "   $a : A ( x == 0 )\n" +
                "then\n" +
                "   modify( $a ) { setX( 1 ) };\n" +
                "end;\t\n" +
                "rule InitC salience 1 when\n" +
                "   $c : C ( y == 0 )\n" +
                "then\n" +
                "   modify( $c ) { setY( 1 ) };\n" +
                "end;\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        AtomicInteger counter = new AtomicInteger(0);
        ksession.setGlobal( "counter", counter );

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "x", 0 );
        ksession.insert(factA);

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        ksession.insert(factB);

        FactType factTypeC = kbase.getFactType( "org.drools.mvel.integrationtests", "C" );
        Object factC = factTypeC.newInstance();
        factTypeC.set( factC, "y", 0 );
        ksession.insert(factC);

        try {
            ksession.fireAllRules();
        } finally {
            assertThat(factTypeC.get(factC, "y")).isEqualTo(2);
            ksession.dispose();
        }
    }

    @Test(timeout = 5000)
    public void testBetaWith2BetaSinksExecNoLoop() throws Exception {
        testBetaWith2BetaSinksExec(false);
    }

    @Test
    public void testBetaWith2BetaSinksExecInfiniteLoop() throws Exception {
        assertThatThrownBy(() -> testBetaWith2BetaSinksExec(true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception executing consequence for rule \"R1\"");
    }

    private void testBetaWith2BetaSinksExec(boolean addInfiniteLoopWatch) throws Exception {
        String rule = "package org.drools.mvel.integrationtests\n" +
                "global java.util.concurrent.atomic.AtomicInteger counter\n" +
                "declare A\n" +
                "   @propertyReactive\n" +
                "   x : int\n" +
                "end\n" +
                "declare B\n" +
                "   @propertyReactive\n" +
                "end\n" +
                "declare C\n" +
                "   @propertyReactive\n" +
                "   y : int\n" +
                "end\n" +
                "declare D\n" +
                "   @propertyReactive\n" +
                "end\n" +
                "rule R1 when\n" +
                "   A ( x == 1 )\n" +
                (addInfiniteLoopWatch ? "   $c : C ( ) @watch(y)\n" : "   $c : C ( )\n") +
                "   B ( )\n" +
                "then " +
                "   modify( $c ) { setY( 2 ) };\n" +
                "   if (counter.incrementAndGet() > 10) throw new RuntimeException();\n" +
                "end;\n" +
                "rule R2 when\n" +
                "   A ( x == 1 )\n" +
                "   C ( ) @watch(y)\n" +
                "   D ( )\n" +
                "then end;\n" +
                "rule InitA when\n" +
                "   $a : A ( x == 0 )\n" +
                "then\n" +
                "   modify( $a ) { setX( 1 ) };\n" +
                "end;\t\n" +
                "rule InitC salience 1 when\n" +
                "   $c : C ( y == 0 )\n" +
                "then\n" +
                "   modify( $c ) { setY( 1 ) };\n" +
                "end;\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        AtomicInteger counter = new AtomicInteger(0);
        ksession.setGlobal( "counter", counter );

        FactType factTypeA = kbase.getFactType( "org.drools.mvel.integrationtests", "A" );
        Object factA = factTypeA.newInstance();
        factTypeA.set( factA, "x", 0 );
        ksession.insert(factA);

        FactType factTypeB = kbase.getFactType( "org.drools.mvel.integrationtests", "B" );
        Object factB = factTypeB.newInstance();
        ksession.insert(factB);

        FactType factTypeC = kbase.getFactType( "org.drools.mvel.integrationtests", "C" );
        Object factC = factTypeC.newInstance();
        factTypeC.set( factC, "y", 0 );
        ksession.insert(factC);

        FactType factTypeD = kbase.getFactType( "org.drools.mvel.integrationtests", "D" );
        Object factD = factTypeD.newInstance();
        ksession.insert(factD);

        try {
            ksession.fireAllRules();
        } finally {
            assertThat(factTypeC.get(factC, "y")).isEqualTo(2);
            ksession.dispose();
        }
    }

    @Test(timeout = 5000)
    public void testTypeDeclarationInitializationForPropertyReactive() {
        // JBRULES-3686
        String rule = "package org.drools.mvel.integrationtests\n" +
                "import java.util.Map;\n" +
                "import java.util.EnumMap;\n" +
                "import " + PropertySpecificTest.DataSample.class.getCanonicalName() + ";\n" +
                "import " + PropertySpecificTest.Model.class.getCanonicalName() + ";\n" +
                "import " + PropertySpecificTest.Parameter.class.getCanonicalName() + ";\n" +
                "\n" +
                "rule 'Init'\n" +
                "when\n" +
                "    $m: Model()\n" +
                "then\n" +
                "    insert(new DataSample($m));\n" +
                "end\n" +
                "\n" +
                "rule \"Rule 1\"\n" +
                "when\n" +
                "    $m: Model()\n" +
                "    $d: DataSample(model == $m)\n" +
                "then\n" +
                "    modify($d){\n" +
                "        addValue(Parameter.PARAM_A, 10.0)\n" +
                "    }\n" +
                "end\n" +
                "\n" +
                "rule \"Rule 2\"\n" +
                "when\n" +
                "    $m: Model()\n" +
                "    $d: DataSample(model == $m, $v: values[Parameter.PARAM_A] > 9.0)\n" +
                "then\n" +
                "    modify($d){\n" +
                "        addMessage(\"Hello\")\n" +
                "    }\n" +
                "end\n" +
                "\n" +
                "rule \"Data without messages\"\n" +
                "salience -100\n" +
                "when\n" +
                "    $m: Model()\n" +
                "    $d: DataSample(model == $m, messaged == false)\n" +
                "then\n" +
                "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Model());
        int fired = ksession.fireAllRules(10); // don't cause infinite loop
        assertThat(fired).isEqualTo(3);
    }

    @PropertyReactive
    public static class DataSample {
        private Model model;
        private Map<Parameter, Double> values = new EnumMap<Parameter, Double>(Parameter.class);
        private List<String> messages = new ArrayList<String>();

        public DataSample() {
        }

        public DataSample(Model model) {
            this.model = model;
        }


        public Model getModel() {
            return model;
        }

        public void setModel(Model model) {
            this.model = model;
        }

        public Map<Parameter, Double> getValues() {
            return values;
        }

        public void setValues(Map<Parameter, Double> values) {
            this.values = values;
        }

        @Modifies({"values"})
        public void addValue(Parameter p, double value){
            this.values.put(p, value);
        }

        public boolean isEmpty(){
            return this.values.isEmpty();
        }

        public List<String> getMessages() {
            return messages;
        }

        public void setMessages(List<String> messages) {
            this.messages = messages;
        }

        @Modifies({"messages", "messaged"})
        public void addMessage(String message){
            this.messages.add(message);
        }

        public boolean isMessaged(){
            return !this.messages.isEmpty();
        }

        public void setMessaged(boolean b){
        }
    }

    public static class Model {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    public static enum Parameter {
        PARAM_A, PARAM_B
    }

    @Test
    public void testRemovedPendingActivation() {
        String rule = "declare Person\n" +
                "@propertyReactive\n" +
                "   name   : String\n" +
                "   age    : int\n" +
                "   weight : int\n" +
                "end\n" +
                "\n" +
                "rule kickoff\n" +
                "salience 100\n" +
                "when\n" +
                "then\n" +
                "    Person p = new Person( \"Joe\", 20, 20 );\n" +
                "    insert( p );\n" +
                "end\n" +
                "\n" +
                "rule y\n" +
                "when\n" +
                "    $p : Person(name == \"Joe\" )\n" +
                "then\n" +
                "    modify($p){\n" +
                "       setAge( 100 )\n" +
                "    }\n" +
                "end\n" +
                "\n" +
                "rule x\n" +
                "when\n" +
                "    $p : Person(name == \"Joe\" )\n" +
                "then\n" +
                "    modify($p){\n" +
                "        setWeight( 100 )\n" +
                "    }\n" +
                "end\n" +
                "\n" +
                "rule z\n" +
                "salience -100\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    System.out.println( $p );\n" +
                "    if ($p.getAge() != 100 || $p.getWeight() != 100) throw new RuntimeException();\n" +
                "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule);
        KieSession ksession = kbase.newKieSession();

        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(4);
    }

    public static class LongFact {

        private Long	longVal;
        private Long	longDiff;

        public LongFact( int i) {
            this.longVal = Long.valueOf(i);
        }

        public Long getLongDiff() {
            return longDiff;
        }

        public Long getLongVal() {
            return longVal;
        }

        public void setLongDiff( Long longDiff ) {
            this.longDiff = longDiff;
        }

        public void setLongVal( Long longVal ) {
            this.longVal = longVal;
        }
    }

    public static class BigDecimalFact {

        private BigDecimal bdVal;
        private BigDecimal bdDiff;

        public BigDecimalFact( int i) {
            this.bdVal = new BigDecimal(i);
        }

        public BigDecimal getBdDiff() {
            return bdDiff;
        }

        public BigDecimal getBdVal() {
            return bdVal;
        }

        public void setBdDiff( BigDecimal bdDiff ) {
            this.bdDiff = bdDiff;
        }

        public void setBdVal( BigDecimal bdVal ) {
            this.bdVal = bdVal;
        }
    }

    @Test
    public void testAccLong() {
        String rule =
                "package com.sample.rules\n" +
                "import " + LongFact.class.getCanonicalName() + "\n" +
                "rule R when\n" +
                "        accumulate( LongFact( $longVal: longVal), $minVal : min($longVal))\n" +
                "        accumulate( LongFact( $longVal2: longVal, $longVal2 > $minVal), $minVal2 : min($longVal2))\n" +
                "\n" +
                "        $minFact: LongFact( longVal == $minVal)\n" +
                "        $minFact2: LongFact( longVal == $minVal2)\n" +
                "\n" +
                "then\n" +
                "    Long $difference = (Long)$minVal2 - (Long)$minVal;\n" +
                "    $minFact2.setLongDiff($difference);\n" +
                "    update($minFact2);\n" +
                "    retract($minFact);\n" +
                "end\n";

        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        KieSession kSession = kbase.newKieSession();

        final int NUM = 5;

        for (int i = 0; i < NUM; i++) {
            LongFact fact = new LongFact(100 + i*i*10);
            kSession.insert(fact);
        }

        int cnt = kSession.fireAllRules();
        assertThat(cnt).isEqualTo(NUM-1);
    }

    @Test
    public void testAccBigDecimal() {
        // DROOLS-4896
        String rule =
                "package com.sample.rules\n" +
                "import java.math.BigDecimal;\n" +
                "import " + BigDecimalFact.class.getCanonicalName() + "\n" +
                "rule R when\n" +
                "        accumulate( BigDecimalFact( $bdVal: bdVal), $minVal : min($bdVal))\n" +
                "        accumulate( BigDecimalFact( $bdVal2: bdVal, $bdVal2 > $minVal), $minVal2 : min($bdVal2); $minVal2 != null)\n" + // guard for DROOLS-6064
                "\n" +
                "        \n" +
                "        $minFact: BigDecimalFact( bdVal == new BigDecimal($minVal.intValue()))\n" +
                "        $minFact2: BigDecimalFact( bdVal == new BigDecimal($minVal2.intValue()))\n" +
                "\n" +
                "then\n" +
                "    BigDecimal $difference = new BigDecimal($minVal2.intValue() - $minVal.intValue());\n" +
                "    $minFact2.setBdDiff($difference);\n" +
                "    update($minFact2);\n" +
                "    retract($minFact);\n" +
                "end\n";

        Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put("drools.propertySpecific", "ALLOWED");
        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration,
                                                                 kieModuleConfigurationProperties,
                                                                 rule);
        KieSession kSession = kbase.newKieSession();

        final int NUM = 5;

        for (int i = 0; i < NUM; i++) {
            BigDecimalFact fact = new BigDecimalFact(100 + i*i*10);
            kSession.insert(fact);
        }

        int cnt = kSession.fireAllRules();
        assertThat(cnt).isEqualTo(NUM-1);
    }
}
