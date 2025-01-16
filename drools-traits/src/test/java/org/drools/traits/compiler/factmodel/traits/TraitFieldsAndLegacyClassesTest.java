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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.drools.drl.parser.DrlParser;
import org.drools.traits.compiler.CommonTraitTest;
import org.drools.traits.compiler.ReviseTraitTestWithPRAlwaysCategory;
import org.drools.traits.core.factmodel.TraitFactoryImpl;
import org.drools.traits.core.factmodel.VirtualPropertyMode;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.definition.type.FactType;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class TraitFieldsAndLegacyClassesTest extends CommonTraitTest {


    public static Collection<VirtualPropertyMode> modes() {
    	return List.of(VirtualPropertyMode.MAP , VirtualPropertyMode.TRIPLES );
    }



    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate0(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits0;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import java.util.*\n"+
                     "import " + Parent.class.getCanonicalName() + ";\n" +
                     "import " + Child.class.getCanonicalName() + ";\n" +
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : Child\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "end\n" +

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Parent p = new Parent(\"parent\", null);\n"+
                     "   Map map = new HashMap();\n"+
                     "   map.put( \"parent\", ParentTrait.class );\n"+
                     "   insert(p);\n"+
                     "   insert(map);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "   $map : HashMap([parent] != null)\n"+
                     "then\n" +
                     "   Object p = don ( $p , (Class) $map.get(\"parent\") );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();
        
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }




    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate1(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Trait;\n" +
                     "" +
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +    //<<<<<<<
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable( logical = true ) \n" +
                     "@propertyReactive\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable \n" +
                     "@propertyReactive\n" +
                     "   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\",c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "salience -1\n"+
                     "when\n" +
                     "    $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait child\" \n" +
                     "when\n" +
                     "    $c : Child( gender == \"male\" )\n" +
                     "then\n" +
                     "   ChildTrait c = don ( $c , ChildTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" \n" +
                     "when\n" +
                     "    $p : ParentTrait( $c : child ##isA ChildTrait.class )\n" +
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";

        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");

    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate2(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits2;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait \n" +    //><><><><><
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable( logical=true )\n" +   //><><><><><
                     "@propertyReactive\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", null);\n"+    //<<<<<
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait child\" \n" +
                     "when\n" +
                     "   $c : Child( gender == \"male\" )\n" +
                     "then\n" +
                     "   ChildTrait c = don ( $c , ChildTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"assign child to parent\" \n" +          //<<<<<<
                     "when\n" +
                     "   $c : Child( gender == \"male\" )\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "   ParentTrait( child not ##isA ChildTrait.class )\n" +
                     "   ChildTrait()\n"+
                     "then\n" +
                     "   " +
                     "   modify ( $p ) { \n" +
                     "       setChild($c);\n"+
                     "   }\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" \n" +
                     "when\n" +
                     "    $p : ParentTrait( child ##isA ChildTrait.class )\n" +
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        KieSession knowledgeSession = kBase.newKieSession();
        TraitFactoryImpl.setMode(mode, kBase );

        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate3(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits3;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable( logical = true )\n" +
                     "@propertyReactive\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", null);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait child\" \n" +
                     "when\n" +
                     "   $c : Child( gender == \"male\" )\n" +
                     "then\n" +
                     "   ChildTrait c = don ( $c , ChildTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"assign child to parent\" \n" +
                     "when\n" +
                     "   Child( gender == \"male\" )\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "   ParentTrait( child not ##isA ChildTrait.class )\n" +
                     "   $c : ChildTrait()\n"+             //<<<<<
                     "then\n" +
                     "   $p.setChild((Child)$c.getCore());\n"+     //<<<<<
                     "   update($p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" \n" +
                     "when\n" +
                     "    $p : ParentTrait( child ##isA ChildTrait.class )\n" +
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );
        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @Category(ReviseTraitTestWithPRAlwaysCategory.class)
    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate4(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits4;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable(logical=true)\n" +          //<<<<<<   @propertyReactive is removed
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+   //<<<<<
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait child\" \n" +
                     "when\n" +
                     "   $p : Parent( $c := child not ##isA ChildTrait )\n"+
                     "   $c := Child( gender == \"male\" )\n" +
                     "then\n" +
                     "   ChildTrait c = don ( $c , ChildTrait.class );\n" +
                     // this modify is necessary to tell the engine that the Parent's Child has gained a type
                     // if enabled, "logical" mode traits render this unnecessary
                     "   modify ( $p ) {}; \n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and a child trait\" \n" +
                     "when\n" +
                     "    $p : Parent( child ##isA ChildTrait.class ) \n" +    //<<<<<
                     "then\n" +
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = new KieHelper(PropertySpecificOption.ALLOWED).addContent( drl, ResourceType.DRL ).build();
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate5(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits5;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Trait;\n"+

                     "global java.util.List list;\n"+
                     "\n" +
                     "" +
                     ""+
                     "declare trait ParentTrait\n" +
                     "" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n" +
                     "@Trait(logical=true) \n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable(logical=true)\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+


                     "rule \"test parent and child traits\" \n" +
                     "\n" +
                     "when\n" +
                     "    $p : ParentTrait( $c : child ##isA ChildTrait.class ) \n" +     //<<<<<
                     "then\n" +
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate6(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits6;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Trait;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import " + Child.class.getCanonicalName() + ";\n" +       //<<<<<<
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@Trait(logical=true) \n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable(logical=true)\n" +
                     "@propertyReactive\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +               //<<<<<
                     "@Traitable(logical=true)\n" +
                     "@propertyReactive\n" +
                     //"   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n" +
                     "" +
                     "rule \"Side effect\" \n" +
                     "when \n" +
                     "  $p : Parent( child ##isA ChildTrait ) \n" +
                     "then \n" +
                     "   list.add(\"correct2\");\n"+
                     "end \n"+
                     "rule \"test parent and child traits\" \n" +
                     "\n" +
                     "when\n" +
                     "    $p : ParentTrait( child ##isA ChildTrait.class )\n" +
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(2);
        assertThat(list).contains("correct", "correct2");
    }


    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate7(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Trait;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import " + Child.class.getCanonicalName() + ";\n" +
                     "import " + Parent.class.getCanonicalName() + ";\n" +  //<<<<<
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait  @position(1)\n" +      //<<<<<
                     "    age : int = 24 @position(0)\n" +
                     "end\n"+

                     "declare trait ChildTrait\n" +
                     "@Trait( logical = true ) \n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "   gender : String\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable( logical=true ) \n" +
                     "@propertyReactive\n" +
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable( logical=true ) \n" +
                     "@propertyReactive\n" +
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent( \"parent\", c );\n"+
                     "   insert(c); insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+
                     "rule \"test parent and child traits\" \n" +
                     "\n" +
                     "when\n" +
//                     "   $c : Child( $gender := gender )\n"+
                     "   $p : ParentTrait( child ##isA ChildTrait )\n" +    //<<<<<
                     "then\n" +
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);

        knowledgeSession.fireAllRules();

        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }




    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate8(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits8;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import " + Child.class.getCanonicalName() + ";\n" +
                     "import " + Parent.class.getCanonicalName() + ";\n" +  //<<<<<
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : ChildTrait\n" +
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +            //<<<<<
                     "@Traitable(logical=true)\n" +
                     "@propertyReactive\n" +
                     //"   name : String\n"+
                     //"   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable(logical=true)\n" +
                     "@propertyReactive\n" +
                     //"   gender : String = \"male\"\n"+
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" \n" +
                     "\n" +
                     "when\n" +
                     "    $p : ParentTrait( child ##isA ChildTrait.class )\n" +
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate9(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits9;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import " + Child.class.getCanonicalName() + ";\n" +
                     "import " + Parent.class.getCanonicalName() + ";\n" +  //<<<<<
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : Child\n" +      //<<<<<
                     "    age : int = 24\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "end\n"+

                     "declare Parent\n" +            //<<<<<
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait and assign the child\" \n" +
                     "\n" +
                     "when\n" +
                     "   $c : Child( gender == \"male\", this not ##isA ChildTrait )\n" +
                     "   $p : Parent( this ##isA ParentTrait )\n" +
                     "then\n" +
                     "   ChildTrait c =  don ( $c , ChildTrait.class );\n"+   //<<<<<<
                     "   modify($p){\n"+
                     "       setChild((Child)c.getCore());}\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" \n" +
                     "\n" +
                     "when\n" +
                     "    $p : ParentTrait( child ##isA ChildTrait.class, child.gender == \"male\" )\n" +    //<<<<<
                     "then\n" +
                     "   //shed ( $p , ParentTrait.class );\n"+
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitFieldUpdate10(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "import " + Child.class.getCanonicalName() + ";\n" +
                     "import " + Parent.class.getCanonicalName() + ";\n" +  //<<<<<
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : Child  @position(1)\n" +      //<<<<<
                     "    age : int = 24 @position(0)\n" +
                     "end\n"+

                     "declare trait ChildTrait\n"+
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "   gender : String\n"+        //<<<<<
                     "end\n"+

                     "declare Parent\n" +            //<<<<<
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child();\n"+
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait p = don ( $p , ParentTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait and assign the child\" \n" +
                     "\n" +
                     "when\n" +
                     "   $c : Child( gender == \"male\", this not ##isA ChildTrait )\n" +
                     "   $p : Parent( this ##isA ParentTrait )\n" +
                     "then\n" +
                     "   ChildTrait c =  don ( $c , ChildTrait.class );\n"+   //<<<<<<
                     "   modify($p){\n"+
                     "       setChild((Child)c.getCore());}\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test parent and child traits\" salience 10\n" +
                     "\n" +
                     "when\n" +
                     "   $c : Child( $gender := gender)\n"+
                     "   $p : ParentTrait( $age, $c; )\n" +    //<<<<<
                     "then\n" +
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @MethodSource("modes")
    public void testTraitTwoParentOneChild(VirtualPropertyMode mode) {

        String drl = "" +
                     "package org.drools.factmodel.traits;\n" +
                     "\n"+
                     "import org.drools.base.factmodel.traits.Traitable;\n"+
                     "import org.drools.base.factmodel.traits.Thing;\n"+
                     "global java.util.List list;\n"+
                     "\n"+
                     "declare trait ParentTrait\n" +
                     "@propertyReactive\n" +
                     "    child : Child  \n" +
                     "    age : int = 24 \n" +
                     "end\n"+

                     "\n"+
                     "declare trait GrandParentTrait\n" +   //<<<<
                     "@propertyReactive\n" +
                     "    grandChild : Child \n" +
                     "    age : int = 64 \n" +
                     "end\n"+

                     "declare trait FatherTrait extends ParentTrait, GrandParentTrait \n"+ //<<<<<
                     "@propertyReactive\n"+
                     "   name : String = \"child\"\n"+
                     "   gender : String\n"+
                     "end\n"+

                     "declare Parent\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   name : String\n"+
                     "   child : Child\n"+
                     "end\n" +

                     "declare Child\n" +
                     "@Traitable\n" +
                     "@propertyReactive\n" +
                     "   name : String\n" +
                     "   gender : String = \"male\"\n" +
                     "end\n"+
                     "\n"+

                     "rule \"Init\" \n" +
                     "\n" +
                     "when\n" +
                     "    \n" +
                     "then\n" +
                     "   Child c = new Child(\"C1\",\"male\");\n"+
                     "   Child c2 = new Child(\"C2\",\"male\");\n"+        //<<<<
                     "   Parent p = new Parent(\"parent\", c);\n"+
                     "   insert(c);insert(p);\n"+
                     "   insert(c2);\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait as father\" \n" +
                     "salience -1000\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   FatherTrait p = don ( $p , FatherTrait.class );\n"+
                     "end\n"+
                     "\n"+

                     "rule \"trait as parent\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : Parent( name == \"parent\" )\n" +
                     "then\n" +
                     "   ParentTrait c =  don ( $p , ParentTrait.class );\n"+   //<<<<<<
                     "end\n"+
                     "\n"+

                     "rule \"trait and assign the grandchild\" \n" +
                     "\n" +
                     "when\n" +
                     "   $c : Child( name == \"C1\" )\n" +
                     "   $p : Parent( child == $c )\n" +
                     "then\n" +
                     "   GrandParentTrait c =  don ( $p , GrandParentTrait.class );\n"+   //<<<<<<
                     "   modify(c){\n"+
                     "       setGrandChild( $c );}\n"+
                     "end\n"+
                     "\n"+

                     "rule \"test three traits\" \n" +
                     "\n" +
                     "when\n" +
                     "   $p : FatherTrait( this ##isA ParentTrait, this ##isA GrandParentTrait )\n" +    //<<<<<
                     "then\n" +
                     "   list.add(\"correct\");\n"+
                     "end\n"+
                     "\n"+
                     "\n";


        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession knowledgeSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        knowledgeSession.setGlobal("list", list);


        knowledgeSession.fireAllRules();
        assertThat(list).hasSize(1);
        assertThat(list).contains("correct");
    }

    @ParameterizedTest
    @Disabled
    @MethodSource("modes")
    public void testTraitWithPositionArgs(VirtualPropertyMode mode){

        String drl = "" +
                     "package org.drools.traits.test;\n" +
                     "\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "\n" +
                     "global java.util.List list;\n" +
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
                     "    studyingCountry : String @position(1)\n" +
                     "    hasAssistantship : boolean\n" +
                     "end\n" +
                     "\n" +
                     "declare trait Worker\n" +
                     "@propertyReactive\n" +
                     "    pob : String @position(0)\n" +
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
                     "declare trait StudentWorker extends Student, Worker\n" +
                     "@propertyReactive\n" +
                     "    uniName : String\n" +
                     "end\n" +
                     "\n" +
                     "rule \"init\"\n" +
                     "when\n" +
                     "then\n" +
                     "    Person p = new Person(\"1234\",\"IR\",true,true);\n" +
                     "    insert( p );\n" +
                     "    list.add(\"initialized\");\n" +
                     "\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being student\"\n" +
                     "when\n" +
                     "    $p : Person( $ssn : ssn, $pob : pob,  isStudent == true )\n" +
                     "    if($pob == \"IR\" ) do[pobIsIR]\n" +
                     "then\n" +
                     "    Student st = (Student) don( $p , Student.class );\n" +
                     "    modify( st ){\n" +
                     "        setStudyingCountry( \"US\" );\n" +
                     "    }\n" +
                     "    list.add(\"student\");\n" +
                     "then[pobIsIR]\n" +
                     "    don( $p , IRCitizen.class );\n" +
                     "    list.add(\"IR citizen\");\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being US citizen\"\n" +
                     "\n" +
                     "when\n" +
                     "    $s : Student( studyingCountry == \"US\" )\n" +
                     "then\n" +
                     "    don( $s , USCitizen.class );\n" +
                     "    list.add(\"US citizen\");\n" +
                     "end\n" +
                     "\n" +
                     "rule \"check for being worker\"\n" +
                     "\n" +
                     "when\n" +
                     "    $p : Student( hasAssistantship == true, $sc : studyingCountry )\n" +
                     "then\n" +
                     "    Worker wr = (Worker) don( $p , Worker.class );\n" +
                     "    modify( wr ){\n" +
                     "        setWorkingCountry( $sc );\n" +
                     "    }\n" +
                     "    list.add(\"worker\");\n" +
                     "end\n" +
                     "\n" +
                     "rule \"position args 1\"\n" +
                     "when\n" +
                     "    Student( $sc : studyingCountry ) @watch( studyingCountry )\n" +
                     "    $w : Worker( $pob , $sc; )\n" +
                     "    USCitizen( )\n" +
                     "    IRCitizen( $pob := pob )\n" +
                     "then\n" +
                     "    list.add(\"You are working in US as student worker\");\n" +
                     "    StudentWorker sw = (StudentWorker) don( $w, StudentWorker.class );\n" +
                     "    modify(sw){\n" +
                     "        setUniName( \"ASU\" );\n" +
                     "    }\n" +
                     "end\n" +
                     "\n" +
                     "rule \"position args 2\"\n" +
                     "when\n" +
                     "    Student( $sc : studyingCountry ) @watch( studyingCountry )\n" +
                     "    $sw : StudentWorker( $pob , $sc; )\n" +
                     "    IRCitizen( $pob := pob )\n" +
                     "then\n" +
                     "    list.add(\"You are studying and working at ASU\");\n" +
                     "end\n";

        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        KieSession kSession = kBase.newKieSession();
        List<String> list = new ArrayList<>();
        kSession.setGlobal("list", list);

        kSession.fireAllRules();

        assertThat(list).contains("initialized");
        assertThat(list).contains("student");
        assertThat(list).contains("IR citizen");
        assertThat(list).contains("US citizen");
        assertThat(list).contains("worker");
        assertThat(list).contains("You are working in US as student worker");
        assertThat(list).contains("You are studying and working at ASU");
    }


    @ParameterizedTest
    @MethodSource("modes")
    public void singlePositionTraitTest(VirtualPropertyMode mode){


        String drl = "" +
                     "package org.drools.traits.test;\n" +
                     "import org.drools.base.factmodel.traits.Traitable;\n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "\n" +
                     "declare Pos\n" +
                     "@propertyReactive\n" +
                     "@Traitable\n" +
                     "end\n" +
                     "\n" +
                     "declare trait PosTrait\n" +
                     "@propertyReactive\n" +
                     "    field0 : int = 100  //@position(0)\n" +
                     "    field1 : int = 101  //@position(1)\n" +
                     "    field2 : int = 102  //@position(0)\n" +
                     "end\n" +
                     "\n" +
                     "declare trait MultiInhPosTrait extends PosTrait\n" +
                     "@propertyReactive\n" +
                     "    mfield0 : int = 200 //@position(0)\n" +
                     "    mfield1 : int = 201 @position(2)\n" +
                     "end\n" +
                     "\n" +
                     "\n";
        KieBase kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );
        KieSession kSession = kBase.newKieSession();

        FactType parent = kBase.getFactType("org.drools.traits.test", "PosTrait");
        assertThat(parent.getField("field0").getIndex()).isEqualTo(0);
        assertThat(parent.getField("field1").getIndex()).isEqualTo(1);
        assertThat(parent.getField("field2").getIndex()).isEqualTo(2);
        FactType child = kBase.getFactType("org.drools.traits.test", "MultiInhPosTrait");
        assertThat(child.getField("field0").getIndex()).isEqualTo(0);
        assertThat(child.getField("field1").getIndex()).isEqualTo(1);
        assertThat(child.getField("mfield1").getIndex()).isEqualTo(2);
        assertThat(child.getField("field2").getIndex()).isEqualTo(3);
        assertThat(child.getField("mfield0").getIndex()).isEqualTo(4);

        drl = "" +
              "package org.drools.traits.test;\n" +
              "import org.drools.base.factmodel.traits.Traitable;\n" +
              "\n" +
              "global java.util.List list;\n" +
              "\n" +
              "\n" +
              "declare Pos\n" +
              "@propertyReactive\n" +
              "@Traitable\n" +
              "end\n" +
              "\n" +
              "declare trait PosTrait\n" +
              "@propertyReactive\n" +
              "    field0 : int = 100  //@position(0)\n" +
              "    field1 : int = 101  //@position(1)\n" +
              "    field2 : int = 102  @position(1)\n" +
              "end\n" +
              "\n" +
              "declare trait MultiInhPosTrait extends PosTrait\n" +
              "@propertyReactive\n" +
              "    mfield0 : int = 200 @position(0)\n" +
              "    mfield1 : int = 201 @position(2)\n" +
              "end\n" +
              "\n" +
              "\n";
        kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        parent = kBase.getFactType("org.drools.traits.test", "PosTrait");
        assertThat(parent.getField("field0").getIndex()).isEqualTo(0);
        assertThat(parent.getField("field2").getIndex()).isEqualTo(1);
        assertThat(parent.getField("field1").getIndex()).isEqualTo(2);
        child = kBase.getFactType("org.drools.traits.test", "MultiInhPosTrait");
        assertThat(child.getField("mfield0").getIndex()).isEqualTo(0);
        assertThat(child.getField("field2").getIndex()).isEqualTo(1);
        assertThat(child.getField("mfield1").getIndex()).isEqualTo(2);
        assertThat(child.getField("field0").getIndex()).isEqualTo(3);
        assertThat(child.getField("field1").getIndex()).isEqualTo(4);

        drl = "" +
              "package org.drools.traits.test;\n" +
              "import org.drools.base.factmodel.traits.Traitable;\n" +
              "\n" +
              "global java.util.List list;\n" +
              "\n" +
              "\n" +
              "declare Pos\n" +
              "@propertyReactive\n" +
              "@Traitable\n" +
              "end\n" +
              "\n" +
              "declare trait PosTrait\n" +
              "@propertyReactive\n" +
              "    field0 : int = 100  @position(5)\n" +
              "    field1 : int = 101  @position(0)\n" +
              "    field2 : int = 102  @position(1)\n" +
              "end\n" +
              "\n" +
              "declare trait MultiInhPosTrait extends PosTrait\n" +
              "@propertyReactive\n" +
              "    mfield0 : int = 200 @position(0)\n" +
              "    mfield1 : int = 201 @position(1)\n" +
              "end\n" +
              "\n" +
              "\n";
        kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        parent = kBase.getFactType("org.drools.traits.test", "PosTrait");
        assertThat(parent.getField("field1").getIndex()).isEqualTo(0);
        assertThat(parent.getField("field2").getIndex()).isEqualTo(1);
        assertThat(parent.getField("field0").getIndex()).isEqualTo(2);
        child = kBase.getFactType("org.drools.traits.test", "MultiInhPosTrait");
        assertThat(child.getField("field1").getIndex()).isEqualTo(0);
        assertThat(child.getField("mfield0").getIndex()).isEqualTo(1);
        assertThat(child.getField("field2").getIndex()).isEqualTo(2);
        assertThat(child.getField("mfield1").getIndex()).isEqualTo(3);
        assertThat(child.getField("field0").getIndex()).isEqualTo(4);

        drl = "" +
              "package org.drools.traits.test;\n" +
              "import org.drools.base.factmodel.traits.Traitable;\n" +
              "\n" +
              "global java.util.List list;\n" +
              "\n" +
              "\n" +
              "declare Pos\n" +
              "@propertyReactive\n" +
              "@Traitable\n" +
              "end\n" +
              "\n" +
              "declare trait PosTrait\n" +
              "@propertyReactive\n" +
              "    field0 : int = 100  //@position(5)\n" +
              "    field1 : int = 101  //@position(0)\n" +
              "    field2 : int = 102  //@position(1)\n" +
              "end\n" +
              "\n" +
              "declare trait MultiInhPosTrait extends PosTrait\n" +
              "@propertyReactive\n" +
              "    mfield0 : int = 200 //@position(0)\n" +
              "    mfield1 : int = 201 //@position(1)\n" +
              "end\n" +
              "\n" +
              "\n";
        kBase = loadKnowledgeBaseFromString(drl);
        TraitFactoryImpl.setMode(mode, kBase );

        parent = kBase.getFactType("org.drools.traits.test", "PosTrait");
        assertThat(parent.getField("field0").getIndex()).isEqualTo(0);
        assertThat(parent.getField("field1").getIndex()).isEqualTo(1);
        assertThat(parent.getField("field2").getIndex()).isEqualTo(2);
        child = kBase.getFactType("org.drools.traits.test", "MultiInhPosTrait");
        assertThat(child.getField("field0").getIndex()).isEqualTo(0);
        assertThat(child.getField("field1").getIndex()).isEqualTo(1);
        assertThat(child.getField("field2").getIndex()).isEqualTo(2);
        assertThat(child.getField("mfield0").getIndex()).isEqualTo(3);
        assertThat(child.getField("mfield1").getIndex()).isEqualTo(4);

    }




    public static class Parent {
        public String name;
        public Child child;

        public String getName() {
            return name;
        }

        public Child getChild() {
            return child;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setChild(Child child) {
            this.child = child;
        }

        public Parent(String name, Child child){
            this.name = name;
            this. child = child;
        }

        @Override
        public String toString() {
            return "Parent{" +
                   "name='" + name + '\'' +
                   ", child=" + child +
                   '}';
        }
    }


    public static class Child {
        private String gender = "male";

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        @Override
        public String toString() {
            return "Child{" +
                   "gender='" + gender + '\'' +
                   '}';
        }
    }

}
