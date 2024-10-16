package org.drools.core.reteoo;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.builder.BuildContext;

public class AccumulateRight extends RightInputAdapterNode<AccumulateNode> {

    public AccumulateRight(int id, ObjectSource rightInput, BuildContext context) {
        super(id, rightInput, context);
    }


    /**
     *  @inheritDoc
     *
     *  If an object is retract, call modify tuple for each
     *  tuple match.
     */
    @Override
    public void retractRightTuple( final TupleImpl rightTuple,
                                   final PropagationContext pctx,
                                   final ReteEvaluator reteEvaluator) {
        final AccumulateMemory memory = (AccumulateMemory) reteEvaluator.getNodeMemory(betaNode);

        BetaMemory bm = memory.getBetaMemory();
        rightTuple.setPropagationContext( pctx );
        doDeleteRightTuple( rightTuple, reteEvaluator, bm );
    }

    @Override
    public void modifyRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType() {
        return NodeTypeEnums.AccumulateRightAdapterNode;
    }
}
