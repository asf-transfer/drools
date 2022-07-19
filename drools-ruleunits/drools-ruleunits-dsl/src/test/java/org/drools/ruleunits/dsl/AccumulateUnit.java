package org.drools.ruleunits.dsl;

import java.util.ArrayList;
import java.util.List;

import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.SingletonStore;

import static org.drools.model.Index.ConstraintType.EQUAL;
import static org.drools.model.Index.ConstraintType.GREATER_THAN;
import static org.drools.model.Index.ConstraintType.NOT_EQUAL;
import static org.drools.model.functions.Function1.identity;
import static org.drools.ruleunits.dsl.Accumulators.avg;
import static org.drools.ruleunits.dsl.Accumulators.max;
import static org.drools.ruleunits.dsl.Accumulators.sum;

public class AccumulateUnit implements RuleUnitDefinition {

    private final DataStore<String> strings;
    private final SingletonStore<Integer> threshold;
    private final List<String> results = new ArrayList<>();


    public AccumulateUnit() {
        this(DataSource.createStore(), DataSource.createSingleton());
    }

    public AccumulateUnit(DataStore<String> strings, SingletonStore<Integer> threshold) {
        this.strings = strings;
        this.threshold = threshold;
    }

    public DataStore<String> getStrings() {
        return strings;
    }

    public List<String> getResults() {
        return results;
    }

    public SingletonStore<Integer> getThreshold() {
        return threshold;
    }

    @Override
    public void defineRules(RulesFactory rulesFactory) {
        // For simple accumulates both the nested form ...
        RuleFactory ruleFactory1 = rulesFactory.addRule();
        // accumulate( $s: /strings[ this.substring(0, 1) != "A" ]; sum($s.length) )
        ruleFactory1.accumulate(
                ruleFactory1.from(strings).filter(s -> s.substring(0, 1), EQUAL, "A"),
                sum(String::length)
            )
            .execute(results, (r, sum) -> r.add("Sum of length of Strings starting with A is " + sum));

        // ... and the fluent one are allowed. In this last case the accumulate is always on the last pattern.
        // accumulate( $s: /strings[ this.substring(0, 1) != "A" ]; max($s.length) )
        rulesFactory.addRule()
                .from(strings).filter(s -> s.substring(0, 1), NOT_EQUAL, "A")
                .accumulate(max(String::length))
                .execute(results, (r, max) -> r.add("Max length of Strings not starting with A is " + max));

        // when the filtering constraint is a beta one with a join defined outside the accumulate only the fluent form can express this
        // $i : /threshold
        // accumulate( $s: /strings[ length >= $i ]; avg($s.length) )
        rulesFactory.addRule().from(threshold)
                .join(strings)
                .filter(String::length, GREATER_THAN, identity())
                .accumulate(avg(String::length))
                .execute(results, (r, t, avg) -> r.add("Average length of Strings longer than threshold " + t + " is " + avg));

        // the join is in the accumulate, so this rule fires (having 0 as result) even without any matching tuple
        // accumulate( $i : /threshold and $s: /strings[ length >= $i ]; sum($s.length) )
        RuleFactory ruleFactory2 = rulesFactory.addRule();
        ruleFactory2.accumulate(
                        ruleFactory2.from(threshold).join(strings).filter(String::length, GREATER_THAN, identity()),
                        sum(String::length)
                )
                .execute(results, (r, sum) -> r.add("Sum of length of Strings above threshold is " + sum));
    }
}
