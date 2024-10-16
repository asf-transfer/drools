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
import java.util.Collection;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.AsyncReceive;
import org.drools.base.rule.AsyncSend;
import org.drools.base.rule.Collect;
import org.drools.base.rule.ConditionalBranch;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.EvalCondition;
import org.drools.base.rule.Forall;
import org.drools.base.rule.From;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.InvalidPatternException;
import org.drools.base.rule.LogicTransformer;
import org.drools.base.rule.NamedConsequence;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.QueryElement;
import org.drools.base.rule.WindowDeclaration;
import org.drools.base.rule.WindowReference;
import org.drools.base.rule.constraint.XpathConstraint;
import org.drools.base.time.impl.Timer;
import org.drools.core.ActivationListenerFactory;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.UpdateContext;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.PhreakBuilder;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.RuleBuilder;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.WindowNode;
import org.drools.core.time.TemporalDependencyMatrix;
import org.kie.api.conf.EventProcessingOption;

public class ReteooRuleBuilder implements RuleBuilder {

    protected BuildUtils utils;

    public ReteooRuleBuilder() {
        this.utils = new BuildUtils();

        this.utils.addBuilder( GroupElement.class,
                               new GroupElementBuilder() );
        this.utils.addBuilder( Pattern.class,
                               new PatternBuilder() );
        this.utils.addBuilder( EvalCondition.class,
                               new EvalBuilder() );
        this.utils.addBuilder( QueryElement.class,
                               new QueryElementBuilder() );
        this.utils.addBuilder( From.class,
                               new FromBuilder() );
        this.utils.addBuilder( Collect.class,
                               new CollectBuilder() );
        this.utils.addBuilder( Accumulate.class,
                               new AccumulateBuilder() );
        this.utils.addBuilder( Timer.class,
                               new TimerBuilder() );
        this.utils.addBuilder( Forall.class,
                               new ForallBuilder() );
        this.utils.addBuilder( EntryPointId.class,
                               new EntryPointBuilder() );
        this.utils.addBuilder( WindowReference.class, 
                               new WindowReferenceBuilder() );
        this.utils.addBuilder( NamedConsequence.class,
                               new NamedConsequenceBuilder() );
        this.utils.addBuilder( ConditionalBranch.class,
                               new ConditionalBranchBuilder() );
        this.utils.addBuilder( XpathConstraint.class,
                               new ReactiveFromBuilder() );
        this.utils.addBuilder( AsyncSend.class,
                               new AsyncSendBuilder() );
        this.utils.addBuilder( AsyncReceive.class,
                               new AsyncReceiveBuilder() );
    }

    /**
     * Creates the corresponting Rete network for the given <code>Rule</code> and adds it to
     * the given rule base.
     * 
     * @param rule
     *            The rule to add.
     * @param kBase
     *            The rulebase to add the rule to.
     *            
     * @return a List<BaseNode> of terminal nodes for the rule             
     * @throws InvalidPatternException
     */
    public List<TerminalNode> addRule(RuleImpl rule, InternalRuleBase kBase, Collection<InternalWorkingMemory> workingMemories) throws InvalidPatternException {

        // the list of terminal nodes
        final List<TerminalNode> termNodes = new ArrayList<>();

        // transform rule and gets the array of subrules
        final GroupElement[] subrules = rule.getTransformedLhs( LogicTransformer.getInstance(), kBase.getGlobals() );

        for (int i = 0; i < subrules.length; i++) {
            // creates a clean build context for each subrule
            final BuildContext context = new BuildContext( kBase, workingMemories );
            context.setRule( rule );
            context.setSubRuleIndex( i );

            // if running in STREAM mode, calculate temporal distance for events
            if (EventProcessingOption.STREAM.equals( kBase.getRuleBaseConfiguration().getEventProcessingMode() )) {
                TemporalDependencyMatrix temporal = this.utils.calculateTemporalDistance( subrules[i] );
                context.setTemporalDistance( temporal );
            }

            if (kBase.getRuleBaseConfiguration().isSequential() ) {
                context.setTupleMemoryEnabled( false );
            } else {
                context.setTupleMemoryEnabled( true );
            }

            // adds subrule
            context.setSubRuleIndex(i);
            addSubRule( context, subrules[i], rule );
            // adds the terminal node to the list of terminal nodes

            termNodes.addAll(context.getTerminals());
        }

        return termNodes;
    }

    private void addSubRule(BuildContext context, GroupElement subrule, RuleImpl rule) throws InvalidPatternException {
        context.setSubRule(subrule);

        // gets the appropriate builder
        ReteooComponentBuilder builder = this.utils.getBuilderFor( subrule );

        // checks if an initial-fact is needed
        if (builder.requiresLeftActivation( this.utils,
                                            subrule )) {
            this.addInitialFactPattern( subrule );
        }

        // builds and attach
        builder.build( context,
                       this.utils,
                       subrule );

        TerminalNode terminal;
        if (!context.isTerminated()) {
            terminal = buildTerminal(context, subrule, rule, utils);
        } else {
            // from a non-conditional NamedConsequence. Conditionals do not generate subrules
            terminal = (TerminalNode) context.getLastNode();
        }

        attachTerminalNode(context, terminal);
    }

