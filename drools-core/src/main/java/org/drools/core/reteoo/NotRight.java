package org.drools.core.reteoo;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.builder.BuildContext;

import static org.drools.core.phreak.RuleNetworkEvaluator.doExistentialUpdatesReorderChildLeftTuple;
import static org.drools.core.phreak.TupleEvaluationUtil.flushLeftTupleIfNecessary;

public class NotRight extends RightInputAdapterNode<NotNode> {

    public NotRight(int id, ObjectSource rightInput, BuildContext context) {
        super(id, rightInput, context);
    }

    @Override
    protected void reorderRightTuple(ReteEvaluator reteEvaluator, TupleImpl rightTuple) {
        doExistentialUpdatesReorderChildLeftTuple(reteEvaluator, (NotNode) betaNode, (RightTuple) rightTuple);
    }

    @Override
    public void assertObject( final InternalFactHandle factHandle,
                              final PropagationContext pctx,
                              final ReteEvaluator reteEvaluator ) {

        final BetaMemory memory = getBetaMemoryFromRightInput(betaNode, reteEvaluator);

        TupleImpl rightTuple = createRightTuple( factHandle,
                                                 this,
                                                 pctx);

        rightTuple.setPropagationContext(pctx);

        boolean stagedInsertWasEmpty = memory.getStagedRightTuples().addInsert( rightTuple );

        if (memory.getAndIncCounter() == 0 && ((NotNode)betaNode).isEmptyBetaConstraints()  ) {
            // strangely we link here, this is actually just to force a network evaluation
            // The assert is then processed and the rule unlinks then.
            // This is because we need the first RightTuple to link with it's blocked
            if ( stagedInsertWasEmpty ) {
                memory.setNodeDirtyWithoutNotify();
            }

            // NotNodes can only be unlinked, if they have no variable constraints
            memory.linkNode( betaNode, reteEvaluator );
        } else if ( stagedInsertWasEmpty ) {
            // nothing staged before, notify rule, so it can evaluate network
            memory.setNodeDirty(betaNode, reteEvaluator);
        }

        flushLeftTupleIfNecessary( reteEvaluator, memory.getOrCreateSegmentMemory( betaNode, reteEvaluator ), isStreamMode() );
    }

    @Override
    public void modifyRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void retractRightTuple(final TupleImpl rightTuple,
                                  final PropagationContext pctx,
                                  final ReteEvaluator reteEvaluator) {
        final BetaMemory memory = (BetaMemory) reteEvaluator.getNodeMemory(betaNode);
        rightTuple.setPropagationContext( pctx );
        doDeleteRightTuple( rightTuple, reteEvaluator, memory );
    }

    @Override
    public void doDeleteRightTuple(final TupleImpl rightTuple,
                                   final ReteEvaluator reteEvaluator,
                                   final BetaMemory memory) {
        TupleSets stagedRightTuples    = memory.getStagedRightTuples();
        boolean   stagedDeleteWasEmpty = stagedRightTuples.addDelete( rightTuple );

        if (  memory.getAndDecCounter() == 1 && ((NotNode)betaNode).isEmptyBetaConstraints()  ) {
            if ( stagedDeleteWasEmpty ) {
                memory.setNodeDirtyWithoutNotify();
            }
            // NotNodes can only be unlinked, if they have no variable constraints
            memory.linkNode( betaNode, reteEvaluator );
        }  else if ( stagedDeleteWasEmpty ) {
            // nothing staged before, notify rule, so it can evaluate network
            memory.setNodeDirty( betaNode, reteEvaluator );
        }

        flushLeftTupleIfNecessary( reteEvaluator, memory.getOrCreateSegmentMemory( betaNode, reteEvaluator ), isStreamMode() );
    }

    @Override
    public int getType() {
        return NodeTypeEnums.NotRightAdapterNode;
    }
}
