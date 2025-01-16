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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ValueResolver;
import org.drools.base.common.DroolsObjectInputStream;
import org.drools.base.common.DroolsObjectOutputStream;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.consequence.Consequence;
import org.drools.base.rule.consequence.ConsequenceContext;
import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.marshalling.ClassObjectMarshallingStrategyAcceptor;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.MockTupleSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.time.impl.DurationTimer;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.drools.core.util.KeyStoreConstants;
import org.drools.core.util.KeyStoreHelper;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.mvel.CommonTestMethodBase;
import org.drools.mvel.compiler.Address;
import org.drools.mvel.compiler.Cell;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.FactA;
import org.drools.mvel.compiler.FactB;
import org.drools.mvel.compiler.FactC;
import org.drools.mvel.compiler.Message;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.compiler.Primitives;
import org.drools.mvel.integrationtests.IteratorToList;
import org.drools.serialization.protobuf.marshalling.IdentityPlaceholderResolverStrategy;
import org.drools.serialization.protobuf.marshalling.RuleBaseNodes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.conf.DeclarativeAgendaOption;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.marshalling.KieMarshallers;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.marshalling.ObjectMarshallingStrategyAcceptor;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.conf.TimedRuleExecutionOption;
import org.kie.api.runtime.conf.TimerJobFactoryOption;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.marshalling.MarshallerFactory;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.serialization.protobuf.SerializationHelper.getSerialisedStatefulKnowledgeSession;

public class MarshallingTest extends CommonTestMethodBase {

    @Test
    public void testSerializable() throws Exception {
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Serializable.drl" );
        KiePackage kpkg = kpkgs.iterator().next();
        kpkg = SerializationHelper.serializeObject( kpkg );

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) loadKnowledgeBase();
        kbase.addPackages( Collections.singleton( kpkg ) );

        final org.kie.api.definition.rule.Rule[] rules = kbase.getKiePackages().iterator().next().getRules().toArray( new org.kie.api.definition.rule.Rule[0] );
        assertThat(rules.length).isEqualTo(4);

        assertThat(rules[0].getName()).isEqualTo("match Person 1");
        assertThat(rules[1].getName()).isEqualTo("match Person 2");
        assertThat(rules[2].getName()).isEqualTo("match Person 3");
        assertThat(rules[3].getName()).isEqualTo("match Integer");

        KieSession ksession = kbase.newKieSession();

        ksession.setGlobal( "list",
                            new ArrayList() );

