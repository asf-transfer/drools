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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.marshalling.ClassObjectMarshallingStrategyAcceptor;
import org.drools.serialization.protobuf.marshalling.JavaSerializableResolverStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.drools.core.marshalling.SerializablePlaceholderResolverStrategy;
import org.drools.mvel.compiler.Person;
import org.kie.api.KieBase;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.io.ResourceType;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class MarshallerTest {

    private Environment env = EnvironmentFactory.newEnvironment();

    public static Stream<ObjectMarshallingStrategy> parameters() {
        return Stream.of(new JavaSerializableResolverStrategy(ClassObjectMarshallingStrategyAcceptor.DEFAULT),
                              new SerializablePlaceholderResolverStrategy(ClassObjectMarshallingStrategyAcceptor.DEFAULT));
    }

    private void setupEnvironment(ObjectMarshallingStrategy strategy) {
        this.env.set( EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, new ObjectMarshallingStrategy[]{ strategy } );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testAgendaDoNotSerializeObject(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        KieSession ksession = null;
        try {
            String str =
                    "import java.util.Collection\n" +
                            "rule R1 when\n" +
                            "    String(this == \"x\" || this == \"y\" || this == \"z\")\n" +
                            "then\n" +
                            "end\n";

            KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
            ksession = kbase.newKieSession(null, env);

            ksession.insert("x");
            ksession.insert("y");
            ksession.insert("z");

            assertThat(ksession.fireAllRules()).isEqualTo(3);

            ReadSessionResult serialisedStatefulKnowledgeSession = SerializationHelper.getSerialisedStatefulKnowledgeSessionWithMessage(ksession, ksession.getKieBase(), true);
            ksession = serialisedStatefulKnowledgeSession.getSession();

            ProtobufMessages.KnowledgeSession deserializedMessage = serialisedStatefulKnowledgeSession.getDeserializedMessage();

            assertThat(ksession.fireAllRules()).isEqualTo(0);
            assertThat(deserializedMessage.getRuleData().getAgenda().getMatchList().stream().anyMatch(ml -> {
                return ml.getTuple().getObjectList().size() > 0;
            })).isFalse();
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromWithFireBeforeSerialization(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import java.util.Collection\n" +
                        "rule R1 when\n" +
                        "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                        "then\n" +
                        "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);
            assertThat(ksession.fireAllRules()).isEqualTo(3);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(0);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromWithFireAfterSerialization(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import java.util.Collection\n" +
                        "rule R1 when\n" +
                        "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                        "then\n" +
                        "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(3);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromWithPartialFiring(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);    	
        String str =
                "import java.util.Collection\n" +
                "rule R1 when\n" +
                "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                "then\n" +
                "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);
            assertThat(ksession.fireAllRules(2)).isEqualTo(2);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void test2FromsWithPartialFiring(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import java.util.Collection\n" +
                "rule R1 when\n" +
                "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                "    String() from [ \"a\", \"b\", \"c\" ]\n" +
                "then\n" +
                "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);
            assertThat(ksession.fireAllRules(5)).isEqualTo(5);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(4);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromAndJoinWithPartialFiring(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import java.util.Collection\n" +
                "rule R1 when\n" +
                "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                "    Integer()\n" +
                "then\n" +
                "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);
            ksession.insert( 42 );
            assertThat(ksession.fireAllRules(2)).isEqualTo(2);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testAgendaReconciliationAccumulate(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "rule X when\n" +
                        "  accumulate ( $p: Person ( getName().startsWith(\"M\")); \n" +
                        "                $sum : sum($p.getAge())  \n" +
                        "              )                          \n" +
                        "then\n" +
                        "  insert($sum);\n" +
                        "end";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);

            ksession.insert(new Person("Mark", 37));
            ksession.insert(new Person("Edson", 35));
            ksession.insert(new Person("Mario", 40));

            assertThat(ksession.fireAllRules()).isEqualTo(1);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(0);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testAgendaReconciliationAccumulate2(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
    	String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "rule X when\n" +
                        "  accumulate ( $p: Person ( getName().startsWith(\"M\")); \n" +
                        "                $sum : sum($p.getAge())  \n" +
                        "              )                          \n" +
                        "then\n" +
                        "  insert($sum);\n" +
                        "end";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);

            ksession.insert(new Person("Mark", 37));
            ksession.insert(new Person("Edson", 35));
            ksession.insert(new Person("Mario", 40));

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testMultiAccumulate(ObjectMarshallingStrategy strategy) throws Exception {
        // DROOLS-5579
    	setupEnvironment(strategy);
    	String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "rule X when\n" +
                        "  accumulate ( Person ( getName().startsWith(\"M\"), $age : age ); \n" +
                        "                $sum : sum( $age ), $max : max( $age )  \n" +
                        "              )                          \n" +
                        "then\n" +
                        "  insert($sum);\n" +
                        "end";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);

            ksession.insert(new Person("Mark", 37));
            ksession.insert(new Person("Edson", 35));
            ksession.insert(new Person("Mario", 40));

            assertThat(ksession.fireAllRules()).isEqualTo(1);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(0);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testSubnetwork(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
    	final String str =
                "rule R1 when\n" +
                        "    String()\n" +
                        "    Long()\n" +
                        "    not( Long() and Integer() )\n" +
                        "then end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL)
                .build(EqualityBehaviorOption.EQUALITY);
        KieSession ksession = null;

        try {
            ksession = kbase.newKieSession(null, env);

            ksession.insert("Luca");
            ksession.insert(2L);
            ksession.insert(10);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(0);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            ksession.delete(ksession.getFactHandle(10));

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testSubnetwork2(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
    	final String str =
                "rule R1 when\n" +
                        "    String()\n" +
                        "    Long()\n" +
                        "    not( Long() and Integer() )\n" +
                        "then end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL)
                .build(EqualityBehaviorOption.EQUALITY);
        KieSession ksession = null;

        try {
            ksession = kbase.newKieSession(null, env);
            ksession.insert("Luca");
            ksession.insert(2L);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);

            ksession.insert("Mario");
            ksession.insert(11);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromJoinWithPartialFiring(ObjectMarshallingStrategy strategy) throws Exception {
    	setupEnvironment(strategy);
        String str =
                "import java.util.Collection\n" +
                        "rule R1 when\n" +
                        "    Integer()\n" +
                        "    String() from [ \"x\", \"y\", \"z\" ]\n" +
                        "then\n" +
                        "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);
            InternalFactHandle fh1 = ( InternalFactHandle ) ksession.insert( 1 );

            assertThat(ksession.fireAllRules(2)).isEqualTo(2);

            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            assertThat(ksession.fireAllRules()).isEqualTo(1);

            // old FH should keep its id
            InternalFactHandle intFH = ( InternalFactHandle ) ksession.getFactHandles().iterator().next();
            assertThat(intFH.getId()).isEqualTo(fh1.getId());

            // serialization/deserialization of derived FHs shouldn't consume more FH ids
            assertThat(((InternalFactHandle) ksession.insert(2)).getId()).isEqualTo(fh1.getId() + 4);
        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }

    public static class LongFact implements Serializable {
        private long value;

        public LongFact() { }

        public LongFact(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;
            LongFact longFact = ( LongFact ) o;
            return value == longFact.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash( value );
        }
    }

    public static class LongFacts implements Serializable {
        private List<LongFact> facts;

        public LongFacts() {
            this.facts = new ArrayList<>();
        }

        public List<LongFact> getFacts() {
            return facts;
        }

        public void setFacts(List<LongFact> facts) {
            this.facts = facts;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;
            LongFacts longFacts = ( LongFacts ) o;
            return Objects.equals( facts, longFacts.facts );
        }

        @Override
        public int hashCode() {
            return Objects.hash( facts );
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testFromWithInsertLogical(ObjectMarshallingStrategy strategy) throws Exception {
        // DROOLS-5713
    	setupEnvironment(strategy);
        String str =
                "import " + LongFact.class.getCanonicalName() + "\n" +
                "import " + LongFacts.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "    LongFacts($lfs: facts)\n" +
                "    $lf: LongFact() from $lfs\n" +
                "then\n" +
                "    insertLogical($lf);" +
                "end\n";

        KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();
        KieSession ksession = null;
        try {
            ksession = kbase.newKieSession(null, env);

            String id = UUID.randomUUID().toString();
            LongFacts longFacts = new LongFacts();
            longFacts.getFacts().add(new LongFact(123456));
            ksession.insert( longFacts );

            assertThat(ksession.fireAllRules()).isEqualTo(1);
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            assertThat(ksession.fireAllRules()).isEqualTo(0);

        } finally {
            if (ksession != null) {
                ksession.dispose();
            }
        }
    }
}
