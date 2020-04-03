/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.pmml.commons.factories;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.drools.compiler.lang.api.CEDescrBuilder;
import org.drools.compiler.lang.api.ConditionalBranchDescrBuilder;
import org.drools.compiler.lang.api.PatternDescrBuilder;
import org.drools.compiler.lang.api.RuleDescrBuilder;
import org.drools.compiler.lang.descr.AndDescr;
import org.drools.compiler.lang.descr.ExistsDescr;
import org.drools.compiler.lang.descr.NotDescr;
import org.drools.compiler.lang.descr.OrDescr;
import org.kie.pmml.commons.exceptions.KiePMMLException;
import org.kie.pmml.models.drooled.ast.KiePMMLDrooledRule;
import org.kie.pmml.models.drooled.executor.KiePMMLStatusHolder;
import org.kie.pmml.models.drooled.tuples.KiePMMLFieldOperatorValue;
import org.kie.pmml.models.drooled.tuples.KiePMMLOperatorValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.pmml.commons.factories.KiePMMLDescrRulesFactory.BREAK_LABEL;
import static org.kie.pmml.commons.factories.KiePMMLDescrRulesFactory.STATUS_HOLDER;

/**
 * Class used to generate <b>Rules</b> (descr) out of a <b>Queue&lt;KiePMMLDrooledRule&gt;</b>
 */
public class KiePMMLDescrLhsFactory {

    static final String INPUT_FIELD = "$inputField";
    static final String INPUT_FIELD_CONDITIONAL = "$inputField.getValue() %s %s";

    static final String VALUE_PATTERN = "value %s %s";
    private static final Logger logger = LoggerFactory.getLogger(KiePMMLDescrLhsFactory.class.getName());

    final CEDescrBuilder<RuleDescrBuilder, AndDescr> builder;

    private KiePMMLDescrLhsFactory(final CEDescrBuilder<RuleDescrBuilder, AndDescr> builder) {
        this.builder = builder;
    }

    public static KiePMMLDescrLhsFactory factory(final CEDescrBuilder<RuleDescrBuilder, AndDescr> builder) {
        return new KiePMMLDescrLhsFactory(builder);
    }

    public void declareLhs(final KiePMMLDrooledRule rule) {
        logger.debug("declareLhs {}", rule);
        final PatternDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>> patternDescrBuilder = builder.pattern(KiePMMLStatusHolder.class.getSimpleName()).id(STATUS_HOLDER, false);
        if (rule.getStatusConstraint() != null) {
            patternDescrBuilder.constraint(rule.getStatusConstraint());
        }
        if (rule.getAndConstraints() != null) {
            rule.getAndConstraints().forEach((type, kiePMMLOperatorValues) -> declareConstraintAndOr("&&", type, kiePMMLOperatorValues));
        }
        if (rule.getOrConstraints() != null) {
            rule.getOrConstraints().forEach((type, kiePMMLOperatorValues) -> declareConstraintAndOr("||", type, kiePMMLOperatorValues));
        }
        if (rule.getXorConstraints() != null) {
            declareConstraintsXor(rule.getXorConstraints());
        }
        if (rule.getNotConstraints() != null) {
            declareNotConstraints(rule.getNotConstraints());
        }

        if (rule.getInConstraints() != null) {
            rule.getInConstraints().forEach(this::declareConstraintIn);
        }
        if (rule.getNotInConstraints() != null) {
            rule.getNotInConstraints().forEach(this::declareConstraintNotIn);
        }
        if (rule.getIfBreakField() != null) {
            declareIfBreak(rule.getIfBreakField(), rule.getIfBreakOperator(), rule.getIfBreakValue());
        }
    }

    protected void declareConstraintAndOr(final String operator, final String patternType, final List<KiePMMLOperatorValue> kiePMMLOperatorValues) {
        String constraintString = kiePMMLOperatorValues.stream()
                .map(kiePMMLOperatorValue -> String.format(VALUE_PATTERN, kiePMMLOperatorValue.getOperator(), kiePMMLOperatorValue.getValue()))
                .collect(Collectors.joining(" " + operator + " "));
        builder.pattern(patternType).constraint(constraintString);
    }

