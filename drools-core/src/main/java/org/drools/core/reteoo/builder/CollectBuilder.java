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
package org.drools.core.reteoo.builder;

import java.util.ArrayList;
import java.util.List;

import org.drools.base.rule.Accumulate;
import org.drools.base.rule.Collect;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.rule.SingleAccumulate;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.base.accumulators.CollectAccumulator;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.TupleStartEqualsConstraint;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.base.rule.constraint.BetaConstraint;

public class CollectBuilder
    implements
    ReteooComponentBuilder {

    /**
     * @inheritDoc
     */
    public void build(final BuildContext context,
                      final BuildUtils utils,
                      final RuleConditionElement rce) {

        boolean existSubNetwort = false;
        final Collect collect = (Collect) rce;
        context.pushRuleComponent( collect );

        final List<BetaConstraint>           resultBetaConstraints  = context.getBetaconstraints();
        final List<AlphaNodeFieldConstraint> resultAlphaConstraints = context.getAlphaConstraints();

        final Pattern sourcePattern = collect.getSourcePattern();

        // get builder for the pattern
        final ReteooComponentBuilder builder = utils.getBuilderFor( sourcePattern );

        // save tuple source and pattern offset for later if needed
        final LeftTupleSource tupleSource = context.getTupleSource();

        // builds the source pattern
        builder.build( context,
                       utils,
                       sourcePattern );

        // if object source is null, then we need to adapt tuple source into a subnetwork
        if ( context.getObjectSource() == null ) {
            TupleToObjectNode tton = CoreComponentFactory.get().getNodeFactoryService().buildRightInputNode(context.getNextNodeId(),
                                                                                                               context.getTupleSource(),
                                                                                                               tupleSource,
                                                                                                               context);

            // attach right input adapter node to convert tuple source into an object source
            context.setObjectSource( utils.attachNode( context, tton ) );

            // restore tuple source from before the start of the sub network
            context.setTupleSource( tupleSource );

            // create a tuple start equals constraint and set it in the context
            final TupleStartEqualsConstraint constraint      = TupleStartEqualsConstraint.getInstance();
            final List<BetaConstraint>       betaConstraints = new ArrayList<>();
            betaConstraints.add( constraint );
            context.setBetaconstraints( betaConstraints );
            existSubNetwort = true;
        }

        BetaConstraints binder = utils.createBetaNodeConstraint( context,
                                                                 context.getBetaconstraints(),
                                                                 false );
        // indexing for the results should be always disabled
        BetaConstraints resultBinder = utils.createBetaNodeConstraint( context,
                                                                       resultBetaConstraints,
                                                                       true );

        // The node needs to declare the vars it returns
        Pattern resultPattern = collect.getResultPattern();

        Accumulate accumulate = new SingleAccumulate( sourcePattern,
                                                      sourcePattern.getRequiredDeclarations(),
                                                      new CollectAccumulator( collect, existSubNetwort ) );

        AccumulateNode accNode = CoreComponentFactory.get().getNodeFactoryService().buildAccumulateNode( context.getNextNodeId(),
                                                                                                            context.getTupleSource(),
                                                                                                            context.getObjectSource(),
                                                                                                            resultAlphaConstraints.toArray( new AlphaNodeFieldConstraint[resultAlphaConstraints.size()] ),
                                                                                                            binder, // source binder
                                                                                                            resultBinder,
                                                                                                            accumulate,
                                                                                                            context );
        context.setTupleSource( utils.attachNode( context, accNode ) );

        // source pattern was bound, so nulling context
        context.setObjectSource( null );
        context.popRuleComponent();
    }

    /**
     * @inheritDoc
     */
    public boolean requiresLeftActivation(final BuildUtils utils,
                                          final RuleConditionElement rce) {
        return true;
    }

}
