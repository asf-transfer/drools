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
package org.drools.model.codegen.execmodel;

import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.CompositeObjectSinkAdapter;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.base.rule.IndexableConstraint;
import org.drools.base.util.index.ConstraintTypeOperator;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.util.DateUtils;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexTest extends BaseModelTest {

    public IndexTest( RUN_TYPE testRunType ) {
        super( testRunType );
    }

    @Test
    public void testBetaIndexOnDeclaration() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "rule R1 when\n" +
                        "  String( $l : length )\n" +
                        "  $p : Person(age == $l)\n" +
                        "then\n" +
                        "end";

        KieSession ksession = getKieSession( str );

        ObjectTypeNode otn = getObjectTypeNodeForClass( ksession, Person.class );
        BetaNode beta = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        IndexableConstraint betaConstraint = (IndexableConstraint) beta.getConstraints()[0];
        assertThat(betaConstraint.getLeftIndexExtractor()).isNotNull();

        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 4) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testBetaIndexWithAccessor() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "rule R1 when\n" +
                        "  $s : String()\n" +
                        "  $p : Person(age == $s.length)\n" +
                        "then\n" +
                        "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 4) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    public static class ObjectWrapper {
        private final Object object;

        public ObjectWrapper( Object object ) {
            this.object = object;
        }

        public Object getObject() {
            return object;
        }
    }

    public static class IntegerWrapper {
        private final Integer integer;

        public IntegerWrapper( Integer integer ) {
            this.integer = integer;
        }

        public Integer getInteger() {
            return integer;
        }
    }

    @Test
    public void testBetaIndexWithImplicitDowncast() {
        // DROOLS-5614
        String str =
                "import " + ObjectWrapper.class.getCanonicalName() + ";" +
                "import " + IntegerWrapper.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  ObjectWrapper( $o : object )\n" +
                "  IntegerWrapper( integer == $o )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( new ObjectWrapper( 42 ) );
        ksession.insert( new IntegerWrapper( 42 ) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testAlphaIndexWithDateEqual() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                     "rule R1 when\n" +
                     "  Person( birthDay == \"01-Oct-2000\" )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Person( birthDay == \"01-Nov-2000\" )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Person( birthDay == \"01-Dec-2000\" )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertConstraintType(ksession.getKieBase(), Person.class, "R1", ConstraintTypeOperator.EQUAL);
        assertConstraintType(ksession.getKieBase(), Person.class, "R2", ConstraintTypeOperator.EQUAL);
        assertConstraintType(ksession.getKieBase(), Person.class, "R3", ConstraintTypeOperator.EQUAL);

        Person person = new Person("John");
        person.setBirthDay(DateUtils.parseDate("01-Nov-2000"));
        ksession.insert(person);

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testAlphaIndexWithDateRelation() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                     "rule R1 when\n" +
                     "  Person( birthDay > \"01-Oct-2000\" )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Person( birthDay <= \"01-Nov-2000\" )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Person( birthDay >= \"01-Dec-2000\" )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertConstraintType(ksession.getKieBase(), Person.class, "R1", ConstraintTypeOperator.GREATER_THAN);
        assertConstraintType(ksession.getKieBase(), Person.class, "R2", ConstraintTypeOperator.LESS_OR_EQUAL);
        assertConstraintType(ksession.getKieBase(), Person.class, "R3", ConstraintTypeOperator.GREATER_OR_EQUAL);

        Person person = new Person("John");
        person.setBirthDay(DateUtils.parseDate("01-Nov-2000"));
        ksession.insert(person);

        assertThat(ksession.fireAllRules()).isEqualTo(2);
    }

    private void assertConstraintType(KieBase kbase, Class<?> factClass, String ruleName, ConstraintTypeOperator expectedType) {
        boolean asserted = false;
        EntryPointNode epn = ((InternalKnowledgeBase) kbase).getRete().getEntryPointNodes().values().iterator().next();
        ObjectTypeNode otn = epn.getObjectTypeNodes().get(new ClassObjectType(factClass));
        ObjectSink[] sinks = otn.getObjectSinkPropagator().getSinks();
        for (ObjectSink objectSink : sinks) {
            AlphaNode alphaNode = (AlphaNode) objectSink;
            Rule rule = alphaNode.getAssociatedRules()[0]; // assume that one rule has one AlphaNode
            if (rule.getName().equals(ruleName)) {
                IndexableConstraint constraint = (IndexableConstraint) alphaNode.getConstraint();
                assertThat(constraint.getConstraintType()).isEqualTo(expectedType);
                asserted = true;
            }
        }
        assertThat(asserted).isTrue();
    }

    @Test
    public void testBetaIndexOn2ValuesOnLeftTuple() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  Integer( $i : this )\n" +
                "  String( $l : length )\n" +
                "  $p : Person(age == $l + $i)\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ObjectTypeNode otn = getObjectTypeNodeForClass( ksession, Person.class );
        BetaNode beta = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        // this beta index is only supported by executable model
        assertThat(beta.getRawConstraints().isIndexed()).isEqualTo(this.testRunType.isExecutableModel());

        ksession.insert( 5 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testNoBetaIndexWithThisPropertyOnRight() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  Integer( $i : this )\n" +
                "  String( $l : length )\n" +
                "  $p : Person($l == age - $i)\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( 5 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testNoBetaIndexWithThisOperationOnLeft() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  String( $l : length )\n" +
                "  $p: Person()\n" +
                "  Integer( $p.getAge() == this + $l )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( 5 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testNoBetaIndexWithThisOperationOnLeft2() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  String( $l : length )\n" +
                "  Person( $age : age )\n" +
                "  Integer( $age == this + $l )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( 5 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testNoBetaIndexWithThisMethodInvocationOnLeft() {
        // DROOLS-5995
        String str =
                "import " + List.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  $lh: List()\n" +
                "  String( $lh.indexOf( this ) == $lh.size() )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );
        assertThat(ksession.fireAllRules()).isEqualTo(0);
    }

    @Test
    public void testBetaIndexOn3ValuesOnLeftTuple() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  Long( $x : intValue )\n" +
                "  Integer( $i : this )\n" +
                "  String( $l : length )\n" +
                "  $p : Person(age == $l + $i + $x)\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ObjectTypeNode otn = getObjectTypeNodeForClass( ksession, Person.class );
        BetaNode beta = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        // this beta index is only supported by executable model
        assertThat(beta.getRawConstraints().isIndexed()).isEqualTo(this.testRunType.isExecutableModel());

        ksession.insert( 2L );
        ksession.insert( 3 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testBetaIndexOn4ValuesOnLeftTuple() {
        // DROOLS-5995
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "  Short( $y : intValue )\n" +
                "  Long( $x : intValue )\n" +
                "  Integer( $i : this )\n" +
                "  String( $l : length )\n" +
                "  $p : Person(age == $l + $i + $x + $y)\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ObjectTypeNode otn = getObjectTypeNodeForClass( ksession, Person.class );
        BetaNode beta = ((RightInputAdapterNode)otn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        // this beta index is only supported by executable model
        assertThat(beta.getRawConstraints().isIndexed()).isEqualTo(this.testRunType.isExecutableModel());

        ksession.insert( (short)1 );
        ksession.insert( 1L );
        ksession.insert( 3 );
        ksession.insert( "test" );
        ksession.insert( new Person("Sofia", 9) );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testAlphaIndexHashed() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                     "rule R1 when\n" +
                     "  Person( age == 10 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Person( age == 20 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Person( age == 30 )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertHashIndex(ksession, Person.class, 3);
    }

    @Test
    public void testAlphaIndexHashedNonGetter() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                     "rule R1 when\n" +
                     "  Person( calcAge == 10 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Person( calcAge == 20 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Person( calcAge == 30 )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertHashIndex(ksession, Person.class, 3);
    }

    private void assertHashIndex(KieSession ksession, Class<?> factClass, int expectedHashedSinkMapSize) {
        EntryPointNode epn = ((InternalKnowledgeBase) ksession.getKieBase()).getRete().getEntryPointNodes().values().iterator().next();
        ObjectTypeNode otn = epn.getObjectTypeNodes().get(new ClassObjectType(factClass));
        CompositeObjectSinkAdapter compositeObjectSinkAdapter = (CompositeObjectSinkAdapter) otn.getObjectSinkPropagator();

        assertThat(compositeObjectSinkAdapter.getHashedSinkMap()).isNotNull();
        assertThat(compositeObjectSinkAdapter.getHashedSinkMap().size()).isEqualTo(expectedHashedSinkMapSize);
    }

    @Test
    public void testAlphaIndexHashedPrimitiveWrapper() {
        String str =
                "import " + Integer.class.getCanonicalName() + ";\n" +
                     "rule R1 when\n" +
                     "  Integer( intValue == 10 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Integer( intValue == 20 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Integer( intValue == 30 )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertHashIndex(ksession, Integer.class, 3);
    }

    @Test
    public void testAlphaIndexHashedNumberInterface() {
        String str =
                "import " + Integer.class.getCanonicalName() + ";\n" +
                     "rule R1 when\n" +
                     "  Number( intValue == 10 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "  Number( intValue == 20 )\n" +
                     "then\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "  Number( intValue == 30 )\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(str);

        assertHashIndex(ksession, Number.class, 3);

        ksession.insert(10);
        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(1);
    }

    @Test
    public void testAlphaIndexWithDeclarationInPattern() {
        final String str =
                "package org.drools.mvel.compiler\n" +
                           "import " + Person.class.getCanonicalName() + ";" +
                           "rule r1 when\n" +
                           "    Person( $rate : 100, " +
                           "            salary > age * $rate )\n" +
                           "then\n" +
                           "end\n";

        KieSession ksession = getKieSession(str);

        ObjectTypeNode otn = getObjectTypeNodeForClass(ksession, Person.class);
        ObjectSink sink = otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(sink).isInstanceOf(AlphaNode.class);

        Person person = new Person("John", 20);
        person.setSalary(5000);
        ksession.insert(person);
        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(1);
    }

    @Test
    public void testAlphaIndexWithDeclarationInPatternWithSameNameProp() {
        final String str =
                "package org.drools.mvel.compiler\n" +
                           "import " + Person.class.getCanonicalName() + ";" +
                           "rule r1 when\n" +
                           "    Person( age : age, " +
                           "            $rate : 100, " +
                           "            salary > age * $rate )\n" +
                           "then\n" +
                           "end\n";

        KieSession ksession = getKieSession(str);

        ObjectTypeNode otn = getObjectTypeNodeForClass(ksession, Person.class);
        ObjectSink sink = otn.getObjectSinkPropagator().getSinks()[0];
        assertThat(sink).isInstanceOf(AlphaNode.class);

        Person person = new Person("John", 20);
        person.setSalary(5000);
        ksession.insert(person);
        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(1);
    }
}