    protected void declareConstraintsXor(final List<KiePMMLFieldOperatorValue> xorConstraints) {
        if (xorConstraints.size() != 2) {
            throw new KiePMMLException("Expecting two fields for XOR constraints, retrieved " + xorConstraints.size());
        }
        final String[] keys = new String[xorConstraints.size()];
        final List<KiePMMLOperatorValue>[] values = new List[xorConstraints.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = xorConstraints.get(i).getName();
            values[i] = Collections.singletonList(xorConstraints.get(i).getKiePMMLOperatorValue());
        }
        // The builder to put in "and" the not and the exists constraints
        final CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr> andBuilder = builder.and();
        final CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr>, NotDescr>, AndDescr> notBuilder = andBuilder.not().and();
        declareNotConstraint(notBuilder, keys[0], values[0]);
        declareNotConstraint(notBuilder, keys[1], values[1]);
        final CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr>, ExistsDescr>, OrDescr> existsBuilder = andBuilder.exists().or();
        declareExistsConstraint(existsBuilder, keys[0], values[0]);
        declareExistsConstraint(existsBuilder.or(), keys[1], values[1]);
    }

    protected void declareNotConstraints(final Map<String, List<KiePMMLOperatorValue>> notConstraints) {
        // The builder to put in "and" the not constraints
        final CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr> andBuilder = builder.and();
        final CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr>, NotDescr>, AndDescr> notBuilder = andBuilder.not().and();
        notConstraints.forEach((fieldName, kiePMMLOperatorValues) -> declareNotConstraint(notBuilder, fieldName, kiePMMLOperatorValues));
    }

    protected void declareNotConstraint(final CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>, AndDescr>, NotDescr>, AndDescr> notBuilder, final String patternType, final List<KiePMMLOperatorValue> kiePMMLOperatorValues) {
        String constraintString = kiePMMLOperatorValues.stream()
                .map(kiePMMLOperatorValue -> String.format(VALUE_PATTERN, kiePMMLOperatorValue.getOperator(), kiePMMLOperatorValue.getValue()))
                .collect(Collectors.joining(" && "));
        notBuilder.pattern(patternType).constraint(constraintString);
    }

    protected void declareExistsConstraint(final CEDescrBuilder<?, ?> existsBuilder, final String patternType, final List<KiePMMLOperatorValue> kiePMMLOperatorValues) {
        String constraintString = kiePMMLOperatorValues.stream()
                .map(kiePMMLOperatorValue -> String.format(VALUE_PATTERN, kiePMMLOperatorValue.getOperator(), kiePMMLOperatorValue.getValue()))
                .collect(Collectors.joining(" || "));
        existsBuilder.pattern(patternType).constraint(constraintString);
    }

    protected void declareConstraintIn(final String patternType, final List<Object> values) {
        String constraints = getInNotInConstraint(values);
        builder.pattern(patternType).constraint(constraints);
    }

    protected void declareConstraintNotIn(final String patternType, final List<Object> values) {
        String constraints = getInNotInConstraint(values);
        builder.not().pattern(patternType).constraint(constraints);
    }

    protected void declareIfBreak(String ifBreakField, String ifBreakOperator, Object ifBreakValue) {
        builder.pattern(ifBreakField).id(INPUT_FIELD, false);
        final ConditionalBranchDescrBuilder<CEDescrBuilder<RuleDescrBuilder, AndDescr>> condBranchBuilder = builder.conditionalBranch();
        condBranchBuilder.condition().constraint(String.format(INPUT_FIELD_CONDITIONAL, ifBreakOperator, ifBreakValue));
        condBranchBuilder.consequence().breaking(true).name(BREAK_LABEL);
    }

    protected String getInNotInConstraint(final List<Object> values) {
        String expressionString = values.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ", "(", ")"));
        return String.format(VALUE_PATTERN, "in", expressionString);
    }
}
