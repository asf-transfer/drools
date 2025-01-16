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

import org.drools.base.base.ClassObjectType;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.Trait;
import org.drools.base.factmodel.traits.Traitable;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.rule.EntryPointId;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.io.ByteArrayResource;
import org.drools.io.ClassPathResource;
import org.drools.kiesession.entrypoints.NamedEntryPoint;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.traits.compiler.CommonTraitTest;
import org.drools.traits.compiler.Person;
import org.drools.traits.core.factmodel.Entity;
import org.drools.traits.core.factmodel.HierarchyEncoder;
import org.drools.traits.core.factmodel.MapWrapper;
import org.drools.traits.core.factmodel.TraitFactoryImpl;
import org.drools.traits.core.factmodel.TraitProxyImpl;
import org.drools.traits.core.factmodel.TraitRegistryImpl;
import org.drools.traits.core.factmodel.TraitTypeMapImpl;
import org.drools.traits.core.factmodel.TripleBasedBean;
import org.drools.traits.core.factmodel.TripleBasedStruct;
import org.drools.traits.core.factmodel.VirtualPropertyMode;
import org.drools.traits.core.reteoo.TraitRuntimeComponentFactory;
import org.drools.traits.core.util.CodedHierarchyImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.KieBaseOption;
import org.kie.api.definition.type.FactType;
import org.kie.api.definition.type.PropertyReactive;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.command.CommandFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TraitTest extends CommonTraitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraitTest.class);
    
    public static Stream<VirtualPropertyMode> parameters() {
        return Stream.of(VirtualPropertyMode.MAP, VirtualPropertyMode.TRIPLES);
    }

    private KieSession getSession(String... ruleFiles) {
        KieHelper kieHelper = new KieHelper();
        for (String file : ruleFiles) {
            kieHelper.kfs.write(new ClassPathResource(file));
        }
        return kieHelper.build().newKieSession();
    }

    private KieSession getSessionFromString(String drl) {
        return new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
    }

    private KieBase getKieBaseFromString(String drl, KieBaseOption... options) {
        return new KieHelper().addContent(drl, ResourceType.DRL).build(options);
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testRetract(VirtualPropertyMode mode) {
        String drl = "package org.drools.compiler.trait.test; \n" +
                "import org.drools.base.factmodel.traits.Traitable; \n" +
                "" +
                "declare Foo @Traitable end\n" +
                "declare trait Bar end \n" +
                "" +
                "rule Init when then\n" +
                "  Foo foo = new Foo(); \n" +
                "  don(foo, Bar.class); \n" +
                "end\n" +
                "rule Retract \n" +
                "when\n" +
                " $bar : Bar()\n" +
                "then\n" +
                "  delete($bar); \n" +
                "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        assertThat(ks.fireAllRules()).isEqualTo(2);

        for (Object o : ks.getObjects()) {
            LOGGER.debug(o.toString());
        }

        assertThat(ks.getObjects()).isEmpty();
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitWrapGetAndSet(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, kb);
        kb.addPackages(kbuilder.getKnowledgePackages());

        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        Class<?> trait = kb.getFactType("org.drools.compiler.trait.test", "Student").getFactClass();

        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        Map<String, Object> virtualFields = imp._getDynamicProperties();
        Map<String, Object> wrapper = proxy.getFields();

        wrapper.put("name", "john");

        wrapper.put("virtualField", "xyz");

        wrapper.entrySet();
        assertThat(wrapper).hasSize(4);
        assertThat(virtualFields).hasSize(2);

        assertThat(wrapper.get("name")).isEqualTo("john");
        assertThat(wrapper.get("virtualField")).isEqualTo("xyz");

        assertThat(impClass.get(imp, "name")).isEqualTo("john");

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitShed(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitShed.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());


        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        assertThat(info).isEmpty();

        ks.fireAllRules();

        assertThat(info).contains("Student");
        assertThat(info).hasSize(1);

        ks.insert("hire");
        ks.fireAllRules();

        assertThat(info).contains("Worker");
        assertThat(info).hasSize(2);

        ks.insert("check");
        ks.fireAllRules();

        assertThat(info).hasSize(4);
        assertThat(info).contains("Conflict");
        assertThat(info).contains("Nothing");

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitDon(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        Collection<? extends Object> wm = ks.getObjects();

        ks.insert("go");
        ks.fireAllRules();

        assertThat(info).contains("DON");
        assertThat(info).contains("SHED");

        Iterator<? extends Object> it = wm.iterator();
        Object x = it.next();
        if (x instanceof String) {
            x = it.next();
        }

        LOGGER.debug(x.getClass().toString());
        LOGGER.debug(x.getClass().getSuperclass().toString());
        LOGGER.debug(List.of(x.getClass().getInterfaces()).toString());
    }
    
    @ParameterizedTest()
    @MethodSource("parameters")
    public void testMixin(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitMixin.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        assertThat(info).contains("27");
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void traitMethodsWithObjects(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitWrapping.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> errors = new ArrayList<>();
        ks.setGlobal("list", errors);

        ks.fireAllRules();

        if (!errors.isEmpty()) {
            LOGGER.error(errors.toString());
        }
        assertThat(errors).isEmpty();

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void traitMethodsWithPrimitives(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitWrappingPrimitives.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> errors = new ArrayList<>();
        ks.setGlobal("list", errors);

        ks.fireAllRules();

        if (!errors.isEmpty()) {
            LOGGER.error(errors.toString());
        }
        assertThat(errors).isEmpty();

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitProxy(VirtualPropertyMode mode) throws Exception {

        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());
        TraitFactoryImpl.setMode(mode, kb);
        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        impClass.set(imp, "name", "aaa");

        Class<?> trait = kb.getFactType("org.drools.compiler.trait.test", "Student").getFactClass();
        Class<?> trait2 = kb.getFactType("org.drools.compiler.trait.test", "Role").getFactClass();

        assertThat(trait).isNotNull();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);
        proxy.getFields().put("field", "xyz");
        //            proxy.getFields().put("name", "aaa");

        assertThat(proxy).isNotNull();

        TraitProxyImpl proxy2 = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);
        assertThat(proxy).isSameAs(proxy2);

        TraitProxyImpl proxy3 = (TraitProxyImpl) tFactory.getProxy(imp, trait2, false);
        assertThat(proxy3).isNotNull();
        assertThat(proxy3.getFields().get("field")).isEqualTo("xyz");
        assertThat(proxy3.getFields().get("name")).isEqualTo("aaa");

        TraitableBean imp2 = (TraitableBean) impClass.newInstance();
        impClass.set(imp2, "name", "aaa");
        TraitProxyImpl proxy4 = (TraitProxyImpl) tFactory.getProxy(imp2, trait, false);
        //            proxy4.getFields().put("name", "aaa");
        proxy4.getFields().put("field", "xyz");

        assertThat(proxy4).isEqualTo(proxy2);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testWrapperSize(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());

        TraitFactoryImpl.setMode(mode, kb);
        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);


        FactType impClass = kb.getFactType("org.drools.compiler.trait.test",                                                 "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        FactType traitClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = traitClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        Map<String, Object> virtualFields = imp._getDynamicProperties();
        Map<String, Object> wrapper = proxy.getFields();
        assertThat(wrapper).hasSize(3);
        assertThat(virtualFields).hasSize(1);

        impClass.set(imp, "name", "john");
        assertThat(wrapper).hasSize(3);
        assertThat(virtualFields).hasSize(1);

        proxy.getFields().put("school", "skol");
        assertThat(wrapper).hasSize(3);
        assertThat(virtualFields).hasSize(1);

        proxy.getFields().put("surname", "xxx");
        assertThat(wrapper).hasSize(4);
        assertThat(virtualFields).hasSize(2);

        //            FactType indClass = kb.getFactType("org.drools.compiler.trait.test","Entity");
        //            TraitableBean ind = (TraitableBean) indClass.newInstance();
        TraitableBean ind = new Entity();

        TraitProxyImpl proxy2 = (TraitProxyImpl) tFactory.getProxy(ind, trait, false);

        Map<String, Object> virtualFields2 = ind._getDynamicProperties();
        Map<String, Object> wrapper2 = proxy2.getFields();
        assertThat(wrapper2).hasSize(3);
        assertThat(virtualFields2).hasSize(3);

        traitClass.set(proxy2, "name", "john");
        assertThat(wrapper2).hasSize(3);
        assertThat(virtualFields2).hasSize(3);

        proxy2.getFields().put("school", "skol");
        assertThat(wrapper2).hasSize(3);
        assertThat(virtualFields2).hasSize(3);

        proxy2.getFields().put("surname", "xxx");
        assertThat(wrapper2).hasSize(4);
        assertThat(virtualFields2).hasSize(4);

        FactType traitClass2 = kb.getFactType("org.drools.compiler.trait.test", "Role");
        Class<?> trait2 = traitClass2.getFactClass();
        //            TraitableBean ind2 = (TraitableBean) indClass.newInstance();
        TraitableBean ind2 = new Entity();

        TraitProxyImpl proxy99 = (TraitProxyImpl) tFactory.getProxy(ind2, trait2, false);

        proxy99.getFields().put("surname", "xxx");
        proxy99.getFields().put("name", "xyz");
        proxy99.getFields().put("school", "skol");

        assertThat(proxy99.getFields()).hasSize(3);

        TraitProxyImpl proxy100 = (TraitProxyImpl) tFactory.getProxy(ind2, trait, false);

        assertThat(proxy100.getFields()).hasSize(4);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testWrapperEmpty(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());
        TraitFactoryImpl.setMode(mode, kb);

        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();

        FactType studentClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = studentClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        Map<String, Object> wrapper = proxy.getFields();
        assertThat(wrapper).isNotEmpty();

        studentClass.set(proxy, "name", "john");
        assertThat(wrapper).isNotEmpty();
        
        studentClass.set(proxy, "name", null);
        assertThat(wrapper).isNotEmpty();

        studentClass.set(proxy, "age", 32);
        assertThat(wrapper).isNotEmpty();

        studentClass.set(proxy, "age", null);
        assertThat(wrapper).isNotEmpty();

        //            FactType indClass = kb.getFactType("org.drools.compiler.trait.test","Entity");
        TraitableBean ind = new Entity();

        FactType RoleClass = kb.getFactType("org.drools.compiler.trait.test", "Role");
        Class<?> trait2 = RoleClass.getFactClass();
        TraitProxyImpl proxy2 = (TraitProxyImpl) tFactory.getProxy(ind, trait2, false);

        Map<String, Object> wrapper2 = proxy2.getFields();
        assertThat(wrapper2).isEmpty();

        proxy2.getFields().put("name", "john");
        assertThat(wrapper2).isNotEmpty();

        proxy2.getFields().put("name", null);
        assertThat(wrapper2).isNotEmpty();

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testWrapperContainsKey(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());


        TraitFactoryImpl.setMode(mode, kb);
        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        impClass.set(imp, "name", "john");

        FactType traitClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = traitClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        Map<String, Object> wrapper = proxy.getFields();

        assertThat(wrapper).containsKey("name");
        assertThat(wrapper).containsKey("school");
        assertThat(wrapper).containsKey("age");
        assertThat(wrapper).doesNotContainKey("surname");

        proxy.getFields().put("school", "skol");
        proxy.getFields().put("surname", "xxx");
        assertThat(wrapper).containsKey("surname");

        //            FactType indClass = kb.getFactType("org.drools.compiler.trait.test","Entity");
        TraitableBean ind = new Entity();

        TraitProxyImpl proxy2 = (TraitProxyImpl) tFactory.getProxy(ind, trait, false);

        Map<String, Object> wrapper2 = proxy2.getFields();
        assertThat(wrapper2).containsKey("name");
        assertThat(wrapper2).containsKey("school");
        assertThat(wrapper2).containsKey("age");
        assertThat(wrapper2).doesNotContainKey("surname");

        traitClass.set(proxy2, "name", "john");
        proxy2.getFields().put("school", "skol");
        proxy2.getFields().put("surname", "xxx");
        assertThat(wrapper2).containsKey("surname");

        FactType traitClass2 = kb.getFactType("org.drools.compiler.trait.test", "Role");
        Class<?> trait2 = traitClass2.getFactClass();
        TraitableBean ind2 = new Entity();

        TraitProxyImpl proxy99 = (TraitProxyImpl) tFactory.getProxy(ind2, trait2, false);
        Map<String, Object> wrapper99 = proxy99.getFields();

        assertThat(wrapper99).doesNotContainKey("name");
        assertThat(wrapper99).doesNotContainKey("school");
        assertThat(wrapper99).doesNotContainKey("age");
        assertThat(wrapper99).doesNotContainKey("surname");

        proxy99.getFields().put("surname", "xxx");
        proxy99.getFields().put("name", "xyz");
        proxy99.getFields().put("school", "skol");

        assertThat(wrapper99).containsKey("name");
        assertThat(wrapper99).containsKey("school");
        assertThat(wrapper99).doesNotContainKey("age");
        assertThat(wrapper99).containsKey("surname");
        assertThat(proxy99.getFields()).hasSize(3);

        TraitableBean ind0 = new Entity();

        TraitProxyImpl proxy100 = (TraitProxyImpl) tFactory.getProxy(ind0, trait2, false);
        Map<String, Object> wrapper100 = proxy100.getFields();
        assertThat(wrapper100).doesNotContainKey("name");
        assertThat(wrapper100).doesNotContainKey("school");
        assertThat(wrapper100).doesNotContainKey("age");
        assertThat(wrapper100).doesNotContainKey("surname");

        TraitProxyImpl proxy101 = (TraitProxyImpl) tFactory.getProxy(ind0, trait, false);
        // object gains properties by virtue of another trait
        // so new props are accessible even using the old proxy
        assertThat(wrapper100).containsKey("name");
        assertThat(wrapper100).containsKey("school");
        assertThat(wrapper100).containsKey("age");
        assertThat(wrapper100).doesNotContainKey("surname");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInternalComponents1(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());

        TraitFactoryImpl.setMode(mode, kb);
        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);


        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        FactType traitClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = traitClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);
        Object proxyFields = proxy.getFields();
        Object coreTraits = imp._getTraitMap();
        Object coreProperties = imp._getDynamicProperties();

        assertThat(proxyFields).isNotNull();
        assertThat(coreTraits).isNotNull();
        assertThat(coreProperties).isNotNull();

        if (mode == VirtualPropertyMode.MAP) {
            assertThat(proxyFields).isInstanceOf(MapWrapper.class);
            assertThat(coreTraits).isInstanceOf(TraitTypeMapImpl.class);
            assertThat(coreProperties).isInstanceOf(HashMap.class);
        } else {
            assertThat(proxyFields.getClass().getName()).isEqualTo("org.drools.compiler.trait.test.Student.org.drools.compiler.trait.test.Imp_ProxyWrapper");

            assertThat(proxyFields).isInstanceOf(TripleBasedStruct.class);
            assertThat(coreTraits).isInstanceOf(TraitTypeMapImpl.class);
            assertThat(coreProperties).isInstanceOf(TripleBasedBean.class);
        }


        StudentProxyImpl2 sp2 = new StudentProxyImpl2(new Imp2(), null);
        LOGGER.debug(sp2.toString());
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testWrapperKeySetAndValues(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());
        TraitFactoryImpl.setMode(mode, kb);

        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        FactType traitClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = traitClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        impClass.set(imp, "name", "john");
        proxy.getFields().put("surname", "xxx");
        proxy.getFields().put("name2", "john");
        proxy.getFields().put("nfield", null);

        assertThat(proxy.getFields()).hasSize(6);
        assertThat(proxy.getFields().keySet()).contains("name", "surname", "age", "school", "name2", "nfield");

        List<Object> col1 = new ArrayList<>(proxy.getFields().values());
        List<Object> col2 = Arrays.asList("john", null, 0, "xxx", "john", null);

        Comparator<Object> comp = new Comparator<Object>() {

            public int compare(Object o1, Object o2) {
                if (o1 == null && o2 != null) {
                    return 1;
                }
                if (o1 != null && o2 == null) {
                    return -1;
                }
                if (o1 == null && o2 == null) {
                    return 0;
                }
                return o1.toString().compareTo(o2.toString());
            }
        };

        Collections.sort(col1, comp);
        Collections.sort(col2, comp);
        assertThat(col2).isEqualTo(col1);

        assertThat(proxy.getFields()).containsValue(null);
        assertThat(proxy.getFields()).containsValue("john");
        assertThat(proxy.getFields()).containsValue(0);
        assertThat(proxy.getFields()).containsValue("xxx");
        assertThat(proxy.getFields()).doesNotContainValue("randomString");
        assertThat(proxy.getFields()).doesNotContainValue(-96);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testWrapperClearAndRemove(VirtualPropertyMode mode) throws Exception {
        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());
        TraitFactoryImpl.setMode(mode, kb);
        TraitFactoryImpl tFactory = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test", "Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        impClass.set(imp, "name", "john");
        FactType traitClass = kb.getFactType("org.drools.compiler.trait.test", "Student");
        Class<?> trait = traitClass.getFactClass();
        TraitProxyImpl proxy = (TraitProxyImpl) tFactory.getProxy(imp, trait, false);

        proxy.getFields().put("surname", "xxx");
        proxy.getFields().put("name2", "john");
        proxy.getFields().put("nfield", null);

        Set<String> set = new HashSet<>();
        set.add("name");
        set.add("surname");
        set.add("age");
        set.add("school");
        set.add("name2");
        set.add("nfield");

        assertThat(proxy.getFields()).hasSize(6);
        assertThat(proxy.getFields().keySet()).isEqualTo(set);

        proxy.getFields().clear();

        Map<String, Object> fields = proxy.getFields();
        assertThat(fields).hasSize(3);
        assertThat(fields).containsKey("age");
        assertThat(fields).containsKey("school");
        assertThat(fields).containsKey("name");

        assertThat(fields.get("age")).isEqualTo(0);
        assertThat(fields.get("school")).isNull();
        assertThat(fields.get("name")).isNotNull();

        proxy.getFields().put("surname", "xxx");
        proxy.getFields().put("name2", "john");
        proxy.getFields().put("nfield", null);
        proxy.getFields().put("age", 24);

        assertThat(proxy.getFields().get("name")).isEqualTo("john");
        assertThat(proxy.getFields().get("surname")).isEqualTo("xxx");
        assertThat(proxy.getFields().get("name2")).isEqualTo("john");
        assertThat(proxy.getFields().get("nfield")).isEqualTo(null);
        assertThat(proxy.getFields().get("age")).isEqualTo(24);
        assertThat(proxy.getFields().get("school")).isEqualTo(null);

        proxy.getFields().remove("surname");
        proxy.getFields().remove("name2");
        proxy.getFields().remove("age");
        proxy.getFields().remove("school");
        proxy.getFields().remove("nfield");
        assertThat(proxy.getFields()).hasSize(3);

        assertThat(proxy.getFields().get("age")).isEqualTo(0);
        assertThat(proxy.getFields().get("school")).isEqualTo(null);
        assertThat(proxy.getFields().get("name")).isEqualTo("john");

        assertThat(proxy.getFields().get("nfield")).isEqualTo(null);
        assertThat(proxy.getFields()).doesNotContainKey("nfield");

        assertThat(proxy.getFields().get("name2")).isEqualTo(null);
        assertThat(proxy.getFields()).doesNotContainKey("name2");

        assertThat(proxy.getFields().get("surname")).isEqualTo(null);
        assertThat(proxy.getFields()).doesNotContainKey("surname");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAEvaluator(VirtualPropertyMode mode) {
        String source = "package org.drools.compiler.trait.test;\n" +
                        "\n" +
                        "import org.drools.base.factmodel.traits.Traitable;\n" +
                        "import org.drools.traits.core.factmodel.Entity;\n" +
                        "import org.drools.base.factmodel.traits.Thing;\n" +
                        "\n" +
                        "global java.util.List list;\n" +
                        "\n" +
                        "\n" +
                        "declare Imp\n" +
                        "    @Traitable\n" +
                        "    name    : String        @key\n" +
                        "end\n" +
                        "\n" +
                        "declare trait Person\n" +
                        "    name    : String \n" +
                        "    age     : int   \n" +
                        "end\n" +
                        "  \n" +
                        "declare trait Worker\n" +
                        "    job     : String\n" +
                        "end\n" +
                        " \n" +
                        "\n" +
                        " \n" +
                        " \n" +
                        "rule \"Init\"\n" +
                        "when\n" +
                        "then\n" +
                        "    Imp core = new Imp(\"joe\");\n" +
                        "    insert(core);\n" +
                        "    don(core, Person.class);\n" +
                        "    don(core, Worker.class);\n" +
                        "\n" +
                        "    Imp core2 = new Imp(\"adam\");\n" +
                        "    insert(core2);\n" +
                        "    don(core2, Worker.class);\n" +
                        "end\n" +
                        "\n" +
                        "rule \"Mod\"\n" +
                        "when\n" +
                        "    $p : Person(name == \"joe\")\n" +
                        "then\n" +
                        "    modify ($p) { setName(\"john\"); }\n" +
                        "end\n" +
                        "\n" +
                        "rule \"Worker Students v6\"\n" +
                        "when\n" +
                        "    $x2 := Person(name == \"john\")\n" +
                        "    $x1 := Worker(core != $x2.core, this not isA $x2)\n" +
                        "then\n" +
                        "    list.add(\"ok\");\n" +
                        "end\n" +
                        "\n" +
                        "\n";

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();
        LOGGER.debug(info.toString());
        assertThat(info).contains("ok");
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsA(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitIsA.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();


        int num = 10;

        LOGGER.debug(info.toString());
        assertThat(info).hasSize(num);
        for (int j = 0; j < num; j++) {
            assertThat(info).contains("" + j);
        }

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testOverrideType(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitOverride.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        assertThatException()
            .as("An exception was expected since a trait can't override the type of a core class field with these settings ")
            .isThrownBy(() -> ks.fireAllRules())
            .extracting(e -> e.getCause())
            .isInstanceOf(UnsupportedOperationException.class);
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testOverrideType2(VirtualPropertyMode mode) {
        String drl = "package org.drools.compiler.trait.test; \n" +
                     "import org.drools.base.factmodel.traits.Traitable; \n" +
                     "" +
                     "declare Foo @Traitable end\n" +
                     "declare trait Bar end \n" +
                     "" +
                     "declare trait Mask fld : Foo end \n" +
                     "declare Face @Traitable fld : Bar end \n" +
                     "" +
                     "rule Don when then\n" +
                     "  Face face = new Face(); \n" +
                     "  don(face, Mask.class); \n" +
                     "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        assertThatException()
            .as("An exception was expected since a trait can't override the type of a core class field with these settings ")
            .isThrownBy(() -> ks.fireAllRules())
            .extracting(e -> e.getCause())
            .isInstanceOf(UnsupportedOperationException.class);
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testOverrideType3(VirtualPropertyMode mode) {
        String drl = "package org.drools.compiler.trait.test; \n" +
                     "import org.drools.base.factmodel.traits.Traitable; \n" +
                     "" +
                     "declare trait Foo end\n" +
                     "declare trait Bar end \n" +
                     "" +
                     "declare trait Mask fld : Foo end \n" +
                     "declare Face @Traitable fld : Bar end \n" +
                     "" +
                     "rule Don when then\n" +
                     "  Face face = new Face(); \n" +
                     "  don(face, Mask.class); \n" +
                     "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        
        assertThatException()
            .as("An exception was expected since a trait can't override the type of a core class field with these settings ")
            .isThrownBy(() -> ks.fireAllRules())
            .extracting(e -> e.getCause())
            .isInstanceOf(UnsupportedOperationException.class);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitLegacy(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitLegacyTrait.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());


        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        printDebugInfoSessionObjects(ks.getObjects(), info);

        assertThat(info).hasSize(5);
        assertThat(info).contains("OK");
        assertThat(info).contains("OK2");
        assertThat(info).contains("OK3");
        assertThat(info).contains("OK4");
        assertThat(info).contains("OK5");

    }

    private void printDebugInfoSessionObjects(final Collection<? extends Object> facts, final List<? extends Object> globalList) {
        LOGGER.debug(" -------------- " + facts.size() + " ---------------- ");
        for (Object o : facts) {
            LOGGER.debug("\t\t" + o);
        }
        LOGGER.debug(" --------------  ---------------- ");
        LOGGER.debug(globalList.toString());
        LOGGER.debug(" --------------  ---------------- ");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitCollections(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitCollections.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());


        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        printDebugInfoSessionObjects(ks.getObjects(), info);

        assertThat(info).hasSize(1);
        assertThat(info).contains("OK");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitCore(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitLegacyCore.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        printDebugInfoSessionObjects(ks.getObjects(), info);

        assertThat(info).contains("OK");
        assertThat(info).contains("OK2");
        assertThat(info).hasSize(2);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitWithEquality(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitWithEquality.drl";

        KieSession ks = getSession(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> info = new ArrayList<>();
        ks.setGlobal("list", info);

        ks.fireAllRules();

        assertThat(info).contains("DON");
        assertThat(info).contains("EQUAL");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitDeclared(VirtualPropertyMode mode) {

        List<Integer> trueTraits = new ArrayList<Integer>();
        List<Integer> untrueTraits = new ArrayList<Integer>();

        KieSession ks = getSession("org/drools/compiler/factmodel/traits/testDeclaredFactTrait.drl");
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        ks.setGlobal("trueTraits", trueTraits);
        ks.setGlobal("untrueTraits", untrueTraits);

        ks.fireAllRules();
        ks.dispose();

        assertThat(trueTraits).contains(1);
        assertThat(trueTraits).doesNotContain(2);
        assertThat(untrueTraits).contains(2);
        assertThat(untrueTraits).doesNotContain(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitPojo(VirtualPropertyMode mode) {

        List<Integer> trueTraits = new ArrayList<Integer>();
        List<Integer> untrueTraits = new ArrayList<Integer>();

        KieSession session = getSession("org/drools/compiler/factmodel/traits/testPojoFactTrait.drl");
        TraitFactoryImpl.setMode(mode, session.getKieBase());

        session.setGlobal("trueTraits", trueTraits);
        session.setGlobal("untrueTraits", untrueTraits);

        session.fireAllRules();
        session.dispose();

        assertThat(trueTraits).contains(1);
        assertThat(trueTraits).doesNotContain(2);
        assertThat(untrueTraits).contains(2);
        assertThat(untrueTraits).doesNotContain(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAOperator(VirtualPropertyMode mode) {
        String source = "org/drools/compiler/factmodel/traits/testTraitIsA2.drl";
        KieSession ksession = getSession(source);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());


        AgendaEventListener ael = mock(AgendaEventListener.class);
        ksession.addEventListener(ael);

        Person student = new Person("student", 18);
        ksession.insert(student);

        ksession.fireAllRules();

        ArgumentCaptor<AfterMatchFiredEvent> cap = ArgumentCaptor.forClass(AfterMatchFiredEvent.class);
        verify(ael, times(3)).afterMatchFired(cap.capture());

        List<AfterMatchFiredEvent> values = cap.getAllValues();

        assertThat(values.get(0).getMatch().getRule().getName()).isEqualTo("create student");
        assertThat(values.get(1).getMatch().getRule().getName()).isEqualTo("print student");
        assertThat(values.get(2).getMatch().getRule().getName()).isEqualTo("print school");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testManyTraits(VirtualPropertyMode mode) {
        String source = "" +
                        "import " + Message.class.getCanonicalName() + ";\n" +
                        "" +
                        "global java.util.List list; \n" +
                        "" +
                        "declare Message\n" +
                        "      @Traitable\n" +
                        "    end\n" +
                        "\n" +
                        "    declare trait NiceMessage\n" +
                        "       message : String\n" +
                        "    end\n" +
                        "" +
                        "rule \"Nice\"\n" +
                        "when\n" +
                        "  $n : NiceMessage($m : message)\n" +
                        "then\n" +
                        "end" +
                        "\n" +
                        "    rule load\n" +
                        "        when\n" +
                        "\n" +
                        "        then\n" +
                        "            Message message = new Message();\n" +
                        "            message.setMessage(\"Hello World\");\n" +
                        "            insert(message);\n" +
                        "            don(message, NiceMessage.class);\n" +
                        "\n" +
                        "            Message unreadMessage = new Message();\n" +
                        "            unreadMessage.setMessage(\"unread\");\n" +
                        "            insert(unreadMessage);\n" +
                        "            don(unreadMessage, NiceMessage.class);\n" +
                        "\n" +
                        "            Message oldMessage = new Message();\n" +
                        "            oldMessage.setMessage(\"old\");\n" +
                        "            insert(oldMessage);\n" +
                        "            don(oldMessage, NiceMessage.class);" +

                        "            list.add(\"OK\");\n" +
                        "    end";
        KieSession ksession = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());


        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Person student = new Person("student", 18);
        ksession.insert(student);

        ksession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains("OK");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitManyTimes(VirtualPropertyMode mode) {

        KieSession ksession = getSession("org/drools/compiler/factmodel/traits/testTraitDonMultiple.drl");
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        assertThat(ksession.getObjects()).hasSize(2);

        assertThat(list).hasSize(5);
        assertThat(list.get(0)).isEqualTo(0);
        assertThat(list).contains(1);
        assertThat(list).contains(2);
        assertThat(list).contains(3);
        assertThat(list).contains(4);
    }

    // BZ #748752
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitsInBatchExecution(VirtualPropertyMode mode) {
        String str = "package org.jboss.qa.brms.traits\n" +
                     "import org.drools.traits.compiler.Person;\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "" +
                     "global java.util.List list;" +
                     "" +
                     "declare Person \n" +
                     "  @Traitable \n" +
                     "end \n" +
                     "" +
                     "declare trait Student\n" +
                     "  school : String\n" +
                     "end\n" +
                     "\n" +
                     "rule \"create student\" \n" +
                     "  when\n" +
                     "    $student : Person(age < 26)\n" +
                     "  then\n" +
                     "    Student s = don($student, Student.class);\n" +
                     "    s.setSchool(\"Masaryk University\");\n" +
                     "end\n" +
                     "\n" +
                     "rule \"print student\"\n" +
                     "  when\n" +
                     "    student : Person(this isA Student)\n" +
                     "  then" +
                     "    list.add(1);\n" +
                     "end\n" +
                     "\n" +
                     "rule \"print school\"\n" +
                     "  when\n" +
                     "    Student($school : school)\n" +
                     "  then\n" +
                     "    list.add(2);\n" +
                     "end";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(str.getBytes()), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new RuntimeException(kbuilder.getErrors().toString());
        }

        List<Integer> list = new ArrayList<>();

        KieBase kbase = kbuilder.newKieBase();
        TraitFactoryImpl.setMode(mode, kbase);

        StatelessKieSession ksession = kbase.newStatelessKieSession();


        ksession.setGlobal("list", list);

        List<Command<?>> commands = new ArrayList<Command<?>>();
        Person student = new Person("student", 18);
        commands.add(CommandFactory.newInsert(student));

        LOGGER.debug("Starting execution...");
        commands.add(CommandFactory.newFireAllRules());
        ksession.execute(CommandFactory.newBatchExecution(commands));
        LOGGER.debug("Finished...");

        assertThat(list).hasSize(2);
        assertThat(list).contains(1);
        assertThat(list).contains(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testManyTraitsStateless(VirtualPropertyMode mode) {
        String source = "" +
                        "import " + Message.class.getCanonicalName() + ";\n" +
                        "" +
                        "global java.util.List list; \n" +
                        "" +
                        "declare Message\n" +
                        "      @Traitable\n" +
                        "    end\n" +
                        "\n" +
                        "    declare trait NiceMessage\n" +
                        "       message : String\n" +
                        "    end\n" +
                        "" +
                        "rule \"Nice\"\n" +
                        "when\n" +
                        "  $n : NiceMessage($m : message)\n" +
                        "then\n" +
                        "end" +
                        "\n" +
                        "    rule load\n" +
                        "        when\n" +
                        "\n" +
                        "        then\n" +
                        "            Message message = new Message();\n" +
                        "            message.setMessage(\"Hello World\");\n" +
                        "            insert(message);\n" +
                        "            don(message, NiceMessage.class);\n" +
                        "\n" +
                        "            Message unreadMessage = new Message();\n" +
                        "            unreadMessage.setMessage(\"unread\");\n" +
                        "            insert(unreadMessage);\n" +
                        "            don(unreadMessage, NiceMessage.class);\n" +
                        "\n" +
                        "            Message oldMessage = new Message();\n" +
                        "            oldMessage.setMessage(\"old\");\n" +
                        "            insert(oldMessage);\n" +
                        "            don(oldMessage, NiceMessage.class);" +

                        "            list.add(\"OK\");\n" +
                        "    end";
        KieBase kb = getKieBaseFromString(source);
        TraitFactoryImpl.setMode(mode, kb);

        KieSession ksession = kb.newKieSession();

        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.execute(CommandFactory.newFireAllRules());

        assertThat(list).hasSize(1);
        assertThat(list).contains("OK");

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testAliasing(VirtualPropertyMode mode) {
        String drl = "package org.drools.traits\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.base.factmodel.traits.Alias;\n" +
                     "" +
                     "global java.util.List list;" +
                     "" +
                     "declare Person \n" +
                     "  @Traitable \n" +
                     "  nomen     : String  @key @Alias(\"fld1\") \n" +
                     "  workPlace : String \n" +
                     "  address   : String \n" +
                     "  serviceYrs: int \n" +
                     "end \n" +
                     "" +
                     "declare trait Student\n" +
                     // this alias maps to the hard field
                     "  name      : String @Alias(\"fld1\") \n" +
                     // this alias works, binding school to workPlace
                     "  school    : String  @Alias(\"workPlace\") \n" +
                     // soft field, will use name 'level'
                     "  grade     : int @Alias(\"level\") \n" +
                     // this will try to bind rank to address
                     "  rank      : int @Alias(\"serviceYrs\") \n" +
                     "end \n" +
                     "\n" +
                     "rule \"create student\" \n" +
                     "  when\n" +
                     "  then\n" +
                     "    Person p = new Person(\"davide\", \"UniBoh\", \"Floor84\", 1); \n" +
                     "    Student s = don(p, Student.class);\n" +
                     "end\n" +
                     "\n" +
                     "rule \"print school\"\n" +
                     "  when\n" +
                     "    $student : Student($school : school == \"UniBoh\",  $f : fields, fields[ \"workPlace\" ] == \"UniBoh\")\n" +
                     "  then \n " +
                     "    $student.setRank(99); \n" +
                     "    $f.put(\"school\", \"Skool\"); \n" +

                     "    list.add($school);\n" +
                     "    list.add($f.get(\"school\"));\n" +
                     "    list.add($student.getSchool());\n" +
                     "    list.add($f.keySet());\n" +
                     "    list.add($f.entrySet());\n" +
                     "    list.add($f.values());\n" +
                     "    list.add($f.containsKey(\"school\"));\n" +
                     "    list.add($student.getRank());\n" +
                     "    list.add($f.get(\"address\"));\n" +
                     "end";

        KieSession ksession = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Object> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list).hasSize(9);
        assertThat(list).contains("UniBoh");
        assertThat(list).contains("Skool");
        assertThat((Collection<String>) list.get(3)).contains("workPlace", "nomen", "level");
        assertThat((Collection<Object>) list.get(5)).contains("davide", "Skool", 0);
        assertThat(list).contains(true);
        assertThat(list).contains("Floor84");
        assertThat(list).contains(99);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitLogicalRemoval(VirtualPropertyMode mode) {
        String drl = "package org.drools.trait.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "declare trait Student\n" +
                     "  age  : int\n" +
                     "  name : String\n" +
                     "end\n" +
                     "\n" +
                     "declare trait Worker\n" +
                     "  wage  : int\n" +
                     "  name : String\n" +
                     "end\n" +
                     "declare Person\n" +
                     "  @Traitable\n" +
                     "  name : String \n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "rule \"Don Logical\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger\")\n" +
                     "then\n" +
                     "  Person p = new Person(\"john\");\n" +
                     "  insertLogical(p); \n" +
                     "  don(p, Student.class, true);\n" +
                     "end\n" +
                     " " +
                     "rule \"Don Logical 2\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger2\")\n" +
                     "  $p : Person(name == \"john\")" +
                     "then\n" +
                     "  don($p, Worker.class, true);\n" +
                     "end";


        KieSession ksession = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Object> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        FactHandle h = ksession.insert("trigger");
        ksession.fireAllRules();
        assertThat(ksession.getObjects()).hasSize(3);

        ksession.delete(h);
        ksession.fireAllRules();

        assertThat(ksession.getObjects()).isEmpty();

        FactHandle h1 = ksession.insert("trigger");
        FactHandle h2 = ksession.insert("trigger2");
        ksession.fireAllRules();

        assertThat(ksession.getObjects()).hasSize(5);

        ksession.delete(h2);
        ksession.fireAllRules();

        assertThat(ksession.getObjects()).hasSize(3);

        ksession.delete(h1);
        ksession.fireAllRules();

        assertThat(ksession.getObjects()).isEmpty();

    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTMSConsistencyWithNonTraitableBeans(VirtualPropertyMode mode) {

        String s1 = "package org.drools.test;\n" +
                    "import org.drools.traits.compiler.Person; \n" +
                    "import org.drools.base.factmodel.traits.Traitable; \n" +
                    "" +
                    "declare Person @Traitable end \n" +
                    "" +
                    "rule \"Init\"\n" +
                    "when\n" +
                    "then\n" +
                    "  insertLogical(new Person(\"x\", 18));\n" +
                    "end\n" +
                    "\n" +
                    "declare trait Student\n" +
                    "  age  : int\n" +
                    "  name : String\n" +
                    "end\n" +
                    "\n" +
                    "rule \"Trait\"\n" +
                    "when\n" +
                    "    $p : Person()\n" +
                    "then\n" +
                    "    don($p, Student.class, true);\n" +
                    "end\n";


        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        ksession.fireAllRules();

        FactHandle personHandle = ksession.getFactHandles(new ClassObjectFilter(Person.class)).iterator().next();
        InternalFactHandle h = ((InternalFactHandle) personHandle);
        ObjectTypeConfigurationRegistry reg = h.getEntryPoint((InternalWorkingMemory) ksession).getObjectTypeConfigurationRegistry();
        ObjectTypeConf conf = reg.getOrCreateObjectTypeConf(h.getEntryPointId(), ((InternalFactHandle) personHandle).getObject());
        assertThat(conf.isTMSEnabled()).isTrue();

        ksession.dispose();
    }






    public static class TBean {
        private String fld;
        public String getFld() { return fld; }
        public void setFld(String fld) { this.fld = fld; }
        public TBean(String fld) { this.fld = fld; }
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitsLegacyWrapperCoherence(VirtualPropertyMode mode) {
        String str = "package org.drools.trait.test; \n" +
                     "global java.util.List list; \n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.traits.compiler.factmodel.traits.TraitTest.TBean;\n" +
                     "" +                "" +
                     "declare TBean \n" +
                     "@Traitable \n" +
                     "end \n " +
                     "" +
                     "declare trait Mask \n" +
                     "  fld : String \n" +
                     "  xyz : int  \n" +
                     "end \n" +
                     "\n " +
                     "rule Init \n" +
                     "when \n" +
                     "then \n" +
                     "  insert(new TBean(\"abc\")); \n" +
                     "end \n" +
                     "" +
                     "rule Don \n" +
                     "no-loop \n" +
                     "when \n" +
                     "  $b : TBean() \n" +
                     "then \n" +
                     "  Mask m = don($b, Mask.class); \n" +
                     "  modify (m) { setXyz(10); } \n" +
                     "  list.add(m); \n" +
                     "end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newByteArrayResource(str.getBytes()), ResourceType.DRL);
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();

        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());
        List<?> list = new ArrayList<Object>();

        ksession.setGlobal("list", list);

        ksession.fireAllRules();


        Collection<? extends Object> yOld = ksession.getObjects();
        assertThat(yOld).hasSize(2);

        TraitableBean coreOld = null;
        for (Object o : yOld) {
            if (o instanceof TraitableBean) {
                coreOld = (TraitableBean) o;
                break;
            }
        }
        assertThat(coreOld).isNotNull();

        assertThat(coreOld.getClass().getSuperclass()).isSameAs(TBean.class);

        assertThat(((TBean) coreOld).getFld()).isEqualTo("abc");
        assertThat(coreOld._getDynamicProperties()).hasSize(1);
        assertThat(coreOld._getTraitMap()).hasSize(1);
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testHasTypes(VirtualPropertyMode mode) throws Exception {

        String source = "org/drools/compiler/factmodel/traits/testTraitDon.drl";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Resource res = ResourceFactory.newClassPathResource(source);
        assertThat(res).isNotNull();
        kbuilder.add(res, ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kb = KnowledgeBaseFactory.newKnowledgeBase();
        kb.addPackages(kbuilder.getKnowledgePackages());
        TraitFactoryImpl traitBuilder = (TraitFactoryImpl) RuntimeComponentFactory.get().getTraitFactory(kb);
        TraitFactoryImpl.setMode(mode, kb);

        FactType impClass = kb.getFactType("org.drools.compiler.trait.test","Imp");
        TraitableBean imp = (TraitableBean) impClass.newInstance();
        impClass.set(imp, "name", "aaabcd");

        Class<?> trait = kb.getFactType("org.drools.compiler.trait.test","Student").getFactClass();
        Class<?> trait2 = kb.getFactType("org.drools.compiler.trait.test","Role").getFactClass();

        assertThat(trait).isNotNull();

        TraitProxyImpl proxy = (TraitProxyImpl) traitBuilder.getProxy(imp, trait, false);
        Thing thing = traitBuilder.getProxy(imp, Thing.class, false);

        TraitableBean core = proxy.getObject();


        TraitProxyImpl proxy2 = (TraitProxyImpl) traitBuilder.getProxy(imp, trait, false);
        Thing thing2 = traitBuilder.getProxy(imp, Thing.class, false);

        assertThat(proxy2).isSameAs(proxy);
        assertThat(thing2).isSameAs(thing);

        assertThat(core.getTraits()).hasSize(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitRedundancy(VirtualPropertyMode mode) {
        String str = "package org.drools.traits.compiler.factmodel.traits; \n" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare trait IStudent end \n" +
                     "" +
                     "declare org.drools.traits.compiler.factmodel.traits.IPerson @typesafe(false) end \n" +
                     "" +
                     "rule \"Students\" \n" +
                     "salience -10" +
                     "when \n" +
                     "   $s : IStudent() \n" +
                     "then \n" +
                     "end \n" +
                     "" +
                     "rule \"Don\" \n" +
                     "no-loop  \n" +
                     "when \n" +
                     "  $p : IPerson(age < 30) \n" +
                     "then \n" +
                     "   don($p, IStudent.class);\n" +
                     "end \n" +
                     "" +
                     "rule \"Check\" \n" +
                     "no-loop \n" +
                     "when \n" +
                     "  $p : IPerson(this isA IStudent) \n" +
                     "then \n" +
                     "   modify ($p) { setAge(37); } \n" +
                     "   shed($p, IStudent.class);\n" +
                     "end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newByteArrayResource(str.getBytes()), ResourceType.DRL);
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();

        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());
        List<?> list = new ArrayList<Object>();

        ksession.setGlobal("list", list);

        ksession.insert(new StudentImpl("skool", "john", 27));


        assertThat(ksession.fireAllRules()).isEqualTo(3);

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitSimpleTypes(VirtualPropertyMode mode) {

        String s1 = "package org.drools.factmodel.traits;\n" +
                    "\n" +
                    "import org.drools.base.factmodel.traits.Traitable;\n" +
                    "" +
                    "declare trait PassMark\n" +
                    "end\n" +
                    "\n" +
                    "declare ExamMark \n" +
                    "@Traitable\n" +
                    "value : long \n" +
                    "end\n" +
                    "" +
                    "rule \"testTraitFieldTypePrimitive\"\n" +
                    "when\n" +
                    "    $mark : ExamMark()\n" +
                    "then\n" +
                    "    don($mark, PassMark.class);\n" +
                    "end\n" +
                    "" +
                    "rule \"Init\" when then insert(new ExamMark()); end \n";



        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            if (o instanceof TraitableBean) {
                TraitableBean tb = (TraitableBean) o;
                assertThat(tb._getTraitMap()).hasSize(1);
                BitSet bs = new BitSet();
                bs.set(0);
                assertThat(tb.getCurrentTypeCode()).isEqualTo(bs);
            }
            if (o instanceof TraitProxyImpl) {
                TraitProxyImpl tp = (TraitProxyImpl) o;
                assertThat(tp.listAssignedOtnTypeCodes()).isEmpty();
            }
        }
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitEncoding(VirtualPropertyMode mode) {
        String s1 = "package org.drools.base.factmodel.traits;\n" +
                    "import " + Entity.class.getCanonicalName() + ";\n" +
                    "declare trait A end\n" +
                    "declare trait B extends A end\n" +
                    "declare trait C extends A end\n" +
                    "declare trait D extends A end\n" +
                    "declare trait E extends B end\n" +
                    "declare trait F extends C end\n" +
                    "declare trait G extends D end\n" +
                    "declare trait H extends D end\n" +
                    "declare trait I extends E end\n" +
                    "declare trait J extends F end\n" +
                    "declare trait K extends G, H end\n" +
                    "declare trait L extends G, H end\n" +
                    "declare trait M extends I, J end\n" +
                    "declare trait N extends K, L end\n" +
                    "" +
                    "rule \"donOneThing\"\n" +
                    "when\n" +
                    "    $x : Entity()\n" +
                    "then\n" +
                    "    don($x, A.class);\n" +
                    "end\n" +
                    "" +
                    "rule \"donManyThing\"\n" +
                    "when\n" +
                    "    String(this == \"y\") \n" +
                    "    $x : Entity()\n" +
                    "then\n" +
                    "    don($x, B.class);\n" +
                    "    don($x, D.class);\n" +
                    "    don($x, F.class);\n" +
                    "    don($x, E.class);\n" +
                    "    don($x, I.class);\n" +
                    "    don($x, K.class);\n" +
                    "    don($x, J.class);\n" +
                    "    don($x, C.class);\n" +
                    "    don($x, H.class);\n" +
                    "    don($x, G.class);\n" +
                    "    don($x, L.class);\n" +
                    "    don($x, M.class);\n" +
                    "    don($x, N.class);\n" +
                    "end\n"
                ;

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        kbase.addPackages(kbuilder.getKnowledgePackages());

        TraitRegistryImpl tr = (TraitRegistryImpl) ((TraitRuntimeComponentFactory) RuntimeComponentFactory.get()).getTraitRegistry(kbase);
        LOGGER.debug(tr.getHierarchy().toString());

        Entity ent = new Entity("x");
        KieSession ksession = kbase.newKieSession();
        ksession.insert(ent);
        ksession.fireAllRules();

        assertThat(ent.getMostSpecificTraits()).hasSize(1);

        ksession.insert("y");
        ksession.fireAllRules();

        LOGGER.debug(ent.getMostSpecificTraits().toString());
        assertThat(ent.getMostSpecificTraits()).hasSize(2);

    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitActualTypeCodeWithEntities(VirtualPropertyMode mode) {
        testTraitActualTypeCodeWithEntities("ent", mode);
    }

    @ParameterizedTest()
    @MethodSource("parameters")
    public void testTraitActualTypeCodeWithCoreMap(VirtualPropertyMode mode) {
        testTraitActualTypeCodeWithEntities("kor", mode);
    }


    void testTraitActualTypeCodeWithEntities(String trig, VirtualPropertyMode mode) {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ClassPathResource("org/drools/compiler/factmodel/traits/testComplexDonShed.drl"), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase);

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();

        ksession.insert(trig);
        ksession.fireAllRules();

        TraitableBean ent = (TraitableBean) ksession.getGlobal("core");

        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("1"));

        ksession.insert("b");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("11"));

        ksession.insert("c");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("1011"));

        ksession.insert("e");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("11011"));

        ksession.insert("-c");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("11"));

        ksession.insert("dg");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("111111"));

        ksession.insert("-f");
        ksession.fireAllRules();
        assertThat(ent.getCurrentTypeCode()).isEqualTo(CodedHierarchyImpl.stringToBitSet("111"));

    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitModifyCore(VirtualPropertyMode mode) {
        String s1 = "package test; " +
                    "import org.drools.base.factmodel.traits.*; " +
                    "" +
                    "global java.util.List list; " +
                    "" +
                    "declare trait Student @PropertyReactive name : String end " +
                    "declare trait Worker @PropertyReactive name : String end " +
                    "declare trait StudentWorker extends Student, Worker @PropertyReactive name : String end " +
                    "declare trait Assistant extends Student, Worker @PropertyReactive name : String end " +
                    "declare Person @Traitable name : String end " +
                    "" +
                    "rule \"Init\"  " +
                    "when  " +
                    "then  " +
                    "  Person p = new Person(\"john\");  " +
                    "  insert(p);  " +
                    "end  " +
                    "" +
                    "rule \"Don\"  " +
                    "no-loop  " +
                    "when  " +
                    "  $p : Person(name == \"john\")  " +
                    "then  " +
                    "  don($p, Student.class);  " +
                    "  don($p, Worker.class);  " +
                    "  don($p, StudentWorker.class);  " +
                    "  don($p, Assistant.class);  " +
                    "end  " +
                    "" +
                    "rule \"Log S\"  " +
                    "when  " +
                    "  $t : Student() @Watch(name) " +
                    "then  " +
                    "  list.add($t.getName());  " +
                    "end  " +
                    "rule \"Log W\"  " +
                    "when  " +
                    "  $t : Worker() @Watch(name) " +
                    "then  " +
                    "  list.add($t.getName());  " +
                    "end  " +
                    "rule \"Log SW\"  " +
                    "when  " +
                    "  $t : StudentWorker() @Watch(name) " +
                    "then  " +
                    "  list.add($t.getName());  " +
                    "end  " +
                    "rule \"Log RA\"  " +
                    "when  " +
                    "  $t : Assistant() @Watch(name) " +
                    "then  " +
                    "  list.add($t.getName());  " +
                    "end  " +
                    "" +
                    "rule \"Mod\"  " +
                    "salience -10  " +
                    "when  " +
                    "  $p : Person(name == \"john\") " +
                    "then  " +
                    "   modify ($p) { setName(\"alan\"); } " +
                    "end  " +
                    "";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (InternalRuleBase) kbase);

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        int k = ksession.fireAllRules();

        assertThat(k).isEqualTo(11);
        assertThat(list).containsExactly("john", "john", "john", "john", "alan", "alan", "alan", "alan");
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitModifyCore2(VirtualPropertyMode mode) {
        String s1 = "package test; " +
                    "import org.drools.base.factmodel.traits.*; " +
                    "" +
                    "declare trait Student @propertyReactive name : String end " +
                    "declare trait Worker @propertyReactive name : String end " +
                    "declare trait StudentWorker extends Student, Worker @propertyReactive name : String end " +
                    "declare trait StudentWorker2 extends StudentWorker @propertyReactive name : String end " +
                    "declare trait Assistant extends Student, Worker @propertyReactive name : String end " +
                    "declare Person @Traitable @propertyReactive name : String end " +
                    "" +
                    "rule \"Init\"  " +
                    "when  " +
                    "then  " +
                    "  Person p = new Person(\"john\");  " +
                    "  insert(p);  " +
                    "end  " +
                    "" +
                    "rule \"Don\"  " +
                    "when  " +
                    "  $p : Person(name == \"john\")  " +
                    "then  " +
                    "  don($p, Worker.class);  " +
                    "  don($p, StudentWorker2.class);  " +
                    "  don($p, Assistant.class);  " +
                    "end  " +
                    "" +
                    "rule \"Log S\"  " +
                    "when  " +
                    "  $t : Student() @watch(name)  " +
                    "then  " +
                    "end  " +
                    "rule \"Log W\"  " +
                    "when  " +
                    "  $t : Worker() @watch(name)  " +
                    "then  " +
                    "end  " +
                    "rule \"Log SW\"  " +
                    "when  " +
                    "  $t : StudentWorker() @watch(name)  " +
                    "then  " +
                    "end  " +
                    "rule \"Log RA\"  " +
                    "when  " +
                    "  $t : Assistant() @watch(name)  " +
                    "then  " +
                    "end  " +
                    "rule \"Log Px\"  " +
                    "salience -1  " +
                    "when  " +
                    "  $p : Person() @watch(name)  " +
                    "then  " +
                    "end  " +
                    "" +
                    "rule \"Mod\"  " +
                    "salience -10  " +
                    "when  " +
                    "  String(this == \"go\")  " +
                    "  $p : Student(name == \"john\")  " +
                    "then  " +
                    "  modify ($p) { setName(\"alan\"); } " +
                    "end  ";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KieSession ksession = kbase.newKieSession();
        int k = ksession.fireAllRules();

        assertThat(k).isEqualTo(7);

        ksession.insert("go");
        k = ksession.fireAllRules();

        assertThat(k).isEqualTo(6);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitModifyCore2a(VirtualPropertyMode mode) {
        String s1 = "package test;\n" +
                    "import org.drools.base.factmodel.traits.*;\n" +
                    "import org.drools.traits.core.factmodel.*;\n" +
                    "global java.util.List list; \n" +
                    "" +
                    "declare trait Student @propertyReactive name : String end\n" +
                    "declare trait Worker @propertyReactive name : String end\n" +
                    "declare trait StudentWorker extends Student, Worker @propertyReactive name : String end\n" +
                    "declare trait Assistant extends Student, Worker @propertyReactive name : String end\n" +
                    "declare Person @Traitable @propertyReactive name : String end\n" +
                    "" +
                    "rule \"Init\" \n" +
                    "when \n" +
                    "then \n" +
                    "  Person p = new Person(\"john\"); \n" +
                    "  insert(p); \n" +
                    "end \n" +
                    "" +
                    "rule \"Don\" \n" +
                    "when \n" +
                    "  $p : Person(name == \"john\") \n" +
                    "then \n" +
                    "  don($p, Worker.class); \n" +
                    "  don($p, StudentWorker.class); \n" +
                    "end \n" +
                    "" +
                    "rule \"Log W\" \n" +
                    "when \n" +
                    "  $t : Worker(this isA StudentWorker) @watch(name) \n" +
                    "then \n" +
                    "  list.add(true); \n" +
                    "end \n" +
                    "rule \"Log SW\" \n" +
                    "when \n" +
                    "  $t : StudentWorker() @watch(name) \n" +
                    "then \n" +
                    "end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());
        KieSession ksession = kbase.newKieSession();

        List<Boolean> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains(true);
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitModifyCore3(VirtualPropertyMode mode) {
        String s1 = "package test;\n" +
                    "import org.drools.base.factmodel.traits.*;\n" +
                    "import org.drools.traits.core.factmodel.*;\n" +
                    "global java.util.List list; \n" +
                    "" +
                    "declare trait A id : int end\n" +
                    "declare trait B extends A end\n" +
                    "declare trait C extends A end\n" +
                    "declare trait D extends A end\n" +
                    "declare trait E extends B end\n" +
                    "declare trait F extends C end\n" +
                    "declare trait G extends D end\n" +
                    "declare trait H extends D end\n" +
                    "declare trait I extends E end\n" +
                    "declare trait J extends F end\n" +
                    "declare trait K extends G, H end\n" +
                    "declare trait L extends G, H end\n" +
                    "declare trait M extends I, J end\n" +
                    "declare trait N extends K, L end\n" +
                    "" +
                    "declare Core @Traitable id : int = 0 end \n" +
                    "" +
                    "rule \"Init\" when \n" +
                    "then \n" +
                    "   insert(new Core());" +
                    "end \n" +
                    "" +
                    "rule \"donManyThing\"\n" +
                    "when\n" +
                    "    $x : Core(id == 0)\n" +
                    "then\n" +
                    "    don($x, A.class);\n" +
                    "    don($x, B.class);\n" +
                    "    don($x, D.class);\n" +
                    "    don($x, F.class);\n" +
                    "    don($x, E.class);\n" +
                    "    don($x, I.class);\n" +
                    "    don($x, K.class);\n" +
                    "    don($x, J.class);\n" +
                    "    don($x, C.class);\n" +
                    "    don($x, H.class);\n" +
                    "    don($x, G.class);\n" +
                    "    don($x, L.class);\n" +
                    "    don($x, M.class);\n" +
                    "    don($x, N.class);\n" +
                    "end\n" +
                    "\n" +
                    "\n" +
                    "\n" +
                    "rule \"Log A\" when $x : A(id == 1) then list.add(1); end \n" +
                    "rule \"Log B\" when $x : B(id == 1) then list.add(2); end \n" +
                    "rule \"Log C\" when $x : C(id == 1) then list.add(3); end \n" +
                    "rule \"Log D\" when $x : D(id == 1) then list.add(4); end \n" +
                    "rule \"Log E\" when $x : E(id == 1) then list.add(5); end \n" +
                    "rule \"Log F\" when $x : F(id == 1) then list.add(6); end \n" +
                    "rule \"Log G\" when $x : G(id == 1) then list.add(7); end \n" +
                    "rule \"Log H\" when $x : H(id == 1) then list.add(8); end \n" +
                    "rule \"Log I\" when $x : I(id == 1) then list.add(9); end \n" +
                    "rule \"Log J\" when $x : J(id == 1) then list.add(10); end \n" +
                    "rule \"Log K\" when $x : K(id == 1) then list.add(11); end \n" +
                    "rule \"Log L\" when $x : L(id == 1) then list.add(12); end \n" +
                    "rule \"Log M\" when $x : M(id == 1) then list.add(13); end \n" +
                    "rule \"Log N\" when $x : N(id == 1) then list.add(14); end \n" +
                    "" +
                    "rule \"Log Core\" when $x : Core($id : id) then end \n" +
                    "" +
                    "rule \"Mod\" \n" +
                    "salience -10 \n" +
                    "when \n" +
                    "  String(this == \"go\") \n" +
                    "  $x : Core(id == 0) \n" +
                    "then \n" +
                    "  modify ($x) { setId(1); }" +
                    "end \n" +
                    "";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());

        List<Integer> list = new ArrayList<>();
        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        ksession.insert("go");
        ksession.fireAllRules();

        assertThat(list).hasSize(14);
        for (int j = 1; j <= 14; j++) {
            assertThat(list).contains(j);
        }


    }







    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitModifyCoreWithPropertyReactivity(VirtualPropertyMode mode) {
        String s1 = "package test;\n" +
                    "import org.drools.base.factmodel.traits.*;\n" +
                    "import org.drools.traits.core.factmodel.*;\n" +
                    "global java.util.List list;\n" +
                    "" +
                    "declare trait Student @propertyReactive " +
                    "   name : String " +
                    "   age : int " +
                    "   grades : double " +
                    "   school : String " +
                    "   aaa : boolean " +
                    "end\n" +
                    "declare trait Worker @propertyReactive " +
                    "   name : String " +
                    "   wage : double " +
                    "end\n" +
                    "declare trait StudentWorker extends Student, Worker @propertyReactive " +
                    "   hours : int " +
                    "end\n" +
                    "declare trait Assistant extends Student, Worker @propertyReactive " +
                    "   address : String " +
                    "end\n" +
                    "declare Person @propertyReactive @Traitable " +
                    "   wage : double " +
                    "   name : String " +
                    "   age : int  " +
                    "end\n" +
                    "" +
                    "rule \"Init\" \n" +
                    "when \n" +
                    "then \n" +
                    "  Person p = new Person(109.99, \"john\", 18); \n" +
                    "  insert(p); \n" +
                    "end \n" +
                    "" +
                    "rule \"Don\" \n" +
                    "when \n" +
                    "  $p : Person(name == \"john\") \n" +
                    "then \n" +
                    "  don($p, StudentWorker.class); \n" +
                    "  don($p, Assistant.class); \n" +
                    "end \n" +
                    "" +
                    "rule \"Log S\" \n" +
                    "when \n" +
                    "  $t : Student(age == 44) \n" +
                    "then \n" +
                    "  list.add(1);\n " +
                    "end \n" +
                    "rule \"Log W\" \n" +
                    "when \n" +
                    "  $t : Worker(name == \"alan\") \n" +
                    "then \n" +
                    "  list.add(2);\n " +
                    "end \n" +
                    "rule \"Log SW\" \n" +
                    "when \n" +
                    "  $t : StudentWorker(age == 44) \n" +
                    "then \n" +
                    "  list.add(3);\n " +
                    "end \n" +
                    "rule \"Log Pers\" \n" +
                    "when \n" +
                    "  $t : Person(age == 44) \n" +
                    "then \n" +
                    "  list.add(4);\n " +
                    "end \n" +
                    "" +
                    "rule \"Mod\" \n" +
                    "salience -10 \n" +
                    "when \n" +
                    "  String(this == \"go\") \n" +
                    "  $p : Student(name == \"john\") \n" +
                    "then \n" +
                    "  modify ($p) { setSchool(\"myschool\"), setAge(44), setName(\"alan\"); } " +
                    "end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());

        List<Integer> list = new ArrayList<Integer>();
        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);
        int k = ksession.fireAllRules();

        ksession.insert("go");
        k = ksession.fireAllRules();

        assertThat(k).isEqualTo(5);

        assertThat(list).hasSize(4);
        assertThat(list).contains(1, 2, 3, 4);

    }




    public static interface IntfParent {}

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitEncodeExtendingNonTrait(VirtualPropertyMode mode) {

        String s1 = "package test;\n" +
                    "import " + IntfParent.class.getCanonicalName() + ";\n" +
                    "" +
                    "declare IntfParent end\n" +
                    "" +
                    "declare trait TChild extends IntfParent end \n" +
                    "";

        String s2 = "package test; declare trait SomeThing end \n";


        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s2.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalRuleBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, kbase);

        kbase.addPackages(kbuilder.getKnowledgePackages());

        KnowledgeBuilder kbuilder2 = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder2.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder2.hasErrors()) {
            fail(kbuilder2.getErrors().toString());
        }

        kbase.addPackages(kbuilder2.getKnowledgePackages());

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void isAWithBackChaining(VirtualPropertyMode mode) {

        String source = "org/drools/compiler/factmodel/traits/testTraitIsAWithBC.drl";
        KieSession ksession = getSession(source);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        ksession.insert("Como");

        ksession.fireAllRules();

        assertThat(list).contains("Italy");
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAEvaluatorOnClassification(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "import org.drools.base.factmodel.traits.Thing\n" +
                        "import org.drools.traits.core.factmodel.Entity\n" +
                        "\n" +
                        "declare trait t.x.D\n" +
                        "    @propertyReactive\n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "declare trait t.x.E\n" +
                        "    @propertyReactive\n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Entity o = new Entity();\n" +
                        "   insert(o);\n" +
                        "   don(o, D.class); \n" +
                        "end\n" +
                        "" +
                        "rule Don when\n" +
                        " $o : Entity() \n" +
                        "then \n" +
                        "end \n" +
                        "" +
                        "rule \"Rule 0 >> http://t/x#D\"\n" +
                        "when\n" +
                        "   $t : org.drools.base.factmodel.traits.Thing($c : core, this not isA t.x.E.class, this isA t.x.D.class) " +
                        "then\n" +
                        "   list.add(\"E\"); \n" +
                        "   don($t, E.class); \n" +
                        "end\n" +
                        "" +
                        "rule React \n" +
                        "when E() then \n" +
                        "   list.add(\"X\"); \n" +
                        "end \n"
                ;

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        assertThat(list).hasSize(2);
        assertThat(list).contains("E", "X");

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testShedWithTMS(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "import org.drools.base.factmodel.traits.Thing\n" +
                        "import org.drools.traits.core.factmodel.Entity\n" +
                        "\n" +
                        "declare trait t.x.D\n" +
                        "    @propertyReactive\n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "declare trait t.x.E\n" +
                        "    @propertyReactive\n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Entity o = new Entity();\n" +
                        "   insert(o);\n" +
                        "   don(o, Thing.class); \n" +
                        "   don(o, D.class); \n" +
                        "end\n" +
                        "" +
                        "rule Don when\n" +
                        " $o : Entity() \n" +
                        "then \n" +
                        "end \n" +
                        "" +
                        "rule \"Rule 0 >> http://t/x#D\"\n" +
                        "when\n" +
                        "   $t : org.drools.base.factmodel.traits.Thing($c : core, _isTop(), this not isA t.x.E.class, this isA t.x.D.class) " +
                        "then\n" +
                        "   list.add(\"E\"); \n" +
                        "   don($t, E.class); \n" +
                        "end\n" +
                        "" +
                        "rule React \n" +
                        "when $x : E() then \n" +
                        "   list.add(\"X\"); \n" +
                        "end \n" +
                        "" +
                        "rule Shed \n" +
                        "when \n" +
                        "   $s : String() \n" +
                        "   $d : Entity() \n" +
                        "then \n" +
                        "   delete($s); \n" +
                        "   shed($d, D.class);\n" +
                        "end \n" +
                        ""
                ;

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).hasSize(2);
        assertThat(list).contains("E", "X");

        ks.insert("shed");
        ks.fireAllRules();

        for (Object o : ks.getObjects()) {
            LOGGER.debug(o.toString());
        }
        assertThat(ks.getObjects()).hasSize(3);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitInitialization(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "declare trait Foo\n" +
                        "   hardList : List = new ArrayList() \n" +
                        "   softList : List = new ArrayList() \n" +
                        "   moreList : List = new ArrayList() \n" +
                        "   otraList : List = new ArrayList() \n" +
                        "   primFld  : int = 3 \n" +
                        "   primDbl  : double = 0.421 \n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "declare Bar\n" +
                        "   @Traitable()\n" +
                        "   hardList : List \n" +
                        "   moreList : List = Arrays.asList(1, 2, 3) \n" +
                        "\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Bar o = new Bar();\n" +
                        "   insert(o);\n" +
                        "   Thing t = don(o, Thing.class); \n" +
                        "   t.getFields().put(\"otraList\", Arrays.asList(42)); \n" +
                        "   don(o, Foo.class); \n" +
                        "end\n" +
                        "" +
                        "rule Don when\n" +
                        "   $x : Foo($h : hardList, $s : softList, $o : otraList, $m : moreList, $i : primFld, $d : primDbl) \n" +
                        "then \n" +
                        "   list.add($h); \n" +
                        "   list.add($s); \n" +
                        "   list.add($o); \n" +
                        "   list.add($m); \n" +
                        "   list.add($i); \n" +
                        "   list.add($d); \n" +
                        "end\n" +
                        ""
                ;

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        assertThat(list).hasSize(6);
        assertThat(list).doesNotContainNull();

        List<Integer> hard = (List) list.get(0);
        List<Integer> soft = (List) list.get(1);
        List<Integer> otra = (List) list.get(2);
        List<Integer> more = (List) list.get(3);

        assertThat(hard).isEmpty();
        assertThat(soft).isEmpty();
        assertThat(more).containsExactly(1, 2, 3);
        assertThat(otra).containsExactly(42);

        assertThat(list).contains(3);
        assertThat(list).contains(0.421);
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUnTraitedBean(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait Foo end\n" +
                        "" +
                        "declare Bar\n" +
                        "   @Traitable\n" +
                        "end\n" +
                        "declare Bar2\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Bar o = new Bar();\n" +
                        "   insert(o);\n" +
                        "   Bar2 o2 = new Bar2();\n" +
                        "   insert(o2);\n" +
                        "end\n" +
                        "" +
                        "rule Check when\n" +
                        "   $x : Bar(this not isA Foo) \n" +
                        "then \n" +
                        "end\n" +
                        "rule Check2 when\n" +
                        "   $x : Bar2(this not isA Foo) \n" +
                        "then \n" +
                        "end\n" +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAOptimization(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A end\n" +
                        "declare trait B extends A end\n" +
                        "declare trait C extends B end\n" +
                        "declare trait D extends A end\n" +
                        "declare trait E extends C, D end\n" +
                        "declare trait F extends E end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Kore k = new Kore();\n" +
                        "   don(k, E.class); \n" +
                        "end\n" +
                        "" +
                        "rule Check_1 when\n" +
                        "   $x : Kore(this isA [ B, D ]) \n" +
                        "then \n" +
                        "   list.add(\" B+D \"); \n" +
                        "end\n" +
                        "" +
                        "rule Check_2 when\n" +
                        "   $x : Kore(this isA [ A ]) \n" +
                        "then \n" +
                        "   list.add(\" A \"); \n" +
                        "end\n" +

                        "rule Check_3 when\n" +
                        "   $x : Kore(this not isA [ F ]) \n" +
                        "then \n" +
                        "   list.add(\" F \"); \n" +
                        "end\n" +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        assertThat(list).hasSize(3);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTypeRefractionOnInsert(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A @propertyReactive end\n" +
                        "declare trait B extends A @propertyReactive end\n" +
                        "declare trait C extends B @propertyReactive end\n" +
                        "declare trait D extends A @propertyReactive end\n" +
                        "declare trait E extends C, D @propertyReactive end\n" +
                        "declare trait F extends E @propertyReactive end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Kore k = new Kore();\n" +
                        "   don(k, B.class); \n" +
                        "   don(k, C.class); \n" +
                        "   don(k, D.class); \n" +
                        "   don(k, E.class); \n" +
                        "   don(k, A.class); \n" +
                        "   don(k, F.class); \n" +
                        "end\n" +
                        "" +
                        "rule Check_1 when\n" +
                        "   $x : A() \n" +
                        "then \n" +
                        "   list.add($x); \n" +
                        "end\n" +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        assertThat(list).hasSize(1);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTypeRefractionOnQuery(VirtualPropertyMode mode) {
        String source = "declare BaseObject\n" +
                        "@Traitable\n" +
                        "id : String @key\n" +
                        "end\n" +
                        "\n" +
                        "declare trait A\n" +
                        "id : String @key\n" +
                        "end\n" +
                        "\n" +
                        "declare trait B extends A\n" +
                        "end\n" +
                        "\n" +
                        "declare trait C extends A\n" +
                        "end\n" +
                        "\n" +
                        "rule \"init\"\n" +
                        "when\n" +
                        "then\n" +
                        "BaseObject $obj = new BaseObject(\"testid123\");\n" +
                        "insert ($obj);\n" +
                        "don($obj, B.class, true);\n" +
                        "don($obj, C.class, true);\n" +
                        "end\n" +
                        "\n" +
                        "query \"QueryTraitA\"\n" +
                        "a : A()\n" +
                        "end";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        ks.fireAllRules();

        QueryResults res = ks.getQueryResults("QueryTraitA");

        assertThat(res).hasSize(1);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTypeRefractionOnQuery2(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A end\n" +
                        "declare trait B extends A end\n" +
                        "declare trait C extends B end\n" +
                        "declare trait D extends A end\n" +
                        "declare trait E extends C, D end\n" +
                        "declare trait F extends E end\n" +
                        "declare trait G extends A end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Kore k = new Kore();\n" +
                        "   don(k, C.class); \n" +
                        "   don(k, D.class); \n" +
                        "   don(k, E.class); \n" +
                        "   don(k, B.class); \n" +
                        "   don(k, A.class); \n" +
                        "   don(k, F.class); \n" +
                        "   don(k, G.class); \n" +
                        "   shed(k, B.class); \n" +
                        "end\n" +
                        "" +
                        "rule RuleA\n" +
                        "when \n" +
                        "   $x : A() \n" +
                        "then \n" +
                        "end\n" +
                        " \n" +
                        "query queryA1\n" +
                        "   $x := A() \n" +
                        "end\n" +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        QueryResults res;
        res = ks.getQueryResults("queryA1");
        assertThat(res).hasSize(1);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNodePartitioningByProxies(VirtualPropertyMode mode) {
        String source = "package t.x  " +
                        "import java.util.*;  " +
                        "import org.drools.base.factmodel.traits.Thing  " +
                        "import org.drools.base.factmodel.traits.Traitable  " +
                        " " +
                        "global java.util.List list;  " +
                        " " +
                        "" +
                        "declare trait A @PropertyReactive end " +
                        "declare trait B extends A @PropertyReactive end " +
                        "declare trait C extends B @PropertyReactive end " +
                        "declare trait D extends A @PropertyReactive end " +
                        "declare trait E extends C, D @PropertyReactive end " +
                        "declare trait F extends E @PropertyReactive end " +
                        "declare trait G extends A @PropertyReactive end " +
                        "" +
                        "declare Kore " +
                        "   @Traitable " +
                        "end " +
                        "" +
                        "rule Init when " +

                        "then " +
                        "   Kore k = new Kore(); " +
                        "   don(k, C.class);  " +
                        "   don(k, D.class);  " +
                        "   don(k, B.class);  " +
                        "   don(k, A.class);  " +
                        "   don(k, F.class);  " +
                        "   don(k, E.class);  " +
                        "   don(k, G.class);  " +
                        "end ";

        for (char c = 'A'; c <= 'G'; c++) {
            String C = "" + c;
            source += "rule Rule" + C +
                      " when " + C + "() then list.add('"+ C + "'); end ";
        }

            source += "rule RuleAll " +
                        "when  " +
                        "   A() B() C() D() E() F() G() " +
                        "then  " +
                        "   list.add('Z'); " +
                        "end " +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Character> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).containsExactly('A', 'B', 'C', 'D', 'E', 'F', 'G', 'Z');


        for (Object o : ks.getObjects(object -> object instanceof TraitableBean)) {
            Set<BitSet> otns = checkOTNPartitioning((TraitableBean) o, ks);
            assertThat(otns).hasSize(7);
        }

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNodePartitioningByProxiesAfterShed(VirtualPropertyMode mode) {
        String source = "package t.x  " +
                        "import java.util.*;  " +
                        "import org.drools.base.factmodel.traits.Thing  \n" +
                        "import org.drools.base.factmodel.traits.Traitable  \n" +
                        " " +
                        "global java.util.List list;  \n" +
                        " " +
                        "" +
                        "declare trait A end \n" +
                        "declare trait B extends A end \n" +
                        "declare trait C extends B end \n" +
                        "declare trait D extends A end \n" +
                        "declare trait E extends C, D end \n" +
                        "declare trait F extends E end \n" +
                        "declare trait G extends A end \n" +
                        "" +
                        "declare Kore \n" +
                        "   @Traitable \n" +
                        "end \n" +
                        "" +
                        "rule Init when \n" +

                        "then \n" +
                        "   Kore k = new Kore(); \n" +
                        "   don(k, C.class);  \n" +
                        "   don(k, D.class);  \n" +
                        "   don(k, B.class);  \n" +
                        "   don(k, A.class);  \n" +
                        "   don(k, F.class);  \n" +
                        "   don(k, E.class);  \n" +
                        "   don(k, G.class);  \n" +
                        "   shed(k, B.class);  \n" +
                        "end \n";

        for (char c = 'A'; c <= 'G'; c++) {
            String C = "" + c;
            source += "rule Rule" + C +
                      " when \n" + C + "() \nthen\n" +
                      " System.out.println(\"Rule " + C + "\");\n" +
                      " list.add('"+ C + "'); \n" +
                      "end \n";
        }

            source += "rule RuleAll \n" +
                        "when  \n" +
                        "   A() D() G() \n" +
                        "then  \n" +
                        "   list.add('Z'); \n" +
                        "end \n" +
                        "";


        System.out.println(source);
        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Character> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).containsExactly('A', 'D', 'G', 'Z');


        for (Object o : ks.getObjects(object -> object instanceof TraitableBean)) {
            Set<BitSet> otns = checkOTNPartitioning((TraitableBean) o, ks);
            assertThat(otns).hasSize(3);
        }
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTypeRefractionOnQueryWithIsA(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A @propertyReactive end\n" +
                        "declare trait B extends A @propertyReactive end\n" +
                        "declare trait C extends B @propertyReactive end\n" +
                        "declare trait D extends A @propertyReactive end\n" +
                        "declare trait E extends C, D @propertyReactive end\n" +
                        "declare trait F extends E @propertyReactive end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "end\n" +
                        "" +
                        "rule Init when\n" +
                        "then\n" +
                        "   Kore k = new Kore();\n" +
                        "   don(k, C.class); \n" +
                        "   don(k, D.class); \n" +
                        "   don(k, E.class); \n" +
                        "   don(k, B.class); \n" +
                        "   don(k, A.class); \n" +
                        "   don(k, F.class); \n" +
                        "   shed(k, B.class); \n" +
                        "end\n" +
                        "" +
                        " \n" +
                        "query queryA\n" +
                        "   $x := Kore(this isA A) \n" +
                        "end\n" +
                        "";


        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        QueryResults res = ks.getQueryResults("queryA");
        Iterator<QueryResultsRow> iter = res.iterator();
        Object a = iter.next().get("$x");
        assertThat(iter).isExhausted();

        assertThat(res).hasSize(1);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCoreUpdate4(VirtualPropertyMode mode) {
        String source = "package t.x \n" +
                        "import java.util.*; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A " +
                        "   age : int \n" +
                        "end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "   @propertyReactive" +
                        "   age : int\n" +
                        "end\n" +
                        "" +
                        "rule Init \n" +
                        "when\n" +
                        "then\n" +
                        "   Kore k = new Kore(44);\n" +
                        "   insert(k); \n" +
                        "end\n" +
                        "" +
                        "" +
                        "rule Don \n" +
                        "no-loop \n" +
                        "when\n" +
                        "   $x : Kore() \n" +
                        "then \n" +
                        "   don($x, A.class); \n" +
                        "end\n" +
                        "rule React \n" +
                        "salience 1" +
                        "when\n" +
                        "   $x : Kore(this isA A.class) \n" +
                        "then \n" +
                        "   list.add($x); \n" +
                        "end\n" +
                        "";
        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);
        ks.fireAllRules();

        for (Object o : ks.getObjects()) {
            LOGGER.debug(o.toString());
        }
        assertThat(list).hasSize(1);
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitLogicalSupportAnddelete(VirtualPropertyMode mode) {
        String drl = "package org.drools.trait.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "declare trait Student\n" +
                     "  age  : int\n" +
                     "  name : String\n" +
                     "end\n" +
                     "\n" +
                     "declare Person\n" +
                     "  @Traitable\n" +
                     "  name : String\n" +
                     "end\n" +
                     "\n" +
                     "rule Init when then insert(new Person(\"john\")); end \n" +
                     "" +
                     "rule \"Don Logical\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger1\")\n" +
                     "  $p : Person() \n" +
                     "then\n" +
                     "  don($p, Student.class, true);\n" +
                     "end\n" +
                     "" +
                     "rule \"Don Logical2\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger2\")\n" +
                     "  $p : Person() \n" +
                     "then\n" +
                     "  don($p, Student.class, true);\n" +
                     "end\n" +
                     "" +
                     "rule \"Undon \"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger3\")\n" +
                     "  $p : Person() \n" +
                     "then\n" +
                     "  shed($p, org.drools.base.factmodel.traits.Thing.class); " +
                     "  delete($s); \n" +
                     "end\n" +
                     " " +
                     "rule \"Don Logical3\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger4\")\n" +
                     "  $p : Person() \n" +
                     "then\n" +
                     "  don($p, Student.class, true);" +
                     "end\n" +
                     " " +
                     "rule \"Undon 2\"\n" +
                     "when\n" +
                     "  $s : String(this == \"trigger5\")\n" +
                     "  $p : Person() \n" +
                     "then\n" +
                     "  delete($s); \n" +
                     "  delete($p); \n" +
                     "end\n" +
                     "";


        KieSession ksession = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List list = new ArrayList();
        ksession.setGlobal("list", list);

        FactHandle h1 = ksession.insert("trigger1");
        FactHandle h2 = ksession.insert("trigger2");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(4);

        ksession.delete(h1);
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(3);

        ksession.delete(h2);
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(1);

        ksession.insert("trigger3");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(1);

        ksession.insert("trigger4");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(3);

        ksession.insert("trigger5");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }
        LOGGER.debug("---------------------------------");

        assertThat(ksession.getObjects()).hasSize(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testShedThing(VirtualPropertyMode mode) {
        String s1 = "package test;\n" +
                    "import org.drools.base.factmodel.traits.*;\n" +
                    "import org.drools.traits.core.factmodel.*;\n" +
                    "global java.util.List list; \n" +
                    "" +
                    "declare trait A id : int end\n" +
                    "declare trait B extends A end\n" +
                    "declare trait C extends A end\n" +
                    "declare trait D extends A end\n" +
                    "declare trait E extends B end\n" +
                    "" +
                    "declare Core @Traitable id : int = 0 end \n" +
                    "" +
                    "rule \"Init\" when \n" +
                    "then \n" +
                    "   insert(new Core());" +
                    "end \n" +
                    "" +
                    "rule \"donManyThing\"\n" +
                    "when\n" +
                    "    $x : Core(id == 0)\n" +
                    "then\n" +
                    "    don($x, A.class);\n" +
                    "    don($x, B.class);\n" +
                    "    don($x, C.class);\n" +
                    "    don($x, D.class);\n" +
                    "    don($x, E.class);\n" +
                    "end\n" +
                    "\n" +
                    "\n" +
                    "" +
                    "rule \"Mod\" \n" +
                    "salience -10 \n" +
                    "when \n" +
                    "  $g : String(this == \"go\") \n" +
                    "  $x : Core(id == 0) \n" +
                    "then \n" +
                    "  shed($x, Thing.class); " +
                    "  delete($g); \n\n" +
                    "end \n" +
                    "";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());

        List list = new ArrayList();
        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        ksession.insert("go");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }

        assertThat(ksession.getObjects()).hasSize(1);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testdeleteThings(VirtualPropertyMode mode) {
        String s1 = "package test;\n" +
                    "import org.drools.base.factmodel.traits.*;\n" +
                    "import org.drools.traits.core.factmodel.*;\n" +
                    "global java.util.List list; \n" +
                    "" +
                    "declare trait A id : int end\n" +
                    "declare trait B extends A end\n" +
                    "declare trait C extends A end\n" +
                    "declare trait D extends A end\n" +
                    "declare trait E extends B end\n" +
                    "" +
                    "declare Core @Traitable id : int = 0 end \n" +
                    "" +
                    "rule \"Init\" when \n" +
                    "then \n" +
                    "   insert(new Core());" +
                    "end \n" +
                    "" +
                    "rule \"donManyThing\"\n" +
                    "when\n" +
                    "    $x : Core(id == 0)\n" +
                    "then\n" +
                    "    don($x, A.class);\n" +
                    "    don($x, B.class);\n" +
                    "    don($x, C.class);\n" +
                    "    don($x, D.class);\n" +
                    "    don($x, E.class);\n" +
                    "end\n" +
                    "\n" +
                    "\n" +
                    "" +
                    "rule \"Mod\" \n" +
                    "salience -10 \n" +
                    "when \n" +
                    "  $g : String(this == \"go\") \n" +
                    "  $x : Core(id == 0) \n" +
                    "then \n" +
                    "  delete($x); \n\n" +
                    "  delete($g); \n\n" +
                    "end \n" +
                    "";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(new ByteArrayResource(s1.getBytes()), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (KieBase) kbase); // not relevant

        kbase.addPackages(kbuilder.getKnowledgePackages());

        List list = new ArrayList();
        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        ksession.insert("go");
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }

        assertThat(ksession.getObjects()).isEmpty();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void traitLogicalRemovalSimple(VirtualPropertyMode mode) {
        String drl = "package org.drools.compiler.trait.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "declare trait Student\n" +
                     " age : int\n" +
                     " name : String\n" +
                     "end\n" +
                     "declare trait Worker\n" +
                     " wage : int\n" +
                     "end\n" +
                     "" +
                     "declare trait Scholar extends Student\n" +
                     "end\n" +
                     "\n" +
                     "declare Person\n" +
                     " @Traitable\n" +
                     " name : String\n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "rule \"Don Logical\"\n" +
                     "when\n" +
                     " $s : String(this == \"trigger\")\n" +
                     "then\n" +
                     " Person p = new Person(\"john\");\n" +
                     " insert(p); \n" +
                     " don(p, Student.class, true);\n" +
                     " don(p, Worker.class);\n" +
                     " don(p, Scholar.class);\n" +
                     "end";


        KieSession ksession = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<Object> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        FactHandle h = ksession.insert("trigger");
        ksession.fireAllRules();
        assertThat(ksession.getObjects()).hasSize(5);

        ksession.delete(h);
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            // lose the string and the Student proxy
            LOGGER.debug(o.toString());
        }
        assertThat(ksession.getObjects()).hasSize(3);

    }



    @Traitable
    public static class TraitableFoo {

        private String id;

        public TraitableFoo(String id, int x, Object k) {
            setId(id);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @Traitable
    public static class XYZ extends TraitableFoo {

        public XYZ() {
            super(null, 0, null);
        }

    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitDonLegacyClassWithoutEmptyConstructor(VirtualPropertyMode mode) {
        String drl = "package org.drools.compiler.trait.test;\n" +
                     "\n" +
                     "import " + TraitableFoo.class.getCanonicalName() + ";" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "" +
                     "declare trait Bar\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Don\"\n" +
                     "no-loop \n" +
                     "when\n" +
                     " $f : TraitableFoo()\n" +
                     "then\n" +
                     "  Bar b = don($f, Bar.class);\n" +
                     "end";


        KieSession ksession = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());
        ksession.addEventListener(new DebugAgendaEventListener());

        ksession.insert(new TraitableFoo("xx", 0, null));
        ksession.fireAllRules();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }

        assertThat(ksession.getObjects()).hasSize(2);
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testdeleteCoreObjectChained(VirtualPropertyMode mode) {
        String source = "package org.drools.test;\n" +
                        "import java.util.List; \n" +
                        "import org.drools.base.factmodel.traits.Thing \n" +
                        "import org.drools.base.factmodel.traits.Traitable \n" +
                        "\n" +
                        "global java.util.List list; \n" +
                        "\n" +
                        "" +
                        "declare trait A " +
                        "   age : int \n" +
                        "end\n" +
                        "" +
                        "declare Kore\n" +
                        "   @Traitable\n" +
                        "   age : int\n" +
                        "end\n" +
                        "" +
                        "rule Init \n" +
                        "when\n" +
                        "   $s : String() \n" +
                        "then\n" +
                        "   Kore k = new Kore(44);\n" +
                        "   insertLogical(k); \n" +
                        "end\n" +
                        "" +
                        "" +
                        "rule Don \n" +
                        "no-loop \n" +
                        "when\n" +
                        "   $x : Kore() \n" +
                        "then \n" +
                        "   don($x, A.class); \n" +
                        "end\n" +
                        "" +
                        "" +
                        "rule delete \n" +
                        "salience -99 \n" +
                        "when \n" +
                        "   $x : String() \n" +
                        "then \n" +
                        "   delete($x); \n" +
                        "end \n" +
                        "\n";

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List list = new ArrayList();
        ks.setGlobal("list", list);

        ks.insert("go");

        ks.fireAllRules();

        for (Object o : ks.getObjects()) {
            LOGGER.debug(o.toString());
        }

        assertThat(ks.getObjects()).isEmpty();

        ks.dispose();
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateLegacyClass(VirtualPropertyMode mode) {
        String source = "package org.drools.text;\n" +
                        "\n" +
                        "global java.util.List list;\n" +
                        "\n" +
                        "import org.drools.traits.compiler.Person;\n" +
                        "import org.drools.base.factmodel.traits.Traitable;\n" +
                        "\n" +
                        "declare Person @Traitable end \n" +
                        "" +
                        "declare trait Student\n" +
                        "  name : String\n" +
                        "end\n" +
                        "\n" +
                        "rule \"Init\"\n" +
                        "salience 10 \n" +
                        "when\n" +
                        "  $p : Person(this not isA Student)\n" +
                        "then\n" +
                        "  don($p, Student.class);\n" +
                        "end\n" +
                        "\n" +
                        "rule \"Go\"\n" +
                        "when\n" +
                        "  $s : String(this == \"X\")\n" +
                        "  $p : Person()\n" +
                        "then\n" +
                        "  delete($s); \n" +
                        "  modify($p) { setName($s); }\n" +
                        "end\n" +
                        "\n" +
                        "rule \"Mod\"\n" +
                        "when\n" +
                        "  Student(name == \"X\")\n" +
                        "then\n" +
                        "  list.add(0);\n" +
                        "end";

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Integer> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.insert(new Person("john", 32));
        ks.insert("X");

        ks.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains(0);

        ks.dispose();
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSoftPropertyClash(VirtualPropertyMode mode) {
        String source = "package org.drools.text;\n" +
                        "\n" +
                        "global java.util.List list;\n" +
                        "\n" +
                        "import org.drools.base.factmodel.traits.Traitable;\n" +
                        "import org.drools.base.factmodel.traits.Alias;\n" +
                        "\n" +
                        "declare Person @Traitable @propertyReactive \n" +
                        "end \n" +
                        "" +
                        "declare trait Student\n" +
                        "   @propertyReactive \n" +
                        "   id : String = \"a\" \n" +
                        "   fld2 : int = 4 \n" +
                        "   fld3 : double = 4.0 \n" +
                        "   fld4 : String = \"hello\" \n" +
                        "   fldZ : String = \"hello\" @Alias(\"fld5\")\n" +
                        "end\n" +
                        "declare trait Worker\n" +
                        "   @propertyReactive \n" +
                        "   id : int = 3 \n" +
                        "   fld2 : String = \"b\" \n " +
                        "   fld3 : int = 11 \n " +
                        "   fld4 : Class = Object.class \n " +
                        "   fldY : int = 42 @Alias(\"fld5\")\n" +
                        "end\n" +
                        "" +
                        "rule \"Init\" when then \n" +
                        "   insert(new Person()); \n" +
                        "end \n" +
                        "" +
                        "\n" +
                        "rule \"Don\"\n" +
                        "when\n" +
                        "   $p : Person() \n" +
                        "then\n" +
                        "  Student $s = (Student) don($p, Student.class);\n" +
                        "  modify ($s) { setId(\"xyz\"); } " +
                        "  " +
                        "  Worker $w = don($p, Worker.class);\n" +
                        "  modify ($w) { setId(99); } " +
                        "end\n" +
                        "\n" +
                        "rule \"Stud\"\n" +
                        "when\n" +
                        "  $s : Student($sid : id == \"xyz\", $f2 : fld2, $f3 : fld3, $f4 : fld4, $f5 : fldZ)\n" +
                        "then\n" +
                        "  list.add($sid); \n" +
                        "  list.add($f2); \n" +
                        "  list.add($f3); \n" +
                        "  list.add($f4); \n" +
                        "  list.add($f5); \n" +
                        "end\n" +
                        "\n" +
                        "rule \"Mod\"\n" +
                        "when\n" +
                        "  $w : Worker($wid : id == 99, $f2 : fld2, $f3 : fld3, $f4 : fld4, $f5 : fldY)\n" +
                        "then\n" +
                        "  list.add($wid); \n" +
                        "  list.add($f2); \n" +
                        "  list.add($f3); \n" +
                        "  list.add($f4); \n" +
                        "  list.add($f5); \n" +
                        "end";

        KieSession ks = getSessionFromString(source);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        assertThat(list).hasSize(5);
        assertThat(list).containsExactly(99, "b", 11, Object.class, 42);

        ks.dispose();
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMultipleModifications(VirtualPropertyMode mode) {
        String drl = "package org.drools.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "" +
                     "global java.util.List list;" +
                     "\n" +
                     "declare Person\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "    ssn : String\n" +
                     "    pob : String\n" +
                     "    isStudent : boolean\n" +
                     "    hasAssistantship : boolean\n" +
                     "end\n" +
                     "\n" +
                     "declare trait Student\n" +
                     "@propertyReactive\n" +
                     "    studyingCountry : String\n" +
                     "    hasAssistantship : boolean\n" +
                     "end\n" +
                     "\n" +
                     "declare trait Worker\n" +
                     "@propertyReactive\n" +
                     "    pob : String\n" +
                     "    workingCountry : String\n" +
                     "end\n" +
                     "\n" +
                     "declare trait USCitizen\n" +
                     "@propertyReactive\n" +
                     "    pob : String = \"US\"\n" +
                     "end\n" +
                     "\n" +
                     "declare trait ITCitizen\n" +
                     "@propertyReactive\n" +
                     "    pob : String = \"IT\"\n" +
                     "end\n" +
                     "\n" +
                     "declare trait IRCitizen\n" +
                     "@propertyReactive\n" +
                     "    pob : String = \"IR\"\n" +
                     "end\n" +
                     "\n" +
                     "rule \"init\"\n" +
                     "when\n" +
                     "then\n" +
                     "    insert(new Person(\"1234\",\"IR\",true,true));\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being student\"\n" +
                     "when\n" +
                     "    $p : Person($ssn : ssn, $pob : pob,  isStudent == true)\n" +
                     "then\n" +
                     "    Student st = (Student) don($p , Student.class);\n" +
                     "    modify(st){\n" +
                     "        setStudyingCountry(\"US\");\n" +
                     "    }\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for IR\"\n" +
                     "when\n" +
                     "    $p : Person(pob == \"IR\")\n" +
                     "then\n" +
                     "    don($p , IRCitizen.class);\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being US citizen\"\n" +
                     "when\n" +
                     "    $s : Student(studyingCountry == \"US\")\n" +
                     "then\n" +
                     "    don($s , USCitizen.class);\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being worker\"\n" +
                     "when\n" +
                     "    $p : Student(hasAssistantship == true, $sc : studyingCountry)\n" +
                     "then\n" +
                     "    Worker wr = (Worker) don($p , Worker.class);\n" +
                     "    modify(wr){\n" +
                     "        setWorkingCountry($sc);\n" +
                     "    }\n" +
                     "\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Join Full\"\n" +
                     "salience -1\n" +
                     "when\n" +
                     "    Student()      // $sc := studyingCountry)\n" +
                     "    USCitizen()\n" +
                     "    IRCitizen()      // $pob := pob)\n" +
                     "    Worker()       // pob == $pob , workingCountry == $sc)\n" +
                     "then\n" +
                     "    list.add(1); " +
                     "end\n" +
                     "\n" +
                     "\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Integer> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        assertThat(list).contains(1);
        assertThat(list).hasSize(1);

        ks.dispose();

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPropagation(VirtualPropertyMode mode) {
        String drl = "package org.drools.test;\n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "\n" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare X @Traitable end \n" +
                     "" +
                     "declare trait A @propertyReactive end\n" +
                     "declare trait B extends A @propertyReactive end\n" +
                     "declare trait C extends B @propertyReactive end \n" +
                     "declare trait D extends C @propertyReactive end\n" +
                     "declare trait E extends B,C @propertyReactive end\n" +
                     "declare trait F extends E @propertyReactive end\n" +
                     "declare trait G extends B @propertyReactive end\n" +
                     "declare trait H extends G @propertyReactive end\n" +
                     "declare trait I extends E,H @propertyReactive end\n" +
                     "declare trait J extends I @propertyReactive end\n" +
                     "" +
                     "rule Init when then X x = new X(); insert(x); don(x, F.class); end \n"+
                     "rule Go when String(this == \"go\") $x : X() then don($x, H.class); end \n" +
                     "rule Go2 when String(this == \"go2\") $x : X() then don($x, D.class); end \n" +
                     "";

        for (int j = 'A'; j <= 'J'; j ++) {
            String x = "" + (char) j;
            drl += "rule \"Log " + x + "\" when " + x + "() then list.add(\"" + x + "\"); end \n";

            drl += "rule \"Log II" + x + "\" salience -1 when " + x + "(";
            drl += "this isA H";
            drl += ") then list.add(\"H" + x + "\"); end \n";
        }

        KieSession ks = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        assertThat(list).hasSize(5);
        assertThat(list).contains("A", "B", "C", "E", "F");

        list.clear();

        LOGGER.debug("---------------------------------------");

        ks.insert("go");
        ks.fireAllRules();

        assertThat(list).hasSize(9);
        assertThat(list).contains("H", "G", "HA", "HB", "HC", "HE", "HF", "HG", "HH");
        LOGGER.debug(list.toString());
        
        list.clear();

        LOGGER.debug("---------------------------------------");

        ks.insert("go2");
        ks.fireAllRules();

        assertThat(list).hasSize(9);
        assertThat(list).contains("D", "HA", "HB", "HC", "HE", "HF", "HG", "HH", "HH", "HD");

        ks.dispose();

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testParentBlockers(VirtualPropertyMode mode) {
        String drl = "package org.drools.test;\n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "\n" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare X @Traitable end \n" +
                     "" +
                     "declare trait A @propertyReactive end\n" +
                     "declare trait B @propertyReactive end\n" +
                     "declare trait C extends A, B @propertyReactive end \n" +
                     "" +
                     "rule Init when then X x = new X(); insert(x); don(x, A.class); don(x, B.class); end \n"+
                     "rule Go when String(this == \"go\") $x : X() then don($x, C.class); end \n" +
                     "rule Go2 when String(this == \"go2\") $x : C() then end \n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        ks.insert("go");
        ks.fireAllRules();

        ks.insert("go2");
        ks.fireAllRules();

        LOGGER.debug("---------------------------------------");

        ks.dispose();

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitLogicalTMS(VirtualPropertyMode mode) {
        String drl = "package org.drools.test;\n" +
                     "import org.drools.base.factmodel.traits.*; \n" +
                     "\n" +
                     "global java.util.List list; \n" +
                     "" +
                     "declare X @Traitable end \n" +
                     "" +
                     "declare trait A @propertyReactive end\n" +
                     "declare trait B @propertyReactive end\n" +
                     "" +
                     "rule Init when then X x = new X(); insert(x); end \n"+
                     "rule Go when String(this == \"go\") $x : X() then don($x, A.class, true); don($x, B.class, true); end \n" +
                     "rule Go2 when String(this == \"go2\") $x : X() then don($x, A.class); end \n" +
                     "rule Go3 when String(this == \"go3\") $x : A() not B() then list.add(100); end \n" +
                     "";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<Object> list = new ArrayList<>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        FactHandle handle = ks.insert("go");
        ks.fireAllRules();

        ks.insert("go2");
        ks.fireAllRules();

        ks.delete(handle);
        ks.fireAllRules();

        LOGGER.debug("---------------------------------------");

        for (Object o : ks.getObjects()) {
            LOGGER.debug(o.toString());
        }

        ks.insert("go3");
        ks.fireAllRules();

        assertThat(list).containsExactly(100);

        ks.dispose();
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitNoType(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Thing;\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "import org.drools.base.factmodel.traits.Trait;\n" +
                     "import org.drools.base.factmodel.traits.Alias;\n" +
                     "import java.util.*;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "\n" +
                     "declare Parent\n" +
                     "@Traitable(logical = true)" +
                     "@propertyReactive\n" +
                     "end\n" +
                     "\n" +
                     "declare trait ChildTrait\n" +
                     "@propertyReactive\n" +
                     "    naam : String = \"kudak\"\n" +
                     "    id : int = 1020\n" +
                     "end\n" +
                     "\n" +
                     "rule \"don\"\n" +
                     "no-loop\n" +
                     "when\n" +
                     "then\n" +
                     "    Parent p = new Parent();" +
                     "    insert(p);\n" +
                     "    ChildTrait ct = don(p , ChildTrait.class);\n" +
                     "    list.add(\"correct1\");\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check\"\n" +
                     "no-loop\n" +
                     "when\n" +
                     "    $c : ChildTrait($n : naam == \"kudak\", id == 1020)\n" +
                     "    $p : Thing(core == $c.core, fields[\"naam\"] == $n)\n" +
                     "then\n" +
                     "    list.add(\"correct2\");\n" +
                     "end";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        List<String> list = new ArrayList<>();
        ksession.setGlobal("list",list);
        ksession.fireAllRules();

        assertThat(list).contains("correct1", "correct2");
    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitdeleteOrder(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.*;\n" +
                     "import org.drools.traits.core.factmodel.*;\n" +
                     "import java.util.*;\n" +
                     "\n" +
                     "declare trait A end \n" +
                     "declare trait B extends A end \n" +
                     "declare trait C end \n" +
                     "\n" +
                     "rule \"don\"\n" +
                     "when \n" +
                     "  $e : Entity() \n" +
                     "then\n" +
                     "  don($e, A.class); \n" +
                     "  don($e, C.class); \n" +
                     "  don($e, B.class); \n" +
                     "end\n" +
                     "";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        FactHandle handle = ksession.insert(new Entity());
        ksession.fireAllRules();

        final List<String> list = new ArrayList<>();

        ksession.addEventListener(new RuleRuntimeEventListener() {
            public void objectInserted(org.kie.api.event.rule.ObjectInsertedEvent objectInsertedEvent) { }
            public void objectUpdated(org.kie.api.event.rule.ObjectUpdatedEvent objectUpdatedEvent) { }
            public void objectDeleted(org.kie.api.event.rule.ObjectDeletedEvent objectRetractedEvent) {
                Object o = objectRetractedEvent.getOldObject();
                if (o instanceof TraitProxyImpl) {
                    String traitName = ((TraitProxyImpl) o)._getTraitName();
                    list.add(traitName.substring(traitName.lastIndexOf(".") + 1));
                }
            }
        });

        ksession.delete(handle);
        ksession.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).containsExactly("B", "C", "A");
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitWithManySoftFields(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.*;\n" +
                     "import org.drools.traits.core.factmodel.*;\n" +
                     "import java.util.*;\n" +
                     "\n" +
                     "declare trait Tx \n";

        for (int j = 0; j < 150; j ++) {
            drl += " fld" + j + " : String \n";
        }

        drl += "" +
               "end \n" +
               "\n" +
               "declare TBean @Traitable fld0 : String end \n" +
               "" +
               "rule \"don\"\n" +
               "when \n" +
               "then\n" +
               "  don(new TBean(), Tx.class); \n" +
               "end\n" +
               "" +
               "";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());

        ksession.fireAllRules();

        assertThat(ksession.getObjects()).hasSize(2);

    }



    public static class CountingWorkingMemoryEventListener implements RuleRuntimeEventListener {

        private int inserts = 0;
        private int updates = 0;
        private int deletes = 0;

        public int getInserts() {
            return inserts;
        }

        public int getUpdates() {
            return updates;
        }

        public int getdeletes() {
            return deletes;
        }

        @Override
        public void objectInserted(org.kie.api.event.rule.ObjectInsertedEvent event) {
            if (! (event.getObject() instanceof String)) {
                inserts++;
            }
        }

        @Override
        public void objectUpdated(org.kie.api.event.rule.ObjectUpdatedEvent event) {
            if (! (event.getObject() instanceof String)) {
                updates++;
            }
        }

        public void objectDeleted(org.kie.api.event.rule.ObjectDeletedEvent objectdeleteedEvent) {
            if (! (objectdeleteedEvent.getOldObject() instanceof String)) {
                deletes++;
            }
        }

        public void reset() {
            inserts = 0;
            deletes = 0;
            updates = 0;
        }
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDonManyTraitsAtOnce(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.*;\n" +
                     "import org.drools.traits.core.factmodel.*;\n" +
                     "import java.util.*;\n" +
                     "\n" +
                     "global List list; \n" +
                     "" +
                     "declare trait A end \n" +
                     "declare trait B end \n" +
                     "declare trait C end \n" +
                     "declare trait D end \n" +
                     "declare trait E end \n" +
                     "declare trait F end \n" +
                     "\n" +
                     "declare TBean @Traitable @propertyReactive fld0 : String end \n" +
                     "" +
                     "rule \"Don 1\"\n" +
                     "when \n" +
                     "then\n" +
                     "  TBean t = new TBean(); \n" +
                     "  don(t, A.class); \n" +
                     "  don(t, B.class); \n" +
                     "end\n" +
                     "" +
                     "rule \"Don 2\" " +
                     "when \n" +
                     "  $s : String(this == \"go\") \n" +
                     "  $t : TBean() \n" +
                     "then \n" +
                     "  list.add(0); \n" +
                     "  don($t, Arrays.asList(C.class, D.class, E.class, F.class), true); \n" +
                     "end \n" +
                     "" +
                     "rule Clear \n" +
                     "when \n" +
                     "  $s : String(this == \"undo\") \n" +
                     "  $t : TBean() \n" +
                     "then \n" +
                     "  delete($s); \n" +
                     "  delete($t); \n" +
                     "end \n" +
                     "" +
                     "rule C \n" +
                     "when\n" +
                     "  B(this isA C) \n" +
                     "then \n" +
                     "  list.add(1); \n" +
                     "end \n" +
                     "rule D \n" +
                     "when\n" +
                     "  D(this isA A, this isA C) \n" +
                     "then \n" +
                     "  list.add(2); \n" +
                     "end \n"+
                     "rule E \n" +
                     "when\n" +
                     "  D(this isA A, this isA E) \n" +
                     "then \n" +
                     "  list.add(3); \n" +
                     "end \n";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());
        List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        CountingWorkingMemoryEventListener cwm = new CountingWorkingMemoryEventListener();
        ksession.addEventListener(cwm);

        ksession.fireAllRules();

        // insert Core Bean, A, B, Thing.
        // Update the bean on don B
        assertThat(cwm.getdeletes()).isEqualTo(0);
        assertThat(cwm.getInserts()).isEqualTo(3);
        assertThat(cwm.getUpdates()).isEqualTo(1);
        cwm.reset();

        FactHandle handle = ksession.insert("go");
        ksession.fireAllRules();

        // don C, D, E, F at once : 4 inserts
        // Update the bean, A and B.
        assertThat(cwm.getdeletes()).isEqualTo(0);
        assertThat(cwm.getInserts()).isEqualTo(4);
        assertThat(cwm.getUpdates()).isEqualTo(3);
        cwm.reset();

        ksession.delete(handle);
        ksession.fireAllRules();

        // logically asserted C, D, E, F are deleteed
        // as a logical deleteion, no update is made. This could be a bug....
        assertThat(cwm.getdeletes()).isEqualTo(4);
        assertThat(cwm.getInserts()).isEqualTo(0);
        assertThat(cwm.getUpdates()).isEqualTo(0);
        cwm.reset();

        for (Object o : ksession.getObjects()) {
            LOGGER.debug(o.toString());
        }

        ksession.insert("undo");
        ksession.fireAllRules();

        // deleteing the core bean
        // A, B, Thing are deleteed too
        assertThat(cwm.getdeletes()).isEqualTo(3);
        assertThat(cwm.getInserts()).isEqualTo(0);
        assertThat(cwm.getUpdates()).isEqualTo(0);
        cwm.reset();


        assertThat(list).hasSize(4);
        assertThat(list).contains(0, 1, 2, 3);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDonManyTraitsAtOnce2(VirtualPropertyMode mode) {
        String drl = "" +
                     "package org.drools.base.factmodel.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.*;\n" +
                     "import org.drools.traits.core.factmodel.*;\n" +
                     "import java.util.*;\n" +
                     "\n" +
                     "global List list; \n" +
                     "" +
                     "declare trait A @propertyReactive end \n" +
                     "declare trait B @propertyReactive end \n" +
                     "\n" +
                     "declare TBean @Traitable @propertyReactive fld0 : String end \n" +
                     "" +
                     "rule \"Don 1\"\n" +
                     "when \n" +
                     "then\n" +
                     "  TBean t = new TBean(); \n" +
                     "  don(t, A.class); \n" +
                     "  don(t, B.class); \n" +
                     "end\n" +
                     "" +
                     "rule \"Test Don A,B\" " +
                     "when \n" +
                     "  A(this isA B) \n" +
                     "then \n" +
                     "  list.add(0); \n" +
                     "end \n";

        KieSession ksession = loadKnowledgeBaseFromString(drl).newKieSession();
        TraitFactoryImpl.setMode(mode, ksession.getKieBase());
        List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        CountingWorkingMemoryEventListener cwm = new CountingWorkingMemoryEventListener();
        ksession.addEventListener(cwm);

        ksession.fireAllRules();

        assertThat(list).containsExactly(0);

        assertThat(cwm.getdeletes()).isEqualTo(0);
        assertThat(cwm.getInserts()).isEqualTo(3);
        assertThat(cwm.getUpdates()).isEqualTo(1);

    }




    @Traitable
    public static class Item {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class TraitRulesThread implements Runnable {
        int threadIndex;
        int numRepetitions;
        KieSession ksession;

        public TraitRulesThread(int threadIndex, int numRepetitions, final KieSession ksession) {
            this.threadIndex = threadIndex;
            this.numRepetitions = numRepetitions;
            this.ksession = ksession;
        }
        public void run() {
            for (int repetitionIndex = 0; repetitionIndex < numRepetitions; repetitionIndex++) {
                final Item i = new Item();
                i.setId(String.format("testId_%d%d", threadIndex, repetitionIndex));
                ksession.insert(i);
                ksession.fireAllRules();
            }
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Disabled("Triple Store is not thread safe and needs to be rewritten")
    public void testMultithreadingTraits(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test;\n" +
                          "import org.drools.base.factmodel.traits.TraitTest.Item;\n" +
                          "declare Item end\n" +
                          "declare trait ItemStyle\n" +
                          "    id: String\n" +
                          "    adjustable: boolean\n" +
                          "end\n" +
                          "rule \"Don ItemStyle\"\n" +
                          "    no-loop true\n" +
                          "    when\n" +
                          "        $p : Item ()\n" +
                          "        not ItemStyle (id == $p.id)\n" +
                          "    then\n" +
                          "        don($p, ItemStyle.class);\n" +
                          "end\n" +
                          "rule \"Item Style - Adjustable\"" +
                          "    no-loop true" +
                          "    when" +
                          "        $style : ItemStyle (!adjustable)" +
                          "        Item (" +
                          "            id == $style.id " +
                          "        )" +
                          "    then" +
                          "        modify($style) {" +
                          "            setAdjustable(true)" +
                          "        };" +
                          "end";
        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);

        // might need to tweak these numbers.  often works with 7-10,100,60, but often fails 15-20,100,60
        int MAX_THREADS = 20;
        int MAX_REPETITIONS = 100;
        int MAX_WAIT_SECONDS = 60;

        final ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        try {
            for (int threadIndex = 0; threadIndex < MAX_THREADS; threadIndex++) {
                executorService.execute(new TraitRulesThread(threadIndex, MAX_REPETITIONS, kbase.newKieSession()));
            }
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
            final List<Runnable> queuedTasks = executorService.shutdownNow();

            assertThat(queuedTasks).isEmpty();
            assertThat(executorService.isTerminated()).isEqualTo(true);
        }
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testShedOneLastTrait(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test;\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "global java.util.List list;\n" +
                          "" +
                          "declare Core @Traitable end\n" +
                          "" +
                          "declare trait Mask\n" +
                          "end\n" +
                          "" +
                          "rule \"Don ItemStyle\"\n" +
                          "    when\n" +
                          "    then\n" +
                          "        don(new Core(), Mask.class);\n" +
                          "end\n" +
                          "" +
                          "rule \"React\" \n" +
                          "    when \n" +
                          "     $s : String() \n" +
                          "        $m : Mask() \n" +
                          "then \n" +
                          "     delete($s); \n" +
                          "     shed($m, Mask.class); \n" +
                          "end\n" +
                          "" +
                          "rule Log \n" +
                          "when \n" +
                          " $t : Thing() \n" +
                          "then \n" +
                          " list.add($t.getClass().getName()); \n" +
                          "end \n";

        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);
        List<String> list = new ArrayList<>();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy");

        knowledgeSession.insert("shed");
        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(2);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy", "org.drools.base.factmodel.traits.Thing.test.Core_Proxy");
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testShedThingCompletelyThenDonAgain(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test;\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "global java.util.List list;\n" +
                          "" +
                          "declare Core @Traitable end\n" +
                          "" +
                          "declare trait Mask end\n" +
                          "declare trait Mask2 end\n" +
                          "" +
                          "rule \"Don ItemStyle\"\n" +
                          "    when\n" +
                          "     $s : String(this == \"don1\") \n" +
                          "    then\n" +
                          "     delete($s); \n" +
                          "        don(new Core(), Mask.class);\n" +
                          "end\n" +
                          "" +
                          "rule \"Clear\" \n" +
                          "    when \n" +
                          "     $s : String(this == \"shed1\") \n" +
                          "        $m : Mask() \n" +
                          "then \n" +
                          "     delete($s); \n" +
                          "     shed($m, Thing.class); \n" +
                          "end\n" +
                          "" +
                          "rule \"Add\" \n" +
                          "    when \n" +
                          "     $s : String(this == \"don2\") \n" +
                          "        $c : Core() \n" +
                          "then \n" +
                          "     delete($s); \n" +
                          "     don($c, Mask2.class); \n" +
                          "end\n" +
                          "" +
                          "rule \"Clear Again\" \n" +
                          "    when \n" +
                          "     $s : String(this == \"shed2\") \n" +
                          "        $m : Mask2() \n" +
                          "then \n" +
                          "     delete($s); \n" +
                          "     shed($m, Mask2.class); \n" +
                          "end\n" +
                          "" +
                          "" +
                          "rule Log \n" +
                          "when \n" +
                          " $t : Thing() \n" +
                          "then \n" +
                          "  list.add($t.getClass().getName()); \n" +
                          "end \n";

        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);
        List<String> list = new ArrayList<>();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.insert("don1");
        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy");

        knowledgeSession.insert("shed1");
        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy");

        knowledgeSession.insert("don2");
        knowledgeSession.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list).hasSize(2);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy", "test.Mask2.test.Core_Proxy");

        knowledgeSession.insert("shed2");
        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(3);
        assertThat(list).containsExactly("test.Mask.test.Core_Proxy", "test.Mask2.test.Core_Proxy", "org.drools.base.factmodel.traits.Thing.test.Core_Proxy");

    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitImplicitInsertionExceptionOnNonTraitable(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test;\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "global java.util.List list;\n" +
                          "" +
                          "declare Core id : String  end\n" +  // should be @Traitable
                          "" +
                          "declare trait Mask  id : String end\n" +
                          "" +
                          "rule \"Don ItemStyle\"\n" +
                          "    when\n" +
                          "    then\n" +
                          "        don(new Core(), Mask.class);\n" +
                          "end\n" +
                          "" +
                          "";

        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);
        ArrayList list = new ArrayList();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        try {
            knowledgeSession.fireAllRules();
            fail("Core is not declared @Traitable, this test should have thrown an exception");
        } catch (Exception csq) {
            assertThat(csq.getCause()).isInstanceOf(IllegalStateException.class);
        }

    }


    @Trait
    public static interface SomeTrait<K> extends Thing<K> {
        public String getFoo();
        public void setFoo(String foo);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTraitLegacyTraitableWithLegacyTrait(VirtualPropertyMode mode) {
        final String s1 = "package org.drools.compiler.factmodel.traits;\n" +
                          "import " + TraitTest.class.getName() + ".SomeTrait; \n" +
                          "import " + StudentImpl.class.getCanonicalName() + ";\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "import org.drools.traits.core.factmodel.*; \n" +
                          "global java.util.List list;\n" +
                          "" +
                          "rule \"Don ItemStyle\"\n" +
                          "    when\n" +
                          "    then\n" +
                          "        don(new StudentImpl(), SomeTrait.class);\n" +
                          "end\n";

        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);
        ArrayList list = new ArrayList();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();

        assertThat(knowledgeSession.getObjects()).hasSize(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsALegacyTrait(VirtualPropertyMode mode) {
        final String s1 = "package org.drools.traits.compiler.factmodel.traits;\n" +
                          "import " + TraitTest.class.getName() + ".SomeTrait; \n" +
                          "import " + StudentImpl.class.getCanonicalName() + ";\n" +
                          "import " + Entity.class.getCanonicalName() + ";\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "global java.util.List list;\n" +
                          "" +
                          "declare trait IStudent end \n" +
                          "" +
                          "rule \"Don ItemStyle\"\n" +
                          "    when\n" +
                          "    then\n" +
                          "        insert(new StudentImpl());\n" +
                          "        don(new Entity(), IStudent.class);\n" +
                          "end\n" +
                          "" +
                          "rule Check " +
                          " when " +
                          "  $s : StudentImpl() " +
                          "  $e : Entity(this isA $s) " +
                          " then " +
                          "  list.add(1); " +
                          " end ";

        KieBase kbase = getKieBaseFromString(s1);
        TraitFactoryImpl.setMode(mode, kbase);
        List<Integer> list = new ArrayList<>();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();

        assertThat(list).containsExactly(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Disabled("Check ReviseTraitTestWithPRAlwaysCategory.java file")
    public void testClassLiteralsWithOr(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; " +
                     "import org.drools.base.factmodel.traits.*; " +
                     "global java.util.List list; " +

                     "declare Foo " +
                     "@Traitable " +
                     "end " +

                     "declare trait A end " +
                     "declare trait B end " +

                     "rule Init " +
                     "when " +
                     "then " +
                     "  Foo f = new Foo(); " +
                     "  insert(f); " +
                     "end " +

                     "rule One " +
                     "when " +
                     "  $f : Foo(this not isA A) " +
                     "then " +
                     "  don($f, A.class); " +
                     "end " +

                     "rule Two " +
                     "when " +
                     "  $f : Foo(this not isA B) " +
                     "then " +
                     "  don($f, B.class); " +
                     "end " +

                     "rule Check " +
                     "when " +
                     "    $f : Foo(this isA B || this isA A) " +
                     "then " +
                     "  list.add(1); " +
                     "end " +

                     "";


        KieBase kbase = new KieHelper(PropertySpecificOption.ALLOWED).addContent(drl, ResourceType.DRL).build();
        TraitFactoryImpl.setMode(mode, kbase);
        List<Integer> list = new ArrayList<>();

        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list).containsExactly(1);

    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsASwappedArg(VirtualPropertyMode mode) {

        String drl = "package org.drools.traits.compiler.factmodel.traits;\n" +
                    "import " + TraitTest.class.getName() + ".SomeTrait; \n" +
                    "import " + StudentImpl.class.getCanonicalName() + ";\n" +
                    "import " + Entity.class.getCanonicalName() + ";\n" +
                    "import org.drools.base.factmodel.traits.*; \n" +
                    "global java.util.List list; " +

                     "declare Foo " +
                     "@Traitable " +
                     "  object : Object " +
                     "end " +

                     "declare Bar " +
                     "@Traitable " +
                     "end " +

                     "declare trait IStudent end " +

                     "rule Init " +
                     "when " +
                     "then " +
                     "  Foo f = new Foo(new StudentImpl()); " +
                     "  don(f, IStudent.class); " +
                     "end " +

                     "rule Match1 " +
                     "when " +
                     "  $f : Foo($x : object) " +
                     "  $p : StudentImpl(this isA $f) from $x " +
                     "then " +
                     "  list.add(1); " +
                     "end " +

                     "rule Match2 " +
                     "when " +
                     "  $f : Foo($x : object) " +
                     "  $p : StudentImpl($f isA this) from $x " +
                     "then " +
                     "  list.add(2); " +
                     "end " +

                     "";


        KieBase kbase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kbase);
        List<Integer> list = new ArrayList<>();

        KieSession ksession = kbase.newKieSession();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list).hasSize(2);
        assertThat(list).contains(1, 2);

    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testHierarchyEncodeOnPackageMerge(VirtualPropertyMode mode) {

        String drl0 = "package org.drools.test; " +
                      "declare trait X end ";

        String drl1 = "package org.drools.test; " +
                     "import org.drools.base.factmodel.traits.*; " +
                     "global java.util.List list; " +

                     "declare trait A end " +
                     "declare trait B extends A end " +
                     "declare trait C extends B end " +

                     "";

        InternalKnowledgeBase knowledgeBase = KnowledgeBaseFactory.newKnowledgeBase();
        TraitFactoryImpl.setMode(mode, (InternalRuleBase) knowledgeBase);

        KnowledgeBuilder kb = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kb.add(new ByteArrayResource(drl0.getBytes()), ResourceType.DRL);
        assertThat(kb.hasErrors()).isFalse();

        knowledgeBase.addPackages(kb.getKnowledgePackages());

        KnowledgeBuilder kb2 = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kb2.add(new ByteArrayResource(drl1.getBytes()), ResourceType.DRL);
        LOGGER.debug(kb2.getErrors().toString());
        assertThat(kb2.hasErrors()).isFalse();

        knowledgeBase.addPackages(kb2.getKnowledgePackages());

        HierarchyEncoder<String> hier = ((TraitRuntimeComponentFactory) RuntimeComponentFactory.get()).getTraitRegistry(knowledgeBase).getHierarchy();
        BitSet b = (BitSet) hier.getCode("org.drools.test.B").clone();
        BitSet c = (BitSet) hier.getCode("org.drools.test.C").clone();

        c.and(b);
        assertThat(c).isEqualTo(b);

    }




    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Disabled
    public void testDonThenReinsert(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test;\n" +
                          "import org.drools.base.factmodel.traits.*; \n" +
                          "import org.drools.traits.compiler.factmodel.traits.TraitTest.TBean;\n" +
                          "global java.util.List list;\n" +
                          "" +
                          "declare TBean " +
                          " @Traitable " +
                          " @propertyReactive " +
                          "end " +
                          "" +
                          "declare trait Mask " +
                          " @propertyReactive " +
                          "end " +
                          "" +
                          "rule 'Don ItemStyle' " +
                          "    when\n" +
                          "     $e : TBean() " +
                          "    then " +
                          "        don($e, Mask.class);\n" +
                          "end\n" +
                          "" +
                          "rule \"React\" \n" +
                          "    when \n" +
                          "        $m : Mask() \n" +
                          "then \n" +
                          "end\n" +
                          "" +
                          "rule Zero when not Object() then end ";

        KieBase kbase = getKieBaseFromString(s1, EqualityBehaviorOption.IDENTITY);

        TraitFactoryImpl.setMode(mode, kbase);
        ArrayList list = new ArrayList();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);
        TBean e = new TBean("aaa");

        int n = knowledgeSession.fireAllRules();
        assertThat(n).isEqualTo(1);

        knowledgeSession.insert(e);
        n = knowledgeSession.fireAllRules();
        assertThat(n).isEqualTo(2);

        knowledgeSession.insert(e);
        n = knowledgeSession.fireAllRules();
        assertThat(n).isEqualTo(0);

        knowledgeSession.delete(knowledgeSession.getFactHandle(e));
        n = knowledgeSession.fireAllRules();
        assertThat(n).isEqualTo(1);

        assertThat(knowledgeSession.getObjects()).isEmpty();

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCastOnTheFly(VirtualPropertyMode mode) throws InterruptedException {
        final String s1 = "package test; " +

                          "import org.drools.base.factmodel.traits.*; " +

                          "global java.util.List list; " +

                          "declare Foo " +
                          " @Traitable " +
                          " @propertyReactive " +
                          " id : int " +
                          "end " +

                          "declare trait Upper " +
                          " @propertyReactive " +
                          " id : int " +
                          "end " +

                          "declare trait Lower extends Upper " +
                          " @propertyReactive " +
                          "end " +

                          "rule Init " +
                          " dialect 'mvel' " +
                          "    when " +
                          "    then " +
                          "     Foo o = insert(new Foo(42)).as(Foo.class); " +
                          "     list.add(o.getId()); " +
                          "end " +

                          "rule Don " +
                          " when " +
                          "     $f : Foo() " +
                          " then " +
                          "     Lower l = don($f, Lower.class); " +
                          "     Upper u = bolster($f).as(Upper.class); " +
                          "     list.add(u.getId() + 1); " +
                          " end ";

        KieBase kbase = getKieBaseFromString(s1);

        TraitFactoryImpl.setMode(mode, kbase);
        List<Integer> list = new ArrayList<>();

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();

        assertThat(list).containsExactly(42, 43);
    }



    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDonModify(VirtualPropertyMode mode) {
        String drl =
                "import org.drools.traits.core.factmodel.Entity;\n" +
                "import org.drools.traits.compiler.factmodel.traits.IPerson;\n" +
                "import org.drools.compiler.factmodel.traits.IStudent;\n" +

                "declare trait IPerson end\n" +

                "declare trait IStudent end\n" +

                "declare trait Person\n" +
                "    name : String\n" +
                "end\n" +

                "rule \"Init\"\n" +
                "when\n" +
                "then\n" +
                "    Entity core = new Entity();\n" +
                "    insert(core);\n" +
                "end\n" +

                "rule Trait when\n" +
                "    $core: Entity()\n" +
                "then\n" +
                "    IPerson x = don($core, IPerson.class, true);\n" +
                "    IStudent s = don($core, IStudent.class, true);\n" +
                "    Person p = don($core, Person.class, true);\n" +
                "end\n" +

                "rule R2 when\n" +
                "    $p: IPerson(name == null)\n" +
                "then\n" +
                "end\n";

        KieBase kbase = getKieBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kbase);
        KieSession kSession = kbase.newKieSession();

        assertThat(kSession.fireAllRules()).isEqualTo(3);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testAlphaNodeSharing(VirtualPropertyMode mode) {
        String drl =
                "package test; " +
                "import " + Entity.class.getName() + " " +

                "declare trait Person\n" +
                "    name : String\n" +
                "end\n" +

                "rule Init " +
                "when " +
                "then " +
                "    don(new Entity(), Person.class); " +
                "end\n" +

                "rule One when" +
                "    $core: Entity(this isA Person) " +
                "then " +
                "end " +

                "rule Two when" +
                "    $core: Entity(this isA Person) " +
                "then " +
                "end " +

                "\n";

        final KieBase kbase = getKieBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kbase);
        KieSession kSession = kbase.newKieSession();

        assertThat(kSession.fireAllRules()).isEqualTo(3);
        NamedEntryPoint nep = ((NamedEntryPoint) kSession.getEntryPoint(EntryPointId.DEFAULT.getEntryPointId()));
        ObjectTypeNode otn = nep.getEntryPointNode().getObjectTypeNodes().get(new ClassObjectType(Entity.class));
        assertThat(otn).isNotNull();
        assertThat(otn.getObjectSinkPropagator().getSinks().length).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPartitionWithSiblingsOnDelete(VirtualPropertyMode mode) {
        String drl =
                "import " + Entity.class.getName() + ";" +
                "global java.util.List list; " +

                "declare trait A @propertyReactive end " +
                "declare trait B extends A @propertyReactive end " +
                "declare trait C extends A @propertyReactive end " +

                "rule Trait when " +
                "    $core: Entity() " +
                "then " +
                "    don($core, A.class); " +
                "    don($core, B.class); " +
                "    don($core, C.class); " +
                "end " +

                "rule Shed when " +
                "   $s: String() " +
                "   $core : Entity() " +
                "then " +
                "   shed($core, C.class); " +
                "end " +

                "rule RA when A() then list.add('A'); end " +
                "rule RB when B() then list.add('B'); end " +
                "rule RC when C() then list.add('C'); end " +
                " ";

        KieBase kbase = new KieHelper().addContent(drl, ResourceType.DRL).build();
        TraitFactoryImpl.setMode(mode, kbase);
        KieSession ksession = kbase.newKieSession();

        List<Character> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Entity e = new Entity();
        ksession.insert(e);
        ksession.fireAllRules();

        assertThat(list).containsExactly('A', 'B', 'C');

        ksession.insert("go");
        ksession.fireAllRules();

        Set<BitSet> s = checkOTNPartitioning(e, ksession);
        assertThat(s).hasSize(2);

        assertThat(list).containsExactly('A', 'B', 'C');
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTupleIntegrityOnModification(VirtualPropertyMode mode) {
        String drl = "package test " +
                     "import " + Entity.class.getName() + ";" +
                     "global java.util.List list; " +

                     "declare trait A @propertyReactive value : int end " +

                     "rule Trait when " +
                     "    $core: Entity() " +
                     "then " +
                     "    A o = don($core, A.class); " +
                     "end " +

                     "rule Test when " +
                     "   $x: A(value == 0) " +
                     "then " +
                     "   list.add(0); " +
                     "end " +

                     "rule Check when " +
                     "   $x: A(value == 42) " +
                     "then " +
                     "   list.add(42); " +
                     "end " +

                     "rule Mood when " +
                     "  $x : A(value != 42) " +
                     "then " +
                     "  modify ($x) { setValue(42); } " +
                     "end ";

        KieBase kbase = getKieBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kbase);
        KieSession ksession = kbase.newKieSession();
        List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.insert(new Entity());
        ksession.fireAllRules();

        for (final Object o : ksession.getObjects(object -> object.getClass().getName().contains("test.A"))) {
            InternalFactHandle handle = (InternalFactHandle) ksession.getFactHandle(o);
            TupleImpl          first  = handle.getFirstLeftTuple();
            assertThat(first).isInstanceOf(RuleTerminalNodeLeftTuple.class);
            assertThat(((RuleTerminalNodeLeftTuple) first).getRule().getName()).isEqualTo("Check");
        }

        assertThat(list).containsExactly(0, 42);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testShedVacancy(VirtualPropertyMode mode) {
        String drl = "package org.drools.test " +
                     "import " + Entity.class.getName() + ";" +
                     "global java.util.List list; " +

                     "declare trait A @propertyReactive end " +
                     "declare trait B @propertyReactive end " +
                     "declare trait C extends A,B @propertyReactive end " +
                     "declare trait D extends B @propertyReactive end " +

                     "rule Trait when " +
                     "then " +
                     "    Entity e = new Entity('x1'); " +
                     "    don(e, C.class); " +
                     "    don(e, D.class); " +
                     "end " +

                     "rule Mood when " +
                     "  $x : B() " +
                     "then " +
                     "end " +

                     "rule Shed when " +
                     "  $s : String() " +
                     "  $x : Entity() " +
                     "then " +
                     "  delete($s); " +
                     "  shed($x, A.class); " +
                     "end " +
                     "";

        KieBase kbase = getKieBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kbase);
        KieSession ksession = kbase.newKieSession();
        List list = new ArrayList();
        ksession.setGlobal("list", list);

        HierarchyEncoder<String> hier = ((TraitRuntimeComponentFactory) RuntimeComponentFactory.get()).getTraitRegistry(((InternalKnowledgeBase) kbase)).getHierarchy();
        BitSet a = (BitSet) hier.getCode("org.drools.test.A").clone();
        BitSet b = (BitSet) hier.getCode("org.drools.test.B").clone();
        BitSet c = (BitSet) hier.getCode("org.drools.test.C").clone();
        BitSet d = (BitSet) hier.getCode("org.drools.test.D").clone();

        int n = ksession.fireAllRules();
        assertThat(n).isEqualTo(2);

        LOGGER.debug("---------------------------------------------------------------\n\n\n ");

        int counter = 0;
        for (Object o : ksession.getObjects()) {
            if (o instanceof TraitProxyImpl) {
                TraitProxyImpl tp = (TraitProxyImpl) o;
                if (tp._getTypeCode().equals(c)) {
                    assertThat(tp.listAssignedOtnTypeCodes()).hasSize(1);
                    assertThat(tp.listAssignedOtnTypeCodes()).contains(b);
                    counter++;
                } else if (tp._getTypeCode().equals(d)) {
                    assertThat(tp.listAssignedOtnTypeCodes()).isEmpty();
                    counter++;
                }
            } else if (o instanceof TraitableBean) {
                TraitableBean tb = (TraitableBean) o;
                LOGGER.debug(tb.getCurrentTypeCode().toString());
                counter++;
            }
        }
        assertThat(counter).isEqualTo(3);


        ksession.insert("go");
        ksession.fireAllRules();

        LOGGER.debug("---------------------------------------------------------------\n\n\n ");

        int counter2 = 0;
        for (Object o : ksession.getObjects()) {
            if (o instanceof TraitProxyImpl) {
                TraitProxyImpl tp = (TraitProxyImpl) o;
                assertThat(tp._getTypeCode()).isEqualTo(d);
                assertThat(tp.listAssignedOtnTypeCodes()).hasSize(1);
                assertThat(tp.listAssignedOtnTypeCodes()).contains(b);
                counter2++;
            } else if (o instanceof TraitableBean) {
                TraitableBean tb = (TraitableBean) o;
                assertThat(tb.getCurrentTypeCode()).isEqualTo(d);
                counter2++;
            }
        }
        assertThat(counter2).isEqualTo(2);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testExternalUpdateWithProxyRefreshInEqualityMode(VirtualPropertyMode mode) {
        String drl = "package org.drools.trait.test; " +
                     "import " + ExtEntity.class.getCanonicalName() + "; " +
                     "global " + List.class.getName() + " list; " +

                     "declare trait Mask " +
                     "  id  : String " +
                     "  num : int " +
                     "end " +

                     "rule Don when " +
                     "  $x : ExtEntity($id : id) " +
                     "then " +
                     "  list.add($id); " +
                     "  don($x, Mask.class); " +
                     "end " +

                     "rule Test when " +
                     "  Mask($n : num) " +
                     "then " +
                     "  list.add($n); " +
                     "end ";

        KieBase kbase = getKieBaseFromString(drl, EqualityBehaviorOption.EQUALITY);
        TraitFactoryImpl.setMode(mode, kbase);

        KieSession ksession = kbase.newKieSession();
        List<Object> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        FactHandle handle = ksession.insert(new ExtEntity("x1", 42));
        ksession.fireAllRules();

        ksession.update(handle, new ExtEntity("x1", 35));
        ksession.fireAllRules();

        assertThat(list).containsExactly("x1", 42, "x1", 42);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAInstanceOf(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; " +
                     "import " + StudentImpl.class.getName() + "; " +
                     "import " + IStudent.class.getName() + "; " +
                     "global java.util.List list; " +

                     "rule Test1 " +
                     "when " +
                     "  StudentImpl(this isA IStudent.class) " +
                     "then list.add(1); end " +

                     "rule Test2 " +
                     "when " +
                     "  IStudent(this isA StudentImpl.class) " +
                     "then list.add(2); end " +

                     "";

        KieBase kbase = getKieBaseFromString(drl);
        List<Integer> list = new ArrayList<>();
        TraitFactoryImpl.setMode(mode, kbase);

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);
        knowledgeSession.insert(new StudentImpl());

        assertThat(knowledgeSession.fireAllRules()).isEqualTo(2);
        assertThat(list).containsExactly(1, 2);
    }


    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIsAInstanceOfNonTraitable(VirtualPropertyMode mode) {

        String drl = "package org.drools.test; " +
                     "global java.util.List list; " +

                     "rule Test1 " +
                     "when " +
                     "  Object(this isA String.class) " +
                     "then list.add(1); end " +

                     "";

        KieBase kbase = getKieBaseFromString(drl);
        List<Integer> list = new ArrayList<>();
        TraitFactoryImpl.setMode(mode, kbase);

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);
        knowledgeSession.insert("hello");

        assertThat(knowledgeSession.fireAllRules()).isEqualTo(1);
        assertThat(list).containsExactly(1);
    }



    @Traitable
    @PropertyReactive
    public static class ExtEntity extends Entity {
        private int num;

        public int getNum() {
            return num;
        }

        public void setNum(int num) {
            this.num = num;
        }

        public ExtEntity(String id, int num) {
            super(id);
            this.num = num;
        }
    }

    protected Set<BitSet> checkOTNPartitioning(TraitableBean core, KieSession wm) {
        Set<BitSet> otns = new HashSet<BitSet>();

        for (Object o : core._getTraitMap().values()) {
            TraitProxyImpl tp = (TraitProxyImpl) o;
            Set<BitSet> localNodes = tp.listAssignedOtnTypeCodes();

            for (BitSet code : localNodes) {
                assertThat(otns).doesNotContain(code);
                otns.add(code);
            }
        }

        return otns;
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSerializeKieBaseWithTraits(VirtualPropertyMode mode) {
        // DRL-1123
        String drl = "package org.drools.test; " +
                     "import " + StudentImpl.class.getName() + "; " +
                     "import " + IStudent.class.getName() + "; " +
                     "global java.util.List list; " +

                     "rule Test1 " +
                     "when " +
                     "  StudentImpl(this isA IStudent.class) " +
                     "then list.add(1); end " +

                     "rule Test2 " +
                     "when " +
                     "  IStudent(this isA StudentImpl.class) " +
                     "then list.add(2); end " +

                     "";

        KieBase kbase = getKieBaseFromString(drl);

        List<Integer> list = new ArrayList<>();
        TraitFactoryImpl.setMode(mode, kbase);

        KieSession knowledgeSession = kbase.newKieSession();
        knowledgeSession.setGlobal("list", list);
        knowledgeSession.insert(new StudentImpl());

        assertThat(knowledgeSession.fireAllRules()).isEqualTo(2);
        assertThat(list).containsExactly(1, 2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMixin2(VirtualPropertyMode mode) {
        String drl =
                 "package org.drools.test.traits\n" +
                 "import " + Scholar.class.getCanonicalName() + ";\n" +
                 "import " + ScholarImpl.class.getCanonicalName() + ";\n" +
                 "\n" +
                 "\n" +
                 "declare Person\n" +
                 "    @Traitable\n" +
                 "    name    : String       = \"john\"     @key\n" +
                 "    age     : int          = 18\n" +
                 "    weight  : Double       = 75.4\n" +
                 "end\n" +
                 "\n" +
                 "declare Scholar end\n" +
                 "\n" +
                 "declare trait Student extends Scholar\n" +
                 "    name    : String\n" +
                 "    age     : int\n" +
                 "    weight  : Double\n" +
                 "    school  : String\n" +
                 "end\n" +
                 "\n" +
                 "\n" +
                 "rule \"Zero\"\n" +
                 "when\n" +
                 "then\n" +
                 "    insert(new Person());\n" +
                 "end\n" +
                 "\n" +
                 "\n" +
                 "rule \"Student\"\n" +
                 "no-loop\n" +
                 "when\n" +
                 "    $p : Person($name : name, $age : age < 25, $weight : weight)\n" +
                 "then\n" +
                 "    Student s = don($p, Student.class);\n" +
                 "        s.setSchool(\"SomeSchool\");\n" +
                 "        s.learn(\" AI \");\n" +
                 "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        ks.fireAllRules();
    }

    @Trait(impl = ScholarImpl.class)
    public interface Scholar<K>  {
        void learn(String subject);
    }

    public static class ScholarImpl<K> implements Scholar<K> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ScholarImpl.class);

        private Thing<K> core;

        public ScholarImpl() { }

        public ScholarImpl(Thing<K> arg) {
            this.core = arg;
        }

        public void learn(String subject) {
            LOGGER.debug("I " + core.getFields().get("name") + ", now know everything about " + subject);
        }
    }

    @Trait(impl = YImpl.class)
    public interface Y {
        String getShared();
        String getYValue();
    }

    public static class YImpl implements Y {

        @Override
        public String getShared() {
            return "Y";
        }

        @Override
        public String getYValue() {
            return "Y";
        }
    }

    @Trait(impl = ZImpl.class)
    public interface Z {
        String getShared();
        String getZValue();
    }

    public static class ZImpl implements Z {

        @Override
        public String getShared() {
            return "Z";
        }

        @Override
        public String getZValue() {
            return "Z";
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMixinWithConflictsUsingDeclarationOrder(VirtualPropertyMode mode) {
        checkMixinResolutionUsesOrder("Y,Z", "Y", mode);
        checkMixinResolutionUsesOrder("Z,Y", "Z", mode);
    }

    private void checkMixinResolutionUsesOrder(String interfaces, String first, VirtualPropertyMode mode) {
        String drl =
                "package org.drools.test.traits\n" +
                "import " + Y.class.getCanonicalName() + ";\n" +
                "import " + Z.class.getCanonicalName() + ";\n" +
                "\n" +
                "global java.util.List list;" +
                "\n" +
                "declare Bean\n" +
                "    @Traitable\n" +
                "    name    : String       = \"xxx\"     @key\n" +
                "end\n" +
                "\n" +
                "\n" +
                "declare X extends " + interfaces + " @Trait(mixinSolveConflicts = Trait.MixinConflictResolutionStrategy.DECLARATION_ORDER) end\n" +
                "\n" +
                "rule Init when\n" +
                "then\n" +
                "    insert(new Bean());\n" +
                "end\n" +
                "\n" +
                "rule Exec no-loop when\n" +
                "    $b : Bean()\n" +
                "then\n" +
                "    X x = don($b, X.class);\n" +
                "    list.add(x.getYValue());\n" +
                "    list.add(x.getZValue());\n" +
                "    list.add(x.getShared());\n" +
                "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<String>();
        ks.setGlobal("list", list);

        ks.fireAllRules();

        LOGGER.debug(list.toString());
        assertThat(list.get(0)).isEqualTo("Y");
        assertThat(list.get(1)).isEqualTo("Z");
        assertThat(list.get(2)).isEqualTo(first);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMixinWithConflictsThrowingError(VirtualPropertyMode mode) {
        String drl =
                "package org.drools.test.traits\n" +
                "import " + Y.class.getCanonicalName() + ";\n" +
                "import " + Z.class.getCanonicalName() + ";\n" +
                "\n" +
                "global java.util.List list;" +
                "\n" +
                "declare Bean\n" +
                "    @Traitable\n" +
                "    name    : String       = \"xxx\"     @key\n" +
                "end\n" +
                "\n" +
                "\n" +
                "declare X extends Y,Z @Trait(mixinSolveConflicts = Trait.MixinConflictResolutionStrategy.ERROR_ON_CONFLICT) end\n" +
                "\n" +
                "rule Init when\n" +
                "then\n" +
                "    insert(new Bean());\n" +
                "end\n" +
                "\n" +
                "rule Exec no-loop when\n" +
                "    $b : Bean()\n" +
                "then\n" +
                "    X x = don($b, X.class);\n" +
                "    list.add(x.getYValue());\n" +
                "    list.add(x.getZValue());\n" +
                "    list.add(x.getShared());\n" +
                "end\n";

        KieSession ks = getSessionFromString(drl);
        TraitFactoryImpl.setMode(mode, ks.getKieBase());

        List<String> list = new ArrayList<String>();
        ks.setGlobal("list", list);

        assertThatException().as("don should fail due to the conflict in getShared() method").isThrownBy(()  -> ks.fireAllRules());
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPreserveAllSetBitMask(VirtualPropertyMode mode) {
        // DROOLS-1699
        String drl =
                "package t.x;\n" +
                "" +
                "import " + Entity.class.getName() + "; " +
                "" +
                "declare trait MyThing end\n" +
                "" +
                "declare trait RootThing extends MyThing " +
                " objProp : java.util.List = new java.util.ArrayList() " +
                "end " +
                "" +
                "declare trait F extends RootThing end\n" +
                "" +
                "declare trait D extends RootThing end\n" +
                "" +
                "declare trait E extends D end\n" +
                "" +
                "rule Init when\n" +
                "then " +
                " Entity e1 = new Entity(\"X\"); " +
                " insert(e1); " +
                " Entity e2 = new Entity(\"Y\"); " +
                " insert(e2); " +
                " " +
                " D d1 = don(e1, D.class, true); " +
                " F f2 = don(e2, F.class, true); " +
                " " +
                " modify (d1) { getObjProp().add(f2.getCore()); } " +
                " modify (f2.getCore()) {} " +
                "end " +
                "" +
                "rule Rec no-loop when\n" +
                " MyThing($x_0 := core, this isA D.class, $p : this#RootThing.objProp) " +
                " exists MyThing($x_1 := core , core memberOf $p, this isA F.class) " +
                "then " +
                " don($x_0, E.class, true); " +
                "end " +
                "" +
                "rule Shed_2 when\n" +
                " $s : String(this == \"go2\") " +
                " $x : E($objs : objProp) " +
                " $y : F($z : core memberOf $objs) " +
                "then " +
                " retract($s); " +
                " modify ($x) { getObjProp().remove($z); } " +
                " modify ($y) {} " +
                "end ";

        KieHelper helper = new KieHelper();
        KieBase kieBase = helper.addContent(drl, ResourceType.DRL).getKieContainer().getKieBase();
        TraitFactoryImpl.setMode(mode, kieBase);

        KieSession kSession = kieBase.newKieSession();
        kSession.fireAllRules();

        kSession.insert("go2");
        kSession.fireAllRules();

        for (Object o : kSession.getObjects(new ClassObjectFilter(Entity.class))) {
            Entity e = (Entity) o;
            if (e.getId().equals("X")) {
                assertThat(e.hasTrait("t.x.D")).isTrue();
                assertThat(e.hasTrait("t.x.E")).isFalse();
                assertThat(e.hasTrait("t.x.F")).isFalse();
                assertThat(((List) e._getDynamicProperties().get("objProp"))).isEmpty();
            } else if (e.getId().equals("Y")) {
                assertThat(e.hasTrait("t.x.F")).isTrue();
                assertThat(e.hasTrait("t.x.D")).isFalse();
                assertThat(e.hasTrait("t.x.E")).isFalse();
            } else {
                fail("Unrecognized entity in WM");
            }
        }
    }
}
