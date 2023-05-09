/*
 * Copyright (c) 2020. Red Hat, Inc. and/or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.model.codegen.execmodel;

import org.apache.commons.math3.util.Pair;
import org.drools.core.base.accumulators.CollectListAccumulateFunction;
import org.drools.core.base.accumulators.CountAccumulateFunction;
import org.drools.core.base.accumulators.IntegerMaxAccumulateFunction;
import org.drools.core.base.accumulators.IntegerSumAccumulateFunction;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.rule.Declaration;
import org.drools.core.rule.accessor.Accumulator;
import org.drools.model.DSL;
import org.drools.model.Global;
import org.drools.model.Index;
import org.drools.model.Model;
import org.drools.model.PatternDSL;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.codegen.execmodel.domain.Child;
import org.drools.model.codegen.execmodel.domain.Parent;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.model.codegen.execmodel.domain.Result;
import org.drools.model.consequences.ConsequenceBuilder;
import org.drools.model.functions.Function1;
import org.drools.model.functions.accumulate.GroupKey;
import org.drools.model.impl.ModelImpl;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.ViewItem;
import org.drools.modelcompiler.KieBaseBuilder;
import org.drools.modelcompiler.dsl.pattern.D;
import org.drools.modelcompiler.util.EvaluationUtil;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.internal.event.rule.RuleEventListener;
import org.kie.internal.event.rule.RuleEventManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.from;

public class GroupByTest extends BaseModelTest {

    public GroupByTest( RUN_TYPE testRunType ) {
        super( testRunType );
    }

    @Test
    public void providedInstance() throws Exception {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<String, Integer> results;\n" +
                "rule X when\n" +
                "groupby( $p: Person (); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sumOfAges : sum($p.getAge()); " +
                "$sumOfAges > 36)" +
                "then\n" +
                "  results.put($key, $sumOfAges);\n" +
                "end";

        KieSession ksession = getKieSession( str );
        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    public static <A> SumAccumulator sumA(ToIntFunction<? super A> func) {
        return new SumAccumulator(func);
    }

    public static class SumAccumulator<C> implements Accumulator { //extends AbstractAccumulateFunction<C> {
        //UniConstraintCollector<A, ResultContainer_, Result_> collector;
        private ToIntFunction func;

        public <A> SumAccumulator(ToIntFunction<? super A> func) {
            this.func = func;
        }

        @Override public Object createWorkingMemoryContext() {
            return null;
        }

        @Override public Object createContext() {
            return new int[1];
        }

        @Override public Object init(Object workingMemoryContext, Object context, Tuple leftTuple, Declaration[] declarations, ReteEvaluator reteEvaluator) {
            ((int[])context)[0] = 0;
            return context;
        }

        @Override public Object accumulate(Object workingMemoryContext, Object context, Tuple leftTuple, InternalFactHandle handle, Declaration[] declarations, Declaration[] innerDeclarations, ReteEvaluator reteEvaluator) {
            int[] ctx = (int[]) context;

            int v = func.applyAsInt(handle.getObject());
            ctx[0] += v;

            Runnable undo = () -> ctx[0] -= v;

            return undo;
        }

        @Override public boolean supportsReverse() {
            return true;
        }

        @Override public boolean tryReverse(Object workingMemoryContext, Object context, Tuple leftTuple, InternalFactHandle handle, Object value, Declaration[] declarations, Declaration[] innerDeclarations, ReteEvaluator reteEvaluator) {
            if (value!=null) {
                ((Runnable) value).run();
            }
            return true;
        }

        @Override public Object getResult(Object workingMemoryContext, Object context, Tuple leftTuple, Declaration[] declarations, ReteEvaluator reteEvaluator) {
            int[] ctx = (int[]) context;
            return ctx[0];
        }
    }


    @Test
    public void testSumPersonAgeGroupByInitialWithAcc() throws Exception {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + GroupKey.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + org.drools.core.base.accumulators.IntegerSumAccumulateFunction.class.getCanonicalName() + ";" +
                "\n" +
                "global Map<Object, Integer> results\n" +
                "rule R1 when\n" +
                "    Person($initial: name.substring(0,1))" +
                "    not GroupKey(key == $initial)" +
                "then\n" +
                "    insert(new GroupKey(\"a\", $initial));\n" +
                "end" +
                "\n" +
                "rule R2 when\n" +
                "    $k: GroupKey(topic == \"a\", $key: key)" +
                "    not Person($key == name.substring(0, 1))" +
                "then\n" +
                "    delete($k);\n" +
                "end\n" +
                "rule R3 when\n" +
                "    GroupKey(topic == \"a\", $key: key)" +
                "    accumulate($p: Person($age: age, $key == name.substring(0, 1)); $sumOfAges: sum($age); $sumOfAges > 10)" +
                "then\n" +
                "    results.put($key, $sumOfAges);\n" +
                "end";

        KieSession ksession = getKieSession(str);
        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get("G")).isEqualTo(35);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    @Test
    public void testGroupPersonsInitial() throws Exception {
        // Note: impossible to express in DRL, since DRL require a minimum of one accumulate
        // function as per https://issues.redhat.com/browse/DROOLS-7436
        Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        Variable<String> var_$key = D.declarationOf(String.class);
        Variable<Person> var_$p = D.declarationOf(Person.class);
        Variable<List> var_$list = D.declarationOf(List.class);

        // groupby( $p: Person (); $key : $p.getName().substring(0, 1) )

        Rule rule1 = D.rule("R1").build(
                D.groupBy(
                        // Patterns
                        D.pattern(var_$p),
                        // Grouping Function
                        var_$p, var_$key, person -> person.getName().substring(0, 1)
                         ),
                // Consequence
                D.on(var_$key,var_results)
                        .execute(($key,results) -> {
                            results.add($key);
                            //System.out.println($key +  ": " + $list);
                        })
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        List results = new ArrayList();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(3);
        assertThat(results)
                .containsExactlyInAnyOrder("G", "E", "M");
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results)
                .containsExactlyInAnyOrder("M");
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results)
                .containsExactlyInAnyOrder("G", "M");
    }

    @Test
    public void testSumPersonAgeGroupByInitial() throws Exception {
        // Note: this appears to be a duplicate of providedInstance
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "import " + Map.class.getCanonicalName() + ";" +
                        "global Map<String, Integer> results;\n" +
                        "rule X when\n" +
                        "groupby( $p: Person (); " +
                        "$key : $p.getName().substring(0, 1); " +
                        "$sumOfAges : sum($p.getAge()); " +
                        "$sumOfAges > 36)" +
                        "then\n" +
                        "  results.put($key, $sumOfAges);\n" +
                        "end";
        KieSession ksession = getKieSession(str);

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    @Test
    public void testSumPersonAgeGroupByInitialWithBetaFilter() throws Exception {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<String, Integer> results;\n" +
                "rule X when\n" +
                "groupby( $p: Person ( $age: age ); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sum : sum($age); " +
                "$sum > $key.length())" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        KieSession ksession = getKieSession(str);

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get("G")).isEqualTo(35);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    @Test
    public void testSumPersonAgeGroupByInitialWithExists() throws Exception {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<String, Integer> results;\n" +
                "rule X when\n" +
                "groupby( $p: Person ( $age : age, $initial : getName().substring(0, 1) ) " +
                "and exists( String( this == $initial ) ); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sum : sum($age); " +
                "$sum > 10)" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        KieSession ksession = getKieSession(str);

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));

        ksession.insert( "G" );
        ksession.insert( "M" );
        ksession.insert( "X" );

        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(35);
        assertThat(results.get("E")).isNull();
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    public static final class MyType {

        private final AtomicInteger counter;
        private final MyType nested;

        public MyType(AtomicInteger counter, MyType nested) {
            this.counter = counter;
            this.nested = nested;
        }

        public MyType getNested() {
            counter.getAndIncrement();
            return nested;
        }
    }

    @Test
    public void testWithNull() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + MyType.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "import " + AtomicInteger.class.getCanonicalName() + ";" +
                "global List<Object> result;\n" +
                "global AtomicInteger mappingFunctionCallCounter;\n" +
                "rule X when\n" +
                "groupby( $p: MyType ( nested != null ) ;" +
                "$key : $p.getNested(); " +
                "$count : count())" +
                "then\n" +
                "  result.add($key);\n" +
                "end";

        KieSession ksession = getKieSession(str);
        AtomicInteger mappingFunctionCallCounter = new AtomicInteger();
        List<Object> result = new ArrayList<>();
        ksession.setGlobal("mappingFunctionCallCounter", mappingFunctionCallCounter);
        ksession.setGlobal("result", result);

        MyType objectWithoutNestedObject = new MyType(mappingFunctionCallCounter, null);
        MyType objectWithNestedObject = new MyType(mappingFunctionCallCounter, objectWithoutNestedObject);
        ksession.insert(objectWithNestedObject);
        ksession.insert(objectWithoutNestedObject);
        ksession.fireAllRules();

        // Side issue: this number is unusually high. Perhaps we should try to implement some cache for this?
        System.out.println("GroupKey mapping function was called " + mappingFunctionCallCounter.get() + " times.");

        assertThat(result).containsOnly(objectWithoutNestedObject);
    }

    @Test
    public void testWithGroupByAfterExists() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + Math.class.getCanonicalName() + ";" +
                "global Map<Integer, Integer> glob;\n" +
                "rule X when\n" +
                "groupby($i: Integer() and exists String();\n" +
                "$key : Math.abs($i); " +
                "$count : count())" +
                "then\n" +
                "  glob.put($key, $count.intValue());\n" +
                "end";

        KieSession session = getKieSession(str);
        Map<Integer, Integer> global = new HashMap<>();
        session.setGlobal("glob", global);

        session.insert("Something");
        session.insert(-1);
        session.insert(1);
        session.insert(2);
        session.fireAllRules();

        assertThat(global.size()).isEqualTo(2);
        assertThat((int) global.get(1)).isEqualTo(2); // -1 and 1 will map to the same key, and count twice.
        assertThat((int) global.get(2)).isEqualTo(1); // 2 maps to a key, and counts once.
    }

    @Test
    public void testWithGroupByAfterExistsWithFrom() {
        // Note: this looks exactly the same as testWithGroupByAfterExists
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + Math.class.getCanonicalName() + ";" +
                "global Map<Integer, Integer> glob;\n" +
                "rule X when\n" +
                "groupby($i: Integer() and exists String();\n" +
                "$key : Math.abs($i); " +
                "$count : count())" +
                "then\n" +
                "  glob.put($key, $count.intValue());\n" +
                "end";

        KieSession session = getKieSession(str);
        Map<Integer, Integer> global = new HashMap<>();
        session.setGlobal("glob", global);

        session.insert("Something");
        session.insert(-1);
        session.insert(1);
        session.insert(2);
        session.fireAllRules();

        assertThat(global.size()).isEqualTo(2);
        assertThat((int) global.get(1)).isEqualTo(2); // -1 and 1 will map to the same key, and count twice.
        assertThat((int) global.get(2)).isEqualTo(1); // 2 maps to a key, and counts once.
    }

    @Test
    public void testGroupBy2Vars() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<Object, Integer> results;\n" +
                "rule X when\n" +
                "groupby ( $p : Person ( $age : age ) and $s : String( $l : length );\n" +
                "          $key : $p.name.substring(0, 1) + $l;\n" +
                "          $sum : sum( $age ); $sum > 10 )" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        KieSession ksession = getKieSession(str);

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert( "test" );
        ksession.insert( "check" );
        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(6);
        assertThat(results.get("G4")).isEqualTo(35);
        assertThat(results.get("E4")).isEqualTo(71);
        assertThat(results.get("M4")).isEqualTo(126);
        assertThat(results.get("G5")).isEqualTo(35);
        assertThat(results.get("E5")).isEqualTo(71);
        assertThat(results.get("M5")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("M4")).isEqualTo(81);
        assertThat(results.get("M5")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(4);
        assertThat(results.get("G4")).isEqualTo(40);
        assertThat(results.get("M4")).isEqualTo(119);
        assertThat(results.get("G5")).isEqualTo(40);
        assertThat(results.get("M5")).isEqualTo(119);
    }

    @Test
    public void testUnexpectedRuleMatch() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Parent.class.getCanonicalName() + ";" +
                "import " + Child.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "import " + Arrays.class.getCanonicalName() + ";" +
                "global List<Object> results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $a: Parent() and exists Child($a.getChild() == this);" +
                "        $child: $a.getChild();" +
                "        $count: count()" +
                "    )" +
                "then\n" +
                "  results.add(Arrays.asList($child, $count));\n" +
                "end";

        KieSession ksession = getKieSession(str);

        List results = new ArrayList();
        ksession.setGlobal( "results", results );

        Child child1 = new Child("Child1", 1);
        Parent parent1 = new Parent("Parent1", child1);
        Child child2 = new Child("Child2", 2);
        Parent parent2 = new Parent("Parent2", child2);

        ksession.insert(parent1);
        ksession.insert(parent2);
        FactHandle toRemove = ksession.insert(child1);
        ksession.insert(child2);

        // Remove child1, therefore it does not exist, therefore there should be no groupBy matches for the child.
        ksession.delete(toRemove);

        // Yet, we still get (Child1, 0).
        ksession.fireAllRules();
        assertThat(results)
                .containsOnly(Arrays.asList(child2, 1L));
    }

    @Test
    public void testCompositeKey() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + CompositeKey.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<Object, Object> results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ( $age : age );" +
                "        $key : new CompositeKey( $p.getName().substring(0, 1), 1 );" +
                "        $sum : sum( $age );" +
                "        $sum > 10" +
                "    )\n" +
                "    $key1: Object() from $key.getKey1()" +
                "then\n" +
                "  results.put($key1, $sum);\n" +
                "end";

        KieSession ksession = getKieSession(str);
        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get("G")).isEqualTo(35);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
    }

    public static class CompositeKey {
        public final Object key1;
        public final Object key2;

        public CompositeKey( Object key1, Object key2 ) {
            this.key1 = key1;
            this.key2 = key2;
        }

        public Object getKey1() {
            return key1;
        }

        public Object getKey2() {
            return key2;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;
            CompositeKey that = ( CompositeKey ) o;
            return Objects.equals( key1, that.key1 ) &&
                    Objects.equals( key2, that.key2 );
        }

        @Override
        public int hashCode() {
            return Objects.hash( key1, key2 );
        }

        @Override
        public String toString() {
            return "CompositeKey{" +
                    "key1=" + key1 +
                    ", key2=" + key2 +
                    '}';
        }
    }

    @Test
    public void testTwoExpressionsOnSecondPattern() {
        // DROOLS-5704
        // Note: impossible to express in DRL, since DRL requires at least one accumulate function
        Global<Set> var_results = D.globalOf(Set.class, "defaultpkg", "results");

        Variable<Person> var_$p1 = D.declarationOf(Person.class);
        Variable<Person> var_$p2 = D.declarationOf(Person.class);
        Variable<Integer> var_$key = D.declarationOf(Integer.class);
        Variable<Integer> var_$join = D.declarationOf(Integer.class);

        PatternDSL.PatternDef<Person> p1pattern = D.pattern(var_$p1)
                .bind(var_$join, Person::getAge);
        PatternDSL.PatternDef<Person> p2pattern = D.pattern(var_$p2)
                .expr(p -> true)
                .expr("Age less than", var_$join, (p1, age) -> p1.getAge() > age,
                        D.betaIndexedBy(Integer.class, Index.ConstraintType.LESS_THAN, 0, Person::getAge, age -> age));

        Rule rule1 = D.rule("R1").build(
                D.groupBy(
                        D.and(p1pattern, p2pattern),
                        var_$p1,
                        var_$p2,
                        var_$key,
                        (p1, p2) -> p1.getAge() + p2.getAge()),
                D.on(var_results, var_$key)
                        .execute(Set::add)
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        Set<Integer> results = new LinkedHashSet<>();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        ksession.insert(new Person("Edoardo", 33));
        ksession.fireAllRules();

        assertThat(results).contains(80, 75, 71);
    }

    @Test
    public void testFromAfterGroupBy() {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Set.class.getCanonicalName() + ";" +
                "global Set<Object> results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ( name != null );" +
                "        $key : $p.name;" +
                "        $count : count()" +
                "    )\n" +
                "    $remappedKey: Object() from $key\n" +
                "    $remappedCount: Long() from $count\n" +
                "then\n" +
                "    if (!($remappedKey instanceof String)) {\n" +
                "        throw new IllegalStateException( \"Name not String, but \" + $remappedKey.getClass() );\n" +
                "    }\n" +
                "end";

        KieSession ksession = getKieSession(str);

        Set<Integer> results = new LinkedHashSet<>();
        ksession.setGlobal( "results", results );

        ksession.insert( new Person( "Mark", 42 ) );
        ksession.insert( new Person( "Edson", 38 ) );
        ksession.insert( new Person( "Edoardo", 33 ) );
        int fireCount = ksession.fireAllRules();
        assertThat( fireCount ).isGreaterThan( 0 );
    }

    @Test
    public void testBindingRemappedAfterGroupBy() {
        // Note: this would create the same DRL as testFromAfterGroupBy
        // since to bind a variable to an expression in DRL, you need to
        // use "from"
        Global<Set> var_results = D.globalOf( Set.class, "defaultpkg", "results" );

        Variable var_$p1 = D.declarationOf( Person.class );
        Variable var_$key = D.declarationOf( String.class );
        Variable var_$count = D.declarationOf( Long.class );
        Variable var_$remapped1 = D.declarationOf( Object.class);
        Variable var_$remapped2 = D.declarationOf( Long.class);

        PatternDSL.PatternDef<Person> p1pattern = D.pattern( var_$p1 )
                                                   .expr( p -> (( Person ) p).getName() != null );

        Rule rule1 = D.rule( "R1" ).build(
              D.groupBy(
                    p1pattern,
                    var_$p1,
                    var_$key,
                    Person::getName,
                    DSL.accFunction( CountAccumulateFunction::new, var_$p1 ).as( var_$count ) ),
              D.pattern( var_$key).bind(var_$remapped1, o -> o),
              D.pattern( var_$count).bind(var_$remapped2, o -> o),
              D.on( var_$remapped1, var_$remapped2 )
               .execute( ( ctx, name, count ) -> {
                   if ( !(name instanceof String) ) {
                       throw new IllegalStateException( "Name not String, but " + name.getClass() );
                   }
               } )
                                         );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        Set<Integer> results = new LinkedHashSet<>();
        ksession.setGlobal( "results", results );

        ksession.insert( new Person( "Mark", 42 ) );
        ksession.insert( new Person( "Edson", 38 ) );
        ksession.insert( new Person( "Edoardo", 33 ) );
        int fireCount = ksession.fireAllRules();
        assertThat( fireCount ).isGreaterThan( 0 );
    }

    @Test
    public void testGroupByUpdatingKey() throws Exception {
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<String, Integer> results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : $p.getName().substring(0, 1);" +
                "        $sumOfAges : sum($p.getAge()), $countOfPersons : count();" +
                "        $sumOfAges > 10" +
                "    )\n" +
                "then\n" +
                "    results.put($key, $sumOfAges + $countOfPersons.intValue());" +
                "end";

        KieSession ksession = getKieSession(str);
        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        Person me = new Person("Mario", 45);
        FactHandle meFH = ksession.insert(me);

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("E")).isEqualTo(73);
        assertThat(results.get("M")).isEqualTo(129);
        results.clear();

        me.setName("EMario");
        ksession.update(meFH, me);
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("E")).isEqualTo(119);
        assertThat(results.get("M")).isEqualTo(83);
    }

    @Test
    public void doesNotRemoveProperly() {
        // Note: impossible to express in DRL, since DRL require a minimum of one accumulate
        // function
        Global<Set> var_results = D.globalOf( Set.class, "defaultpkg", "results" );

        Variable<Person> var_$p1 = D.declarationOf( Person.class );
        Variable<Integer> var_$key = D.declarationOf( Integer.class );

        PatternDSL.PatternDef<Person> p1pattern = D.pattern( var_$p1 )
                .expr( p -> p.getName() != null );

        Set<Integer> results = new LinkedHashSet<>();
        Rule rule1 = D.rule( "R1" ).build(
                D.groupBy(
                        p1pattern,
                        var_$p1,
                        var_$key,
                        Person::getAge),
                D.on( var_$key )
                        .execute( ( ctx, key ) -> {
                            results.add(key);
                        } )
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        (( RuleEventManager ) ksession).addEventListener( new RuleEventListener() {
            @Override
            public void onDeleteMatch( Match match) {
                if (!match.getRule().getName().equals( "R1" )) {
                    return;
                }
                RuleTerminalNodeLeftTuple tuple = (RuleTerminalNodeLeftTuple) match;
                InternalFactHandle handle = tuple.getFactHandle();
                Object[] array = (Object[]) handle.getObject();
                results.remove( array[array.length-1] );
            }

            @Override
            public void onUpdateMatch(Match match) {
                onDeleteMatch(match);
            }
        });
        ksession.setGlobal( "results", results );

        ksession.insert( new Person( "Mark", 42 ) );
        ksession.insert( new Person( "Edson", 38 ) );
        int edoardoAge = 33;
        FactHandle fh1 = ksession.insert( new Person( "Edoardo", edoardoAge ) );
        FactHandle fh2 = ksession.insert( new Person( "Edoardo's clone", edoardoAge ) );
        ksession.fireAllRules();
        assertThat( results ).contains(edoardoAge);

        // Remove first Edoardo. Nothing should happen, because age 33 is still present.
        ksession.delete(fh1);
        ksession.fireAllRules();
        assertThat( results ).contains(edoardoAge);

        // Remove Edoardo's clone. The group for age 33 should be undone.
        ksession.delete(fh2);
        ksession.fireAllRules();
        System.out.println(results);
        assertThat( results ).doesNotContain(edoardoAge);
    }

    @Test
    public void testTwoGroupBy() {
        // DROOLS-5697
        // TODO: $key1 does not get resolved as a variable in the outer groupby
        /*
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Group.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map<String, Integer> results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        groupby(" +
                "            $p : Person ($age: age);" +
                "            $key1 : $p.getName().substring(0, 3);" +
                "            $sumOfAges : sum($age)" +
                "        ) and $g1: Group() from new Group($key1, $sumOfAges) and " +
                "        $g1SumOfAges: Integer() from $g1.getValue();" +
                "        $key : ((String) ($g1.getKey())).substring(0, 2);" +
                "        $maxOfValues : max($g1SumOfAges)" +
                "    )\n" +
                "then\n" +
                "    System.out.println($key + \" -> \" + $maxOfValues);\n" +
                "    results.put($key, $maxOfValues);\n" +
                "end";

        KieSession ksession = getKieSession(str); */

        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");

        Variable<String> var_$key_1 = D.declarationOf(String.class);
        Variable<Person> var_$p = D.declarationOf(Person.class);
        Variable<Integer> var_$age = D.declarationOf(Integer.class);
        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
        Variable<Group> var_$g1 = D.declarationOf(Group.class, "$g1", D.from(var_$key_1, var_$sumOfAges, ($k, $v) -> new Group($k, $v)));
        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
        Variable<String> var_$key_2 = D.declarationOf(String.class);
        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);

        Rule rule1 = D.rule("R1").build(
                D.groupBy(
                        D.and(
                                D.groupBy(
                                        D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
                                        var_$p, var_$key_1, person -> person.getName().substring(0, 3),
                                        D.accFunction( IntegerSumAccumulateFunction::new, var_$age).as(var_$sumOfAges)),
                                D.pattern(var_$g1).bind(var_$g1_value, group -> (Integer) group.getValue()) ),
                        var_$g1, var_$key_2, groupResult -> ((String)groupResult.getKey()).substring(0, 2),
                        D.accFunction( IntegerMaxAccumulateFunction::new, var_$g1_value).as(var_$maxOfValues)),
                D.on(var_$key_2, var_results, var_$maxOfValues)
                        .execute(($key, results, $maxOfValues) -> {
                            System.out.println($key + " -> " + $maxOfValues);
                            results.put($key, $maxOfValues);
                        })
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edson", 38));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();
        System.out.println("-----");

        /*
         * In the first groupBy:
         *   Mark+Mario become "(Mar, 87)"
         *   Maciej becomes "(Mac, 39)"
         *   Geoffrey becomes "(Geo, 35)"
         *   Edson becomes "(Eds, 38)"
         *   Edoardo becomes "(Edo, 33)"
         *
         * Then in the second groupBy:
         *   "(Mar, 87)" and "(Mac, 39)" become "(Ma, 87)"
         *   "(Eds, 38)" and "(Edo, 33)" become "(Ed, 38)"
         *   "(Geo, 35)" becomes "(Ge, 35)"
         */

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get("Ma")).isEqualTo(87);
        assertThat(results.get("Ed")).isEqualTo(38);
        assertThat(results.get("Ge")).isEqualTo(35);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();
        System.out.println("-----");

        // No Mario anymore, so "(Mar, 42)" instead of "(Mar, 87)".
        // Therefore "(Ma, 42)".
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("Ma")).isEqualTo(42);
        results.clear();

        // "(Geo, 35)" is gone.
        // "(Mat, 38)" is added, but Mark still wins, so "(Ma, 42)" stays.
        ksession.delete(geoffreyFH);
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("Ma")).isEqualTo(42);
    }

    @Test
    @Ignore // FIXME This does not work, because Declaration only works with function1
    public void testTwoGroupByUsingBindings() {
        // DROOLS-5697
        // Note: this look like it would generate the same DRL as testTwoGroupBy
        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");

        Variable<String> var_$key_1 = D.declarationOf(String.class);
        Variable<Person> var_$p = D.declarationOf(Person.class);
        Variable<Integer> var_$age = D.declarationOf(Integer.class);
        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
        Variable<Group> var_$g1 = D.declarationOf(Group.class); // "$g1", D.from(var_$key_1, var_$sumOfAges, ($k, $v) -> new Group($k, $v)));
        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
        Variable<String> var_$key_2 = D.declarationOf(String.class);
        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);

        Rule rule1 = D.rule("R1").build(
              D.groupBy(
                    D.and(
                          D.groupBy(
                                D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
                                var_$p, var_$key_1, person -> person.getName().substring(0, 3),
                                D.accFunction( IntegerSumAccumulateFunction::new, var_$age).as(var_$sumOfAges)),
                          D.pattern(var_$key_1).bind(var_$g1, var_$sumOfAges, ($k, $v) -> new Group($k, $v)), // Currently this does not work
                          D.pattern(var_$g1).bind(var_$g1_value, group -> (Integer) group.getValue()) ),
                    var_$g1, var_$key_2, groupResult -> ((String)groupResult.getKey()).substring(0, 2),
                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$g1_value).as(var_$maxOfValues)),
              D.on(var_$key_2, var_results, var_$maxOfValues)
               .execute(($key, results, $maxOfValues) -> {
                   System.out.println($key + " -> " + $maxOfValues);
                   results.put($key, $maxOfValues);
               })
                                       );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edson", 38));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();
        System.out.println("-----");

        /*
         * In the first groupBy:
         *   Mark+Mario become "(Mar, 87)"
         *   Maciej becomes "(Mac, 39)"
         *   Geoffrey becomes "(Geo, 35)"
         *   Edson becomes "(Eds, 38)"
         *   Edoardo becomes "(Edo, 33)"
         *
         * Then in the second groupBy:
         *   "(Mar, 87)" and "(Mac, 39)" become "(Ma, 87)"
         *   "(Eds, 38)" and "(Edo, 33)" become "(Ed, 38)"
         *   "(Geo, 35)" becomes "(Ge, 35)"
         */

        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get("Ma")).isEqualTo(87);
        assertThat(results.get("Ed")).isEqualTo(38);
        assertThat(results.get("Ge")).isEqualTo(35);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();
        System.out.println("-----");

        // No Mario anymore, so "(Mar, 42)" instead of "(Mar, 87)".
        // Therefore "(Ma, 42)".
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("Ma")).isEqualTo(42);
        results.clear();

        // "(Geo, 35)" is gone.
        // "(Mat, 38)" is added, but Mark still wins, so "(Ma, 42)" stays.
        ksession.delete(geoffreyFH);
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("Ma")).isEqualTo(42);
    }

    public static class Group {
        private final Object key;
        private final Object value;

        public Group( Object key, Object value ) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
    }

    @Test
    public void testEmptyPatternOnGroupByKey() throws Exception {
        // DROOLS-6031
        // Note: I am unsure if this can be correctly tested in DRL
        // (in particular, will `String()` be considered an
        // "empty" pattern)?
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<String> var_$key = D.declarationOf(String.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);

        Rule rule1 = D.rule("R1").build(
                D.groupBy(
                        // Patterns
                        D.pattern(var_$p),
                        // Grouping Function
                        var_$p, var_$key, person -> person.getName().substring(0, 1)),
                // Filter
                D.pattern(var_$key),
                // Consequence
                D.on(var_$key, var_results)
                        .execute(($key, results) -> results.add($key))
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        List<String> results = new ArrayList<String>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));

        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo("M");
    }

    @Test
    public void testFilterOnGroupByKey() throws Exception {
        // DROOLS-6031
        // Cannot be tested with DRL because DRL requires at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<String> var_$key = D.declarationOf(String.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);

        Rule rule1 = D.rule("R1").build(
                D.groupBy(
                        // Patterns
                        D.pattern(var_$p),
                        // Grouping Function
                        var_$p, var_$key, person -> person.getName().substring(0, 1)),
                // Filter
                D.pattern(var_$key).expr(s -> s.length() > 0).expr(s -> s.length() < 2),
                // Consequence
                D.on(var_$key, var_results)
                        .execute(($key, results) -> results.add($key))
        );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        List<String> results = new ArrayList<String>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));

        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo("M");
    }

    @Test
    public void testDecomposedGroupByKey() throws Exception {
        // DROOLS-6031
        // Note: Cannot be tested with DRL because DRL requires at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Pair<String, String>> var_$key = (Variable) D.declarationOf(Pair.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);

        final Variable<String> var_$subkeyA = D.declarationOf(String.class);
        final Variable<String> var_$subkeyB = D.declarationOf(String.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        // Patterns
                        PatternDSL.pattern(var_$p),
                        // Grouping Function
                        var_$p, var_$key, person -> Pair.create(
                                person.getName().substring(0, 1),
                                person.getName().substring(1, 2))),
                // Bindings
                D.pattern(var_$key)
                        .bind(var_$subkeyA, Pair::getKey)
                        .bind(var_$subkeyB, Pair::getValue),
                // Consequence
                D.on(var_$subkeyA, var_$subkeyB, var_results)
                        .execute(($a, $b, results) -> {
                            results.add($a);
                            results.add($b);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<String> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0)).isEqualTo("M");
        assertThat(results.get(1)).isEqualTo("a");
    }

    @Test
    public void testDecomposedGroupByKeyAndAccumulate() throws Exception {
        // DROOLS-6031
        // TODO: Duplicate parameter $p
        /*
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "import " + Pair.class.getCanonicalName() + ";" +
                        "import " + List.class.getCanonicalName() + ";" +
                        "global List<Object> results;\n" +
                        "rule X when\n" +
                        "    groupby(" +
                        "        $person : Person ();" +
                        "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                        "        $accresult : count()" +
                        "    )\n" +
                        "    $subkeyA : Object() from $key.getKey()\n" +
                        "    $subkeyB : Object() from $key.getValue()\n" +
                        "    eval($accresult > 0)" +
                        "then\n" +
                        "    results.add($subkeyA);\n" +
                        "    results.add($subkeyB);\n" +
                        "    results.add($accresult);\n" +
                        "end";

        KieSession ksession = getKieSession(str);
        */
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Pair<String, String>> var_$key = (Variable) D.declarationOf(Pair.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);

        final Variable<String> var_$subkeyA = D.declarationOf(String.class);
        final Variable<String> var_$subkeyB = D.declarationOf(String.class);
        final Variable<Long> var_$accresult = D.declarationOf(Long.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        // Patterns
                        PatternDSL.pattern(var_$p),
                        // Grouping Function
                        var_$p, var_$key, person -> Pair.create(
                                person.getName().substring(0, 1),
                                person.getName().substring(1, 2)),
                        D.accFunction(CountAccumulateFunction::new).as(var_$accresult)),
                // Bindings
                D.pattern(var_$key)
                        .bind(var_$subkeyA, Pair::getKey)
                        .bind(var_$subkeyB, Pair::getValue),
                D.pattern(var_$accresult).expr( l -> l > 0 ),
                // Consequence
                D.on(var_$subkeyA, var_$subkeyB, var_$accresult, var_results)
                        .execute(($a, $b, $accresult, results) -> {
                            results.add($a);
                            results.add($b);
                            results.add($accresult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0)).isEqualTo("M");
        assertThat(results.get(1)).isEqualTo("a");
        assertThat(results.get(2)).isEqualTo(1L);
    }

    @Test
    public void testDecomposedGroupByKeyAnd2Accumulates() throws Exception {
        // DROOLS-6031
        // TODO: this
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Pair<String, String>> var_$key = (Variable) D.declarationOf(Pair.class);
        final Variable<Pair> var_$accumulate = D.declarationOf(Pair.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Person> var_$p2 = D.declarationOf(Person.class);

        final Variable<String> var_$subkeyA = D.declarationOf(String.class);
        final Variable<String> var_$subkeyB = D.declarationOf(String.class);
        final Variable<List> var_$accresult = D.declarationOf(List.class);
        final Variable<List> var_$accresult2 = D.declarationOf(List.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        // Patterns
                        D.and(
                                D.pattern(var_$p),
                                D.pattern(var_$p2)
                                        .bind(var_$accumulate, var_$p, Pair::create)
                        ),
                        // Grouping Function
                        var_$p, var_$key, person -> Pair.create(
                                person.getName().substring(0, 1),
                                person.getName().substring(1, 2)),
                        D.accFunction(CollectListAccumulateFunction::new, var_$accumulate).as(var_$accresult),
                        D.accFunction(CollectListAccumulateFunction::new, var_$accumulate).as(var_$accresult2)),
                // Bindings
                D.pattern(var_$key)
                        .bind(var_$subkeyA, Pair::getKey)
                        .bind(var_$subkeyB, Pair::getValue),
                D.pattern(var_$accresult),
                // Consequence
                D.on(var_$subkeyA, var_$subkeyB, var_$accresult, var_results)
                        .execute(($a, $b, $accresult, results) -> {
                            results.add($a);
                            results.add($b);
                            results.add($accresult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0)).isEqualTo("M");
        assertThat(results.get(1)).isEqualTo("a");
    }

    @Test
    public void testDecomposedGroupByKeyAnd2AccumulatesInConsequence() throws Exception {
        // DROOLS-6031
        // TODO: this
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Pair<String, String>> var_$key = (Variable) D.declarationOf(Pair.class);
        final Variable<Pair> var_$accumulate = D.declarationOf(Pair.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Person> var_$p2 = D.declarationOf(Person.class);

        final Variable<String> var_$subkeyA = D.declarationOf(String.class);
        final Variable<String> var_$subkeyB = D.declarationOf(String.class);
        final Variable<List> var_$accresult = D.declarationOf(List.class);
        final Variable<List> var_$accresult2 = D.declarationOf(List.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        // Patterns
                        D.and(
                                D.pattern(var_$p),
                                D.pattern(var_$p2)
                                        .bind(var_$accumulate, var_$p, Pair::create)
                        ),
                        // Grouping Function
                        var_$p, var_$key, person -> Pair.create(
                                person.getName().substring(0, 1),
                                person.getName().substring(1, 2)),
                        D.accFunction(CollectListAccumulateFunction::new, var_$accumulate).as(var_$accresult),
                        D.accFunction(CollectListAccumulateFunction::new, var_$accumulate).as(var_$accresult2)),
                // Bindings
                D.pattern(var_$accresult2),
                // Consequence
                D.on(var_$key, var_$accresult, var_$accresult2, var_results)
                        .execute(($key, $accresult, $accresult2, results) -> {
                            results.add($key);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(1);
    }

    @Test
    public void testNestedGroupBy1a() throws Exception {
        // DROOLS-6045
        // Note: Cannot be expressed in DRL because DRL require at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Object> var_$key = D.declarationOf(Object.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Object> var_$accresult = D.declarationOf(Object.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.accumulate(
                        D.and(
                                D.groupBy(
                                        // Patterns
                                        D.pattern(var_$p),
                                        // Grouping Function
                                        var_$p, var_$key, Person::getAge),
                                // Bindings
                                D.pattern(var_$key)
                                        .expr(k -> ((Integer)k) > 0)
                        ),
                        D.accFunction(CollectListAccumulateFunction::new, var_$key).as(var_$accresult)
                ),
                // Consequence
                D.on(var_$accresult, var_results)
                        .execute(($accresult, results) -> {
                            results.add($accresult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        System.out.println(results);
        assertThat(results).containsOnly(Collections.singletonList(42));
    }

    @Test
    public void testNestedGroupBy1b() throws Exception {
        // DROOLS-6045
        // Note: Cannot be expressed in DRL because DRL require at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Object> var_$key = D.declarationOf(Object.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Object> var_$accresult = D.declarationOf(Object.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.accumulate(
                        D.and(
                                D.groupBy(
                                        // Patterns
                                        D.pattern(var_$p),
                                        // Grouping Function
                                        var_$p, var_$key, Person::getAge),
                                // Bindings
                                D.pattern(var_$key)
                        ),
                        D.accFunction(CollectListAccumulateFunction::new, var_$key).as(var_$accresult)
                ),
                // Consequence
                D.on(var_$accresult, var_results)
                        .execute(($accresult, results) -> {
                            results.add($accresult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results).containsOnly(Collections.singletonList(42));
    }

    @Test
    public void testNestedGroupBy2() throws Exception {
        // DROOLS-6045
        // Note: Cannot be expressed in DRL because DRL require at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Object> var_$key = D.declarationOf(Object.class);
        final Variable<Object> var_$keyOuter = D.declarationOf(Object.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Object> var_$accresult = D.declarationOf(Object.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        D.and(
                                D.groupBy(
                                        // Patterns
                                        D.pattern(var_$p),
                                        // Grouping Function
                                        var_$p, var_$key, Person::getAge),
                                // Bindings
                                D.pattern(var_$key)
                                        .expr(k -> ((Integer)k) > 0)
                        ),
                        var_$key, var_$keyOuter, k -> ((Integer)k) * 2,
                        D.accFunction(CollectListAccumulateFunction::new, var_$keyOuter).as(var_$accresult)
                ),
                // Consequence
                D.on(var_$keyOuter, var_$accresult, var_results)
                        .execute(($outerKey, $accresult, results) -> {
                            results.add($accresult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @Test
    public void testNestedGroupBy3() {
        // DROOLS-6045
        // Note: Cannot be expressed in DRL because DRL require at least one accumulate function
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Object> var_$key = D.declarationOf(Object.class);
        final Variable<Object> var_$keyOuter = D.declarationOf(Object.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Object> var_$accresult = D.declarationOf(Object.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        D.and(
                                D.groupBy(
                                        // Patterns
                                        D.pattern(var_$p),
                                        // Grouping Function
                                        var_$p, var_$key, Person::getName,
                                        D.accFunction(CountAccumulateFunction::new).as(var_$accresult)),
                                // Bindings
                                D.pattern(var_$accresult)
                                        .expr(c -> ((Long)c) > 0)
                        ),
                        var_$key, var_$accresult, var_$keyOuter, Pair::create
                ),
                // Consequence
                D.on(var_$keyOuter, var_results)
                        .execute(($outerKey, results) -> {
                            results.add($outerKey);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results).containsOnly(Pair.create("Mark", 1L));
    }

    @Test
    public void testFilterOnAccumulateResultWithDecomposedGroupByKey() throws Exception {
        // DROOLS-6045
        // TODO: Duplicate parameter $p
        /*
        Assume.assumeTrue("Only PATTERN_DSL work for now", testRunType == RUN_TYPE.PATTERN_DSL);
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "import " + Pair.class.getCanonicalName() + ";" +
                        "import " + List.class.getCanonicalName() + ";" +
                        "global List<Object> results;\n" +
                        "rule X when\n" +
                        "    groupby(" +
                        "        $p : Person ($age: age, this != null);" +
                        "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                        "        $accresult : sum($age)" +
                        "    )\n" +
                        "    $subkeyA : Object() from $key.getKey()\n" +
                        "    $subkeyB : Object() from $key.getValue()\n" +
                        "    eval($accresult != null && $subkeyA != null && $subkeyB != null)" +
                        "then\n" +
                        "    results.add($subkeyA);\n" +
                        "    results.add($subkeyB);\n" +
                        "    results.add($accresult);\n" +
                        "end";

        KieSession ksession = getKieSession(str);
        */
        final Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        final Variable<Pair<String, String>> var_$key = (Variable) D.declarationOf(Pair.class);
        final Variable<Person> var_$p = D.declarationOf(Person.class);
        final Variable<Integer> var_$pAge = D.declarationOf(Integer.class);

        final Variable<Object> var_$subkeyA = D.declarationOf(Object.class);
        final Variable<Object> var_$subkeyB = D.declarationOf(Object.class);
        final Variable<Integer> var_$accresult = D.declarationOf(Integer.class);

        final Rule rule1 = PatternDSL.rule("R1").build(
                D.groupBy(
                        // Patterns
                        PatternDSL.pattern(var_$p)
                                .bind(var_$pAge, Person::getAge)
                                .expr(Objects::nonNull),
                        // Grouping Function
                        var_$p, var_$key, person -> Pair.create(
                                person.getName().substring(0, 1),
                                person.getName().substring(1, 2)),
                        D.accFunction( IntegerSumAccumulateFunction::new, var_$pAge).as(var_$accresult)),
                // Bindings
                D.pattern(var_$key)
                        .bind(var_$subkeyA, Pair::getKey)
                        .bind(var_$subkeyB, Pair::getValue),
                D.pattern(var_$accresult)
                        .expr("Some expr", var_$subkeyA, var_$subkeyB, (a, b, c) -> true),
                // Consequence
                D.on(var_$subkeyA, var_$subkeyB, var_$accresult, var_results)
                        .execute(($a, $b, $accResult, results) -> {
                            results.add($a);
                            results.add($b);
                            results.add($accResult);
                        })
        );

        final Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        final KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        final List<Object> results = new ArrayList<>();
        ksession.setGlobal( "results", results );

        ksession.insert( "A" );
        ksession.insert( "test" );
        ksession.insert(new Person("Mark", 42));
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0)).isEqualTo("M");
        assertThat(results.get(1)).isEqualTo("a");
        assertThat(results.get(2)).isEqualTo(42);
    }
// These two test are commented out, until we figure out the correct way to do this and limitations.
// If no correct way can be found, the tests can be deleted.
//    @Test
//    public void testErrorOnCompositeBind() {
//        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");
//
//        Variable<String> var_$key_1 = D.declarationOf(String.class);
//        Variable<Person> var_$p = D.declarationOf(Person.class);
//        Variable<Integer> var_$age = D.declarationOf(Integer.class);
//        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
//        Variable<Group> var_$g1 = D.declarationOf(Group.class);
//        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
//        Variable<String> var_$key_2 = D.declarationOf(String.class);
//        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);
//
//        Rule rule1 = D.rule("R1").build(
//              D.groupBy(
//                    D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
//                    var_$p, var_$key_1, groupResult -> var_$p.getName().substring(0, 2),
//                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$age).as(var_$maxOfValues)),
//              D.pattern(var_$key_1).bind(var_$g1, var_$maxOfValues, (k, s) -> new Group(k, s)),
//              D.on(var_$key_1)
//               .execute($key -> { System.out.println($key); }));
//
//        try {
//            Model      model    = new ModelImpl().addRule(rule1).addGlobal(var_results);
//            KieSession ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
//            fail("Composite Bindings are not allowed");
//        } catch(Exception e) {
//
//        }
//    }
//
//    @Test
//    public void testErrorOnNestedCompositeBind() {
//        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");
//
//        Variable<String> var_$key_1 = D.declarationOf(String.class);
//        Variable<Person> var_$p = D.declarationOf(Person.class);
//        Variable<Integer> var_$age = D.declarationOf(Integer.class);
//        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
//        Variable<Group> var_$g1 = D.declarationOf(Group.class);
//        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
//        Variable<String> var_$key_2 = D.declarationOf(String.class);
//        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);
//
//        Rule rule1 = D.rule("R1").build(
//              D.groupBy(
//                    D.and(
//                          D.groupBy(
//                                D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
//                                var_$p, var_$key_1, person -> person.getName().substring(0, 3),
//                                D.accFunction( IntegerSumAccumulateFunction::new, var_$age).as(var_$sumOfAges)),
//                          D.pattern(var_$key_1).bind(var_$g1, var_$sumOfAges, (k, s) -> new Group(k, s))), // this should fail, due to two declarations
//                    var_$g1, var_$key_2, groupResult -> ((String)groupResult.getKey()).substring(0, 2),
//                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$g1_value).as(var_$maxOfValues)),
//              D.on(var_$key_1)
//               .execute($key -> { System.out.println($key); }));
//
//        try {
//            Model      model    = new ModelImpl().addRule(rule1).addGlobal(var_results);
//            KieSession ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
//            fail("Composite Bindings are not allowed");
//        } catch(Exception e) {
//
//        }
//    }

    @Test
    public void testNestedRewrite() {
        // DROOLS-5697
        Global<List> var_results = D.globalOf(List.class, "defaultpkg", "results");

        Variable<Person> var_$p = D.declarationOf(Person.class);
        Variable<Integer> var_$age = D.declarationOf(Integer.class);
        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
        Variable<Integer> var_$g1 = D.declarationOf(Integer.class);
        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);

        Rule rule1 = D.rule("R1").build(
              D.accumulate(
                    D.and(
                          D.accumulate(
                                D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
                                D.accFunction( IntegerSumAccumulateFunction::new, var_$age).as(var_$sumOfAges)),
                          D.pattern(var_$sumOfAges).bind(var_$g1, (s) -> s+1)),
                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$g1).as(var_$maxOfValues)),
              D.on(var_results, var_$maxOfValues)
               .execute((results, $maxOfValues) -> {
                   System.out.println($maxOfValues);
                   results.add($maxOfValues);
               })
                                       );

        Model model = new ModelImpl().addRule( rule1 ).addGlobal( var_results );
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel( model ).newKieSession();

        List results = new ArrayList();
        ksession.setGlobal( "results", results );

        FactHandle fhMark = ksession.insert(new Person("Mark", 42));
        FactHandle fhEdoardo = ksession.insert(new Person("Edoardo", 33));
        ksession.fireAllRules();
        assertThat(results.contains(76)).isTrue();

        ksession.insert(new Person("Edson", 38));
        ksession.fireAllRules();
        assertThat(results.contains(114)).isTrue();

        ksession.delete(fhEdoardo);
        ksession.fireAllRules();
        assertThat(results.contains(81)).isTrue();

        ksession.update(fhMark, new Person("Mark", 45));
        ksession.fireAllRules();
        assertThat(results.contains(84)).isTrue();
    }
}

