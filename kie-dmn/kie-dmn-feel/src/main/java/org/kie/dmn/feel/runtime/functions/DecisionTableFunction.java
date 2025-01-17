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
package org.kie.dmn.feel.runtime.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.impl.FEELBuilder;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.runtime.decisiontables.DTDecisionRule;
import org.kie.dmn.feel.runtime.decisiontables.DTInputClause;
import org.kie.dmn.feel.runtime.decisiontables.DTOutputClause;
import org.kie.dmn.feel.runtime.decisiontables.DecisionTableImpl;
import org.kie.dmn.feel.runtime.decisiontables.HitPolicy;
import org.kie.dmn.feel.runtime.events.FEELEventBase;
import org.kie.dmn.feel.util.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionTableFunction
        extends BaseFEELFunction {

    public static final DecisionTableFunction INSTANCE = new DecisionTableFunction();

    private static final Logger LOG = LoggerFactory.getLogger( DecisionTableFunction.class );

    private DecisionTableFunction() {
        super( "decision table" );
    }

    /**
     @param inputExpressionList a list of the N>=0 input expressions in display order
     @param inputValuesList * a list of N input values, corresponding to the input expressions. Each
     list element is a unary tests literal (see below).
     @param outputs * a name (a string matching grammar rule 27) or a list of M>0 names
     @param outputValues * if outputs is a list, then output values is a list of lists of values, one list
     per output; else output values is a list of values for the one output.
     Each value is a string.
     @param ruleList a list of R>0 rules. A rule is a list of N input entries followed by M
     output entries. An input entry is a unary tests literal. An output entry is
     an expression represented as a string.
     @param hitPolicy * one of: "U", "A", “P”, “F”, "R", "O", "C", "C+", "C#", "C<", “C>”
     (default is "U")
     @param defaultOutputValue * if outputs is a list, then default output value is a context with entries
     composed of outputs and output values; else default output value is one
     of the output values.
     */
    public Object invoke(@ParameterName("ctx") EvaluationContext ctx, 
            @ParameterName("outputs") Object outputs,
            @ParameterName("input expression list") Object inputExpressionList,
            @ParameterName("input values list") List<?> inputValuesList,
            @ParameterName("output values") Object outputValues,
            @ParameterName("rule list") List<List> ruleList,
            @ParameterName("hit policy") String hitPolicy,
            @ParameterName("default output value") Object defaultOutputValue) {
        // input expression list can have a single element or be a list
        // TODO isn't ^ conflicting with the specs page 136 "input expression list: a LIST of the"
        List<String> inputExpressions = inputExpressionList instanceof List ? (List) inputExpressionList : Collections.singletonList( (String) inputExpressionList );

        List<DTInputClause> inputs;
        if ( inputValuesList != null ) {
            List<UnaryTest> inputValues = inputValuesList.stream().map( o -> toUnaryTest(ctx, o) ).collect( Collectors.toList() );
            if ( inputValues.size() != inputExpressions.size() ) {
                // TODO handle compilation error
            }
            // zip inputExpression with its inputValue
            inputs = IntStream.range( 0, inputExpressions.size() )
                    .mapToObj( i -> new DTInputClause( inputExpressions.get( i ), inputValuesList.toString(), Collections.singletonList( inputValues.get( i ) ), null, false) )
                    .collect( Collectors.toList() );
        } else {
            inputs = inputExpressions.stream().map( ie -> new DTInputClause( ie, null, null, null, false ) ).collect( Collectors.toList() );
        }

        List<String> parseOutputs = outputs instanceof List ? (List) outputs : Collections.singletonList( (String) outputs );
        List<DTOutputClause> outputClauses;
        if ( outputValues != null ) {
            if ( parseOutputs.size() == 1 ) {
                outputClauses = new ArrayList<>();
                List<UnaryTest> outputValuesCompiled = objectToUnaryTestList( ctx, Collections.singletonList( (List<Object>) outputValues ) ).get(0);
                outputClauses.add( new DTOutputClause( parseOutputs.get( 0 ), outputValuesCompiled ) );
            } else {
                List<List<UnaryTest>> listOfList = objectToUnaryTestList( ctx, (List<List<Object>>) outputValues );
                // zip inputExpression with its inputValue
                outputClauses = IntStream.range( 0, parseOutputs.size() )
                        .mapToObj( i -> new DTOutputClause( parseOutputs.get( i ), listOfList.get( i ) ) )
                        .collect( Collectors.toList() );
            }
        } else {
            outputClauses = parseOutputs.stream().map( out -> new DTOutputClause( out, null ) ).collect( Collectors.toList() );
        }

        // TODO parse default output value.
        FEEL feel = FEELBuilder.builder().build();
        List<DTDecisionRule> decisionRules = IntStream.range( 0, ruleList.size() )
                .mapToObj( index -> toDecisionRule( ctx, feel, index, ruleList.get( index ), inputExpressions.size() ) )
                .collect( Collectors.toList() );

        // TODO is there a way to avoid UUID and get from _evaluation_ ctx the name of the wrapping context? 
        // TODO also in this case it is using an ad-hoc created FEEL instance instead of the "hosted" one.
        DecisionTableImpl dti = new DecisionTableImpl(UUID.randomUUID().toString(), inputExpressions, inputs, outputClauses, decisionRules, HitPolicy.fromString(hitPolicy), FEELBuilder.builder().build());
        return new DTInvokerFunction( dti );
    }

    protected List<List<UnaryTest>> objectToUnaryTestList(EvaluationContext ctx, List<List<Object>> values) {
        if ( values == null || values.isEmpty() ) {
            return Collections.emptyList();
        }
        List<List<UnaryTest>> tests = new ArrayList<>();
        for ( List<Object> lo : values ) {
            List<UnaryTest> uts = new ArrayList<>(  );
            tests.add(uts);
            for( Object t : lo ) {
                uts.add( toUnaryTest( ctx, t ) );
            }
        }
        return tests;
    }

    /**
     * Convert row to DTDecisionRule
     * @param mainCtx the main context is used to identify the hosted FEELEventManager
     * @param embeddedFEEL a possibly cached embedded FEEL to compile the output expression, error will be reported up to the mainCtx
     * @param index
     * @param rule
     * @param inputSize
     * @return
     */
    private static DTDecisionRule toDecisionRule(EvaluationContext mainCtx, FEEL embeddedFEEL, int index, List<?> rule, int inputSize) {
        // TODO should be check indeed block of inputSize n inputs, followed by block of outputs.
        DTDecisionRule dr = new DTDecisionRule( index, null );
        for ( int i = 0; i < rule.size(); i++ ) {
            Object o = rule.get( i );
            if ( i < inputSize ) {
                dr.getInputEntry().add( toUnaryTest( mainCtx, o ) );
            } else {
                FEELEventListener ruleListener = event -> mainCtx.notifyEvt( () -> new FEELEventBase(event.getSeverity(),
                                                                                                     Msg.createMessage(Msg.ERROR_COMPILE_EXPR_DT_FUNCTION_RULE_IDX, index+1, event.getMessage()),
                                                                                                     event.getSourceException()));
                embeddedFEEL.addListener(ruleListener);
                CompiledExpression compiledExpression = embeddedFEEL.compile((String) o, embeddedFEEL.newCompilerContext());
                dr.getOutputEntry().add( compiledExpression );
                embeddedFEEL.removeListener(ruleListener);
            }
        }
        return dr;
    }

    private static UnaryTest toUnaryTest(EvaluationContext ctx, Object o) {
        if ( o instanceof UnaryTest ) {
            return (UnaryTest) o;
        } else if ( o instanceof Range ) {
            return (c, x) -> {
                try {
                    return ((Range) o).includes(ctx.getFEELDialect(), x );
                } catch ( Exception e ) {
                    ctx.notifyEvt( () -> new FEELEventBase( FEELEvent.Severity.ERROR,
                                                            Msg.createMessage( Msg.EXPRESSION_IS_RANGE_BUT_VALUE_IS_NOT_COMPARABLE, o.toString(), x.toString() ),
                                                            e ) );
                    throw e;
                }
            };
        } else if ( o instanceof List ) {
            return (c, x) -> ((List<?>) o).contains( x );
        } else {
            return (c, x) -> x.equals( o );
        }
    }

}
