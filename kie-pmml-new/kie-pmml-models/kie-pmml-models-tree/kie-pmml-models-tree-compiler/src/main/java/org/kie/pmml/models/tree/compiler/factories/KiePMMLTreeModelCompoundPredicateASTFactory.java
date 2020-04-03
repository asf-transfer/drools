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
package org.kie.pmml.models.tree.compiler.factories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import org.dmg.pmml.CompoundPredicate;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.drools.core.util.StringUtils;
import org.kie.pmml.commons.enums.StatusCode;
import org.kie.pmml.commons.exceptions.KiePMMLException;
import org.kie.pmml.models.drooled.ast.KiePMMLDrooledRule;
import org.kie.pmml.models.drooled.tuples.KiePMMLFieldOperatorValue;
import org.kie.pmml.models.drooled.tuples.KiePMMLOperatorValue;
import org.kie.pmml.models.drooled.tuples.KiePMMLOriginalTypeGeneratedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLASTFactoryUtils.getConstraintEntryFromSimplePredicates;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLASTFactoryUtils.getXORConstraintEntryFromSimplePredicates;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLTreeModelASTFactory.STATUS_NULL;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLTreeModelASTFactory.STATUS_PATTERN;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLTreeModelASTFactory.SURROGATE_GROUP_PATTERN;

/**
 * Class used to generate <code>KiePMMLDrooledRule</code>s out of a <code>CompoundPredicate</code>
 */
public class KiePMMLTreeModelCompoundPredicateASTFactory {

    private static final Logger logger = LoggerFactory.getLogger(KiePMMLTreeModelCompoundPredicateASTFactory.class.getName());
    private final CompoundPredicate compoundPredicate;
    private final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap;
    private final Queue<KiePMMLDrooledRule> rules;

    private KiePMMLTreeModelCompoundPredicateASTFactory(final CompoundPredicate compoundPredicate, final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap, final Queue<KiePMMLDrooledRule> rules) {
        this.compoundPredicate = compoundPredicate;
        this.fieldTypeMap = fieldTypeMap;
        this.rules = rules;
    }

    public static KiePMMLTreeModelCompoundPredicateASTFactory factory(final CompoundPredicate compoundPredicate, final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap, final Queue<KiePMMLDrooledRule> rules) {
        return new KiePMMLTreeModelCompoundPredicateASTFactory(compoundPredicate, fieldTypeMap, rules);
    }

    public void declareRuleFromCompoundPredicate(final String parentPath,
                                                 final String currentRule,
                                                 final Object result,
                                                 boolean isFinalLeaf) {
        logger.debug("declareIntermediateRuleFromCompoundPredicate {} {} {} {}", compoundPredicate, parentPath, currentRule, result);
        switch (compoundPredicate.getBooleanOperator()) {
            case SURROGATE:
                declareRuleFromCompoundPredicateSurrogate(parentPath, currentRule, result, isFinalLeaf);
                break;
            case AND:
                declareRuleFromCompoundPredicateAndOrXor(parentPath, currentRule, result, isFinalLeaf);
                break;
            case OR:
                declareRuleFromCompoundPredicateAndOrXor(parentPath, currentRule, result, isFinalLeaf);
                break;
            case XOR:
                declareRuleFromCompoundPredicateAndOrXor(parentPath, currentRule, result, isFinalLeaf);
                break;
        }
    }

    public void declareRuleFromCompoundPredicateAndOrXor(final String parentPath,
                                                         final String currentRule,
                                                         final Object result,
                                                         boolean isFinalLeaf) {
        logger.debug("declareIntermediateRuleFromCompoundPredicateAndOrXor {} {} {}", compoundPredicate, parentPath, currentRule);
        String statusConstraint = StringUtils.isEmpty(parentPath) ? STATUS_NULL : String.format(STATUS_PATTERN, parentPath);
        // Managing only SimplePredicates for the moment being
        final List<Predicate> simplePredicates = compoundPredicate.getPredicates().stream().filter(predicate -> predicate instanceof SimplePredicate).collect(Collectors.toList());
        if (CompoundPredicate.BooleanOperator.XOR.equals((compoundPredicate.getBooleanOperator()))) {
            if (simplePredicates.size() < 2) {
                throw new KiePMMLException("At least two elements expected for XOR operations");
            }
            if (simplePredicates.size() > 2) {
                // Not managed yet
                throw new KiePMMLException("More then two elements not managed, yet, for XOR operations");
            }
        }
        final Map<String, List<SimplePredicate>> predicatesByField = simplePredicates.stream()
                .map(child -> (SimplePredicate) child)
                .collect(groupingBy(child -> fieldTypeMap.get(child.getField().getValue()).getGeneratedType()));
        final Map<String, List<KiePMMLOperatorValue>> constraints = new HashMap<>();
        String statusToSet = isFinalLeaf ? StatusCode.DONE.getName() : currentRule;
        KiePMMLDrooledRule.Builder builder = KiePMMLDrooledRule.builder(currentRule, statusToSet)
                .withStatusConstraint(statusConstraint);
        switch (compoundPredicate.getBooleanOperator()) {
            case AND:
                predicatesByField.forEach((fieldName, predicates) -> constraints.putAll(getConstraintEntryFromSimplePredicates(fieldName, predicates, fieldTypeMap)));
                builder = builder.withAndConstraints(constraints);
                break;
            case OR:
                predicatesByField.forEach((fieldName, predicates) -> constraints.putAll(getConstraintEntryFromSimplePredicates(fieldName, predicates, fieldTypeMap)));
                builder = builder.withOrConstraints(constraints);
                break;
            case XOR:
                List<KiePMMLFieldOperatorValue> xorConstraints = getXORConstraintEntryFromSimplePredicates(simplePredicates, fieldTypeMap);
                builder = builder.withXorConstraints(xorConstraints);
                break;
            default:
                break;
        }
        if (isFinalLeaf) {
            builder = builder.withResult(result)
                    .withResultCode(StatusCode.OK);
        }
        rules.add(builder.build());
    }

    public void declareRuleFromCompoundPredicateSurrogate(final String parentPath,
                                                          final String currentRule,
                                                          final Object result,
                                                          boolean isFinalLeaf) {
        logger.debug("declareRuleFromCompoundPredicateSurrogate {} {} {} {}", compoundPredicate, parentPath, currentRule, result);
        final String agendaActivationGroup = String.format(SURROGATE_GROUP_PATTERN, currentRule);
        KiePMMLDrooledRule.Builder builder = KiePMMLDrooledRule.builder(currentRule, null)
                .withStatusConstraint(String.format(STATUS_PATTERN, parentPath))
                .withFocusedAgendaGroup(agendaActivationGroup);
        rules.add(builder.build());
        // Managing only SimplePredicates for the moment being
        final List<Predicate> simplePredicates = compoundPredicate.getPredicates().stream().filter(predicate -> predicate instanceof SimplePredicate).collect(Collectors.toList());
        simplePredicates.forEach(predicate -> {
            SimplePredicate simplePredicate = (SimplePredicate) predicate;
            KiePMMLTreeModelSimplePredicateASTFactory.factory(simplePredicate, fieldTypeMap, rules).declareRuleFromSimplePredicateSurrogate(parentPath, currentRule, agendaActivationGroup, result, isFinalLeaf);
        });
    }
}
