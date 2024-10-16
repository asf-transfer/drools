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
package org.drools.mvel.compiler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.core.common.TupleSets;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.drools.tms.TruthMaintenanceSystemFactoryImpl;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class MemoryLeakTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public MemoryLeakTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void testStagedTupleLeak() throws Exception {
        // BZ-1056599
        String str =
                "rule R1 when\n" +
                "    $i : Integer()\n" +
                "then\n" +
                "    insertLogical( $i.toString() );\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $i : Integer()\n" +
                "then\n" +
                "    delete( $i );\n" +
                "end\n" +
                "\n" +
                "rule R3 when\n" +
                "    $l : Long()\n" +
                "    $s : String( this == $l.toString() )\n" +
                "then\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        TruthMaintenanceSystemFactoryImpl tms = (TruthMaintenanceSystemFactoryImpl) TruthMaintenanceSystemFactory.get();
        tms.clearEntryPointsMap();

        for ( int i = 0; i < 10; i++ ) {
            ksession.insert( i );
            ksession.fireAllRules();
        }

        Rete rete = ( (InternalRuleBase) kbase ).getRete();
        JoinNode joinNode = null;
        for ( ObjectTypeNode otn : rete.getObjectTypeNodes() ) {
            if ( String.class == otn.getObjectType().getValueType().getClassType() ) {
                joinNode = ((JoinRightAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
                break;
            }
        }

        assertThat(joinNode).isNotNull();
        InternalWorkingMemory wm                = (InternalWorkingMemory) ksession;
        BetaMemory memory            = (BetaMemory) wm.getNodeMemory(joinNode);
        TupleSets stagedRightTuples = memory.getStagedRightTuples();
        assertThat(stagedRightTuples.getDeleteFirst()).isNull();
        assertThat(stagedRightTuples.getInsertFirst()).isNull();

        // DROOLS-6809
        assertThat(tms.getEntryPointsMapSize()).isEqualTo(1);
        ksession.dispose();
        assertThat(tms.getEntryPointsMapSize()).isEqualTo(0);
    }

    @Test
    public void testStagedLeftTupleLeak() throws Exception {
        // BZ-1058874
        String str =
                "rule R1 when\n" +
                "    String( this == \"this\" )\n" +
                "    String( this == \"that\" )\n" +
                "then\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();
        ksession.fireAllRules();

        for ( int i = 0; i < 10; i++ ) {
            FactHandle fh = ksession.insert( "this" );
            ksession.fireAllRules();
            ksession.delete( fh );
            ksession.fireAllRules();
        }

        Rete rete = ( (InternalRuleBase) kbase ).getRete();
        LeftInputAdapterNode liaNode = null;
        for ( ObjectTypeNode otn : rete.getObjectTypeNodes() ) {
            if ( String.class == otn.getObjectType().getValueType().getClassType() ) {
                AlphaNode alphaNode = (AlphaNode) otn.getObjectSinkPropagator().getSinks()[0];
                liaNode = (LeftInputAdapterNode) alphaNode.getObjectSinkPropagator().getSinks()[0];
                break;
            }
        }

        assertThat(liaNode).isNotNull();
        InternalWorkingMemory wm = (InternalWorkingMemory) ksession;
        LeftInputAdapterNode.LiaNodeMemory memory = wm.getNodeMemory(liaNode);
        TupleSets stagedLeftTuples = memory.getSegmentMemory().getStagedLeftTuples();
        assertThat(stagedLeftTuples.getDeleteFirst()).isNull();
        assertThat(stagedLeftTuples.getInsertFirst()).isNull();
    }

    @Test
    public void testBetaMemoryLeakOnFactDelete() {
        // DROOLS-913
        String drl =
                "rule R1 when\n" +
                "    $a : Integer(this == 1)\n" +
                "    $b : String()\n" +
                "    $c : Integer(this == 2)\n" +
                "then \n" +
                "end\n" +
                "rule R2 when\n" +
                "    $a : Integer(this == 1)\n" +
                "    $b : String()\n" +
                "    $c : Integer(this == 3)\n" +
                "then \n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();

        FactHandle fh1 = ksession.insert( 1 );
        FactHandle fh2 = ksession.insert( 3 );
        FactHandle fh3 = ksession.insert( "test" );
        ksession.fireAllRules();

        ksession.delete( fh1 );
        ksession.delete( fh2 );
        ksession.delete( fh3 );
        ksession.fireAllRules();

        NodeMemories nodeMemories = ( (InternalWorkingMemory) ksession ).getNodeMemories();
        for ( int i = 0; i < nodeMemories.length(); i++ ) {
            Memory memory = nodeMemories.peekNodeMemory( i );
            if ( memory != null && memory.getSegmentMemory() != null ) {
                SegmentMemory segmentMemory = memory.getSegmentMemory();
                System.out.println( memory );
                TupleImpl deleteFirst = memory.getSegmentMemory().getStagedLeftTuples().getDeleteFirst();
                if ( segmentMemory.getRootNode() instanceof JoinNode ) {
                    BetaMemory bm = (BetaMemory) segmentMemory.getNodeMemories()[0];
                    assertThat(bm.getLeftTupleMemory().size()).isEqualTo(0);
                }
                System.out.println( deleteFirst );
                assertThat(deleteFirst).isNull();
            }
        }
    }

    @Test(timeout = 5000)
    @Ignore("The checkReachability method is not totally reliable and can fall in an endless loop." +
            "We need to find a better way to check this.")
    public void testLeakAfterSessionDispose() {
        // DROOLS-1655
        String drl =
                "import " + Person.class.getCanonicalName() + "\n" +
                "rule R when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "end\n";

        ReleaseId releaseId = KieUtil.generateReleaseId("test");
        final List<Resource> resources = KieUtil.getResourcesFromDrls(drl);
        KieUtil.getKieModuleFromResources(releaseId, kieBaseTestConfiguration, resources.toArray(new Resource[]{}));
        KieContainer kContainer = KieServices.Factory.get().newKieContainer(releaseId);
        KieBase kBase = KieBaseUtil.getDefaultKieBaseFromReleaseId(releaseId);
        KieSession ksession = kBase.newKieSession();

        ksession.insert( new Person("Mario", 40) );
        ksession.fireAllRules();

        checkReachability( ksession, Person.class::isInstance, true );
        checkReachability( kBase, ksession::equals, true );
        checkReachability( kContainer, ksession::equals, true );

        ksession.dispose();

        checkReachability( kContainer, Person.class::isInstance, false );
        checkReachability( kBase, ksession::equals, false );
        checkReachability( kContainer, ksession::equals, false );
    }

    private static void checkReachability( Object root, Predicate<Object> condition, boolean reachable ) {
        Collection<Object> results = checkObject(root, condition);
        assertThat(reachable ^ results.isEmpty()).isTrue();
    }

    private static Collection<Object> checkObject(final Object object, final Predicate<Object> condition) {
        Collection<Object> results = new ArrayList<>();
        final Set<Object> visited = Collections.newSetFromMap( new IdentityHashMap<>() );
        List<Object> childObjects = checkObject(object, condition, results, visited);
        while (childObjects != null && !childObjects.isEmpty()) {
            childObjects = checkObjects(childObjects, condition, results, visited);
        }
        return results;
    }

    private static List<Object> checkObjects(final List<Object> objects, final Predicate<Object> condition,
            final Collection<Object> results, final Set<Object> visited) {
        final List<Object> childObjects = new ArrayList<>();
        objects.forEach(object -> childObjects.addAll(checkObject(object, condition, results, visited)));
        return childObjects;
    }

    private static List<Object> checkObject(final Object object, final Predicate<Object> condition,
            final Collection<Object> results, final Set<Object> visited) {
        final List<Object> childObjects = new ArrayList<>();
        if (object != null) {
            if (!visited.add(object)) {
                return childObjects;
            }
            if (condition.test(object)) {
                results.add(object);
            } else {
                if (object instanceof Object[]) {
                    for (Object child: (Object[]) object) {
                        if (child != null) {
                            childObjects.addAll(getFieldsFromObject(child));
                        }
                    }
                } else if (!object.getClass().isArray()) {
                    childObjects.addAll(getFieldsFromObject(object));
                }
            }
        }
        return childObjects;
    }

    private static List<Object> getFieldsFromObject(final Object object) {
        final List<Object> childObjects = new ArrayList<>();
        for (Class c = object.getClass(); c != Object.class; c = c.getSuperclass()) {
            for (Field field: c.getDeclaredFields()) {
                if ( Modifier.isStatic( field.getModifiers() )) {
                    continue;
                }
                if (field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    childObjects.add(field.get(object));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return childObjects;
    }
}
