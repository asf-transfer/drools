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
import java.util.Collection;
import java.util.List;

import org.drools.base.factmodel.traits.Trait;
import org.drools.base.factmodel.traits.Traitable;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.traits.compiler.CommonTraitTest;
import org.drools.traits.core.factmodel.TraitFactoryImpl;
import org.drools.traits.core.factmodel.VirtualPropertyMode;
import org.drools.traits.core.util.StandaloneTraitFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.definition.type.PropertyReactive;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.traits.compiler.factmodel.traits.TraitTestUtils.createStandaloneTraitFactory;

public class LegacyTraitTest extends CommonTraitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyTraitTest.class);

    public static Collection<VirtualPropertyMode> modes() {
        return List.of(VirtualPropertyMode.MAP, VirtualPropertyMode.TRIPLES);
    }

    private KieSession getSessionFromString(String drl) {
        KnowledgeBuilder knowledgeBuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        knowledgeBuilder.add(ResourceFactory.newByteArrayResource(drl.getBytes()),
                              ResourceType.DRL);
        if (knowledgeBuilder.hasErrors()) {
            throw new RuntimeException(knowledgeBuilder.getErrors().toString());
        }

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages(knowledgeBuilder.getKnowledgePackages());

        KieSession session = kbase.newKieSession();
        return session;
    }

    // Getters and setters are both needed. They should refer to an attribute with the same name
    public static class PatientImpl implements Patient {
        private String name;
        public String getName() {
            return this.name;
        }
        public void setName(String name) {
            this.name = name;
        }

    }

    public static interface Pers {
        public String getName();
    }

    public static interface Patient extends Pers {
    }

    public static interface Procedure {
        public Patient getSubject();
        public void setSubject(Patient p);
    }

    public static interface ExtendedProcedure extends Procedure {
        public Pers getPers();
        public void setPers(Pers pers);
    }

    public class ProcedureImpl implements Procedure {

        public ProcedureImpl() {
        }

        private Patient subject;

        @Override
        public Patient getSubject() {
            return this.subject;
        }
        public void setSubject(Patient patient) {
            this.subject = patient;
        }
    }

    public class ExtendedProcedureImpl extends ProcedureImpl implements ExtendedProcedure {

        public ExtendedProcedureImpl() {
        }

        private Pers pers;

        @Override
        public Pers getPers() {
            return this.pers;
        }
        public void setPers(Pers pers) {
            this.pers = pers;
        }
    }


    @ParameterizedTest
    @MethodSource("modes")
    public void traitWithPojoInterface(VirtualPropertyMode mode) {
        String source = "package org.drools.compiler.test;\n" +
                        "import " + LegacyTraitTest.Procedure.class.getCanonicalName()   + ";\n" +
                        "import " + LegacyTraitTest.class.getCanonicalName() + ";\n" +
                        "import " + LegacyTraitTest.ExtendedProcedureImpl.class.getCanonicalName() + ";\n" +
                        "import " + LegacyTraitTest.ExtendedProcedure.class.getCanonicalName()  + ";\n" +

                        // enhanced so that declaration is not needed
                        // "declare ProcedureImpl end " +
                        "declare trait ExtendedProcedure " +
                        "   @role(event)" +
                        "end " +

                        // Surgery must be declared as trait, since it does not extend Thing
                        "declare trait Surgery extends ExtendedProcedure end " +

                        "declare ExtendedProcedureImpl " +
                        "    @Traitable " +
                        "end " +

                        "rule 'Don Procedure' " +
                        "when " +
                        "    $p : ExtendedProcedure() " +
                        "then " +
                        "    don($p, Surgery.class); " +
                        "end " +

                        "rule 'Test 1' " +
                        "dialect 'mvel' " +
                        "when " +
                        "    $s1 : ExtendedProcedure($subject : subject) " +
                        "    $s2 : ExtendedProcedure(subject == $subject) " +
                        "then " +
                        "end " +

                        "rule 'Test 2' " +
                        "dialect 'mvel' " +
                        "when " +
                        "    $s1 : ExtendedProcedure($subject : subject.name) " +
                        "    $s2 : ExtendedProcedure(subject.name == $subject) " +
                        "then " +
                        "end " +

                        "rule 'Test 3' " +
                        "dialect 'mvel' " +
                        "when " +
                        "    $s1 : ExtendedProcedure() " +
                        "then " +
                        "    update($s1); " +
                        "end " +
                        "\n";

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        ExtendedProcedureImpl procedure1 = new ExtendedProcedureImpl();
        ExtendedProcedureImpl procedure2 = new ExtendedProcedureImpl();

        PatientImpl patient1 = new PatientImpl();
        patient1.setName("John");
        procedure1.setSubject(patient1);
        procedure1.setPers(new PatientImpl());

        PatientImpl patient2 = new PatientImpl();
        patient2.setName("John");
        procedure2.setSubject(patient2);
        procedure2.setPers(new PatientImpl());

        ks.insert(procedure1);
        ks.insert(procedure2);

        ks.fireAllRules(500);
    }


    @Traitable
    @PropertyReactive
    public static class BarImpl implements Foo { }

    public static interface Root { }

    public static interface Trunk extends Root { }

    @PropertyReactive
    @Trait
    public static interface Foo extends Trunk { }

    @ParameterizedTest
    @MethodSource("modes")
    public void traitWithMixedInterfacesExtendingEachOther(VirtualPropertyMode mode) {
        String source = "package org.drools.compiler.test;" +
                        "import " + BarImpl.class.getCanonicalName() + "; " +
                        "import " + Foo.class.getCanonicalName() + "; " +
                        "import " + Trunk.class.getCanonicalName() + "; " +
                        "global java.util.List list; " +

                        // We need to redeclare the interfaces as traits, the annotation on the original class is not enough here
                        "declare trait Foo end " +
                        // notice that the declarations do not include supertypes, and are out of order. The engine will figure out what to do
                        "declare trait Root end " +

                        "declare trait Foo2 extends Foo " +
                        "  @propertyReactive " +
                        "end " +

                        "rule 'Bar Don'" +
                        "when " +
                        "   $b : BarImpl(this ##isA Foo.class, this not ##isA Foo2.class)\n" +
                        "   String()\n" +
                        "then " +
                        "   list.add(3); " +
                        "   retract($b); " +
                        "end " +

                        "rule 'Don Bar' " +
                        "no-loop " +
                        "when " +
                        "    $b : Foo() " +
                        "then " +
                        "    list.add(1); " +
                        "    don($b, Foo2.class); " +
                        "end " +

                        "rule 'Cant really shed Foo but Foo2' " +
                        "when " +
                        "   $b : Foo2() " +
                        "then " +
                        "   list.add(2); " +
                        "   shed($b, Foo.class); " +
                        "   insert(\"done\");" +
                         "end " +

                        "";


        KieSession ks = getSessionFromString(source);
        KieBase kieBase = ks.getKieBase();
        TraitFactoryImpl.setMode(mode, kieBase);
        List<Integer> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.insert(new BarImpl());

        int n = ks.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).containsExactly(1, 2, 3);
        assertThat(n).isEqualTo(3);
    }



    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitWithNonAccessorMethodShadowing(VirtualPropertyMode mode) throws Exception {
        StandaloneTraitFactory factory = createStandaloneTraitFactory();
        SomeInterface r = (SomeInterface) factory.don(new SomeClass(), SomeInterface.class);
		r.prepare();
		assertThat(r.getFoo()).isEqualTo(42);
		assertThat(r.doThis("that")).isEqualTo("I did that");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testPojoExtendInterface(VirtualPropertyMode mode) {
        // DROOLS-697
        // It is now allowed for a declared type to extend an interface
        // The interface itself will be added to the implements part of the generated class

        final String s1 = "package test;\n" +

                "declare Poojo extends Mask " +
                "end " +

                "declare trait Mask " +
                "end " +
                "";

        KieHelper kh = new KieHelper();
        kh.addContent(s1, ResourceType.DRL);

        assertThat(kh.verify().getMessages(Message.Level.ERROR)).isEmpty();
    }

}
