/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.dmn.core.compiler;

import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.core.ast.BusinessKnowledgeModelNode;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.core.api.DMNExpressionEvaluator;
import org.kie.dmn.core.ast.DecisionServiceNodeImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.util.Msg;
import org.kie.dmn.model.v1_1.DecisionService;
import org.kie.dmn.model.v1_1.FunctionDefinition;

public class DecisionServiceCompiler {

    public void compileNode(DecisionService drge, DMNCompilerImpl compiler, DMNModelImpl model) {
        DecisionService bkm = (DecisionService) drge;
        DMNType type = null;
        if ( bkm.getVariable() == null ) {
            DMNCompilerHelper.reportMissingVariable(model, drge, bkm, Msg.MISSING_VARIABLE_FOR_BKM);
            return;
        }
        DMNCompilerHelper.checkVariableName( model, bkm, bkm.getName() );
        if ( bkm.getVariable() != null && bkm.getVariable().getTypeRef() != null ) {
            type = compiler.resolveTypeRef(model, bkm, bkm.getVariable(), bkm.getVariable().getTypeRef());
        } else {
            // for now the call bellow will return type UNKNOWN
            type = compiler.resolveTypeRef(model, bkm, bkm, null);
        }
        DecisionServiceNodeImpl bkmn = new DecisionServiceNodeImpl(bkm, type);
        model.addDecisionService(bkmn);
    }

    public void compileEvaluator(DMNNode node, DMNCompilerImpl compiler, DMNCompilerContext ctx, DMNModelImpl model) {
        DecisionServiceNodeImpl ni = (DecisionServiceNodeImpl) node;
        compiler.linkRequirements(model, ni);

        ctx.enterFrame();
        try {
            for (DMNNode dep : ni.getDependencies().values()) {
                if( dep instanceof BusinessKnowledgeModelNode ) {
                    // might need to create a DMNType for "functions" and replace the type here by that
                    ctx.setVariable( dep.getName(), ((BusinessKnowledgeModelNode)dep).getResultType() );
                }
            }
            // to allow recursive call from inside a BKM node, a variable for self must be available for the compiler context:
            ctx.setVariable(ni.getName(), ni.getResultType());
            FunctionDefinition funcDef = ni.getBusinessKnowledModel().getEncapsulatedLogic();
            DMNExpressionEvaluator exprEvaluator = compiler.getEvaluatorCompiler().compileExpression(ctx, model, ni, ni.getName(), funcDef);
            ni.setEvaluator(exprEvaluator);
        } finally {
            ctx.exitFrame();
        }
    }
}