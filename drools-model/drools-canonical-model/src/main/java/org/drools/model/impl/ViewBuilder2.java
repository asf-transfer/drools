/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.model.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.drools.model.Condition;
import org.drools.model.Consequence;
import org.drools.model.Pattern;
import org.drools.model.PatternDSL.PatternBindingImpl;
import org.drools.model.PatternDSL.PatternDef;
import org.drools.model.PatternDSL.PatternExprImpl;
import org.drools.model.PatternDSL.PatternItem;
import org.drools.model.RuleItem;
import org.drools.model.RuleItemBuilder;
import org.drools.model.consequences.NamedConsequenceImpl;
import org.drools.model.patterns.AccumulatePatternImpl;
import org.drools.model.patterns.CompositePatterns;
import org.drools.model.patterns.ExistentialPatternImpl;
import org.drools.model.patterns.PatternImpl;
import org.drools.model.view.AccumulateExprViewItem;
import org.drools.model.view.CombinedExprViewItem;
import org.drools.model.view.ExistentialExprViewItem;
import org.drools.model.view.ViewItem;

import static java.util.stream.Collectors.toList;

import static org.drools.model.impl.NamesGenerator.generateName;

public class ViewBuilder2 {

    private ViewBuilder2() { }

    public static CompositePatterns viewItems2Patterns( RuleItemBuilder<?>[] viewItemBuilders ) {
        List<RuleItem> ruleItems = Stream.of( viewItemBuilders ).map( RuleItemBuilder::get ).collect( toList() );
        Iterator<RuleItem> ruleItemIterator = ruleItems.iterator();

        List<Condition> conditions = new ArrayList<>();
        Map<String, Consequence> consequences = new LinkedHashMap<>();

        while (ruleItemIterator.hasNext()) {
            RuleItem ruleItem = ruleItemIterator.next();

            if (ruleItem instanceof Consequence) {
                Consequence consequence = (Consequence) ruleItem;
                String name = ruleItemIterator.hasNext() ? generateName("consequence") : RuleImpl.DEFAULT_CONSEQUENCE_NAME;
                consequences.put(name, consequence);
                conditions.add( new NamedConsequenceImpl( name, consequence.isBreaking() ) );
                continue;
            }

            conditions.add( ruleItem2Condition( ruleItem ) );
        }

        return new CompositePatterns( Condition.Type.AND, conditions, consequences );
    }

    private static Condition ruleItem2Condition(RuleItem ruleItem) {
        if ( ruleItem instanceof PatternDef ) {
            PatternDef patternDef = ( PatternDef ) ruleItem;
            PatternImpl pattern = new PatternImpl( patternDef.getFirstVariable() );
            for (PatternItem patternItem : patternDef.getItems()) {
                if ( patternItem instanceof PatternExprImpl ) {
                    pattern.addConstraint( (( PatternExprImpl ) patternItem).asConstraint( patternDef ) );
                } else if ( patternItem instanceof PatternBindingImpl ) {
                    pattern.addBinding( (( PatternBindingImpl ) patternItem).asBinding( patternDef ) );
                }
            }
            return pattern;
        }

        if ( ruleItem instanceof CombinedExprViewItem ) {
            CombinedExprViewItem combined = ( CombinedExprViewItem ) ruleItem;
            List<Condition> conditions = new ArrayList<>();
            for (ViewItem expr : combined.getExpressions()) {
                conditions.add(ruleItem2Condition( expr ));
            }
            return new CompositePatterns( combined.getType(), conditions );
        }

        if ( ruleItem instanceof ExistentialExprViewItem ) {
            ExistentialExprViewItem existential = (ExistentialExprViewItem) ruleItem;
            return new ExistentialPatternImpl( ruleItem2Condition( existential.getExpression() ), existential.getType() );
        }

        if ( ruleItem instanceof AccumulateExprViewItem ) {
            AccumulateExprViewItem acc = (AccumulateExprViewItem) ruleItem;

            Condition newCondition = ruleItem2Condition( acc.getExpr() );
            if (newCondition instanceof Pattern ) {
                return new AccumulatePatternImpl((Pattern) newCondition, Optional.empty(), acc.getAccumulateFunctions());
            } else if (newCondition instanceof CompositePatterns) {
                return new AccumulatePatternImpl(null, Optional.of(newCondition), acc.getAccumulateFunctions());
            } else {
                throw new RuntimeException("Unknown pattern");
            }
        }

        throw new UnsupportedOperationException( "Unknown " + ruleItem );
    }
}
