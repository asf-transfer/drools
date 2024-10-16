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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.InitialFact;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElement.Type;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.RuleConditionElement;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BaseNode;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.TupleStartEqualsConstraint;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.base.rule.constraint.BetaConstraint;
import org.kie.api.definition.rule.Propagation;

public class GroupElementBuilder
        implements
        ReteooComponentBuilder {

    protected final Map<Type, ReteooComponentBuilder> geBuilders = new HashMap<>();

    public GroupElementBuilder() {
        this.geBuilders.put( GroupElement.AND,
                             new AndBuilder() );
        this.geBuilders.put( GroupElement.OR,
                             new OrBuilder() );
        this.geBuilders.put( GroupElement.NOT,
                             new NotBuilder() );
        this.geBuilders.put( GroupElement.EXISTS,
                             new ExistsBuilder() );
    }

    /**
     * @inheritDoc
     */
    public void build(final BuildContext context,
                      final BuildUtils utils,
                      final RuleConditionElement rce) {
        final GroupElement ge = (GroupElement) rce;

        final ReteooComponentBuilder builder = this.geBuilders.get( ge.getType() );

        context.push( ge );
        context.pushRuleComponent( ge );

        builder.build( context,
                       utils,
                       rce );

        context.pop();
        context.popRuleComponent();
    }

    /**
     * @inheritDoc
     */
    public boolean requiresLeftActivation(final BuildUtils utils,
                                          final RuleConditionElement rce) {
        final GroupElement ge = (GroupElement) rce;

        final ReteooComponentBuilder builder = this.geBuilders.get( ge.getType() );

        return builder.requiresLeftActivation( utils,
                                               rce );
    }

    public static class AndBuilder
            implements
            ReteooComponentBuilder {

        /**
         * @inheritDoc
         *
         * And group elements just iterate over their children
         * selecting and calling the build procedure for each one
         */
        public void build(final BuildContext context,
                          final BuildUtils utils,
                          final RuleConditionElement rce) {

            final GroupElement ge = (GroupElement) rce;

            if (ge.getChildren().size() == 1) {
                RuleConditionElement child = ge.getChildren().get(0);
                final ReteooComponentBuilder builder = utils.getBuilderFor(child);
                builder.build( context, utils, child );
                buildTupleSource( context, utils, isTerminalAlpha( context, child ) );
            } else {

                for (final RuleConditionElement child : ge.getChildren()) {
                    final ReteooComponentBuilder builder = utils.getBuilderFor( child );
                    builder.build( context, utils, child );
                    buildTupleSource( context, utils, false );
                    buildJoinNode( context, utils );
                }
            }
        }

        private boolean isTerminalAlpha( BuildContext context, RuleConditionElement child ) {
            boolean isInitialFact = ((Pattern) child).getObjectType().isAssignableTo(InitialFact.class);
            boolean hasTimer = context.getRule().getTimer() != null;
            RuleBaseConfiguration conf = context.getRuleBase().getRuleBaseConfiguration();
            boolean lockOnActive = context.getRule().isLockOnActive();
            boolean eager = context.getRule().getMetaData(Propagation.class.getName()) != null || context.getRule().getMetaData(Propagation.class.getSimpleName()) != null;
            return !isInitialFact && !hasTimer && !lockOnActive && !eager &&
                    !conf.isParallelEvaluation() && !conf.isSequential() && !conf.isDeclarativeAgenda();
        }

        public static void buildTupleSource(BuildContext context, BuildUtils utils, boolean terminal) {
            // if a previous object source was bound, but no tuple source
            if (context.getObjectSource() != null && context.getTupleSource() == null) {
                // we know this is the root OTN, so record it
                BaseNode source = context.getObjectSource();
                while ( !(source.getType() ==  NodeTypeEnums.ObjectTypeNode ) ) {
                    source = source.getParent();
                }
                context.setRootObjectTypeNode( (ObjectTypeNode) source );


                // adapt it to a Tuple source
                context.setTupleSource( utils.attachNode( context,
                        CoreComponentFactory.get().getNodeFactoryService()
                                                                 .buildLeftInputAdapterNode( context.getNextNodeId(),
                                                                                             context.getObjectSource(),
                                                                                             context, terminal ) ) );
                context.setObjectSource( null );
            }
        }

        public static void buildJoinNode(BuildContext context, BuildUtils utils) {
            // if there was a previous tuple source, then a join node is needed
            if (context.getObjectSource() != null && context.getTupleSource() != null) {
                // so, create the tuple source and clean up the constraints and object source
                final BetaConstraints betaConstraints = utils.createBetaNodeConstraint( context,
                                                                                        context.getBetaconstraints(),
                                                                                        false );

                JoinNode joinNode = CoreComponentFactory.get()
                                           .getNodeFactoryService().buildJoinNode( context.getNextNodeId(),
                                                                                   context.getTupleSource(),
                                                                                   context.getObjectSource(),
                                                                                   betaConstraints,
                                                                                   context);

                context.setTupleSource( utils.attachNode( context, joinNode));
                context.setBetaconstraints( null );
                context.setObjectSource( null );
            }
        }

        public boolean requiresLeftActivation(final BuildUtils utils,
                                              final RuleConditionElement rce) {
            final GroupElement and = (GroupElement) rce;

            // need to check this because in the case of an empty rule, the root AND
            // will have no child
            if ( and.getChildren().isEmpty() ) {
                return true;
            }

            final RuleConditionElement child = and.getChildren().get( 0 );
            final ReteooComponentBuilder builder = utils.getBuilderFor( child );

            return builder.requiresLeftActivation( utils,
                                                   child );
        }
    }

    public static class OrBuilder
            implements
            ReteooComponentBuilder {

        /**
         * @inheritDoc
         */
        public void build(final BuildContext context,
                          final BuildUtils utils,
                          final RuleConditionElement rce) {
            throw new RuntimeException( "BUG: Can't build a rete network with an inner OR group element" );
        }

        public boolean requiresLeftActivation(final BuildUtils utils,
                                              final RuleConditionElement rce) {
            throw new RuntimeException( "BUG: Can't build a rete network with an inner OR group element" );
        }
    }

    public static class NotBuilder
            implements
            ReteooComponentBuilder {

        /**
         * @inheritDoc
         *
         * Not must verify what is the class of its child:
         *
         * If it is a pattern, a simple NotNode is added to the rulebase
         * If it is a group element, than a subnetwork must be created
         */
        public void build(final BuildContext context,
                          final BuildUtils utils,
                          final RuleConditionElement rce) {
            final GroupElement not = (GroupElement) rce;

            final LeftTupleSource tupleSource = context.getTupleSource();

            // get child
            final RuleConditionElement child = not.getChildren().get( 0 );

            // get builder for child
            final ReteooComponentBuilder builder = utils.getBuilderFor( child );

            // builds the child
            builder.build( context,
                           utils,
                           child );

            // if it is a subnetwork
            if ( context.getObjectSource() == null && context.getTupleSource() != null ) {
                TupleToObjectNode tton = CoreComponentFactory.get().getNodeFactoryService().buildRightInputNode(context.getNextNodeId(),
                                                                                                                   context.getTupleSource(),
                                                                                                                   tupleSource,
                                                                                                                   context);

                // attach right input adapter node to convert tuple source into an object source
                context.setObjectSource( utils.attachNode( context, tton ) );

                // restore tuple source from before the start of the sub network
                context.setTupleSource( tupleSource );

                // create a tuple start equals constraint and set it in the context
                final TupleStartEqualsConstraint constraint = TupleStartEqualsConstraint.getInstance();
                final List<BetaConstraint>       predicates = new ArrayList<>();
                predicates.add( constraint );
                context.setBetaconstraints( predicates );
            }

            NodeFactory nfactory = CoreComponentFactory.get().getNodeFactoryService();

            final BetaConstraints betaConstraints = utils.createBetaNodeConstraint( context,
                                                                                    context.getBetaconstraints(),
                                                                                    false );
            // then attach the NOT node. It will work both as a simple not node
            // or as subnetwork join node as the context was set appropriatelly
            // in each case


            NotNode node = nfactory.buildNotNode( context.getNextNodeId(),
                                                  context.getTupleSource(),
                                                  context.getObjectSource(),
                                                  betaConstraints,
                                                  context );

            node.setEmptyBetaConstraints( context.getBetaconstraints().isEmpty() );

            context.setTupleSource( utils.attachNode( context, node ) );
            context.setBetaconstraints( null );
            context.setObjectSource( null );
        }

        public boolean requiresLeftActivation(final BuildUtils utils,
                                              final RuleConditionElement rce) {
            return true;
        }
    }

    public static class ExistsBuilder
            implements
            ReteooComponentBuilder {

        /**
         * @inheritDoc
         *
         * Exists must verify what is the class of its child:
         *
         * If it is a pattern, a simple ExistsNode is added to the rulebase
         * If it is a group element, than a subnetwork must be created
         */
        public void build(final BuildContext context,
                          final BuildUtils utils,
                          final RuleConditionElement rce) {
            final GroupElement exists = (GroupElement) rce;

            final LeftTupleSource tupleSource = context.getTupleSource();

            // get child
            final RuleConditionElement child = exists.getChildren().get( 0 );

            // get builder for child
            final ReteooComponentBuilder builder = utils.getBuilderFor( child );

            // builds the child
            builder.build( context,
                           utils,
                           child );

            // if it is a subnetwork
            if ( context.getObjectSource() == null && context.getTupleSource() != null ) {
                TupleToObjectNode tton = CoreComponentFactory.get().getNodeFactoryService().buildRightInputNode(context.getNextNodeId(),
                                                                                                                   context.getTupleSource(),
                                                                                                                   tupleSource,
                                                                                                                   context);

                // attach right input adapter node to convert tuple source into an object source
                context.setObjectSource( utils.attachNode( context, tton ) );

                // restore tuple source from before the start of the sub network
                context.setTupleSource( tupleSource );


                final List<BetaConstraint> betaConstraints = new ArrayList<>();
                context.setBetaconstraints( betaConstraints ); // Empty list ensures EmptyBetaConstraints is assigned
            }

            NodeFactory nfactory = CoreComponentFactory.get().getNodeFactoryService();

            final BetaConstraints betaConstraints = utils.createBetaNodeConstraint( context,
                                                                                    context.getBetaconstraints(),
                                                                                    false );

            ExistsNode node = nfactory.buildExistsNode(context.getNextNodeId(),
                                                       context.getTupleSource(),
                                                       context.getObjectSource(),
                                                       betaConstraints,
                                                       context);

            // then attach the EXISTS node. It will work both as a simple exists node
            // or as subnetwork join node as the context was set appropriatelly
            // in each case
            context.setTupleSource( utils.attachNode( context, node ) );
            context.setBetaconstraints( null );
            context.setObjectSource( null );
        }

        /**
         * @inheritDoc
         */
        public boolean requiresLeftActivation(final BuildUtils utils,
                                              final RuleConditionElement rce) {
            return true;
        }
    }

}