    private static TerminalNode buildTerminal(BuildContext context, GroupElement subrule, RuleImpl rule, BuildUtils utils) {
        return buildTerminalNodeForConsequence(context, subrule, context.getSubRuleIndex(), null, rule.getTimer(), utils);
    }

    public static TerminalNode buildTerminalNodeForConsequence(BuildContext context, GroupElement subrule, int subRuleIndex,
                                                               NamedConsequence namedConsequence, Timer timer, BuildUtils utils) {
        RuleImpl rule = context.getRule();
        if  (timer != null ) {
            ReteooComponentBuilder builder = utils.getBuilderFor( Timer.class );
            builder.build(context, utils, rule.getTimer());
        }

        ActivationListenerFactory factory = context.getRuleBase().getRuleBaseConfiguration().getActivationListenerFactory( rule.getActivationListener() );

        if (namedConsequence != null) {
            context.setConsequenceName(namedConsequence.getConsequenceName());
        }
        TerminalNode terminal = factory.createActivationListener( context.getNextNodeId(),
                                                                  context.getTupleSource(),
                                                                  rule,
                                                                  subrule,
                                                                  subRuleIndex,
                                                                  context );
        context.setConsequenceName( null );

        // adds the terminal node to the list of nodes created/added by this sub-rule
        context.getNodes().add((BaseNode) terminal);

        return terminal;
    }

    private static void attachTerminalNode(BuildContext context, TerminalNode terminalNode) {
        context.getTerminals().add(terminalNode);
        context.terminate();

        BaseNode baseTerminalNode = (BaseNode) terminalNode;
        context.getNodes().add(baseTerminalNode);
        baseTerminalNode.networkUpdated(new UpdateContext());
        baseTerminalNode.attach(context);

        setPathEndNodes(context, terminalNode);

        if (!PhreakBuilder.isEagerSegmentCreation() || context.getRuleBase().hasSegmentPrototypes()) {
            // only need to process this, if segment protos exist
            PhreakBuilder.get().addRule(terminalNode, context.getWorkingMemories(), context.getRuleBase());
        }
    }

    private static void setPathEndNodes(BuildContext context, TerminalNode terminalNode) {
        // Store the paths in reverse order, from the outermost (the main path) to the innermost subnetwork paths
        PathEndNode[] pathEndNodes = context.getPathEndNodes().toArray(new PathEndNode[context.getPathEndNodes().size()]);
        for ( int i = 0; i < pathEndNodes.length; i++ ) {
            PathEndNode node = context.getPathEndNodes().get(pathEndNodes.length-1-i);
            pathEndNodes[i] = node;
            if (node.getType() == NodeTypeEnums.TupleToObjectNode && node.getPathEndNodes() != null) {
                PathEndNode[] SubnetworkNodes = new PathEndNode[node.getPathEndNodes().length + i];
                System.arraycopy( pathEndNodes, 0, SubnetworkNodes, 0, i );
                System.arraycopy( node.getPathEndNodes(), 0, SubnetworkNodes, i, node.getPathEndNodes().length );
                node.setPathEndNodes( SubnetworkNodes );
            } else {
                node.setPathEndNodes( pathEndNodes );
            }
        }

        terminalNode.visitLeftTupleNodes(n -> n.addAssociatedTerminal(terminalNode));
    }

    /**
     * Adds a query pattern to the given subrule
     */
    private void addInitialFactPattern( final GroupElement subrule ) {

        // creates a pattern for initial fact
        final Pattern pattern = new Pattern( 0,
                                             ClassObjectType.InitialFact_ObjectType );

        // adds the pattern as the first child of the given AND group element
        subrule.addChild( 0,
                          pattern );
    }

    public void addEntryPoint(final String id, final InternalRuleBase kBase, Collection<InternalWorkingMemory> workingMemories) {
        // creates a clean build context for each subrule
        final BuildContext context = new BuildContext( kBase, workingMemories );
        EntryPointId ep = new EntryPointId( id );
        ReteooComponentBuilder builder = utils.getBuilderFor( ep );
        builder.build(context, utils, ep);
    }

    public WindowNode addWindowNode(WindowDeclaration window, InternalRuleBase kBase, Collection<InternalWorkingMemory> workingMemories) {

        // creates a clean build context for each subrule
        BuildContext context = new BuildContext( kBase, workingMemories );
        context.setTupleMemoryEnabled( !kBase.getRuleBaseConfiguration().isSequential() );

        // builds and attach
        WindowBuilder.INSTANCE.build( context, this.utils, window );
        return (WindowNode) context.getObjectSource();
    }
}
