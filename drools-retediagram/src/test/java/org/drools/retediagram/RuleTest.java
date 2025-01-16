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
package org.drools.retediagram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.drools.core.reteoo.ReteDumper;
import org.drools.mvel.CommonTestMethodBase;
import org.drools.retediagram.ReteDiagram.Layout;
import org.drools.retediagram.model.Measurement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleTest extends CommonTestMethodBase {
	static final Logger LOG = LoggerFactory.getLogger(RuleTest.class);

    @BeforeAll
    public static void init() { // route dependencies using java util Logging to slf4j 
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        java.util.logging.Logger.getLogger("").setLevel(Level.FINEST); 
    }
	
	@Test
	public void test() {
	    KieBase kieBase = new KieHelper()
		        .addFromClassPath("/rules.drl")
		        .build();

	    LOG.info("Creating kieSession");
	    KieSession session = kieBase.newKieSession();
        
        LOG.info("Populating globals");
        Set<String> check = new HashSet<String>();
        session.setGlobal("controlSet", check);
        
        LOG.info("Now running data");
        
        Measurement mRed= new Measurement("color", "red");
        session.insert(mRed);
        session.fireAllRules();
        
        Measurement mGreen= new Measurement("color", "green");
        session.insert(mGreen);
        session.fireAllRules();
        
        Measurement mBlue= new Measurement("color", "blue");
        session.insert(mBlue);
        session.fireAllRules();
        
        LOG.info("Final checks");

        assertThat(session.getObjects().size()).as("Size of object in Working Memory is 3").isEqualTo(3);
        assertThat(check.contains("red")).as("contains red").isTrue();
        assertThat(check.contains("green")).as("contains green").isTrue();
        assertThat(check.contains("blue")).as("contains blue").isTrue();
	    
	    ReteDumper.dumpRete(session);
	    System.out.println("---");
	    ReteDiagram.newInstance()
	            .configLayout(Layout.VLEVEL)
	            // needs: System.setProperty("java.awt.headless", "false"); for: .configOpenFile(true, true)
	            .diagramRete(session.getKieBase());
	}

    @Test
    public void testVeryBasic() {
        String drl =
                "import org.drools.retediagram.model.*;\n" +
                "rule R1\n" +
                "when\n" +
                "  $p : Person( age > 18 )\n" + 
                "then\n" + 
                "  System.out.println(\"Person can drive \"+$p);\n"+
                "end"
                ;
        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                                               .build().newKieSession();
 
        ReteDiagram.newInstance().diagramRete(kieSession);
    }
	
	@Test
    public void testManyAccumulatesWithSubnetworks() {
        String drl = "package org.drools.compiler.tests; \n" +
                     "" +
                     "declare FunctionResult\n" +
                     "    father  : Applied\n" +
                     "end\n" +
                     "\n" +
                     "declare Field\n" +
                     "    applied : Applied\n" +
                     "end\n" +
                     "\n" +
                     "declare Applied\n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "rule \"Seed\"\n" +
                     "when\n" +
                     "then\n" +
                     "    Applied app = new Applied();\n" +
                     "    Field fld = new Field();\n" +
                     "\n" +
                     "    insert( app );\n" +
                     "    insert( fld );\n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "\n" +
                     "\n" +
                     "rule \"complexSubNetworks\"\n" +
                     "when\n" +
                     "    $fld : Field( $app : applied )\n" +
                     "    $a : Applied( this == $app )\n" +
                     "    accumulate (\n" +
                     "        $res : FunctionResult( father == $a ),\n" +
                     "        $args : collectList( $res )\n" +
                     "    )\n" +
                     "    accumulate (\n" +
                     "        $res : FunctionResult( father == $a ),\n" +
                     "        $deps : collectList( $res )\n" +
                     "    )\n" +
                     "    accumulate (\n" +
                     "        $x : String()\n" +
                     "        and\n" +
                     "        not String( this == $x ),\n" +
                     "        $exprFieldList : collectList( $x )\n" +
                     "    )\n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KnowledgeBuilderConfiguration kbConf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        KieBase kbase = loadKnowledgeBaseFromString(kbConf, drl);

        KieSession ksession = kbase.newKieSession();

        int num = ksession.fireAllRules();
        // only one rule should fire, but the partial propagation of the asserted facts should not cause a runtime NPE
        assertThat(num).isEqualTo(1);
        ReteDiagram.newInstance().configLayout(Layout.PARTITION).diagramRete(ksession);
    }


    @Test
    public void testLinkRiaNodesWithSubSubNetworks() {
        String drl = "package org.drools.compiler.tests; \n" +
                     "" +
                     "import java.util.*; \n" +
                     "" +
                     "global List list; \n" +
                     "" +
                     "declare MyNode\n" +
                     "end\n" +
                     "" +
                     "rule Init\n" +
                     "when\n" +
                     "then\n" +
                     "    insert( new MyNode() );\n" +
                     "    insert( new MyNode() );\n" +
                     "end\n" +
                     "" +
                     "" +
                     "rule \"Init tree nodes\"\n" +
                     "salience -10\n" +
                     "when\n" +
                     "    accumulate (\n" +
                     "                 MyNode(),\n" +
                     "                 $x : count( 1 )\n" +
                     "               )\n" +
                     "    accumulate (\n" +
                     "                 $n : MyNode()\n" +
                     "                 and\n" +
                     "                 accumulate (\n" +
                     "                    $val : Double( ) from Arrays.asList( 1.0, 2.0, 3.0 ),\n" +
                     "                    $rc : count( $val );\n" +
                     "                    $rc == 3 \n" +
                     "                 ),\n" +
                     "                 $y : count( $n )\n" +
                     "               )\n" +
                     "then\n" +
                     "  list.add( $x ); \n" +
                     "  list.add( $y ); \n" +
                     "  System.out.println( $x ); \n" +
                     "  System.out.println( $y ); \n" +
                     "end\n";

        KnowledgeBuilderConfiguration kbConf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        KieBase kbase = loadKnowledgeBaseFromString(kbConf, drl);

        KieSession ksession = kbase.newKieSession();
        List<Long> list = new ArrayList<Long>();
        ksession.setGlobal( "list", list );

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).intValue()).isEqualTo(2);
        assertThat(list.get(1).intValue()).isEqualTo(2);
        
        ReteDiagram.newInstance().configLayout(Layout.PARTITION).diagramRete(ksession);
    }
    
    @Test
    public void testMArio() {
        String drl =
                "rule R1y ruleflow-group \"Y\" when\n" +
                "    Integer() \n" +
                "    Number() from accumulate ( Integer( ) and $s : String( ) ; count($s) )\n" +
                "then\n" +
                "    System.out.println(\"R1\");" +
                "end\n" +
                "\n" +
                "rule R1x ruleflow-group \"X\" when\n" +
                "    Integer() \n" +
                "    Number() from accumulate ( Integer( ) and $s : String( ) ; count($s) )\n" +
                "then\n" +
                "    System.out.println(\"R1\");" +
                "end\n" +
                "" +
                "rule R2 ruleflow-group \"X\" when\n" +
                "    $i : Integer()\n" +
                "then\n" +
                "    System.out.println(\"R2\");" +
                "    update($i);" +
                "end\n";
 
        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                                               .build().newKieSession();
 
        ReteDiagram.newInstance().diagramRete(kieSession);
    }
    
    @Test
    public void testMario20161021() {
        String drl = "import java.util.Set;\n" +
                "declare Notification end\n" +
                "declare Fall end\n" +
                "declare NetworkElement end\n" +
                "rule R1 when\n" +
                "        $notification : Notification()\n" +
                "        $epcs : Set() from collect( Fall() )\n" +
                "    then\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "        $notification : Notification()\n" +
                "        not Fall()\n" +
                "        $epcs : Set() from collect(Fall())\n" +
                "\n" +
                "    then\n" +
                "end\n" +
                "\n" +
                "rule R3 when\n" +
                "        $ne : NetworkElement()\n" +
                "    then\n" +
                "end";
        
        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                .build().newKieSession();
        ReteDumper.dumpRete(kieSession);
        ReteDiagram.newInstance().diagramRete(kieSession);
    }
    
    @Test
    public void testTzimani20161021() {
        String drl = "import java.util.Set;\ndeclare Person end\ndeclare FactWithCheese end\ndeclare Cheese end\n"+
                "rule \"R1\"\n" +
                "    when\n" +
                "        $person : Person()\n" +
                "        $aFacts : Set() from collect( FactWithCheese() )\n" +
                "    then\n" +
                "end\n" +
                "\n" +
                "rule \"R2\"\n" +
                "    when\n" +
                "        $person : Person()\n" +
                "        not FactWithCheese()\n" +
                "        $aFacts : Set() from collect( FactWithCheese() )\n" +
                "\n" +
                "    then\n" +
                "end\n" +
                "\n" +
                "rule \"R3\"\n" +
                "    when\n" +
                "        $cheese : Cheese()\n" +
                "    then\n" +
                "end";
        
        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                .build().newKieSession();
        ReteDumper.dumpRete(kieSession);
        ReteDiagram.newInstance().diagramRete(kieSession);
    }
    
    @Test
    public void testDROOLS1360() {
        String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list;\n" +
                //                "rule R1y when\n" +
                //                "    AtomicInteger() \n" +
                //                "    Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )" +
                //                "    eval(false)\n" +
                //                "then\n" +
                //                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $i : AtomicInteger( get() < 3 )\n" +
                "then\n" +
                "    $i.incrementAndGet();" +
                "    insert(\"test\" + $i.get());" +
                "    update($i);" +
                "end\n" +
                "\n" +
                "rule R1x when\n" +
                "    AtomicInteger() \n" +
                "    $c : Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )\n" +
                "    eval(true)\n" +
                "then\n" +
                "    list.add($c);" +
                "end\n"
                ;
        
        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                .build().newKieSession();
        ReteDumper.dumpRete(kieSession);
        ReteDiagram.newInstance().diagramRete(kieSession);
    }
    
    @Test
    public void testDROOLS_1326_simple() {
        String drl = "package "+this.getClass().getPackage().getName()+";\n" + 
                "import "+MyPojo.class.getCanonicalName()+"\n" + 
                "global java.util.Set controlSet;\n" + 
                "rule R1\n" + 
                "when\n" + 
                "    $my: MyPojo(\n" + 
                "        vBoolean == true,\n" + 
                "        $s : vString, vString != null,\n" + 
                "        $l : vLong\n" + 
                "    )\n" + 
                "    not MyPojo(\n" + 
                "        vBoolean == true,\n" + 
                "        vString == $s,\n" + 
                "        vLong > $l\n" + 
                "    )\n" + 
                "then\n" + 
                "    System.out.println($my);\n" + 
                "    System.out.println($l);\n" + 
                "    controlSet.add($l);\n" + 
                "end";
        System.out.println(drl);
        
        KieSession session = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
        ReteDumper.dumpRete(session);
        ReteDiagram.newInstance().diagramRete(session);
    }
    
    @Test
    public void testDROOLS_1326_simple_again() {
            String drl = "package "+this.getClass().getPackage().getName()+";\n" + 
                    "import "+MyPojo.class.getCanonicalName()+"\n" + 
                    "global java.util.Set controlSet;\n" + 
                    "rule R1\n" + 
                    "when\n" + 
                    "    $my: MyPojo(\n" + 
                    "        vBoolean == true,\n" + 
                    "        $s : vString, vString != null,\n" + 
                    "        $l : vLong\n" + 
                    "    )\n" + 
                    "    not MyPojo(\n" + 
                    "        vBoolean == true,\n" + 
                    "        vString.equals($s),\n" + 
                    "        vLong > $l\n" + 
                    "    )\n" + 
                    "then\n" + 
                    "    System.out.println(\"->> firing with \"+ kcontext.getKieRuntime().getFactHandle($my) );\n" + 
                    "    System.out.println(\"->> firing with \"+$l);\n" + 
                    "    controlSet.add($l);\n" + 
                    "end";
            System.out.println(drl);
            
            KieSession session = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
            ReteDumper.dumpRete(session);
            ReteDiagram.newInstance().diagramRete(session);
    }
    
    public static class MyPojo {
        private boolean vBoolean;
        private String vString;
        private long vLong;
        
        public MyPojo(boolean vBoolean, String vString, long vLong) {
            super();
            this.vBoolean = vBoolean;
            this.vString = vString;
            this.vLong = vLong;
        }
        
        public boolean isvBoolean() {
            return vBoolean;
        }
        
        public boolean getvBoolean() {
            return vBoolean;
        }

        
        public String getvString() {
            return vString;
        }
        
        public long getvLong() {
            return vLong;
        }
        
        public void setvBoolean(boolean vBoolean) {
            this.vBoolean = vBoolean;
        }
        
        public void setvString(String vString) {
            this.vString = vString;
        }
        
        public void setvLong(long vLong) {
            this.vLong = vLong;
        }
    }
    
    public static class LongHolder {

        private final Long value;

        public LongHolder(Long value) {
            this.value = value;
        }

        public Long getValue() {
            return value;
        }

    }
    
    public static class WeirdObject {
        private Integer status;
        private WeirdNested nested;
        
        public WeirdObject(Integer status, WeirdNested nested) {
            this.status = status;
            this.nested = nested;
        }

        public Integer getStatus() {
            return status;
        }
        
        public Long added() {
            return nested.getId();
        }
        
        public WeirdNested getNested() {
            return nested;
        }
        
    }
    public static class WeirdNested {
        private Long id;

        public WeirdNested(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
        
    }
    
    @Test
    public void testCheck() {
        String drl = "package i.p;\n" 
                + "import " + LongHolder.class.getCanonicalName() + "\n"
                + "import " + WeirdObject.class.getCanonicalName() + "\n"
                + "import " + TestObjectEnum.class.getCanonicalName() + "\n"
                + "rule fileArule1 when\n"
                + "  $t : LongHolder()\n"
                + "  WeirdObject(added == $t.value, status == TestObjectEnum.ONE.getValue() )\n"
                + "then\n"
                + "end\n"
                + "rule fileArule2 when\n"
                + "  $t : LongHolder()\n"
                + "  WeirdObject(nested.id == $t.value, status == 0 )\n"
                + "then\n"
                + "end\n"
                ;
        
        String drl2 = "package c.t.p;\n" 
                + "import " + WeirdObject.class.getCanonicalName() + "\n"
                + "import " + TestObjectEnum.class.getCanonicalName() + "\n"
                + "rule fileBrule1 when\n"
                + "  WeirdObject(status == 1)\n"
                + "then\n"
                + "end\n"
                ;

        KieSession kieSession = new KieHelper()
                    .addContent(drl2, ResourceType.DRL)
                    .addContent(drl, ResourceType.DRL)
                    .build().newKieSession();
        
        ReteDiagram.newInstance().diagramRete(kieSession);
        kieSession.addEventListener(new DebugAgendaEventListener());

        kieSession.insert(new LongHolder(12345L));
        kieSession.insert(new WeirdObject(1, new WeirdNested(12345L)));

        kieSession.fireAllRules();
    }
    
    public static class TestObjectFunction {
        public static int return1() { 
            return 1;
        }
    }
    
    public static enum TestObjectEnum {
        ZERO(0),
        ONE(1);
        private int value;
        TestObjectEnum(int value) {
            this.value = value;
        }
        public int getValue() {
            return this.value;
        }
    }

    @Test
    public void test20180111() {
        String drl = "global java.util.List list;\n" +
                     "rule R0 when\n" +
                     "    $i : Integer( intValue == 0 )\n" +
                     "    String( toString == $i.toString )\n" +
                     "then\n" +
                     "    list.add($i);\n" +
                     "end\n" +
                     "rule R1 when\n" +
                     "    $i : Integer( intValue == 1 )\n" +
                     "    String( toString == $i.toString )\n" +
                     "then\n" +
                     "    list.add($i);\n" +
                     "end\n" +
                     "rule R2 when\n" +
                     "    $i : Integer( intValue == 2 )\n" +
                     "    String( toString == $i.toString )\n" +
                     "then\n" +
                     "    list.add($i);\n" +
                     "end\n" +
                     "rule R3 when\n" +
                     "    $i : Integer( intValue == 2 )\n" +
                     "    String( length == $i )\n" +
                     "then\n" +
                     "    list.add($i);\n" +
                     "end";
        KnowledgeBuilderConfiguration kbConf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        KieBase kbase = loadKnowledgeBaseFromString(kbConf, drl);

        KieSession ksession = kbase.newKieSession();
        ReteDiagram.newInstance().configLayout(Layout.PARTITION).diagramRete(ksession);
    }
}