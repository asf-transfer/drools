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
package org.drools.traits.compiler.factmodel.traits;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.drools.core.common.InternalFactHandle;
import org.drools.base.factmodel.traits.CoreWrapper;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitField;
import org.drools.base.factmodel.traits.Traitable;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.core.impl.InternalRuleBase;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.serialization.protobuf.SerializationHelper;
import org.drools.traits.compiler.CommonTraitTest;
import org.drools.traits.core.factmodel.TraitFactoryImpl;
import org.drools.traits.core.factmodel.VirtualPropertyMode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.definition.type.FactType;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class LogicalTraitTest extends CommonTraitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicalTraitTest.class);

    public static Stream<VirtualPropertyMode> parameters() {
        return Stream.of(VirtualPropertyMode.MAP, VirtualPropertyMode.TRIPLES);
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAlias(VirtualPropertyMode mode) throws Exception {

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newClassPathResource( "org/drools/compiler/factmodel/traits/testTraitedAliasing.drl" ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();

        List<Object> list = new ArrayList<>(  );
        ks.setGlobal( "list", list );

        ks.fireAllRules();

        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }

        ks = SerializationHelper.getSerialisedStatefulKnowledgeSession(ks, true );

        LOGGER.debug( list.toString() );
        assertThat(list).doesNotContain(Boolean.FALSE);
        assertThat(list).hasSize(8);
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAliasTraitOnClass(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X \n" +
                     "  fld : T \n" +
                     "end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( logical = true ) \n" +
                     "  fld : K \n" +
                     "end \n" +
                     "" +
                     "declare trait T @Trait( logical=true ) end \n" +
                     "declare K @Traitable() end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  Y y = new Y( new K() ); \n" +
                     "  don( y, X.class ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "when \n" +
                     "  X( fld isA T ) \n" +
                     "then \n" +
                     "  list.add( \"ok\" );" +
                     "end \n";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        ks.fireAllRules();
        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }
        assertThat(list).containsExactly("ok");

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAliasClassOnTrait(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X \n" +
                     "  fld : K \n" +
                     "end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( logical = true ) \n" +
                     "  fld : T \n" +
                     "end \n" +
                     "" +
                     "declare trait T @Trait( logical=true ) end \n" +
                     "declare K @Traitable() end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  K k = new K(); \n" +
                     "  T t = don( k, T.class ); \n" +
                     "  Y y = new Y( t ); \n" +
                     "  don( y, X.class ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "salience 1 \n" +
                     "when \n" +
                     "  $x : Y( fld isA T ) \n" +
                     "then \n" +
                     "  list.add( \"ok1\" );" +
                     "end \n" +
                     "" +
                     "rule Check2 \n" +
                     "when \n" +
                     "  $x : X( fld isA T ) \n" +
                     "then \n" +
                     "  list.add( \"ok2\" );" +
                     "end \n" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        ks.fireAllRules();
        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }
        assertThat(list).containsExactly("ok1", "ok2");

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAliasTraitOnTrait(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X \n" +
                     "  fld : A \n" +
                     "end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( logical = true ) \n" +
                     "  fld : B \n" +
                     "end \n" +
                     "" +
                     "declare trait A @Trait( logical=true ) end \n" +
                     "declare trait B @Trait( logical=true ) end \n" +
                     "declare K @Traitable() end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  K k = new K(); \n" +
                     "  B b = don( k, B.class ); \n" +
                     "  Y y = new Y( b ); \n" +
                     "  don( y, X.class ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "salience 1 \n" +
                     "when \n" +
                     "  $x : Y( fld isA B, fld isA A ) \n" +
                     "then \n" +
                     "  list.add( \"ok\" ); \n" +
                     "end \n" +
                     "" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        ks.fireAllRules();
        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }
        assertThat(list).containsExactly("ok");

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void initializationConflictManagement(VirtualPropertyMode mode) {
        String drl = "package org.drools.test; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "global java.util.List list2; \n" +
                     "" +
                     "declare trait A name : String = \"1\" age : Integer = 18 end \n" +
                     "declare trait B name : String = \"5\" age : Integer = 50 end \n" +
                     "declare trait C extends A,B name : String = \"7\" age : Integer = 37 end \n" +
                     "" +
                     "declare X @Traitable( logical = true ) name : String end \n" +
                     "" +
                     "" +
                     "rule Init \n" +
                     "when \n" +
                     "then \n" +
                     "  X x = new X(); \n" +
                     "  A a = don( x, A.class ); \n" +
                            // default 1, from A
                     "      list.add( x.getName() ); \n" +
                     "      list2.add( a.getAge() ); \n" +
                     "  B b = don( x, B.class ); \n" +
                            // conflicting defaults A and B, nullify
                     "      list.add( x.getName() ); \n" +
                     "      list2.add( b.getAge() ); \n" +
                     "end \n" +
                     "" +
                     "rule Later \n" +
                     "no-loop \n" +
                     "when \n" +
                     "  $x : X() \n" +
                     "then \n" +
                     "  $x.setName( \"xyz\" ); \n" +
                            // set to "xyz"
                     "      list.add( $x.getName() ); \n" +
                     "  C c = don( $x, C.class ); \n" +
                            // keep "xyz" even if C has a default
                     "      list.add( $x.getName() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "  $x.setName( null ); \n" +
                     "  c.setAge( 99 ); \n" +
                            // now revert to default by current most specific type, C
                     "      list.add( $x.getName() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "  c.setName( \"aaa\" ); \n" +
                     "  c.setAge( null ); \n" +
                            // set to "aaa"
                     "      list.add( $x.getName() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "end \n" +
                     "" +
                     "";
        KieBase knowledgeBase = loadKnowledgeBaseFromString( drl );
        TraitFactoryImpl.setMode(mode, knowledgeBase );

        KieSession knowledgeSession = knowledgeBase.newKieSession();
        List<String> list = new ArrayList<>();
        List<Integer> list2 = new ArrayList<>();
        knowledgeSession.setGlobal( "list", list );
        knowledgeSession.setGlobal( "list2", list2 );

        knowledgeSession.fireAllRules();

        for ( Object o : knowledgeSession.getObjects() ) {
            LOGGER.debug( o.toString());
        }

        LOGGER.debug( list.toString() );
        LOGGER.debug( list2.toString() );
        assertThat(list).containsExactly("1", null, "xyz", "xyz", "7", "aaa");
        assertThat(list2).containsExactly(18, null, 37, 99, 37);

        try {
            knowledgeSession = SerializationHelper.getSerialisedStatefulKnowledgeSession( knowledgeSession, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testInitializationConflictManagementPrimitiveTypes(VirtualPropertyMode mode) {
        String drl = "package org.drools.test; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "global java.util.List list2; \n" +
                     "" +
                     "declare trait A rate : double = 1.0 age : int = 18 end \n" +
                     "declare trait B rate : double = 5.0 age : int = 50 end \n" +
                     "declare trait C extends A,B rate : double = 7 age : int = 37 end \n" +
                     "" +
                     "declare X @Traitable( logical = true ) rate : double end \n" +
                     "" +
                     "" +
                     "rule Init \n" +
                     "when \n" +
                     "then \n" +
                     "  X x = new X(); \n" +
                     "  A a = don( x, A.class ); \n" +
                     // default 1, from A
                     "      list.add( x.getRate() ); \n" +
                     "      list2.add( a.getAge() ); \n" +
                     "  B b = don( x, B.class ); \n" +
                     // conflicting defaults A and B, nullify
                     "      list.add( x.getRate() ); \n" +
                     "      list2.add( b.getAge() ); \n" +
                     "end \n" +
                     "" +
                     "rule Later \n" +
                     "no-loop \n" +
                     "when \n" +
                     "  $x : X() \n" +
                     "then \n" +
                     "  $x.setRate( 16.3 ); \n" +
                     // set to "xyz"
                     "      list.add( $x.getRate() ); \n" +
                     "  C c = don( $x, C.class ); \n" +
                     // keep "xyz" even if C has a default
                     "      list.add( $x.getRate() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "  $x.setRate( 0.0 ); \n" +
                     "  c.setAge( 99 ); \n" +
                     "      list.add( $x.getRate() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "  c.setRate( -0.72 ); \n" +
                     "  c.setAge( 0 ); \n" +
                     // set to "aaa"
                     "      list.add( $x.getRate() ); \n" +
                     "      list2.add( c.getAge() ); \n" +
                     "end \n" +
                     "" +
                     "";
        KieBase knowledgeBase = loadKnowledgeBaseFromString( drl );
        TraitFactoryImpl.setMode(mode, knowledgeBase );

        KieSession knowledgeSession = knowledgeBase.newKieSession();
        List<Double> list = new ArrayList<>();
        List<Integer> list2 = new ArrayList<>();
        knowledgeSession.setGlobal( "list", list );
        knowledgeSession.setGlobal( "list2", list2 );

        knowledgeSession.fireAllRules();

        for ( Object o : knowledgeSession.getObjects() ) {
            LOGGER.debug( o.toString() );
        }

        LOGGER.debug( list.toString() );
        LOGGER.debug( list2.toString() );
        assertThat(list).containsExactly(1.0, 0.0, 16.3, 16.3, 0.0, -0.72);
        assertThat(list2).containsExactly(18, 0, 37, 99, 0);

        try {
            knowledgeSession = SerializationHelper.getSerialisedStatefulKnowledgeSession( knowledgeSession, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

        knowledgeSession.dispose();
    }


    @Traitable( logical = true )
    public static class Qty {
        private Integer num;

        public Qty( Integer num ) {
            this.num = num;
        }

        public Integer getNum() {
            return num;
        }

        public void setNum( Integer num ) {
            this.num = num;
        }
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    @Disabled("check file ReviseTraitTestWithPRAlwaysCategory.java") // 
    public void testHardGetSetOnLogicallyTraitedField(VirtualPropertyMode mode) {
        String drl = "package org.drools.test; " +
                     "import " + Qty.class.getCanonicalName() + "; " +
                     "" +
                     "global java.util.List list; " +

                     "declare Obs @Traitable( logical = true ) value : Qty end " +

                     "declare trait TObs @Trait( logical = true ) value : TQty end " +
                     "declare trait TQty @Trait( logical = true ) num : Integer end " +

                     "rule Init " +
                     "when " +
                     "then " +
                     "  Obs o = new Obs( new Qty( 42 ) ); " +
                     "  don( o, TObs.class ); " +
                     "end " +

                     "rule Log " +
                     "when " +
                     "  $o : TObs( $val : value.num ) " +
                     "then " +
                     "  list.add( $val ); " +
                     "end " +

                     "rule Change " +
                     "when " +
                     "  $s : String() " +
                     "  $o : TObs() " +
                     "then " +
                     "  delete( $s ); " +
                     "  modify( $o ) { getValue().setNum( 99 ); } " +
                     "end ";

        KieBase knowledgeBase = new KieHelper(PropertySpecificOption.ALLOWED).addContent( drl, ResourceType.DRL ).build();
        TraitFactoryImpl.setMode(mode, knowledgeBase );

        KieSession knowledgeSession = knowledgeBase.newKieSession();
        List<Integer> list = new ArrayList<>();
        knowledgeSession.setGlobal( "list", list );

        knowledgeSession.fireAllRules();
        assertThat(list).containsExactly(42);

        knowledgeSession.insert( "x" );
        knowledgeSession.fireAllRules();

        Object o  = knowledgeSession.getObjects(new ClassObjectFilter(Qty.class)).iterator().next();
        assertThat(((Qty) o).getNum()).isEqualTo((Integer) 99);
        assertThat(((CoreWrapper) o)._getFieldTMS().get("num", Integer.class)).isEqualTo(99);

        assertThat(list).containsExactly(42, 99);
        knowledgeSession.dispose();
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testFieldTypeDonMap(VirtualPropertyMode mode) {
        String drl = "package org.drools.test; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "global java.util.List list2; \n" +
                     "" +
                     "declare trait T " +
                     "  hardShort : short = 1 \n" +
                     "  hardChar : char = 1 \n" +
                     "  hardByte : byte = 1 \n" +
                     "  hardInt : int = 1 \n" +
                     "  hardLong : long = 1 \n" +
                     "  hardFloat : float = 1.0f \n" +
                     "  hardDouble : double = 1.0 \n" +
                     "  hardBoolean : boolean = true \n" +
                     "  hardString : String = \"x\" \n" +
                     "  softShort : short = 1 \n" +
                     "  softChar : char = 1 \n" +
                     "  softByte : byte = 1 \n" +
                     "  softInt : int = 1 \n" +
                     "  softLong : long = 1 \n" +
                     "  softFloat : float = 1.0f \n" +
                     "  softDouble : double = 1.0 \n" +
                     "  softBoolean : boolean = true \n" +
                     "  softString : String = \"x\" \n" +
                     "end \n" +
                     "" +
                     "declare X @Traitable( logical = true ) " +
                     "  hardShort : short  \n" +
                     "  hardChar : char  \n" +
                     "  hardByte : byte  \n" +
                     "  hardInt : int \n" +
                     "  hardLong : long  \n" +
                     "  hardFloat : float  \n" +
                     "  hardDouble : double  \n" +
                     "  hardBoolean : boolean  \n" +
                     "end \n" +
                     "" +
                     "" +
                     "rule Init \n" +
                     "when \n" +
                     "then \n" +
                     "  X x = new X(); \n" +
                     "  don( x, T.class ); \n" +
                     "end \n" +
                     "" +
                     "" +
                     "";
        KieBase knowledgeBase = loadKnowledgeBaseFromString( drl );
        TraitFactoryImpl.setMode(mode, knowledgeBase );

        KieSession knowledgeSession = knowledgeBase.newKieSession();

        knowledgeSession.fireAllRules();

        for ( Object o : knowledgeSession.getObjects() ) {
            LOGGER.debug( o.toString() );
        }

        try {
            knowledgeSession = SerializationHelper.getSerialisedStatefulKnowledgeSession( knowledgeSession, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }


        knowledgeSession.dispose();

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testDataStructs(VirtualPropertyMode mode) {
        String drl = "package org.drools.test; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "global java.util.List list2; \n" +
                     "" +
                     "declare trait T \n" +
                     "  hardString : String = \"x\" \n" +
                     "  hardString : String = \"x\" \n" +
                     "  softString : String = \"x\" \n" +
                     "  hardFloat : float = 5.9f \n" +
                     "end \n" +
                     "" +
                     "declare X @Traitable( logical = true ) \n" +
                     "  id : int @key \n" +
                     "  hardString : String  = \"a\" \n" +
                     "  hardInt    : int  = 12 \n" +
                     "  hardDouble : double  = 42.0 \n" +
                     "  hardFloat : float  = 2.3f \n" +
                     "end \n" +
                     "" +
                     "rule Init \n" +
                     "when \n" +
                     "then \n" +
                     "  X x = new X( 1 ); \n" +
                     "  x.setHardFloat( 8.42f ); \n" +
                     "  insert( x ); \n" +
                     "      x.setHardDouble( -11.2 ); \n" +
                     "  X x2 = new X( 2, \"b\", 13, 44.0, 16.5f ); \n" +
                     "      x2.setHardInt( -1 ); \n" +
                     "  insert( x2 ); \n" +
                     "  don( x, T.class ); \n" +
                     "  don( x2, T.class ); \n" +
                     "end \n" +
                     "";

        KieBase knowledgeBase = loadKnowledgeBaseFromString( drl );
        TraitFactoryImpl.setMode(mode, knowledgeBase );

        KieSession knowledgeSession = knowledgeBase.newKieSession();

        knowledgeSession.fireAllRules();

        FactType X = knowledgeBase.getFactType( "org.drools.test", "X" );

        for ( Object o : knowledgeSession.getObjects() ) {
            if ( X.getFactClass().isInstance( o ) ) {
                switch ( (Integer) X.get( o, "id" ) ) {
                    case 1 :
                        assertThat(X.get(o, "hardString")).isEqualTo("a");
                        assertThat(X.get(o, "hardInt")).isEqualTo(12);
                        assertThat(X.get(o, "hardDouble")).isEqualTo(-11.2);
                        assertThat(X.get(o, "hardFloat")).isEqualTo(8.42f);
                        break;
                    case 2 :
                        assertThat(X.get(o, "hardString")).isEqualTo("b");
                        assertThat(X.get(o, "hardInt")).isEqualTo(-1);
                        assertThat(X.get(o, "hardDouble")).isEqualTo(44.0);
                        assertThat(X.get(o, "hardFloat")).isEqualTo(16.5f);
                        break;
                    default:
                        fail( "Unexpected id " );
                }
            }
            LOGGER.debug( o.toString() );
        }

        try {
            knowledgeSession = SerializationHelper.getSerialisedStatefulKnowledgeSession( knowledgeSession, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }


        knowledgeSession.dispose();

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void shadowAliasSelf(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait VIP \n" +
                     "@Trait( logical=true ) \n" +
                     "  friend : VIP \n" +
                     "end \n" +
                     "" +
                     "declare Pers \n" +
                     "@Traitable( logical = true ) \n" +
                     "  friend : Pers \n" +
                     "end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  Pers p = new Pers(); " +
                     "  p.setFriend( p ); \n\n" +
                     "  don( p, VIP.class ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "salience 1 \n" +
                     "when \n" +
                     "  $x : Pers( friend isA VIP ) \n" +
                     "then \n" +
                     "  list.add( \"ok1\" );" +
                     "end \n" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        ks.fireAllRules();
        assertThat(list).isEqualTo(List.of("ok1"));

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void traitOnSet(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait T \n" +
                     "@Trait( logical=true ) \n" +
                     "  fld : S \n" +
                     "end \n" +
                     "" +
                     "declare trait U \n" +
                     "@Trait( logical=true ) \n" +
                     "  fld : R \n" +
                     "end \n" +
                     "" +
                     "declare trait S \n" +
                     "@Trait( logical=true ) \n" +
                     "end \n" +
                     "" +
                     "declare trait R \n" +
                     "@Trait( logical=true ) \n" +
                     "end \n" +
                     "" +
                     "declare K \n" +
                     "@propertyReactive \n" +
                     "@Traitable( logical = true ) \n" +
                     "  id : int @key\n" +
                     "  fld : K \n" +
                     "end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  K k = new K(1); " +
                     "  don( k, T.class ); \n" +
                     "  modify ( k ) { \n" +
                     "    setFld( new K(99) ); \n" +
                     "  } \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "salience 1 \n" +
                     "when \n" +
                     "  $x : K( fld isA S, fld not isA R ) \n" +
                     "then \n" +
                     "  list.add( \"ok1\" );" +
                     "end \n" +
                     "" +
                     "rule Check2 \n" +
                     "salience 1 \n" +
                     "when \n" +
                     "  String() \n" +
                     "  $x : K( fld isA S ) \n" +
                     "then \n" +
                     "  don( $x, U.class ); \n" +
                     "end \n" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();

        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        int n = ks.fireAllRules();
        LOGGER.debug( "Rules fired " + n );

        LOGGER.debug( "------------- ROUND TRIP -------------" );

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

        ks.insert( "go" );
        ks.fireAllRules();

        assertThat(list).isEqualTo(List.of("ok1"));
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAliasTraitOnClassLogicalRetract(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X \n" +
                     "  fld : T \n" +
                     "  fld2 : Q \n" +
                     "end \n" +
                     "" +
                     "declare trait W \n" +
                     "  fld : T \n" +
                     "end \n" +
                     "declare trait V \n" +
                     "  fld2 : Q \n" +
                     "end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( logical = true ) \n" +
                     "  fld : K \n" +
                     "  fld2 : Object \n" +
                     "end \n" +
                     "" +
                     "declare trait T @Trait( logical=true ) end \n" +
                     "declare trait Q @Trait( logical=true ) end \n" +
                     "declare K @Traitable() end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "  $s : String( this == \"go\" ) \n" +
                     "then \n" +
                     "  K k = new K(); \n" +
                     "  Y y = new Y( k, null ); \n" +
                     "  insert( y ); \n" +
                     "  insert( k ); \n" +
                     "" +
                     "  don( k, Q.class ); \n" +
                     "" +
                     "  don( y, X.class, true ); \n" +
                     "  don( y, W.class ); \n" +
                     "  don( y, V.class ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "when \n" +
                     "  $x : X( this isA V, fld isA T ) \n" +
                     "then \n" +
                     "  shed( $x, V.class ); \n" +
                     "  list.add( \"ok\" );" +
                     "end \n" +
                     "" +
                     "rule Check2 \n" +
                     "salience 10 \n" +
                     "when \n" +
                     "  String( this == \"go2\" ) \n" +
                     "  Q() \n" +
                     "  not X() not V() " +
                     "then \n" +
                     "  list.add( \"ok2\" );" +
                     "end \n" +
                     "" +
                     "rule Check3 \n" +
                     "salience 5 \n" +
                     "when \n" +
                     "  String( this == \"go2\" ) \n" +
                     "  K( this isA Q ) \n" +
                     "  not X() not V() " +
                     "then \n" +
                     "  list.add( \"ok3\" );" +
                     "end \n" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        FactHandle handle = ks.insert( "go" );

        ks.fireAllRules();
        assertThat(list).containsExactly("ok");

        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o  + " >> " + ((InternalFactHandle)ks.getFactHandle( o )).getEqualityKey() );
        }

        ks.retract( handle );
        ks.fireAllRules();

        for ( Object o : ks.getObjects( new ClassObjectFilter( ks.getKieBase().getFactType( "org.drools.test", "Y" ).getFactClass() ) ) ) {
            assertThat(o).isInstanceOf(TraitableBean.class);
            TraitableBean tb = (TraitableBean) o;

            TraitField fld = tb._getFieldTMS().getRegisteredTraitField("fld" );
            Set<Class<?>> types = fld.getRangeTypes();
            assertThat(types).hasSize(2);

            TraitField fld2 = tb._getFieldTMS().getRegisteredTraitField("fld2" );
            Set<Class<?>> types2 = fld2.getRangeTypes();
            assertThat(types2).hasSize(1);
        }

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

        ks.setGlobal( "list", list );

        ks.insert( "go2" );
        ks.fireAllRules();

        LOGGER.debug( list.toString() );

        assertThat(list).containsExactly("ok", "ok2", "ok3");

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testShadowAliasClassOnTraitLogicalRetract(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X \n" +
                     "  fld : K \n" +
                     "  fld2 : K \n" +
                     "end \n" +
                     "" +
                     "declare trait W \n" +
                     "  fld : Q \n" +
                     "end \n" +
                     "declare trait V \n" +
                     "  fld2 : T \n" +
                     "end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( logical = true ) \n" +
                     "  fld : T \n" +
                     "  fld2 : Q \n" +
                     "end \n" +
                     "" +
                     "declare trait T @Trait( logical=true ) id : int end \n" +
                     "declare trait Q @Trait( logical=true ) id : int end \n" +
                     "declare K @Traitable() id : int end \n" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "  $s : String( this == \"go\" ) \n" +
                     "then \n" +
                     "  K k1 = new K( 1 ); \n" +
                     "  K k2 = new K( 2 ); \n" +
                     "  T t = don( k1, T.class ); \n" +
                     "  Q q = don( k2, Q.class ); \n" +

                     "  Y y = new Y( t, q ); \n" +
                     "  insert( y ); \n" +
                     "" +
                     "  don( y, X.class, true ); \n" +
                     "end \n" +
                     "" +
                     "rule Check \n" +
                     "when \n" +
                     "  String( this == \"go\" ) \n" +
                     "  $x : X( $f1 : fld, $f2 : fld2 ) \n" +
                     "then \n" +
                     "  list.add( $f1.getId() );" +
                     "  list.add( $f2.getId() );" +
                     "end \n" +
                     "" +
                     "rule Check2\n" +
                     "when \n" +
                     "  not String( this == \"go\" ) \n" +
                     "  $x : Y( $f1 : fld, $f2 : fld2 ) \n" +
                     "then \n" +
                     "  list.add( $f1.getId() );" +
                     "  list.add( $f2.getId() );" +
                     "end \n";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List<Integer> list = new ArrayList<>();
        ks.setGlobal( "list", list );

        FactHandle handle = ks.insert( "go" );

        ks.fireAllRules();
        assertThat(list).containsExactly(1, 2);

        ks.retract( handle );
        ks.fireAllRules();

        for ( Object o : ks.getObjects( new ClassObjectFilter( ks.getKieBase().getFactType( "org.drools.test", "Y" ).getFactClass() ) ) ) {
            assertThat(o).isInstanceOf(TraitableBean.class);
        }

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

        LOGGER.debug( list.toString() );
        assertThat(list).containsExactly(1, 2, 1, 2);

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testSerial(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; \n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "import org.drools.base.factmodel.traits.Trait; \n" +
                     "" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait X end \n" +
                     "declare trait Z end \n" +
                     "" +
                     "declare Y \n" +
                     "@Traitable( ) \n" +
                     "end \n" +
                     "" +
                     "" +
                     "rule Don \n" +
                     "when \n" +
                     "then \n" +
                     "  Y y = new Y( ); \n" +
                     "  don( y, X.class ); \n" +
                     "  don( y, Z.class ); \n" +
                     "end \n" +
                     "" +
                     "";

        KnowledgeBuilder kbuilderImpl = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilderImpl.add( ResourceFactory.newByteArrayResource( drl.getBytes() ), ResourceType.DRL );
        if ( kbuilderImpl.hasErrors() ) {
            fail( kbuilderImpl.getErrors().toString() );
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages( kbuilderImpl.getKnowledgePackages() );

        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        KieSession ks = kbase.newKieSession();
        List list = new ArrayList();
        ks.setGlobal( "list", list );

        ks.fireAllRules();
        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }

        try {
            ks = SerializationHelper.getSerialisedStatefulKnowledgeSession( ks, true );
        } catch ( Exception e ) {
            fail( e.getMessage(), e );
        }

        for ( Object o : ks.getObjects() ) {
            LOGGER.debug( o.toString() );
        }
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitMismatchTypes(VirtualPropertyMode mode)
    {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Thing;\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.base.factmodel.traits.Trait;\n" +
                     "import org.drools.base.factmodel.traits.Alias;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "\n" +
                     "" +
                     "declare Parent\n" +
                     "@Traitable( logical = true )\n" +
                     "@propertyReactive\n" +
                     "    id : int\n" +
                     "end\n" +
                     "\n" +
                     "declare trait ParentTrait\n" +
                     "@Trait( logical = true )" + //does not have effect
                     "@propertyReactive\n" +
                     "    id : float\n" +   //different exception for Float
                     "end\n" +
                     "\n" +
                     "rule \"init\"\n" +
                     "when\n" +
                     "then\n" +
                     "    Parent p = new Parent(1010);\n" +
                     "    insert( p );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"don\"\n" +
                     "when\n" +
                     "    $p : Parent(id > 1000)\n" +
                     "then\n" +
                     "    Thing t = don( $p , ParentTrait.class );\n" +
                     "    list.add( t );\n" +
                     "end";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Thing> list = new ArrayList<>();
        ksession.setGlobal("list",list);
        ksession.fireAllRules();

        assertThat(list).hasSize(1).containsNull();
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitMismatchTypes2(VirtualPropertyMode mode)
    {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Thing;\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.base.factmodel.traits.Trait;\n" +
                     "import org.drools.base.factmodel.traits.Alias;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "\n" +
                     "declare Foo end \n" +
                     "declare Bar end \n" +
                     "" +
                     "declare Parent\n" +
                     "@Traitable( logical = true )\n" +
                     "@propertyReactive\n" +
                     "    id : Foo\n" +
                     "end\n" +
                     "\n" +
                     "declare trait ParentTrait\n" +
                     "@Trait( logical = true )" + //does not have effect
                     "@propertyReactive\n" +
                     "    id : Bar\n" +
                     "end\n" +
                     "\n" +
                     "rule \"init\"\n" +
                     "when\n" +
                     "then\n" +
                     "    Parent p = new Parent(new Foo());\n" +
                     "    insert( p );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"don\"\n" +
                     "when\n" +
                     "    $p : Parent(id != null)\n" +
                     "then\n" +
                     "    Thing t = don( $p , ParentTrait.class );\n" +
                     "    list.add( t );\n" +
                     "end";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Thing> list = new ArrayList<>();
        ksession.setGlobal("list",list);
        ksession.fireAllRules();

        assertThat(list).hasSize(1).containsNull();
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitMismatchTypes3(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.base.factmodel.traits.Trait;\n" +
                     "import org.drools.base.factmodel.traits.Alias;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "\n" +
                     "declare Foo end \n" +
                     "declare Bar extends Foo end \n" +
                     "" +
                     "declare Parent\n" +
                     "@Traitable( logical = true )\n" +
                     "@propertyReactive\n" +
                     "    id : Foo\n" +
                     "end\n" +
                     "\n" +
                     "declare trait ParentTrait\n" +
                     "@Trait( logical = true )" + //does not have effect
                     "@propertyReactive\n" +
                     "    id : Bar\n" +
                     "end\n" +
                     "\n" +
                     "rule \"init\"\n" +
                     "when\n" +
                     "then\n" +
                     "    Parent p = new Parent(null);\n" +
                     "    insert( p );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"don\"\n" +
                     "when\n" +
                     "    $p : Parent()\n" +
                     "then\n" +
                     "    ParentTrait pt = don( $p , ParentTrait.class );\n" +
                     "    pt.setId( new Bar() ); \n" +
                     "   list.add( $p.getId() ); \n" +
                     "end";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Object> list = new ArrayList<>();
        ksession.setGlobal("list",list);
        ksession.fireAllRules();

        LOGGER.debug( "list" + list );

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getClass().getName()).isEqualTo("org.drools.base.factmodel.traits.test.Bar");
    }
}
