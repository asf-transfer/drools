package org.drools.core.reteoo;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.builder.BuildContext;

public class ExistsRight extends RightInputAdapterNode<ExistsNode> {

    public ExistsRight(int id, ObjectSource rightInput, BuildContext context) {
        super(id, rightInput, context);
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
    public void modifyRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType() {
        return NodeTypeEnums.ExistsRightAdapterNode;
    }
}
