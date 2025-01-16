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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.mvel.CommonTestMethodBase;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.CheeseEqual;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.compiler.Sensor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.serialization.protobuf.SerializationHelper.getSerialisedStatefulKnowledgeSession;

public class TruthMaintenanceTest extends CommonTestMethodBase {

    @Test
    public void testLogicalInsertionsDynamicRule() throws Exception {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newClassPathResource("test_LogicalInsertionsDynamicRule.drl",
                getClass()),
                      ResourceType.DRL );

        if ( kbuilder.hasErrors() ) {
            fail( kbuilder.getErrors().toString() );
        }

        Collection<KiePackage> kpkgs = kbuilder.getKnowledgePackages();
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) getKnowledgeBase();
        kbase.addPackages( kpkgs );
        KieSession ksession = createKnowledgeSession(kbase);
        try {
            final Cheese c1 = new Cheese( "a",
                                          1 );
            final Cheese c2 = new Cheese( "b",
                                          2 );
            final Cheese c3 = new Cheese( "c",
                                          3 );
            List list;

            ksession.insert( c1 );
            FactHandle h = ksession.insert( c2 );
            ksession.insert( c3 );
            ksession.fireAllRules();

            ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                              true );

            // Check logical Insertions where made for c2 and c3        
            list = new ArrayList( ksession.getObjects( new ClassObjectFilter( Person.class ) ) );
            assertThat(list.size()).isEqualTo(2);
            assertThat(list.contains(new Person( c1.getType() ))).isFalse();
            assertThat(list.contains(new Person( c2.getType() ))).isTrue();
            assertThat(list.contains(new Person( c3.getType() ))).isTrue();

            // this rule will make a logical assertion for c1 too
            kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
            kbuilder.add( ResourceFactory.newClassPathResource( "test_LogicalInsertionsDynamicRule2.drl",
                                                                getClass() ),
                          ResourceType.DRL );
            if ( kbuilder.hasErrors() ) {
                fail( kbuilder.getErrors().toString() );
            }
            Collection<KiePackage> kpkgs2 = kbuilder.getKnowledgePackages();
            kbase.addPackages( kpkgs2 );

            ksession.fireAllRules();

            ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                              true );

            kbase = (InternalKnowledgeBase) ksession.getKieBase();

            // check all now have just one logical assertion each
            list = new ArrayList( ksession.getObjects( new ClassObjectFilter( Person.class ) ) );
            assertThat(list.size()).isEqualTo(3);
            assertThat(list.contains(new Person( c1.getType() ))).isTrue();
            assertThat(list.contains(new Person( c2.getType() ))).isTrue();
            assertThat(list.contains(new Person( c3.getType() ))).isTrue();

            ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                              true );

            // check the packages are correctly populated
            assertThat(kbase.getKiePackages().size()).isEqualTo(3);
            KiePackage test = null, test2 = null;
            // different JVMs return the package list in different order
            for( KiePackage kpkg : kbase.getKiePackages() ) {
                if( kpkg.getName().equals( "org.drools.compiler.test" )) {
                    test = kpkg;
                } else if( kpkg.getName().equals( "org.drools.compiler.test2" )) {
                    test2 = kpkg;
                }
            }

            assertThat(test).isNotNull();
            assertThat(test2).isNotNull();
            assertThat(test.getRules().iterator().next().getName()).isEqualTo("rule1");
            assertThat(test2.getRules().iterator().next().getName()).isEqualTo("rule2");

            // now remove the first rule
            kbase.removeRule( test.getName(),
                              test.getRules().iterator().next().getName() );
            // different JVMs return the package list in different order
            for( KiePackage kpkg : kbase.getKiePackages() ) {
                if( kpkg.getName().equals( "org.drools.compiler.test" )) {
                    test = kpkg;
                } else if( kpkg.getName().equals( "org.drools.compiler.test2" )) {
                    test2 = kpkg;
                }
            }
            assertThat(test).isNotNull();
            assertThat(test2).isNotNull();

            // Check the rule was correctly remove
            assertThat(test.getRules().size()).isEqualTo(0);
            assertThat(test2.getRules().size()).isEqualTo(1);
            assertThat(test2.getRules().iterator().next().getName()).isEqualTo("rule2");

            list = new ArrayList( ksession.getObjects( new ClassObjectFilter( Person.class ) ) );
            assertThat(list.size()).as("removal of the rule should result in retraction of c3's logical assertion").isEqualTo(2);
            assertThat(list.contains(new Person( c1.getType() ))).as("c1's logical assertion should not be deleted").isTrue();
            assertThat(list.contains(new Person( c2.getType() ))).as("c2's logical assertion should  not be deleted").isTrue();
            assertThat(list.contains(new Person( c3.getType() ))).as("c3's logical assertion should be  deleted").isFalse();

            c2.setPrice( 3 );
            h = getFactHandle( h, ksession );
            ksession.update( h,
                             c2 );
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession( ksession,
                                                              true );
            list = new ArrayList( ksession.getObjects( new ClassObjectFilter( Person.class ) ) );
            assertThat(list.size()).as("c2 now has a higher price, its logical assertion should  be cancelled").isEqualTo(1);
            assertThat(list.contains(new Person( c2.getType() ))).as("The logical assertion cor c2 should have been deleted").isFalse();
            assertThat(list.contains(new Person( c1.getType() ))).as("The logical assertion  for c1 should exist").isTrue();

            // different JVMs return the package list in different order
            for( KiePackage kpkg : kbase.getKiePackages() ) {
                if( kpkg.getName().equals( "org.drools.compiler.test" )) {
                    test = kpkg;
                } else if( kpkg.getName().equals( "org.drools.compiler.test2" )) {
                    test2 = kpkg;
                }
            }
            assertThat(test).isNotNull();
            assertThat(test2).isNotNull();

            kbase.removeRule( test2.getName(),
                              test2.getRules().iterator().next().getName() );

            // different JVMs return the package list in different order
            for( KiePackage kpkg : kbase.getKiePackages() ) {
                if( kpkg.getName().equals( "org.drools.compiler.test" )) {
                    test = kpkg;
                } else if( kpkg.getName().equals( "org.drools.compiler.test2" )) {
                    test2 = kpkg;
                }
            }
            assertThat(test).isNotNull();
            assertThat(test2).isNotNull();

            assertThat(test.getRules().size()).isEqualTo(0);
            assertThat(test2.getRules().size()).isEqualTo(0);
            list = new ArrayList( ksession.getObjects( new ClassObjectFilter( Person.class ) ) );
            assertThat(list.size()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertions() throws Exception {
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newClassPathResource( "test_LogicalInsertions.drl",
                                                            getClass() ),
                      ResourceType.DRL );
        Collection<KiePackage> kpkgs = kbuilder.getKnowledgePackages();

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kpkgs );
        KieSession session = createKnowledgeSession(kbase);
        try {
            final List list = new ArrayList();
            session.setGlobal( "list",
                               list );

            final Cheese brie = new Cheese( "brie",
                                            12 );
            FactHandle brieHandle = session.insert( brie );

            final Cheese provolone = new Cheese( "provolone",
                                                 12 );
            FactHandle provoloneHandle = session.insert( provolone );

            session.fireAllRules();

            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );

            System.out.println( list );
            assertThat(list.size()).isEqualTo(3);

            assertThat(session.getObjects().size()).isEqualTo(3);

            brieHandle = getFactHandle( brieHandle, session );
            session.delete( brieHandle );

            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );

            assertThat(session.getObjects().size()).isEqualTo(2);

            provoloneHandle = getFactHandle( provoloneHandle, session );
            session.delete( provoloneHandle );
            session.fireAllRules();

            assertThat(session.getObjects().size()).isEqualTo(0);
        } finally {
            session.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertionsBacking() throws Exception {
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newClassPathResource( "test_LogicalInsertionsBacking.drl",
                                                            getClass() ),
                      ResourceType.DRL );
        Collection<KiePackage> kpkgs = kbuilder.getKnowledgePackages();

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kpkgs );
        KieSession session = createKnowledgeSession(kbase);
        try {
            final Cheese cheese1 = new Cheese( "c",
                                               1 );
            final Cheese cheese2 = new Cheese( cheese1.getType(),
                                               1 );

            FactHandle h1 = session.insert( cheese1 );
            session.fireAllRules();

            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );

            Collection< ? > list = session.getObjects( new ClassObjectFilter( cheese1.getType().getClass() ) );
            assertThat(list.size()).isEqualTo(1);
            // probably dangerous, as contains works with equals, not identity
            assertThat(list.iterator().next()).isEqualTo(cheese1.getType());

            FactHandle h2 = session.insert( cheese2 );
            session.fireAllRules();

            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );

            list = session.getObjects( new ClassObjectFilter( cheese1.getType().getClass() ) );
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.iterator().next()).isEqualTo(cheese1.getType());

            assertThat(session.getObjects().size()).isEqualTo(3);

            h1 = getFactHandle( h1, session );
            session.delete( h1 );
            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );
            session.fireAllRules();
            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );
            list = session.getObjects( new ClassObjectFilter( cheese1.getType().getClass() ) );
            assertThat(list.size()).as("cheese-type " + cheese1.getType() + " was deleted, but should not. Backed by cheese2 => type.").isEqualTo(1);
            assertThat(list.iterator().next()).as("cheese-type " + cheese1.getType() + " was deleted, but should not. Backed by cheese2 => type.").isEqualTo(cheese1.getType());

            h2 = getFactHandle( h2, session );
            session.delete( h2 );
            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );
            session.fireAllRules();
            session = getSerialisedStatefulKnowledgeSession( session,
                                                             true );
            list = session.getObjects( new ClassObjectFilter( cheese1.getType().getClass() ) );
            assertThat(list.size()).as("cheese-type " + cheese1.getType() + " was not deleted, but should have. Neither  cheese1 => type nor cheese2 => type is true.").isEqualTo(0);
        } finally {
            session.dispose();
        }
    }

    @Test
    @Timeout(10000)
    //@Ignore("in Java 8, the byte[] generated by serialization are not the same and requires investigation")
    public void testLogicalInsertionsWithModify() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_LogicalInsertionsWithUpdate.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final Person p = new Person( "person" );
            p.setAge( 2 );
            FactHandle h = ksession.insert( p );
            assertThat(ksession.getObjects().size()).isEqualTo(1);

            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, false);
            assertThat(ksession.getObjects().size()).isEqualTo(2);

            Collection l = ksession.getObjects( new ClassObjectFilter( CheeseEqual.class ) );
            assertThat(l.size()).isEqualTo(1);
            assertThat(((CheeseEqual) l.iterator().next()).getPrice()).isEqualTo(2);

            h = getFactHandle( h, ksession );
            ksession.delete( h );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, false);
            assertThat(ksession.getObjects().size()).isEqualTo(0);

            TruthMaintenanceSystem tms = TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem((ReteEvaluator) ksession);

            final java.lang.reflect.Field field = tms.getClass().getDeclaredField( "equalityKeyMap" );
            field.setAccessible( true );
            final Map<EqualityKey, EqualityKey> m = (Map<EqualityKey, EqualityKey>) field.get(tms);
            field.setAccessible( false );
            assertThat(m.size()).as("assertMap should be empty").isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertions2() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_LogicalInsertions2.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final List events = new ArrayList();

            ksession.setGlobal( "events", events );

            final Sensor sensor = new Sensor( 80,
                                              80 );
            FactHandle handle = ksession.insert( sensor );

            // everything should be normal

            ksession = getSerialisedStatefulKnowledgeSession( ksession, false );
            ksession.fireAllRules();

            Collection list = ksession.getObjects();

            assertThat(list.size()).as("Only sensor is there").isEqualTo(1);
            assertThat(events.size()).as("Only one event").isEqualTo(1);

            // problems should be detected
            sensor.setPressure( 200 );
            sensor.setTemperature( 200 );

            handle = getFactHandle( handle, ksession );
            ksession.update( handle, sensor );

            ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

            ksession.fireAllRules();
            list = ksession.getObjects();

            assertThat(list.size()).as("Only sensor is there").isEqualTo(1);

            TruthMaintenanceSystem tms =  TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem( (ReteEvaluator) ksession );
            assertThat(tms.getEqualityKeysSize()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    //@Ignore("in Java 8, the byte[] generated by serialization are not the same and requires investigation")
    public void testLogicalInsertionsNot() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_LogicalInsertionsNot.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final Person a = new Person( "a" );
            final Cheese cheese = new Cheese( "brie",
                                              1 );
            ksession.setGlobal( "cheese",
                                cheese );

            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            Collection list = ksession.getObjects();
            assertThat(list.size()).as("i was not asserted by not a => i.").isEqualTo(1);
            assertThat(list.iterator().next()).as("i was not asserted by not a => i.").isEqualTo(cheese);

            FactHandle h = ksession.insert( a );

            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            // no need to fire rules, assertion alone removes justification for i,
            // so it should be deleted.
            // workingMemory.fireAllRules();
            ksession.fireAllRules();
            list = ksession.getObjects();

            assertThat(list.size()).as("a was not asserted or i not deleted.").isEqualTo(1);
            assertThat(list.iterator().next()).as("a was asserted.").isEqualTo(a);
            assertThat(list.contains(cheese)).as("i was not rectracted.").isFalse();

            // no rules should fire, but nevertheless...
            // workingMemory.fireAllRules();
            assertThat(((InternalAgenda) ((StatefulKnowledgeSessionImpl) ksession).getAgenda()).getAgendaGroupsManager().agendaSize()).as("agenda should be empty.").isEqualTo(0);

            h = getFactHandle( h, ksession );
            ksession.delete( h );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            list = ksession.getObjects();
            assertThat(list.size()).as("i was not asserted by not a => i.").isEqualTo(1);
            assertThat(list.iterator().next()).as("i was not asserted by not a => i.").isEqualTo(cheese);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    @Disabled("Currently cannot support updates")
    //@Ignore("in Java 8, the byte[] generated by serialization are not the same and requires investigation")
    public void testLogicalInsertionsUpdateEqual() throws Exception {
        // calling update on a justified FH, states it
        KieBase kbase = loadKnowledgeBase("test_LogicalInsertionsUpdateEqual.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final Person p = new Person( "person" );
            p.setAge( 2 );
            FactHandle h = ksession.insert( p );
            assertThat(ksession.getObjects().size()).isEqualTo(1);

            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            assertThat(ksession.getObjects().size()).isEqualTo(2);
            Collection l = ksession.getObjects( new ClassObjectFilter( CheeseEqual.class ) );
            assertThat(l.size()).isEqualTo(1);
            assertThat(((CheeseEqual) l.iterator().next()).getPrice()).isEqualTo(3);

            h = getFactHandle( h, ksession );
            ksession.delete( h );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);


            Collection list = ksession.getObjects();
            // CheeseEqual was updated, making it stated, so it wouldn't have been logically deleted
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.iterator().next()).isEqualTo(new CheeseEqual("person", 3));
            FactHandle fh = ksession.getFactHandle( list.iterator().next() );
            ksession.delete( fh );

            list = ksession.getObjects();
            assertThat(list.size()).isEqualTo(0);

            TruthMaintenanceSystem tms =  TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem( (ReteEvaluator) ksession );

            final java.lang.reflect.Field field = tms.getClass().getDeclaredField( "equalityKeyMap" );
            field.setAccessible( true );
            final Map<EqualityKey, EqualityKey> m = (Map<EqualityKey, EqualityKey>) field.get( tms );
            field.setAccessible( false );
            assertThat(m.size()).as("assertMap should be empty").isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    //@Ignore("in Java 8, the byte[] generated by serialization are not the same and requires investigation")
    public void testLogicalInsertionsWithExists() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_LogicalInsertionWithExists.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final Person p1 = new Person( "p1",
                                          "stilton",
                                          20 );
            p1.setStatus( "europe" );
            FactHandle c1FactHandle = ksession.insert( p1 );
            final Person p2 = new Person( "p2",
                                          "stilton",
                                          30 );
            p2.setStatus( "europe" );
            FactHandle c2FactHandle = ksession.insert( p2 );
            final Person p3 = new Person( "p3",
                                          "stilton",
                                          40 );
            p3.setStatus( "europe" );
            FactHandle c3FactHandle = ksession.insert( p3 );
            ksession.fireAllRules();

            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);

            // all 3 in europe, so, 2 cheese
            Collection cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(2);

            // europe=[ 1, 2 ], america=[ 3 ]
            p3.setStatus( "america" );
            c3FactHandle = getFactHandle( c3FactHandle, ksession );
            ksession.update( c3FactHandle,
                             p3 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(1);

            // europe=[ 1 ], america=[ 2, 3 ]
            p2.setStatus( "america" );
            c2FactHandle = getFactHandle( c2FactHandle, ksession );
            ksession.update( c2FactHandle,
                             p2 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(1);

            // europe=[ ], america=[ 1, 2, 3 ]
            p1.setStatus( "america" );
            c1FactHandle = getFactHandle( c1FactHandle, ksession );
            ksession.update( c1FactHandle,
                             p1 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(2);

            // europe=[ 2 ], america=[ 1, 3 ]
            p2.setStatus( "europe" );
            c2FactHandle = getFactHandle( c2FactHandle, ksession );
            ksession.update( c2FactHandle,
                             p2 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(1);

            // europe=[ 1, 2 ], america=[ 3 ]
            p1.setStatus( "europe" );
            c1FactHandle = getFactHandle( c1FactHandle, ksession );
            ksession.update( c1FactHandle,
                             p1 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(1);

            // europe=[ 1, 2, 3 ], america=[ ]
            p3.setStatus( "europe" );
            c3FactHandle = getFactHandle( c3FactHandle, ksession );
            ksession.update( c3FactHandle,
                             p3 );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            cheeseList = ksession.getObjects(new ClassObjectFilter(Cheese.class));
            assertThat(cheeseList.size()).isEqualTo(2);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertions3() throws Exception {
        KieBase kbase = loadKnowledgeBase("test_logicalInsertions3.drl");
        KieSession ksession = kbase.newKieSession();
        try {
            final List list = new ArrayList();
            ksession.setGlobal( "events", list );

            // asserting the sensor object
            final Sensor sensor = new Sensor( 150, 100 );
            FactHandle sensorHandle = ksession.insert( sensor );

            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession( ksession, true );

            // alarm must sound
            assertThat(list.size()).isEqualTo(2);
            assertThat(ksession.getObjects().size()).isEqualTo(2);

            // modifying sensor
            sensor.setTemperature( 125 );
            sensorHandle = getFactHandle( sensorHandle, ksession );
            ksession.update( sensorHandle,
                             sensor );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);

            // alarm must continue to sound
            assertThat(list.size()).isEqualTo(3);
            assertThat(ksession.getObjects().size()).isEqualTo(2);

            // modifying sensor
            sensor.setTemperature( 80 );
            sensorHandle = getFactHandle( sensorHandle, ksession );
            ksession.update( sensorHandle,
                             sensor );
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();

            // no alarms anymore
            assertThat(list.size()).isEqualTo(3);
            assertThat(ksession.getObjects().size()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertionsAccumulatorPattern() throws Exception {
        // JBRULES-449
        KieBase kbase = loadKnowledgeBase( "test_LogicalInsertionsAccumulatorPattern.drl" );
        KieSession ksession = kbase.newKieSession();
        try {
            ksession.setGlobal( "ga",
                                "a" );
            ksession.setGlobal( "gb",
                                "b" );
            ksession.setGlobal( "gs",
                                Short.valueOf( (short) 3 ) );

            ksession.fireAllRules();

            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);

            FactHandle h = ksession.insert(Integer.valueOf( 6 ) );
            assertThat(ksession.getObjects().size()).isEqualTo(1);

            ksession.fireAllRules();
            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            assertThat(ksession.getObjects(new ClassObjectFilter( CheeseEqual.class )).size()).as("There should be 2 CheeseEqual in Working Memory, 1 justified, 1 stated").isEqualTo(2);
            assertThat(ksession.getObjects().size()).isEqualTo(6);

            h = getFactHandle( h, ksession );
            ksession.delete( h );
            ksession.fireAllRules();

            for ( Object o : ksession.getObjects() ) {
                System.out.println( o );
            }

            ksession = getSerialisedStatefulKnowledgeSession(ksession, true);
            assertThat(ksession.getObjects(new ClassObjectFilter( CheeseEqual.class )).size()).isEqualTo(0);
            assertThat(ksession.getObjects(new ClassObjectFilter( Short.class )).size()).isEqualTo(0);
            assertThat(ksession.getObjects().size()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    @Timeout(10000)
    public void testLogicalInsertionsModifySameRuleGivesDifferentLogicalInsertion() throws Exception {
        // TODO JBRULES-1804

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newClassPathResource( "test_LogicalInsertionsModifySameRuleGivesDifferentLogicalInsertion.drl",
                                                            getClass() ),
                      ResourceType.DRL );
        Collection<KiePackage> pkgs = kbuilder.getKnowledgePackages();

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) getKnowledgeBase();
        kbase.addPackages( pkgs );
        KieSession session = createKnowledgeSession(kbase);
        try {
            Sensor sensor1 = new Sensor( 100, 0 );
            FactHandle sensor1Handle = session.insert( sensor1 );
            Sensor sensor2 = new Sensor( 200, 0 );
            FactHandle sensor2Handle = session.insert( sensor2 );
            Sensor sensor3 = new Sensor( 200, 0 );
            FactHandle sensor3Handle = session.insert( sensor3 );

            session.fireAllRules();

            session = getSerialisedStatefulKnowledgeSession( session, true );

            List temperatureList = new ArrayList( session.getObjects( new ClassObjectFilter( Integer.class ) ) );
            assertThat(temperatureList.contains(Integer.valueOf(100))).isTrue();
            assertThat(temperatureList.contains(Integer.valueOf(200))).isTrue();
            assertThat(temperatureList.size()).isEqualTo(2);

            sensor1.setTemperature( 150 );
            sensor1Handle =  getFactHandle( sensor1Handle, session );
            session.update( sensor1Handle, sensor1 );

            session = getSerialisedStatefulKnowledgeSession( session, true );
            session.fireAllRules();

            temperatureList = new ArrayList( session.getObjects( new ClassObjectFilter( Integer.class ) ) );
            assertThat(temperatureList.contains(Integer.valueOf(100))).isFalse();
            assertThat(temperatureList.contains(Integer.valueOf(150))).isTrue();
            assertThat(temperatureList.contains(Integer.valueOf(200))).isTrue();
            assertThat(temperatureList.size()).isEqualTo(2);
        } finally {
            session.dispose();
        }
    }

    public InternalFactHandle getFactHandle(FactHandle factHandle,
                                            StatefulKnowledgeSessionImpl session) {
        Map<Long, FactHandle> handles = new HashMap<>();
        for ( FactHandle fh : session.getFactHandles() ) {
            handles.put( fh.getId(), fh );
        }
        return (InternalFactHandle) handles.get( factHandle.getId() );
    }

    public InternalFactHandle getFactHandle(FactHandle factHandle,
                                            KieSession ksession) {
        Map<Long, FactHandle> handles = new HashMap<>();
        for ( FactHandle fh : ksession.getFactHandles() ) {
            handles.put( fh.getId(), fh );
        }
        return (InternalFactHandle) handles.get( factHandle.getId() );
    }
}

