/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.traits.compiler.factmodel.traits;

import org.drools.core.factmodel.traits.CoreWrapper;
import org.drools.core.factmodel.traits.Trait;
import org.drools.traits.core.factmodel.Entity;
import org.drools.traits.core.factmodel.LogicalTypeInconsistencyException;
import org.drools.core.factmodel.traits.Thing;
import org.drools.traits.core.util.StandaloneTraitFactory;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.traits.compiler.factmodel.traits.TraitTestUtils.createStandaloneTraitFactory;

public class StandaloneTest {

    private StandaloneTraitFactory factory;

    @BeforeEach
    public void init() {
        ProjectClassLoader loader = ProjectClassLoader.createProjectClassLoader();
        factory = createStandaloneTraitFactory();
    }

    @Test
    void testThing() throws LogicalTypeInconsistencyException {
        // Entity is @Traitable and implements TraitableBean natively
        // Thing is a Trait
        // --> just call getProxy
        Entity core = new Entity( "x" );
        Thing thing = factory.don(core, Thing.class);
        assertThat(thing).isNotNull();
    }


    @Test
    void testHierarchy() throws LogicalTypeInconsistencyException {
        Imp imp = new Imp();
        imp.setName("john doe");

        // Imp is not a TraitableBean, so we need to wrap it first
        // IStudent is a Thing, so it will work directly
        CoreWrapper<Imp> core = factory.makeTraitable(imp, Imp.class);
        IStudent student = (IStudent) factory.don(core, IStudent.class);

        System.out.println(student.getName());
        System.out.println(student.getSchool());
        assertThat(student.getName()).isEqualTo("john doe");
        assertThat(student.getSchool()).isNull();

        IPerson p = (IPerson) factory.don(core, IPerson.class);

        student.setName("alan ford");

        System.out.println(p.getName());
        assertThat(p.getName()).isEqualTo("alan ford");
    }


    @Trait
    public static interface IFoo {
        public String getName();
        public void setName( String n );
    }

    @Test
    void testLegacy() throws LogicalTypeInconsistencyException {
        Imp imp = new Imp();
        imp.setName("john doe");

        // Imp is not a TraitableBean, so we need to wrap it first
        // IFoo is not a Thing, but it will be extended internally
        CoreWrapper<Imp> core = factory.makeTraitable(imp, Imp.class);
        IFoo foo = (IFoo) factory.don(core, IFoo.class);

        System.out.println(foo.getName());
        System.out.println(foo instanceof Thing);

        assertThat(foo.getName()).isEqualTo("john doe");
        assertThat(foo instanceof Thing).isTrue();
    }




}