        final Person bob = new Person( "bob" );
        ksession.insert( bob );

        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                                              true );

        assertThat(ksession.getFactCount()).isEqualTo(1);
        assertThat(ksession.getObjects().iterator().next()).isEqualTo(bob);

        assertThat(((InternalAgenda) ksession.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(2);

        ksession.fireAllRules();

        List list = (List) ksession.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo(Integer.valueOf( 4 ));

        // need to create a new collection or otherwise the collection will be identity based
        List< ? > objects = new ArrayList<Object>( ksession.getObjects() );
        assertThat(objects.size()).isEqualTo(2);
        assertThat(objects.contains(bob)).isTrue();
        assertThat(objects.contains(new Person( "help" ))).isTrue();
    }

    @Test
    public void testSerializeWorkingMemoryAndRuleBase1() throws Exception {
        // has the first newStatefulSession before the ruleBase is serialised
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Serializable.drl" );

        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();

        KieSession session = kBase.newKieSession();

        kBase.addPackages( kpkgs );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.setGlobal( "list",
                           new ArrayList() );

        final Person bob = new Person( "bob" );
        session.insert( bob );
        ((InternalWorkingMemory)session).flushPropagations();

        org.kie.api.definition.rule.Rule[] rules = kBase.getPackage("org.drools.compiler.test").getRules().toArray(new org.kie.api.definition.rule.Rule[0]);

        assertThat(rules.length).isEqualTo(4);

        assertThat(rules[0].getName()).isEqualTo("match Person 1");
        assertThat(rules[1].getName()).isEqualTo("match Person 2");
        assertThat(rules[2].getName()).isEqualTo("match Person 3");
        assertThat(rules[3].getName()).isEqualTo("match Integer");

        assertThat(session.getObjects().size()).isEqualTo(1);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).get(0)).isEqualTo(bob);

        assertThat(((InternalAgenda) session.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(2);

        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);
        session.fireAllRules();

        final List list = (List) session.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo(Integer.valueOf( 4 ));

        assertThat(session.getObjects().size()).isEqualTo(2);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(bob)).isTrue();
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(new Person( "help" ))).isTrue();

    }

    @Test
    public void testSerializeWorkingMemoryAndRuleBase2() throws Exception {
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Serializable.drl" );

        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        KieSession session = kBase.newKieSession();

        // serialise the working memory before population
        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);

        session.setGlobal( "list",
                           new ArrayList() );

        final Person bob = new Person( "bob" );
        session.insert( bob );
        ((InternalWorkingMemory)session).flushPropagations();

        org.kie.api.definition.rule.Rule[] rules = kBase.getPackage("org.drools.compiler.test").getRules().toArray(new org.kie.api.definition.rule.Rule[0]);

        assertThat(rules.length).isEqualTo(4);

        assertThat(rules[0].getName()).isEqualTo("match Person 1");
        assertThat(rules[1].getName()).isEqualTo("match Person 2");
        assertThat(rules[2].getName()).isEqualTo("match Person 3");
        assertThat(rules[3].getName()).isEqualTo("match Integer");

        assertThat(session.getObjects().size()).isEqualTo(1);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).get(0)).isEqualTo(bob);

        assertThat(((InternalAgenda) session.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(2);

        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);
        session.fireAllRules();

        final List list = (List) session.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo(Integer.valueOf( 4 ));

        assertThat(session.getObjects().size()).isEqualTo(2);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(bob)).isTrue();
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(new Person( "help" ))).isTrue();
    }

    @Test
    public void testSerializeWorkingMemoryAndRuleBase3() throws Exception {
        // has the first newStatefulSession after the ruleBase is serialised
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Serializable.drl" );

        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        KieSession session = kBase.newKieSession();

        session.setGlobal( "list",
                           new ArrayList() );

        final Person bob = new Person( "bob" );
        session.insert( bob );

        // now try serialising with a fully populated wm from a serialised rulebase
        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);

        org.kie.api.definition.rule.Rule[] rules = kBase.getPackage("org.drools.compiler.test").getRules().toArray(new org.kie.api.definition.rule.Rule[0]);

        assertThat(rules.length).isEqualTo(4);

        assertThat(rules[0].getName()).isEqualTo("match Person 1");
        assertThat(rules[1].getName()).isEqualTo("match Person 2");
        assertThat(rules[2].getName()).isEqualTo("match Person 3");
        assertThat(rules[3].getName()).isEqualTo("match Integer");

        assertThat(session.getObjects().size()).isEqualTo(1);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).get(0)).isEqualTo(bob);

        assertThat(((InternalAgenda) session.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(2);

        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);

        session.fireAllRules();

        final List list = (List) session.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo(Integer.valueOf( 4 ));

        assertThat(session.getObjects().size()).isEqualTo(2);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(bob)).isTrue();
        assertThat(IteratorToList.convert(session.getObjects().iterator()).contains(new Person( "help" ))).isTrue();
    }

    @Test
    public void testSerializeAdd() throws Exception {
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        KieSession session = kBase.newKieSession();
        List list = new ArrayList();
        session.setGlobal( "list",
                           list );

        InternalFactHandle stilton = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                      10 ) );
        InternalFactHandle brie = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                   10 ) );
        session.fireAllRules();

        assertThat(1).isEqualTo(list.size());
        assertThat(list.get(0)).isEqualTo("stilton");

        // now recreate the rulebase, deserialize the session and test it
        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);
        list = (List) session.getGlobal( "list" );

        assertThat(list).isNotNull();
        assertThat(1).isEqualTo(list.size());
        assertThat(list.get(0)).isEqualTo("stilton");

        kpkgs = loadKnowledgePackages("test_Dynamic3.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        kBase.addPackages(kpkgs);

        InternalFactHandle stilton2 = (InternalFactHandle) session.insert( new Cheese( "stilton", 10 ) );
        InternalFactHandle brie2 = (InternalFactHandle) session.insert( new Cheese( "brie", 10 ) );
        InternalFactHandle bob = (InternalFactHandle) session.insert( new Person( "bob", 30 ) );
        session.fireAllRules();

        assertThat(3).isEqualTo(list.size());
        assertThat(list).contains(bob.getObject(), "stilton");

        session.dispose();
    }

    @Test
    public void testSerializationOfIndexedWM() throws Exception {
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Serializable2.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        org.kie.api.definition.rule.Rule[] rules = kBase.getPackage("org.drools.compiler").getRules().toArray(new org.kie.api.definition.rule.Rule[0] );
        assertThat(rules.length).isEqualTo(3);

        KieSession session = kBase.newKieSession();

        session.setGlobal( "list",
                           new ArrayList() );

        final Primitives p = new Primitives();
        p.setBytePrimitive( (byte) 1 );
        p.setShortPrimitive( (short) 2 );
        p.setIntPrimitive(3);
        session.insert( p );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        assertThat(session.getObjects().size()).isEqualTo(1);
        assertThat(IteratorToList.convert(session.getObjects().iterator()).get(0)).isEqualTo(p);

        assertThat(((InternalAgenda) session.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(3);

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        session.fireAllRules();

        final List list = (List) session.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo("1");
        assertThat(list.get(1)).isEqualTo("2");
        assertThat(list.get(2)).isEqualTo("3");

    }

    /*
     *  Here I am inserting data points which are not used by any rule (e.g Person).
     *  Later adding rule (e.g. Rule: 'match Person') for those data points.
     *
     *  Result: Pkg/Rule addition is failing with ClassCastException
     */
    @Test
    public void testSerializeAdd2() throws Exception {
        //Create a rulebase, a session, and test it
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        List results = new ArrayList();
        KieSession session = kBase.newKieSession();
        session.setGlobal( "results",
                           results );

        InternalFactHandle stilton1 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       10 ) );
        session.insert( new Cheese( "brie",
                                    10 ) );
        InternalFactHandle bob = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                  10 ) );

        // fire rules
        session.fireAllRules();
        // check the results are correct
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo(stilton1.getObject());


        // serialize session and rulebase
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dynamically add a new package
        kpkgs = loadKnowledgePackages("test_Dynamic3_0.drl" );
        kBase.addPackages(SerializationHelper.serializeObject( kpkgs ));
        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);

        InternalFactHandle stilton2 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        session.insert( new Cheese( "brie",
                                    20 ) );
        InternalFactHandle mark = (InternalFactHandle) session.insert( new Person( "mark",
                                                                                   20 ) );
        session.fireAllRules();

        results = (List) session.getGlobal( "results" );
        assertThat(results.size()).isEqualTo(4);

        assertThat(results.get(1)).isEqualTo(stilton2.getObject());

        assertThat(results.get(2)).isEqualTo(bob.getObject());

        assertThat(results.get(3)).isEqualTo(mark.getObject());

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dispose session
        session.dispose();

    }

    /*
     *  Here I am inserting data points(e.g. Cheese) with  'stilton' / 'brie' as type value.
     *  Initially I had only 1 rule ('like stilton') for data points(e.g. Cheese) having type as 'stilton'.
     *
     *  Later added new rule ('like brie')  for data points(e.g. Cheese) having type as 'brie'.
     *
     *  Result: new rule is not getting fired for new data points having type as 'brie'.
     *          Only for old data points having type as 'brie' the new rule got fired.
     */
    @Test
    public void testSerializeAdd_newRuleNotFiredForNewData() throws Exception {
        //Create a rulebase, a session, and test it
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        List results = new ArrayList();
        KieSession session = kBase.newKieSession();
        session.setGlobal( "results",
                           results );

        InternalFactHandle stilton1 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       10 ) );
        InternalFactHandle brie1 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    10 ) );
        session.fireAllRules();

        // serialize session
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dynamically add a new package
        kpkgs = loadKnowledgePackages("test_Dynamic1_1.drl" );
        kBase.addPackages(SerializationHelper.serializeObject( kpkgs ));
        session = getSerialisedStatefulKnowledgeSession(session, kBase, true);

        InternalFactHandle stilton2 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        InternalFactHandle brie2 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    20 ) );
        InternalFactHandle brie3 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    30 ) );
        session.fireAllRules();
        assertThat(results.size()).isEqualTo(5);

        assertThat(results.get(1)).isEqualTo(stilton2.getObject());

        assertThat(results.contains(brie1.getObject())).isTrue();
        assertThat(results.contains(brie3.getObject())).isTrue();
        assertThat(results.contains(brie3.getObject())).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.dispose();
    }

    /*
     *  Works Fine if both the scenarios mentioned above are skipped.
     */
    @Test
    public void testSerializeAdd3() throws Exception {
        //Create a rulebase, a session, and test it
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        List results = new ArrayList();
        KieSession session = kBase.newKieSession();
        session.setGlobal( "results",
                           results );


        InternalFactHandle stilton1 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       10 ) );
        InternalFactHandle brie1 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    10 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo(stilton1.getObject());

        // serialize session
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dynamic add pkgs
        kpkgs = loadKnowledgePackages("test_Dynamic3_0.drl" );
        kBase.addPackages(SerializationHelper.serializeObject( kpkgs ));
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        InternalFactHandle stilton2 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        InternalFactHandle brie2 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    20 ) );
        InternalFactHandle bob1 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   20 ) );
        InternalFactHandle bob2 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   30 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(4);
        assertThat(results.get(1)).isEqualTo(stilton2.getObject());
        assertThat(results.contains(bob2.getObject())).isTrue();
        assertThat(results.contains(bob1.getObject())).isTrue();

        // serialize session
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dynamic add pkgs
        kpkgs = loadKnowledgePackages("test_Dynamic1_2.drl" );
        kBase.addPackages(SerializationHelper.serializeObject( kpkgs ));
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        InternalFactHandle stilton3 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       40 ) );
        InternalFactHandle brie3 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    40 ) );
        InternalFactHandle bob3 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   40 ) );
        InternalFactHandle bob4 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   40 ) );
        InternalFactHandle addr1 = (InternalFactHandle) session.insert( new Address( "bangalore" ) );
        InternalFactHandle addr2 = (InternalFactHandle) session.insert( new Address( "India" ) );

        session.fireAllRules();

        assertThat(results.size()).isEqualTo(9);
        assertThat(results.get(4)).isEqualTo(stilton3.getObject());
        assertThat(results.get(5)).isEqualTo(bob4.getObject());
        assertThat(results.get(6)).isEqualTo(bob3.getObject());
        assertThat(results.contains(addr2.getObject())).isTrue();
        assertThat(results.contains(addr1.getObject())).isTrue();

        // serialize session
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        InternalFactHandle stilton4 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       50 ) );
        InternalFactHandle brie4 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    50 ) );
        InternalFactHandle bob5 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   50 ) );
        InternalFactHandle bob6 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   50 ) );
        InternalFactHandle addr3 = (InternalFactHandle) session.insert( new Address( "Tripura" ) );
        InternalFactHandle addr4 = (InternalFactHandle) session.insert( new Address( "Agartala" ) );

        session.fireAllRules();

        assertThat(results.size()).isEqualTo(14);
        assertThat(results.get(9)).isEqualTo(stilton4.getObject());
        assertThat(results.get(10)).isEqualTo(bob6.getObject());
        assertThat(results.get(11)).isEqualTo(bob5.getObject());
        assertThat(results.contains(addr4.getObject())).isTrue();
        assertThat(results.contains(addr3.getObject())).isTrue();

        // serialize session
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.dispose();
    }

    /*
     * I have tried both the scenarios
     * 1. Remove a rule from a pkg.
     * 2. Remove a pkg
     *
     * But both cases after inserting associated data points (i.e data points which are used to fire/activate the removed rule)
     * session.fireAllRules() is throwing NoClassDefFoundError
     */
    @Test
    public void testSerializeAddRemove_NoClassDefFoundError() throws Exception {

        //Create a rulebase, a session, and test it
        Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        List results = new ArrayList();
        KieSession session = kBase.newKieSession();
        session.setGlobal( "results",
                           results );

        InternalFactHandle stilton1 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       10 ) );
        InternalFactHandle brie1 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    10 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo(stilton1.getObject());

        // serialize session and rulebase
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        // dynamic add pkgs
        kpkgs = loadKnowledgePackages("test_Dynamic3_0.drl" );
        kBase.addPackages(SerializationHelper.serializeObject( kpkgs ));
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        results.clear();

        InternalFactHandle stilton2 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        InternalFactHandle brie2 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    20 ) );
        InternalFactHandle bob1 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   20 ) );
        InternalFactHandle bob2 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   30 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0)).isEqualTo(stilton2.getObject());
        assertThat(results.contains(bob1.getObject())).isTrue();
        assertThat(results.contains(bob2.getObject())).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        results.clear();

        // CASE 1: remove rule
        kBase.removeRule( "org.drools.compiler.test",
                          "like stilton" );

        InternalFactHandle stilton3 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        InternalFactHandle brie3 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    20 ) );
        InternalFactHandle bob3 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   20 ) );
        InternalFactHandle bob4 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   30 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.contains(bob3.getObject())).isTrue();
        assertThat(results.contains(bob4.getObject())).isTrue();

        // deserialize the session and test it
        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        results.clear();

        // CASE 2: remove pkg
        kBase.removeKiePackage( "org.drools.compiler.test" );

        InternalFactHandle stilton4 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       20 ) );
        InternalFactHandle brie4 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    20 ) );
        InternalFactHandle bob5 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   20 ) );
        InternalFactHandle bob6 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   30 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.contains(bob5.getObject())).isTrue();
        assertThat(results.contains(bob6.getObject())).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        results.clear();

        session.setGlobal( "results",
                           results );

        InternalFactHandle stilton5 = (InternalFactHandle) session.insert( new Cheese( "stilton",
                                                                                       30 ) );
        InternalFactHandle brie5 = (InternalFactHandle) session.insert( new Cheese( "brie",
                                                                                    30 ) );
        InternalFactHandle bob7 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   30 ) );
        InternalFactHandle bob8 = (InternalFactHandle) session.insert( new Person( "bob",
                                                                                   40 ) );
        session.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.contains(bob7.getObject())).isTrue();
        assertThat(results.contains(bob8.getObject())).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.dispose();

    }

    /*
     *  Testing the signature framework
     */
    @Test
    public void testSignedSerialization1() throws Exception {
        try {
            setPrivateKeyProperties();
            setPublicKeyProperties();

            //Compile a package, add it to kbase, serialize both
            Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl" );
            kpkgs = SerializationHelper.serializeObject( kpkgs );
            InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
            kBase.addPackages(kpkgs);

        } finally {
            unsetPrivateKeyProperties();
            unsetPublicKeyProperties();
        }
    }

    /*
     *  Deserializing a signed package without the proper public key
     *  should fail.
     */
    @Test
    public void testSignedSerialization2() throws Exception {
        try {
            // set only the serialisation properties, but not the deserialization
            setPrivateKeyProperties();

            try {
                // Test package serialization/deserialization
                Collection<KiePackage>  kpkgs = loadKnowledgePackages("test_Dynamic1_0.drl");
                fail( "Deserialisation should have failed." );
            } catch ( Exception e ) {
                // success
            }
        } finally {
            unsetPrivateKeyProperties();
        }
    }

    /*
     *  Deserializing a signed rulebase without the proper public key
     *  should fail.
     */
    @Test
    public void testSignedSerialization3() throws Exception {
        try {
            // set only the serialisation properties, but not the deserialization
            setPrivateKeyProperties();

            // create the kpkgs, but do not let them serialize
            Collection<KiePackage>  kpkgs = loadKnowledgePackages(null, false, "test_Dynamic1_0.drl" );
            InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
            kBase.addPackages(kpkgs);

            try {
                kBase = SerializationHelper.serializeObject( kBase );
                fail( "Deserialisation should have failed." );
            } catch ( Exception e ) {
                // success
            }
        } finally {
            unsetPrivateKeyProperties();
        }
    }

    /*
     *  A client environment configured to use signed serialization
     *  should refuse any non-signed serialized rulebase
     */
    @Test
    public void testSignedSerialization4() throws Exception {

        Collection<KiePackage>  kpkgs = loadKnowledgePackages(null, false, "test_Dynamic1_0.drl" );
        kpkgs = SerializationHelper.serializeObject( kpkgs );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKnowledgeBase();
        kBase.addPackages(kpkgs);

        try {
            // set only the deserialisation properties, but not the serialization
            setPublicKeyProperties();
            kBase = SerializationHelper.serializeObject( kBase );
            fail( "Should not deserialize an unsigned rulebase on an environment configured to work with signed rulebases." );
        } catch ( Exception e ) {
            // success
        } finally {
            unsetPublicKeyProperties();
        }
    }

    private void setPublicKeyProperties() {
        // Set the client properties to de-serialise the signed packages
        URL clientKeyStoreURL = getClass().getResource( "droolsClient.keystore" );
        System.setProperty( KeyStoreConstants.PROP_SIGN, "true" );
        System.setProperty( KeyStoreConstants.PROP_PUB_KS_URL, clientKeyStoreURL.toExternalForm() );
        System.setProperty( KeyStoreConstants.PROP_PUB_KS_PWD, "clientpwd" );
        KeyStoreHelper.reInit();
    }

    private void unsetPublicKeyProperties() {
        // Un-set the client properties to de-serialise the signed packages
        System.setProperty( KeyStoreConstants.PROP_SIGN, "" );
        System.setProperty( KeyStoreConstants.PROP_PUB_KS_URL, "" );
        System.setProperty( KeyStoreConstants.PROP_PUB_KS_PWD, "" );
        KeyStoreHelper.reInit();
    }

    private void setPrivateKeyProperties() {
        // Set the server properties to serialise the signed packages
        URL serverKeyStoreURL = getClass().getResource( "droolsServer.keystore" );
        System.setProperty( KeyStoreConstants.PROP_SIGN, "true" );
        System.setProperty( KeyStoreConstants.PROP_PVT_KS_URL, serverKeyStoreURL.toExternalForm() );
        System.setProperty( KeyStoreConstants.PROP_PVT_KS_PWD, "serverpwd" );
        System.setProperty( KeyStoreConstants.PROP_PVT_ALIAS, "droolsKey" );
        System.setProperty( KeyStoreConstants.PROP_PVT_PWD, "keypwd" );
        KeyStoreHelper.reInit();
    }

    private void unsetPrivateKeyProperties() {
        // Un-set the server properties to serialise the signed packages
        System.setProperty( KeyStoreConstants.PROP_SIGN, "" );
        System.setProperty( KeyStoreConstants.PROP_PVT_KS_URL, "" );
        System.setProperty( KeyStoreConstants.PROP_PVT_KS_PWD, "" );
        System.setProperty( KeyStoreConstants.PROP_PVT_ALIAS, "" );
        System.setProperty( KeyStoreConstants.PROP_PVT_PWD, "" );
        KeyStoreHelper.reInit();
    }

    @Test
    public void testEmptyRule() throws Exception {
        String rule = "package org.drools.compiler.test;\n";
        rule += "global java.util.List list\n";
        rule += "rule \"Rule 1\"\n";
        rule += "when\n";
        rule += "then\n";
        rule += "    list.add( \"fired\" );\n";
        rule += "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule );

        // Make sure the rete node map is created correctly
        Map<Integer, BaseNode> nodes = RuleBaseNodes.getNodeMap((InternalKnowledgeBase) kBase);
        assertThat(nodes.size()).isEqualTo(2);
        assertThat(((ClassObjectType) ((ObjectTypeNode) nodes.get(2)).getObjectType()).getClassType().getSimpleName()).isEqualTo("InitialFactImpl");
        assertThat(((RuleTerminalNode) nodes.get(4)).getRule().getName()).isEqualTo("Rule 1");

        KieSession session = kBase.newKieSession();

        List list = new ArrayList();
        session.setGlobal( "list",
                           list );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        assertThat(session.fireAllRules()).isEqualTo(1);

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        assertThat(((List) session.getGlobal("list")).size()).isEqualTo(1);
        assertThat(((List) session.getGlobal("list")).get(0)).isEqualTo("fired");
    }

    @Test
    public void testDynamicEmptyRule() throws Exception {
        String rule1 = "package org.drools.compiler.test;\n";
        rule1 += "global java.util.List list\n";
        rule1 += "rule \"Rule 1\"\n";
        rule1 += "when\n";
        rule1 += "then\n";
        rule1 += "    list.add( \"fired1\" );\n";
        rule1 += "end";

        String rule2 = "package org.drools.compiler.test;\n";
        rule2 += "global java.util.List list\n";
        rule2 += "rule \"Rule 2\"\n";
        rule2 += "when\n";
        rule2 += "then\n";
        rule2 += "    list.add( \"fired2\" );\n";
        rule2 += "end";

        InternalKnowledgeBase kBase = (InternalKnowledgeBase) loadKnowledgeBaseFromString(rule1);

        // Make sure the rete node map is created correctly
        Map<Integer, BaseNode> nodes = RuleBaseNodes.getNodeMap(kBase);

        // Make sure the rete node map is created correctly
        assertThat(nodes.size()).isEqualTo(2);
        assertThat(((ClassObjectType) ((ObjectTypeNode) nodes.get(2)).getObjectType()).getClassType().getSimpleName()).isEqualTo("InitialFactImpl");
        assertThat(((RuleTerminalNode) nodes.get(4)).getRule().getName()).isEqualTo("Rule 1");

        KieSession session = kBase.newKieSession();

        List list = new ArrayList();
        session.setGlobal( "list",
                           list );
        KieSession session1 = getSerialisedStatefulKnowledgeSession( session, kBase, false );
        session1.fireAllRules();

        assertThat(((List) session1.getGlobal("list")).size()).isEqualTo(1);

        KieSession session2 = getSerialisedStatefulKnowledgeSession( session1, kBase, false );

        session.dispose();
        session1.dispose();

        Collection<KiePackage>  kpkgs = loadKnowledgePackagesFromString( rule2 );
        kBase.addPackages( kpkgs );

        session2.fireAllRules();
        System.out.println(session2.getGlobal( "list" ));

        assertThat(((List) session2.getGlobal("list")).size()).isEqualTo(2);
        assertThat(((List) session2.getGlobal("list")).get(0)).isEqualTo("fired1");
        assertThat(((List) session2.getGlobal("list")).get(1)).isEqualTo("fired2");
    }

    @Test
    public void testSinglePattern() throws Exception {
        String rule = "package org.drools.compiler.test;\n";
        rule += "import " + Person.class.getCanonicalName()+ "\n";
        rule += "global java.util.List list\n";
        rule += "rule \"Rule 1\"\n";
        rule += "when\n";
        rule += "    $p : Person( ) \n";
        rule += "then\n";
        rule += "    list.add( $p );\n";
        rule += "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule );

        // Make sure the rete node map is created correctly
        Map<Integer, BaseNode> nodes = RuleBaseNodes.getNodeMap((InternalKnowledgeBase) kBase);
        assertThat(nodes.size()).isEqualTo(3);
        assertThat(((ClassObjectType) ((ObjectTypeNode) nodes.get(3)).getObjectType()).getClassType().getSimpleName()).isEqualTo("Person");
        assertThat(((RuleTerminalNode) nodes.get(5)).getRule().getName()).isEqualTo("Rule 1");

        KieSession session = kBase.newKieSession();

        List list = new ArrayList();
        session.setGlobal( "list",
                           list );

        Person p = new Person( "bobba fet",
                               32 );
        session.insert( p );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        session.fireAllRules();

        assertThat(((List) session.getGlobal("list")).size()).isEqualTo(1);
        assertThat(((List) session.getGlobal("list")).get(0)).isEqualTo(p);
    }

    @Test
    public void testSingleRuleSingleJoinNodePattern() throws Exception {
        String rule = "package org.drools.compiler.test;\n";
        rule += "import " + Person.class.getCanonicalName() + "\n";
        rule += "import " + Cheese.class.getCanonicalName() + "\n";
        rule += "global java.util.List list\n";
        rule += "rule \"Rule 1\"\n";
        rule += "when\n";
        rule += "    $c : Cheese( ) \n";
        rule += "    $p : Person( cheese == $c ) \n";
        rule += "then\n";
        rule += "    list.add( $p );\n";
        rule += "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule );

        // Make sure the rete node map is created correctly
        Map<Integer, BaseNode> nodes = RuleBaseNodes.getNodeMap( (InternalKnowledgeBase) kBase);

        assertThat(nodes.size()).isEqualTo(5);
        assertThat(((ClassObjectType) ((ObjectTypeNode) nodes.get(3)).getObjectType()).getClassType().getSimpleName()).isEqualTo("Cheese");
        assertThat(((ClassObjectType) ((ObjectTypeNode) nodes.get(5)).getObjectType()).getClassType().getSimpleName()).isEqualTo("Person");
        assertThat(nodes.get(6).getClass().getSimpleName().endsWith("JoinNode")).as("Should end with JoinNode").isTrue();
        assertThat(((RuleTerminalNode) nodes.get(7)).getRule().getName()).isEqualTo("Rule 1");

        KieSession session = kBase.newKieSession();

        List list = new ArrayList();
        session.setGlobal( "list",
                           list );

        Cheese stilton = new Cheese( "stilton",
                                     25 );
        Cheese brie = new Cheese( "brie",
                                  49 );
        Person bobba = new Person( "bobba fet",
                                   32 );
        bobba.setCheese( stilton );

        Person vadar = new Person( "darth vadar",
                                   32 );

        session.insert( stilton );
        session.insert( bobba );
        session.insert( vadar );
        session.insert( brie );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.fireAllRules();

        assertThat(((List) session.getGlobal("list")).size()).isEqualTo(1);
        assertThat(((List) session.getGlobal("list")).get(0)).isEqualTo(bobba);

        Person c3po = new Person( "c3p0",
                                  32 );
        c3po.setCheese( stilton );
        session.insert( c3po );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.fireAllRules();

        assertThat(((List) session.getGlobal("list")).size()).isEqualTo(2);
        assertThat(((List) session.getGlobal("list")).get(1)).isEqualTo(c3po);

        Person r2d2 = new Person( "r2d2",
                                  32 );
        r2d2.setCheese( brie );
        session.insert( r2d2 );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.fireAllRules();

        assertThat(((List) session.getGlobal("list")).size()).isEqualTo(3);
        assertThat(((List) session.getGlobal("list")).get(2)).isEqualTo(r2d2);
    }

    @Test
    public void testMultiRuleMultiJoinNodePatternsWithHalt() throws Exception {
        String rule1 =
                "package org.drools.compiler.test;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "rule \"Rule 1\"\n" +
                "when\n" +
                "    $c : Cheese( ) \n" +
                "    $p : Person( cheese == $c ) \n" +
                "then\n" +
                "    list.add( $p );\n" +
                "end";

        String rule2 =
                "package org.drools.compiler.test;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "import " + Cell.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "rule \"Rule 2\"\n" +
                "when\n" +
                "    $c : Cheese( ) \n" +
                "    $p : Person( cheese == $c ) \n" +
                "    $x : Cell( value == $p.age ) \n" +
                "then\n" +
                "    list.add( $x );\n" +
                "end";

        String rule3 =
                "package org.drools.compiler.test;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + FactA.class.getCanonicalName() + "\n" +
                "import " + FactB.class.getCanonicalName() + "\n" +
                "import " + FactC.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "rule \"Rule 3\"\n" +
                "when\n" +
                "    $a : FactA( field2 > 10 ) \n" +
                "    $b : FactB( f2 >= $a.field2 ) \n" +
                "    $p : Person( name == \"darth vadar\" ) \n" +
                "    $c : FactC( f2 >= $b.f2 ) \n" +
                "then\n" +
                "    list.add( $c );\n" +
                "    drools.halt();\n" +
                "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule1, rule2, rule3 );


        KieSession session = kBase.newKieSession();

        List list = new ArrayList();
        session.setGlobal( "list",
                           list );

        Cheese stilton = new Cheese( "stilton",
                                     25 );
        Cheese brie = new Cheese( "brie",
                                  49 );
        Person bobba = new Person( "bobba fet",
                                   30 );
        bobba.setCheese( stilton );
        Person vadar = new Person( "darth vadar",
                                   38 );
        Person c3po = new Person( "c3p0",
                                  17 );
        c3po.setCheese( stilton );
        Person r2d2 = new Person( "r2d2",
                                  58 );
        r2d2.setCheese( brie );

        session.insert( stilton );
        session.insert( bobba );
        session.insert( vadar );
        session.insert( brie );
        session.insert( c3po );
        session.insert( r2d2 );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );
        session.fireAllRules();

        list = (List) session.getGlobal( "list" );
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.contains(r2d2)).isTrue();
        assertThat(list.contains(c3po)).isTrue();
        assertThat(list.contains(bobba)).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        Cell cell30 = new Cell( 30 );
        session.insert( cell30 );
        Cell cell58 = new Cell( 58 );
        session.insert( cell58 );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.fireAllRules();

        assertThat(list.size()).isEqualTo(5);
        assertThat(list.contains(cell30)).isTrue();
        assertThat(list.contains(cell58)).isTrue();

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.insert( new FactA( 15 ) );
        session.insert( new FactB( 20 ) );
        FactC factC27 = new FactC( 27 );
        session.insert( factC27 );
        FactC factC52 = new FactC( 52 );
        session.insert( factC52 );

        session = getSerialisedStatefulKnowledgeSession( session, kBase, true );

        session.fireAllRules();
        session.fireAllRules();

        assertThat(list.size()).isEqualTo(7);
        assertThat(list.contains(factC52)).isTrue();
        assertThat(list.contains(factC27)).isTrue();
    }

    @Test
    public void testNot() throws Exception {
        String header =
                "package org.drools.compiler.test;\n" +
                "import java.util.List;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global java.util.List list;\n";

        String rule1 =
                "rule \"not rule test\"\n" +
                "salience 10\n" +
                "when\n" +
                "    Person()\n" +
                "    not Cheese( price >= 5 )\n" +
                "then\n" +
                "    list.add( new Integer( 5 ) );\n" +
                "end\n";

        KieBase kBase = loadKnowledgeBaseFromString( header + rule1 );

        Environment env = EnvironmentFactory.newEnvironment();
        env.set( EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, new ObjectMarshallingStrategy[]{
                new IdentityPlaceholderResolverStrategy( ClassObjectMarshallingStrategyAcceptor.DEFAULT )} );

        KieSession ksession = kBase.newKieSession( null, env );

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        // add a person, no cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Person bobba = new Person( "bobba fet",
                                   50 );
        ksession.insert( bobba );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(1);

        // add another person, no cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Person darth = new Person( "darth vadar",
                                   200 );
        ksession.insert( darth );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        // add cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Cheese stilton = new Cheese( "stilton",
                                     5 );
        ksession.insert( stilton );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        // remove cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.retract( ksession.getFactHandle( stilton ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // put 2 cheeses back in
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.insert( stilton );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Cheese brie = new Cheese( "brie",
                                  18 );
        ksession.insert( brie );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // now remove a cheese, should be no change
        ksession.retract( ksession.getFactHandle( stilton ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // now remove a person, should be no change
        ksession.retract( ksession.getFactHandle( bobba ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        //removal remaining cheese, should increase by one, as one person left
        ksession.retract( ksession.getFactHandle( brie ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(5);
    }

    @Test
    public void testExists() throws Exception {
        String header =
                "package org.drools.compiler.test;\n" +
                "import java.util.List;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global java.util.List list;\n";

        String rule1 =
                "rule \"not rule test\"\n" +
                "salience 10\n" +
                "when\n" +
                "    Person()\n" +
                "    exists Cheese( price >= 5 )\n" +
                "then\n" +
                "    list.add( new Integer( 5 ) );\n" +
                "end\n";

        KieBase kBase = loadKnowledgeBaseFromString( header + rule1 );

        Environment env = EnvironmentFactory.newEnvironment();
        env.set( EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, new ObjectMarshallingStrategy[]{
                 new IdentityPlaceholderResolverStrategy( ClassObjectMarshallingStrategyAcceptor.DEFAULT )} );


        KieSession ksession = kBase.newKieSession( null, env );
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        // add a person, no cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Person bobba = new Person( "bobba fet",
                                   50 );
        ksession.insert( bobba );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(0);

        // add another person, no cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Person darth = new Person( "darth vadar",
                                   200 );
        ksession.insert( darth );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(0);

        // add cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Cheese stilton = new Cheese( "stilton",
                                     5 );
        ksession.insert( stilton );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        // remove cheese
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.retract( ksession.getFactHandle( stilton ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        // put 2 cheeses back in
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.insert( stilton );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        Cheese brie = new Cheese( "brie",
                                  18 );
        ksession.insert( brie );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // now remove a cheese, should be no change
        ksession.retract( ksession.getFactHandle( stilton ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // now remove a person, should be no change
        ksession.retract( ksession.getFactHandle( bobba ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        //removal remaining cheese, no
        ksession.retract( ksession.getFactHandle( brie ) );
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(4);

        // put one cheese back in, with one person should increase by one
        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );
        ksession.insert( stilton );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(5);
    }

    @Test
    public void testTruthMaintenance() throws Exception {
        String header =
                "package org.drools.compiler.test;\n" +
                "import java.util.List;\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global Cheese cheese;\n" +
                "global Person person;\n" +
                "global java.util.List list;\n";

        String rule1 =
                "rule \"not person then cheese\"\n" +
                "when \n" +
                "    not Person() \n" +
                "then \n" +
                "    if (list.size() < 3) { \n" +
                "        list.add(new Integer(0)); \n" +
                "        insertLogical( cheese ); \n" +
                         "    }\n" +
                "    drools.halt();\n" +
                "end\n";

        String rule2 =
                "rule \"if cheese then person\"\n" +
                "when\n" +
                "    Cheese()\n" +
                "then\n" +
                "    if (list.size() < 3) {\n" +
                "        list.add(new Integer(0));\n" +
                "        insertLogical( person );\n" +
                "    }\n" +
                "    drools.halt();\n" +
                "end\n";


        KieBase kBase = loadKnowledgeBaseFromString( header + rule1 + rule2 );

        KieSession ksession = kBase.newKieSession( );

        final List list = new ArrayList();

        final Person person = new Person( "person" );
        final Cheese cheese = new Cheese( "cheese",
                                          0 );
        ksession.setGlobal( "cheese",
                           cheese );
        ksession.setGlobal( "person",
                           person );
        ksession.setGlobal( "list",
                           list );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(1);

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(3);

        // should not grow any further
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(3);
    }

    @Test
    public void testActivationGroups() throws Exception {
        String rule1 = "package org.drools.compiler.test;\n";
        rule1 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule1 += "global java.util.List list\n";
        rule1 += "rule \"Rule 1\"\n";
        rule1 += "    activation-group \"activation-group-1\"\n";
        rule1 += "when\n";
        rule1 += "    $c : Cheese( ) \n";
        rule1 += "then\n";
        rule1 += "    list.add( \"rule1\" );\n";
        rule1 += "    drools.halt();\n";
        rule1 += "end";

        String rule2 = "package org.drools.compiler.test;\n";
        rule2 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule2 += "global java.util.List list\n";
        rule2 += "rule \"Rule 2\"\n";
        rule2 += "    salience 10\n";
        rule2 += "    activation-group \"activation-group-1\"\n";
        rule2 += "when\n";
        rule2 += "    $c : Cheese( ) \n";
        rule2 += "then\n";
        rule2 += "    list.add( \"rule2\" );\n";
        rule2 += "    drools.halt();\n";
        rule2 += "end";

        String rule3 = "package org.drools.compiler.test;\n";
        rule3 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule3 += "global java.util.List list\n";
        rule3 += "rule \"Rule 3\"\n";
        rule3 += "    activation-group \"activation-group-1\"\n";
        rule3 += "when\n";
        rule3 += "    $c : Cheese( ) \n";
        rule3 += "then\n";
        rule3 += "    list.add( \"rule3\" );\n";
        rule3 += "    drools.halt();\n";
        rule3 += "end";

        String rule4 = "package org.drools.compiler.test;\n";
        rule4 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule4 += "global java.util.List list\n";
        rule4 += "rule \"Rule 4\"\n";
        rule4 += "    activation-group \"activation-group-2\"\n";
        rule4 += "when\n";
        rule4 += "    $c : Cheese( ) \n";
        rule4 += "then\n";
        rule4 += "    list.add( \"rule4\" );\n";
        rule4 += "    drools.halt();\n";
        rule4 += "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule1, rule2, rule3, rule4);
        KieSession ksession = kBase.newKieSession( );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        final Cheese brie = new Cheese( "brie",
                                        12 );
        ksession.insert( brie );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        assertThat(ksession.fireAllRules()).isEqualTo(1); // only 1 as it halt

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        assertThat(ksession.fireAllRules()).isEqualTo(1); // it would have halt again
        assertThat(ksession.fireAllRules()).isEqualTo(0); // make sure nothing to resume

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo("rule2");
        assertThat(list.get(1)).isEqualTo("rule4");
    }

    @Test
    public void testAgendaGroups() throws Exception {
        String rule1 = "package org.drools.compiler.test;\n";
        rule1 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule1 += "global java.util.List list\n";
        rule1 += "rule \"Rule 1\"\n";
        rule1 += "    agenda-group \"agenda-group-1\"\n";
        rule1 += "when\n";
        rule1 += "    $c : Cheese( ) \n";
        rule1 += "then\n";
        rule1 += "    list.add( \"rule1\" );\n";
        rule1 += "    drools.halt();\n";
        rule1 += "end";

        String rule2 = "package org.drools.compiler.test;\n";
        rule2 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule2 += "global java.util.List list\n";
        rule2 += "rule \"Rule 2\"\n";
        rule2 += "    salience 10\n";
        rule2 += "    agenda-group \"agenda-group-1\"\n";
        rule2 += "when\n";
        rule2 += "    $c : Cheese( ) \n";
        rule2 += "then\n";
        rule2 += "    list.add( \"rule2\" );\n";
        rule2 += "    drools.halt();\n";
        rule2 += "end";

        String rule3 = "package org.drools.compiler.test;\n";
        rule3 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule3 += "global java.util.List list\n";
        rule3 += "rule \"Rule 3\"\n";
        rule3 += "    salience 10\n";
        rule3 += "    agenda-group \"agenda-group-2\"\n";
        rule3 += "    activation-group \"activation-group-2\"\n";
        rule3 += "when\n";
        rule3 += "    $c : Cheese( ) \n";
        rule3 += "then\n";
        rule3 += "    list.add( \"rule3\" );\n";
        rule3 += "    drools.halt();\n";
        rule3 += "end";

        String rule4 = "package org.drools.compiler.test;\n";
        rule4 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule4 += "global java.util.List list\n";
        rule4 += "rule \"Rule 4\"\n";
        rule4 += "    agenda-group \"agenda-group-2\"\n";
        rule4 += "    activation-group \"activation-group-2\"\n";
        rule4 += "when\n";
        rule4 += "    $c : Cheese( ) \n";
        rule4 += "then\n";
        rule4 += "    list.add( \"rule4\" );\n";
        rule4 += "    drools.halt();\n";
        rule4 += "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule1, rule2, rule3, rule4);
        KieSession ksession = kBase.newKieSession( );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        final Cheese brie = new Cheese( "brie",
                                        12 );
        ksession.insert( brie );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.getAgenda().getAgendaGroup("agenda-group-1" ).setFocus( );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.fireAllRules();
        assertThat(list.get(0)).isEqualTo("rule2");

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.getAgenda().getAgendaGroup("agenda-group-2" ).setFocus( );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.fireAllRules();
        assertThat(list.get(1)).isEqualTo("rule3");

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.fireAllRules();
        assertThat(list.get(2)).isEqualTo("rule1");
    }

    @Test
    public void testRuleFlowGroups() throws Exception {
        String rule1 = "package org.drools.compiler.test;\n";
        rule1 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule1 += "global java.util.List list\n";
        rule1 += "rule \"Rule 1\"\n";
        rule1 += "    ruleflow-group \"ruleflow-group-1\"\n";
        rule1 += "when\n";
        rule1 += "    $c : Cheese( ) \n";
        rule1 += "then\n";
        rule1 += "    list.add( \"rule1\" );\n";
        rule1 += "    drools.halt();\n";
        rule1 += "end";

        String rule2 = "package org.drools.compiler.test;\n";
        rule2 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule2 += "global java.util.List list\n";
        rule2 += "rule \"Rule 2\"\n";
        rule2 += "    salience 10\n";
        rule2 += "    ruleflow-group \"ruleflow-group-1\"\n";
        rule2 += "when\n";
        rule2 += "    $c : Cheese( ) \n";
        rule2 += "then\n";
        rule2 += "    list.add( \"rule2\" );\n";
        rule2 += "    drools.halt();\n";
        rule2 += "end";

        String rule3 = "package org.drools.compiler.test;\n";
        rule3 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule3 += "global java.util.List list\n";
        rule3 += "rule \"Rule 3\"\n";
        rule3 += "    salience 10\n";
        rule3 += "    ruleflow-group \"ruleflow-group-2\"\n";
        rule3 += "    activation-group \"activation-group-2\"\n";
        rule3 += "when\n";
        rule3 += "    $c : Cheese( ) \n";
        rule3 += "then\n";
        rule3 += "    list.add( \"rule3\" );\n";
        rule3 += "    drools.halt();\n";
        rule3 += "end";

        String rule4 = "package org.drools.compiler.test;\n";
        rule4 += "import " + Cheese.class.getCanonicalName() + "\n";
        rule4 += "global java.util.List list\n";
        rule4 += "rule \"Rule 4\"\n";
        rule4 += "    ruleflow-group \"ruleflow-group-2\"\n";
        rule4 += "    activation-group \"activation-group-2\"\n";
        rule4 += "when\n";
        rule4 += "    $c : Cheese( ) \n";
        rule4 += "then\n";
        rule4 += "    list.add( \"rule4\" );\n";
        rule4 += "    drools.halt();\n";
        rule4 += "end";

        KieBase kbase = loadKnowledgeBaseFromString( rule1, rule2, rule3, rule4 );
        KieSession ksession = getSerialisedStatefulKnowledgeSession( kbase.newKieSession(), true );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        final Cheese brie = new Cheese( "brie",
                                        12 );
        ksession.insert( brie );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true  );
        ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup( "ruleflow-group-1" );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true  );
        ksession.fireAllRules();
        assertThat(list.get(0)).isEqualTo("rule2");

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true  );
        ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup( "ruleflow-group-2" );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true  );
        ksession.fireAllRules();
        assertThat(list.get(1)).isEqualTo("rule3");

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true  );
        ksession.fireAllRules();
        assertThat(list.get(2)).isEqualTo("rule1");
    }

    @Test
    public void testAccumulate() throws Exception {
        String rule = "package org.drools\n" +
                "import " + Message.class.getCanonicalName() + "\n" +
                "global java.util.List results\n" +
                "rule MyRule\n" +
                "  when\n" +
                "    $n : Number( intValue >= 2 ) from accumulate ( m: Message( ), count( m ) )\n" +
                "  then\n" +
                "    results.add($n);\n" +
                "end";

        KieBase kBase = loadKnowledgeBaseFromString( rule );
        KieSession ksession = getSerialisedStatefulKnowledgeSession( kBase.newKieSession(), true );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        ksession.setGlobal( "results",
                           new ArrayList() );

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.insert( new Message() );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        List results = (List) ksession.getGlobal( "results" );

        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ksession.fireAllRules();
        assertThat(((Number) results.get(0)).intValue()).isEqualTo(3);

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

        assertThat(((InternalAgenda) ksession.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(1);
        ksession.fireAllRules();
        assertThat(((Number) results.get(1)).intValue()).isEqualTo(5);
    }

    @Test
    public void testAccumulate2() throws Exception {
        String str = "package org.drools\n" + "\n" +
                     "import " + Message.class.getCanonicalName() + "\n" +
                     "rule MyRule\n" + "  when\n" +
                     "    Number( intValue >= 5 ) from accumulate ( m: Message( ), count( m ) )\n" +
                     "  then\n" +
                     "    System.out.println(\"Found messages\");\n" +
                     "end\n";

        KieBase kBase = loadKnowledgeBaseFromString(str);
        KieSession ksession = kBase.newKieSession();

        ksession = getSerialisedStatefulKnowledgeSession( ksession, true );
        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ksession.insert( new Message() );
        ((InternalWorkingMemory)ksession).flushPropagations();

        assertThat(((InternalAgenda) ksession.getAgenda()).getAgendaGroupsManager().agendaSize()).isEqualTo(1);
    }

    @Test
    public void testAccumulateSessionSerialization() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_AccumulateSerialization.drl" );
        KieSession ksession = kbase.newKieSession();

        final List<Number> results = new ArrayList<Number>();

        ksession.setGlobal( "results",
                            results );

        ksession.insert( new Cheese( "stilton",
                                     10 ) );
        ksession.insert( new Cheese( "brie",
                                     5 ) );
        ksession.insert( new Cheese( "provolone",
                                     150 ) );
        ksession.insert( new Cheese( "brie",
                                     20 ) );
        ksession.insert( new Person( "Bob",
                                     "brie" ) );

        ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                          true );

        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).intValue()).isEqualTo(25);
    }

    /**
     * test that creates a new knowledge base, new stateful session, inserts new
     * fact, serializes the knowledge base and session and fact using one output
     * stream then deserializes and verifies that fact in the session is the
     * same as fact that was deserialized,
     *
     * from the ObjectOutputStream API: "Multiple references to a single object
     * are encoded using a reference sharing mechanism so that graphs of objects
     * can be restored to the same shape as when the original was written."
     *
     * This is still not fixed, as mentioned in the JIRA
     *
     * JBRULES-2048
     *
     * @throws Exception
     */
    @Test 
    @Disabled
    public void testDroolsObjectOutputInputStream() throws Exception {
        KieBase kbase = loadKnowledgeBase("org/drools/compiler/integrationtests/test_Serializable.drl"  );
        KieSession session = kbase.newKieSession();
        Person bob = new Person();
        session.insert( bob );

        assertThat(bob).as("these two object references should be same").isSameAs(session.getObjects().iterator().next());

        Marshaller marshaller = createSerializableMarshaller( kbase );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new DroolsObjectOutputStream( baos );
        out.writeObject( bob );
        out.writeObject( kbase );
        marshaller.marshall( out,
                             session );
        out.flush();
        out.close();

        ObjectInputStream in = new DroolsObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
        Person deserializedBob = (Person) in.readObject();
        kbase = (InternalKnowledgeBase) in.readObject();
        marshaller = createSerializableMarshaller( kbase );
        session = marshaller.unmarshall(in);

        assertThat(deserializedBob).as("these two object references should be same").isSameAs(session.getObjects().iterator().next());
        in.close();
    }

    @Test
    public void testAccumulateSerialization() throws Exception {
        KieBase kbase = loadKnowledgeBase( "org/drools/serialization/protobuf/test_SerializableAccumulate.drl"  );
        KieSession ksession = kbase.newKieSession();

        ksession.setGlobal( "results",
                            new ArrayList() );

        Cheese t1 = new Cheese( "brie",
                                10 );
        Cheese t2 = new Cheese( "brie",
                                15 );
        Cheese t3 = new Cheese( "stilton",
                                20 );
        Cheese t4 = new Cheese( "brie",
                                30 );

        ksession.insert( t1 );
        ksession.insert( t2 );
        ksession.insert( t3 );
        ksession.insert( t4 );

        //ksession.fireAllRules();
        Marshaller marshaller = createSerializableMarshaller( kbase );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new DroolsObjectOutputStream( baos );
        marshaller.marshall( out,
                             ksession );
        out.flush();
        out.close();

        ObjectInputStream in = new DroolsObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
        marshaller = createSerializableMarshaller( kbase );
        ksession = marshaller.unmarshall(in);
        in.close();

        // setting the global again, since it is not serialized with the session
        List<List> results = new ArrayList<List>();
        ksession.setGlobal( "results",
                            results );
        assertThat(results).isNotNull();

        ksession.fireAllRules();
        ksession.dispose();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).size()).isEqualTo(3);
    }

    @Test
    public void testMarshallWithNot() throws Exception {
        String str =
                "import " + getClass().getCanonicalName() + ".*\n" +
                        "rule one\n" +
                        "when\n" +
                        "   A()\n" +
                        "   not(B())\n" +
                        "then\n" +
                        "System.out.println(\"a\");\n" +
                        "end\n" +
                        "\n" +
                        "rule two\n" +
                        "when\n" +
                        "   A()\n" +
                        "then\n" +
                        "System.out.println(\"b\");\n" +
                        "end\n";

        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );

        KieBase kBase = loadKnowledgeBaseFromString(config, str);
        KieSession ksession = kBase.newKieSession();
        ksession.insert( new A() );
        MarshallerFactory.newMarshaller( kBase ).marshall( new ByteArrayOutputStream(), ksession );
    }

    @Test
    public void testMarshallEvents() throws Exception {
        String str =
                "import " + getClass().getCanonicalName() + ".*\n" +
                        "declare A\n" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "declare B\n" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "rule one\n" +
                        "when\n" +
                        "   $a : A()\n" +
                        "   B(this after $a)\n" +
                        "then\n" +
                        "insert(new C());" +
                        "end\n";

        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );

        KieBase kBase = loadKnowledgeBaseFromString(config, str);

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        KieSession ksession = kBase.newKieSession( ksconf, null );

        ksession.insert( new A() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        ksession.insert( new B() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        ksession.fireAllRules();
        assertThat(ksession.getObjects().size()).isEqualTo(2);
    }

    @Test 
    @Disabled("This test is suspicious to say the least...")
    public void testScheduledActivation() {
        InternalKnowledgeBase knowledgeBase = KnowledgeBaseFactory.newKnowledgeBase();
        InternalKnowledgePackage impl = CoreComponentFactory.get().createKnowledgePackage( "test" );

        BuildContext buildContext = new BuildContext( knowledgeBase, Collections.emptyList() );
        //simple rule that fires after 10 seconds
        final RuleImpl rule = new RuleImpl( "test-rule" );
        new RuleTerminalNode(1, new MockTupleSource(2, buildContext), rule, rule.getLhs(), 0, buildContext );

        final List<String> fired = new ArrayList<String>();

        rule.setConsequence( new Consequence() {
            @Override
            public String getName() {
                return "default";
            }

            @Override
            public void evaluate(ConsequenceContext knowledgeHelper, ValueResolver valueResolver) throws Exception {
                fired.add( "a" );
            }
        } );

        rule.setTimer( new DurationTimer( 10000 ) );
        rule.setPackage( "test" );
        impl.addRule( rule );

        knowledgeBase.addPackages( Collections.singleton( impl ) );
        SessionConfiguration config = KieServices.get().newKieSessionConfiguration().as(SessionConfiguration.KEY);
        config.setClockType( ClockType.PSEUDO_CLOCK );
        KieSession ksession = knowledgeBase.newKieSession( config, KieServices.get().newEnvironment() );
        PseudoClockScheduler scheduler = (PseudoClockScheduler) ksession.getSessionClock();
        Marshaller marshaller = MarshallerFactory.newMarshaller( knowledgeBase );

        ksession.insert( "cheese" );
        assertThat(fired.isEmpty()).isTrue();
        //marshall, then unmarshall session
        readWrite( knowledgeBase, ksession, config );
        //the activations should fire after 10 seconds
        assertThat(fired.isEmpty()).isTrue();
        scheduler.advanceTime( 12, TimeUnit.SECONDS );
        assertThat(fired.isEmpty()).isFalse();

    }

    public static class A
            implements
            Serializable {

        @Override
        public String toString() {
            return "A[]";
        }

    }

    public static class B
            implements
            Serializable {
        @Override
        public String toString() {
            return "B[]";
        }
    }

    public static class C
            implements
            Serializable {
        @Override
        public String toString() {
            return "C[]";
        }
    }

    @Test
    public void testMarshallEntryPointsWithExpires() throws Exception {
        String str =
                "package org.domain.test \n" +
                        "import " + getClass().getCanonicalName() + ".*\n" +
                        "global java.util.List list\n" +
                        "declare A\n" +
                        " @role( event )\n" +
                        " @expires( 10s )\n" +
                        "end\n" +
                        "declare B\n" +
                        "" +
                        " @role( event )\n" +
                        " @expires( 10s )\n" +
                        "end\n" +
                        "" +
                        "declare C\n" +
                        " @role( event )\n" +
                        " @expires( 15s )\n" +
                        "end\n" +
                        "" +
                        "rule a1\n" +
                        "when\n" +
                        "   $a : A() from entry-point 'a-ep'\n" +
                        "then\n" +
                        "list.add( $a );" +
                        "end\n" +
                        "" +
                        "rule b1\n" +
                        "when\n" +
                        "   $b : B() from entry-point 'b-ep'\n" +
                        "then\n" +
                        "list.add( $b );" +
                        "end\n" +
                        "" +
                        "rule c1\n" +
                        "when\n" +
                        "   $c : C() from entry-point 'c-ep'\n" +
                        "then\n" +
                        "list.add( $c );" +
                        "end\n";

        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );

        KieBase kBase = loadKnowledgeBaseFromString(config, str);

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        KieSession ksession = kBase.newKieSession( ksconf, null );

        List list = new ArrayList();
        ksession.setGlobal( "list", list );
        EntryPoint aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        EntryPoint bep = ksession.getEntryPoint( "b-ep" );
        bep.insert( new B() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        EntryPoint cep = ksession.getEntryPoint( "c-ep" );
        cep.insert( new C() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        ksession.fireAllRules();

        ksession = marshallStatefulKnowledgeSession( ksession );

        assertThat(list.size()).isEqualTo(3);

        aep = ksession.getEntryPoint( "a-ep" );
        assertThat(aep.getFactHandles().size()).isEqualTo(1);

        bep = ksession.getEntryPoint( "b-ep" );
        assertThat(bep.getFactHandles().size()).isEqualTo(1);

        cep = ksession.getEntryPoint( "c-ep" );
        assertThat(cep.getFactHandles().size()).isEqualTo(1);

        PseudoClockScheduler timeService = ksession.getSessionClock();
        timeService.advanceTime( 11, TimeUnit.SECONDS );

        ksession = marshallStatefulKnowledgeSession( ksession );

        ksession.fireAllRules();

        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        assertThat(aep.getFactHandles().size()).isEqualTo(0);

        bep = ksession.getEntryPoint( "b-ep" );
        assertThat(bep.getFactHandles().size()).isEqualTo(0);

        cep = ksession.getEntryPoint( "c-ep" );
        assertThat(cep.getFactHandles().size()).isEqualTo(1);
    }

    @Test
    public void testMarshallEntryPointsWithNot() throws Exception {
        String str =
                "package org.domain.test \n" +
                        "import " + getClass().getCanonicalName() + ".*\n" +
                        "global java.util.List list\n" +
                        "declare A\n" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "declare B\n" +
                        "" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "" +
                        "rule a1\n" +
                        "when\n" +
                        "   $a : A() from entry-point 'a-ep'\n" +
                        "   not B( this after[0s, 10s] $a) from entry-point 'a-ep'\n" +
                        "then\n" +
                        "list.add( $a );" +
                        "end\n";

        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );

        KieBase kBase = loadKnowledgeBaseFromString(config, str);

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        KieSession ksession = kBase.newKieSession( ksconf, null );

        List list = new ArrayList();
        ksession.setGlobal( "list", list );
        EntryPoint aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );

        ksession = marshallStatefulKnowledgeSession( ksession );

        PseudoClockScheduler timeService = (PseudoClockScheduler) ksession.getSessionClock();
        timeService.advanceTime( 3, TimeUnit.SECONDS );

        ksession = marshallStatefulKnowledgeSession( ksession );

        ksession.fireAllRules();

        ksession = marshallStatefulKnowledgeSession( ksession );

        assertThat(list.size()).isEqualTo(0);
    }

    @Test 
    @Disabled("beta4 phreak")
    public void testMarshallEntryPointsWithSlidingTimeWindow() throws Exception {
        String str =
                "package org.domain.test \n" +
                        "import " + getClass().getCanonicalName() + ".*\n" +
                        "import java.util.List\n" +
                        "global java.util.List list\n" +
                        "declare A\n" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "declare B\n" +
                        "" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "" +
                        "rule a1\n" +
                        "when\n" +
                        "   $l : List() from collect( A()  over window:time(30s) from entry-point 'a-ep') \n" +
                        "then\n" +
                        "   list.add( $l );" +
                        "end\n";

        KieBaseConfiguration conf = RuleBaseFactory.newKnowledgeBaseConfiguration();
        conf.setOption( EventProcessingOption.STREAM );
        final KieBase kbase = loadKnowledgeBaseFromString( conf, str );

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        KieSession ksession = createKnowledgeSession(kbase, ksconf);

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        EntryPoint aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        list.clear();
        ksession.fireAllRules();
        ksession = marshallStatefulKnowledgeSession( ksession );
        assertThat(((List) list.get(0)).size()).isEqualTo(2);

        PseudoClockScheduler timeService = (PseudoClockScheduler) ksession.getSessionClock();
        timeService.advanceTime( 15, TimeUnit.SECONDS );
        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        list.clear();
        ksession.fireAllRules();
        ksession = marshallStatefulKnowledgeSession( ksession );
        assertThat(((List) list.get(0)).size()).isEqualTo(4);

        timeService = (PseudoClockScheduler) ksession.getSessionClock();
        timeService.advanceTime( 20, TimeUnit.SECONDS );
        ksession = marshallStatefulKnowledgeSession( ksession );

        list.clear();
        ksession.fireAllRules();
        assertThat(((List) list.get(0)).size()).isEqualTo(2);
    }

    @Test
    public void testMarshallEntryPointsWithSlidingLengthWindow() throws Exception {
        String str =
                "package org.domain.test \n" +
                        "import " + getClass().getCanonicalName() + ".*\n" +
                        "import java.util.List\n" +
                        "global java.util.List list\n" +
                        "declare A\n" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "declare B\n" +
                        "" +
                        " @role( event )\n" +
                        " @expires( 10m )\n" +
                        "end\n" +
                        "" +
                        "rule a1\n" +
                        "when\n" +
                        "   $l : List() from collect( A()  over window:length(3) from entry-point 'a-ep') \n" +
                        "then\n" +
                        "   list.add( $l );" +
                        "end\n";

        KieBaseConfiguration conf = RuleBaseFactory.newKnowledgeBaseConfiguration();
        conf.setOption( EventProcessingOption.STREAM );
        final KieBase kbase = loadKnowledgeBaseFromString( conf, str );

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        KieSession ksession = createKnowledgeSession(kbase, ksconf);

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        EntryPoint aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        list.clear();
        ksession.fireAllRules();
        ksession = marshallStatefulKnowledgeSession( ksession );
        assertThat(((List) list.get(0)).size()).isEqualTo(2);

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        aep = ksession.getEntryPoint( "a-ep" );
        aep.insert( new A() );
        ksession = marshallStatefulKnowledgeSession( ksession );

        list.clear();
        ksession.fireAllRules();
        ksession = marshallStatefulKnowledgeSession( ksession );
        assertThat(((List) list.get(0)).size()).isEqualTo(3);
    }

    @Test
    public void testMarshalWithProtoBuf() throws Exception {
        KieBase kbase = loadKnowledgeBase( "test_Serializable.drl" );
        KieSession ksession = kbase.newKieSession();

        ksession.setGlobal( "list",
                            new ArrayList() );
        final Person bob = new Person( "bob" );
        ksession.insert( bob );

        ksession = marshallStatefulKnowledgeSession( ksession );

        assertThat(ksession.getFactCount()).isEqualTo(1);
        assertThat(ksession.getObjects().iterator().next()).isEqualTo(bob);

        int fired = ksession.fireAllRules();

        assertThat(fired).isEqualTo(3);

        List list = (List) ksession.getGlobal( "list" );

        assertThat(list.size()).isEqualTo(3);
        // because of agenda-groups
        assertThat(list.get(0)).isEqualTo(Integer.valueOf( 4 ));

        Collection<? extends Object> facts = ksession.getObjects();
        System.out.println( new ArrayList( facts ) );
        assertThat(facts.size()).isEqualTo(2);
    }

    private KieSession marshallStatefulKnowledgeSession(KieSession ksession) throws IOException, ClassNotFoundException {
        Globals globals = ksession.getGlobals();

        KieBase kbase = ksession.getKieBase();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MarshallerFactory.newMarshaller( kbase ).marshall( out, ksession );

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( TimerJobFactoryOption.get("trackable") );
        ksconf.setOption( ClockTypeOption.PSEUDO );

        Environment env = EnvironmentFactory.newEnvironment();
        env.set( EnvironmentName.GLOBALS, globals );
        ksession = MarshallerFactory.newMarshaller( kbase ).unmarshall( new ByteArrayInputStream( out.toByteArray() ), ksconf, env );

        return ksession;
    }

    private void readWrite(KieBase knowledgeBase,
                           KieSession ksession,
                           KieSessionConfiguration config) {
        try {
            Marshaller marshaller = MarshallerFactory.newMarshaller( knowledgeBase );
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            marshaller.marshall( o, ksession );
            ksession = marshaller.unmarshall( new ByteArrayInputStream( o.toByteArray() ), config, KieServices.get().newEnvironment() );
            ksession.fireAllRules();
            //scheduler = ksession.<SessionClock>getSessionClock();
        } catch ( Exception e ) {
            throw new RuntimeException( e );
        }
    }

    private Marshaller createSerializableMarshaller(KieBase knowledgeBase) {
        ObjectMarshallingStrategyAcceptor acceptor = MarshallerFactory.newClassFilterAcceptor( new String[]{"*.*"} );
        ObjectMarshallingStrategy strategy = MarshallerFactory.newSerializeMarshallingStrategy( acceptor );
        Marshaller marshaller = MarshallerFactory.newMarshaller( knowledgeBase,
                                                                 new ObjectMarshallingStrategy[]{strategy} );
        return marshaller;
    }

    @Test
    public void testMarshallWithCollects() throws Exception {
        // BZ-1193600
        String str =
                "import java.util.Collection\n" +
                "rule R1 when\n" +
                "    Collection(empty==false) from collect( Integer() )\n" +
                "    Collection() from collect( String() )\n" +
                "then\n" +
                "end\n" +
                "rule R2 when then end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = kbase.newKieSession();

        try {
            Marshaller marshaller = MarshallerFactory.newMarshaller(kbase);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshall(baos, ksession);
            marshaller = MarshallerFactory.newMarshaller(kbase);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            baos.close();
            ksession = marshaller.unmarshall(bais);
            bais.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception :" + e.getMessage());
        }
    }

    @Test
    public void testMarshallWithTimedRule() {
        // DROOLS-795
        String drl = "rule \"Rule A Timeout\"\n" +
                     "when\n" +
                     "    String( this == \"ATrigger\" )\n" +
                     "then\n" +
                     "   insert (new String( \"A-Timer\") );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Timer For rule A Timeout\"\n" +
                     "    timer ( int: 5s )\n" +
                     "when\n" +
                     "   String( this == \"A-Timer\")\n" +
                     "then\n" +
                     "   delete ( \"A-Timer\" );\n" +
                     "   delete ( \"ATrigger\" );\n" +
                     "end\n";

        KieBase kbase = new KieHelper().addContent( drl, ResourceType.DRL )
                                       .build( EqualityBehaviorOption.EQUALITY,
                                               DeclarativeAgendaOption.ENABLED,
                                               EventProcessingOption.STREAM );

        KieSessionConfiguration sessionConfig = RuleBaseFactory.newKnowledgeSessionConfiguration();
        sessionConfig.setOption( ClockTypeOption.PSEUDO );
        KieSession ksession = kbase.newKieSession(sessionConfig, null);

        ksession.insert( "ATrigger" );

        assertThat(ksession.getFactCount()).isEqualTo(1);
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(2);

        SessionPseudoClock clock = ksession.getSessionClock();
        clock.advanceTime( 4, TimeUnit.SECONDS );

        assertThat(ksession.getFactCount()).isEqualTo(2);
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(2);

        ksession = marshallAndUnmarshall( kbase, ksession, sessionConfig);
        clock = ksession.getSessionClock();

        clock.advanceTime( 4, TimeUnit.SECONDS );

        assertThat(ksession.getFactCount()).isEqualTo(2);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(ksession.getFactCount()).isEqualTo(0);
    }

    @Test
    @Disabled("Reproduces with pseudoclock. It takes too long with system clock")
    public void testMarshallWithTimedRuleRealClock() {
        // DROOLS-795
        String drl = "rule \"Rule A Timeout\"\n" +
                     "when\n" +
                     "    String( this == \"ATrigger\" )\n" +
                     "then\n" +
                     "   insert (new String( \"A-Timer\") );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Timer For rule A Timeout\"\n" +
                     "    timer ( int: 5s )\n" +
                     "when\n" +
                     "   String( this == \"A-Timer\")\n" +
                     "then\n" +
                     "   delete ( \"A-Timer\" );\n" +
                     "   delete ( \"ATrigger\" );\n" +
                     "end\n";

        KieBase kbase = new KieHelper().addContent( drl, ResourceType.DRL )
                                       .build( EqualityBehaviorOption.EQUALITY,
                                               DeclarativeAgendaOption.ENABLED,
                                               EventProcessingOption.STREAM );

        KieSession ksession = kbase.newKieSession();

        ksession.insert( "ATrigger" );

        assertThat(ksession.getFactCount()).isEqualTo(1);
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(2);

        try {
            Thread.sleep( 4000L );
        } catch (InterruptedException e) {
            throw new RuntimeException( e );
        }

        assertThat(ksession.getFactCount()).isEqualTo(2);
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(2);

        ksession = marshallAndUnmarshall( kbase, ksession, null);

        try {
            Thread.sleep( 4000L );
        } catch (InterruptedException e) {
            throw new RuntimeException( e );
        }

        assertThat(ksession.getFactCount()).isEqualTo(2);
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(0);
    }

    public static KieSession marshallAndUnmarshall(KieBase kbase, KieSession ksession, KieSessionConfiguration sessionConfig) {
        return marshallAndUnmarshall(kbase, kbase, ksession, sessionConfig);
    }

    public static KieSession marshallAndUnmarshall(KieBase kbase1, KieBase kbase2, KieSession ksession, KieSessionConfiguration sessionConfig) {
        // Serialize and Deserialize
        try {
            KieMarshallers kieMarshallers = KieServices.Factory.get().getMarshallers();
            Marshaller marshaller = kieMarshallers.newMarshaller( kbase1 );
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshall(baos, ksession);
            marshaller = kieMarshallers.newMarshaller( kbase2 );
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            baos.close();
            ksession = marshaller.unmarshall(bais, sessionConfig, null);
            bais.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception :" + e.getMessage());
        }
        return ksession;
    }

    @Test
    public void testSnapshotRecoveryScheduledRulesPlain() throws Exception {
        // DROOLS-1537
        String drl = "package com.drools.restore.reproducer\n" +
                     "global java.util.List list;\n" +
                     "global java.util.List list2;\n" +
                     "rule R1\n" +
                     " timer (int: 20s)\n" +
                     " when\n" +
                     "   $m : String( this == \"Hello World1\" )\n" +
                     " then\n" +
                     "   list.add( $m );\n" +
                     "   retract( $m );\n" +
                     "end\n" +
                     "rule R2\n" +
                     " timer (int: 30s)\n" +
                     " when\n" +
                     "   $m : String( this == \"Hello World2\" )\n" +
                     " then\n" +
                     "   list2.add( $m );\n" +
                     "   retract( $m );\n" +
                     "end\n";

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );
        ksconf.setOption( TimedRuleExecutionOption.YES );
        ksconf.setOption(TimerJobFactoryOption.get("trackable"));
        ksconf.setOption(ClockTypeOption.PSEUDO);

        KieBase kbase1 = new KieHelper().addContent( drl, ResourceType.DRL )
                                        .build( EventProcessingOption.STREAM );
        KieSession ksession = kbase1.newKieSession( ksconf, null );

        PseudoClockScheduler timeService = (PseudoClockScheduler) ksession.getSessionClock();

        List list = new ArrayList();
        ksession.setGlobal("list", list);
        List list2 = new ArrayList();
        ksession.setGlobal("list2", list2);

        ksession.insert("Hello World1");
        ksession.insert("Hello World2");

        ksession.fireAllRules();
        timeService.advanceTime(10500, TimeUnit.MILLISECONDS);

        KieBase kbase2 = new KieHelper().addContent( drl, ResourceType.DRL )
                                        .build( EventProcessingOption.STREAM );

        ksession = marshallAndUnmarshall( kbase1, kbase2, ksession, ksconf );
        ksession.setGlobal("list", list);
        ksession.setGlobal("list2", list2);

        PseudoClockScheduler timeService2 = (PseudoClockScheduler) ksession.getSessionClock();

        ksession.fireAllRules();

        long accumulatedSleepTime = 0;
        for (int i = 0; i < 6; i++) {
            timeService2.advanceTime(5050, TimeUnit.MILLISECONDS);
            accumulatedSleepTime += 5050;
            assertThat(list.size()).isEqualTo(i < 1 ? 0 : 1);
            assertThat(list2.size()).isEqualTo(i < 3 ? 0 : 1);
        }
    }

    @Test
    public void testKsessionSerializationWithInsertLogical() {
        List<String> firedRules = new ArrayList<>();
        String str =
                "import java.util.Date;\n" +
                "import " + Promotion.class.getCanonicalName() + ";\n" +
                "\n" +
                "declare Person\n" +
                "	name : String\n" +
                "	dateOfBirth : Date\n" +
                "end\n" +
                "\n" +
                "declare Employee extends Person\n" +
                "	job : String\n" +
                "end\n" +
                "\n" +
                "rule \"Insert Alice\"\n" +
                "	when\n" +
                "	then\n" +
                "		Employee alice = new Employee(\"Alice\", new Date(1973, 7, 2), \"Vet\");\n" +
                "		insert(alice);\n" +
                "		System.out.println(\"Insert Alice\");\n" +
                "end\n" +
                "\n" +
                "rule \"Insert Bob\"\n" +
                "	when\n" +
                "		Person(name == \"Alice\")\n" +
                "	then\n" +
                "		Person bob = new Person(\"Bob\", new Date(1973, 7, 2));\n" +
                "		insertLogical(bob);\n" +
                "		System.out.println(\"InsertLogical Bob\");\n" +
                "end\n" +
                "\n" +
                "rule \"Insert Claire\"\n" +
                "	when\n" +
                "		Person(name == \"Bob\")\n" +
                "	then\n" +
                "		Employee claire = new Employee(\"Claire\", new Date(1973, 7, 2), \"Student\");\n" +
                "		insert(claire);\n" +
                "		System.out.println(\"Insert Claire\");\n" +
                "end\n" +
                "\n" +
                "rule \"Promote\"\n" +
                "	when\n" +
                "		p : Promotion(n : name, j : job)\n" +
                "		e : Employee(name == n)\n" +
                "	then\n" +
                "		modify(e) {\n" +
                "			setJob(j)\n" +
                "		}\n" +
                "		delete(p);\n" +
                "		System.out.printf(\"Promoted %s to %s%n\", n, j);\n" +
                "end\n";

        KieBase kbase = loadKnowledgeBaseFromString( str );
        KieSession ksession = kbase.newKieSession();

        ksession.fireAllRules(); // insertLogical Person(Bob)

        // Serialize and Deserialize
        try {
            Marshaller marshaller = MarshallerFactory.newMarshaller( kbase );
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshall( baos, ksession );
            marshaller = MarshallerFactory.newMarshaller( kbase );
            ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
            baos.close();
            ksession = marshaller.unmarshall(bais);
            bais.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail( "unexpected exception :" + e.getMessage() );
        }

        ksession.insert( new Promotion( "Claire", "Scientist" ) );
        int result = ksession.fireAllRules();

        assertThat(result).isEqualTo(1);
    }

    public static class Promotion {
        private String name;
        private String job;

        public Promotion( String name, String job ) {
            this.setName( name );
            this.setJob( job );
        }

        public String getName() {
            return this.name;
        }

        public void setName( String name ) {
            this.name = name;
        }

        public String getJob() {
            return this.job;
        }

        public void setJob( String job ) {
            this.job = job;
        }
    }

    @Test
    @Timeout(10000)
    public void testDisposeAfterMarshall() throws InterruptedException, IOException {
        // DROOLS-4413

        String str = "package com.sample\n" +
                "rule R when\n" +
                "  $s : String()\n" +
                "then\n" +
                "  System.out.println($s);\n" +
                "end\n";

        KieBase kbase = new KieHelper().addContent( str, ResourceType.DRL ).build();
        KieSession ksession = kbase.newKieSession();

        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            System.out.println("Firing.");
            latch.countDown();
            ksession.fireUntilHalt();
            System.out.println("Halted.");
        });
        t.start();

        // wait fireUntilHalt to be invoked
        latch.await();
        Thread.sleep( 100L );

        // Halt the session without adding any facts
        ksession.halt();

        KieMarshallers kMarshallers = KieServices.Factory.get().getMarshallers();
        ObjectMarshallingStrategy oms = kMarshallers.newSerializeMarshallingStrategy();
        Marshaller marshaller = kMarshallers.newMarshaller( kbase, new ObjectMarshallingStrategy[]{ oms } );
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            marshaller.marshall(baos, ksession);
        }

        // Destroy
        ksession.dispose();
        ksession.destroy();

        // Wait for our thread to exit
        // ** The thread exits if we call t.interrupt();
        t.join();
    }

    @Test
    public void cepActivation_shouldFireActivation() throws Exception {
        // DROOLS-7531
        String str =
                "import " + getClass().getCanonicalName() + ".*\n" +
                "declare A\n" +
                " @role( event )\n" +
                " @expires( 10m )\n" +
                "end\n" +
                "declare B\n" +
                " @role( event )\n" +
                " @expires( 10m )\n" +
                "end\n" +
                "rule one\n" +
                "when\n" +
                "   $a : A()\n" +
                "   not ( B(this after[0s, 5s] $a) )\n" +
                "then\n" +
                "  System.out.println(\"Fired!\");\n" +
                "end\n";

        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption(EventProcessingOption.STREAM);

        KieBase kBase = loadKnowledgeBaseFromString(config, str);

        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption(ClockTypeOption.PSEUDO);
        KieSession ksession = kBase.newKieSession(ksconf, null);
        PseudoClockScheduler sessionClock = (PseudoClockScheduler) ksession.getSessionClock();

        ksession.insert(new A());
        sessionClock.advanceTime(6, TimeUnit.SECONDS);

        ksession = marshallStatefulKnowledgeSession(ksession);

        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(1);
    }
}
