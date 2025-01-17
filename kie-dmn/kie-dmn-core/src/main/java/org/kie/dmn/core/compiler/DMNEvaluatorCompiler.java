/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.core.compiler;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.swing.DefaultListSelectionModel;
import javax.xml.namespace.QName;

import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.core.ast.BusinessKnowledgeModelNode;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.core.api.DMNExpressionEvaluator;
import org.kie.dmn.api.core.EvaluatorResult;
import org.kie.dmn.core.ast.DMNBaseNode;
import org.kie.dmn.core.ast.DMNConditionalEvaluator;
import org.kie.dmn.core.ast.DMNContextEvaluator;
import org.kie.dmn.core.ast.DMNDTExpressionEvaluator;
import org.kie.dmn.core.ast.DMNFilterEvaluator;
import org.kie.dmn.core.ast.DMNFunctionDefinitionEvaluator;
import org.kie.dmn.core.ast.DMNInvocationEvaluator;
import org.kie.dmn.core.ast.DMNIteratorEvaluator;
import org.kie.dmn.core.ast.DMNListEvaluator;
import org.kie.dmn.core.ast.DMNLiteralExpressionEvaluator;
import org.kie.dmn.core.ast.DMNRelationEvaluator;
import org.kie.dmn.core.ast.EvaluatorResultImpl;
import org.kie.dmn.core.compiler.alphanetbased.DMNAlphaNetworkEvaluatorCompiler;
import org.kie.dmn.core.impl.BaseDMNTypeImpl;
import org.kie.dmn.core.impl.CompositeTypeImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.pmml.AbstractPMMLInvocationEvaluator;
import org.kie.dmn.core.pmml.AbstractPMMLInvocationEvaluator.PMMLInvocationEvaluatorFactory;
import org.kie.dmn.core.pmml.DMNImportPMMLInfo;
import org.kie.dmn.core.pmml.PMMLModelInfo;
import org.kie.dmn.core.util.Msg;
import org.kie.dmn.core.util.MsgUtil;
import org.kie.dmn.core.util.NamespaceUtil;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.impl.RootExecutionFrame;
import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.runtime.decisiontables.DTDecisionRule;
import org.kie.dmn.feel.runtime.decisiontables.DTInputClause;
import org.kie.dmn.feel.runtime.decisiontables.DTOutputClause;
import org.kie.dmn.feel.runtime.decisiontables.DecisionTableImpl;
import org.kie.dmn.feel.runtime.functions.BaseFEELFunction;
import org.kie.dmn.feel.runtime.functions.DTInvokerFunction;
import org.kie.dmn.model.api.Binding;
import org.kie.dmn.model.api.BusinessKnowledgeModel;
import org.kie.dmn.model.api.Conditional;
import org.kie.dmn.model.api.Context;
import org.kie.dmn.model.api.ContextEntry;
import org.kie.dmn.model.api.DMNElement;
import org.kie.dmn.model.api.Decision;
import org.kie.dmn.model.api.DecisionRule;
import org.kie.dmn.model.api.DecisionTable;
import org.kie.dmn.model.api.Expression;
import org.kie.dmn.model.api.Filter;
import org.kie.dmn.model.api.For;
import org.kie.dmn.model.api.FunctionDefinition;
import org.kie.dmn.model.api.FunctionKind;
import org.kie.dmn.model.api.HitPolicy;
import org.kie.dmn.model.api.Import;
import org.kie.dmn.model.api.InformationItem;
import org.kie.dmn.model.api.InputClause;
import org.kie.dmn.model.api.Invocation;
import org.kie.dmn.model.api.Iterator;
import org.kie.dmn.model.api.LiteralExpression;
import org.kie.dmn.model.api.OutputClause;
import org.kie.dmn.model.api.Quantified;
import org.kie.dmn.model.api.Relation;
import org.kie.dmn.model.api.UnaryTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMNEvaluatorCompiler implements DMNDecisionLogicCompiler {

    private static final Logger logger = LoggerFactory.getLogger(DMNEvaluatorCompiler.class);

    protected final DMNCompilerImpl compiler;

    protected DMNEvaluatorCompiler(DMNCompilerImpl compiler) {
        this.compiler = compiler;
    }

    public static DMNEvaluatorCompiler dmnEvaluatorCompilerFactory(DMNCompilerImpl dmnCompiler,
                                                                   DMNCompilerConfigurationImpl dmnCompilerConfig) {
        if (dmnCompilerConfig.isUseAlphaNetwork()) {
            logger.debug("Using AlphaNetDMNEvaluatorCompiler.");
            return new DMNAlphaNetworkEvaluatorCompiler(dmnCompiler);
        } else {
            logger.debug("default DMNEvaluatorCompiler.");
            return new DMNEvaluatorCompiler(dmnCompiler);
        }
    }

    @Override
    public DMNExpressionEvaluator compileExpression(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                    String exprName, Expression expression) {
        if (expression == null) {
            if (node instanceof DecisionNode) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.WARN,
                                      node.getSource(),
                                      model,
                                      null,
                                      null,
                                      Msg.MISSING_EXPRESSION_FOR_DECISION,
                                      node.getIdentifierString());
            } else if (node instanceof BusinessKnowledgeModelNode) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.WARN,
                                      node.getSource(),
                                      model,
                                      null,
                                      null,
                                      Msg.MISSING_EXPRESSION_FOR_BKM,
                                      node.getIdentifierString());
            } else {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      node.getSource(),
                                      model,
                                      null,
                                      null,
                                      Msg.MISSING_EXPRESSION_FOR_NODE,
                                      node.getIdentifierString());
            }
        } else if (expression instanceof LiteralExpression) {
            return compileLiteralExpression(ctx, model, node, exprName, (LiteralExpression) expression);
        } else if (expression instanceof DecisionTable) {
            return compileDecisionTable(ctx, model, node, exprName, (DecisionTable) expression);
        } else if (expression instanceof FunctionDefinition) {
            return compileFunctionDefinition(ctx, model, node, exprName, (FunctionDefinition) expression);
        } else if (expression instanceof Context) {
            return compileContext(ctx, model, node, exprName, (Context) expression);
        } else if (expression instanceof org.kie.dmn.model.api.List) {
            return compileList(ctx, model, node, exprName, (org.kie.dmn.model.api.List) expression);
        } else if (expression instanceof Relation) {
            return compileRelation(ctx, model, node, exprName, (Relation) expression);
        } else if (expression instanceof Invocation) {
            return compileInvocation(ctx, model, node, (Invocation) expression);
        } else if (expression instanceof Conditional) {
            return compileConditional(ctx, model, node, exprName, (Conditional) expression);
        } else if (expression instanceof Iterator) {
            return compileIterator(ctx, model, node, exprName, (Iterator) expression);
        } else if (expression instanceof Filter) {
            return compileFilter(ctx, model, node, exprName, (Filter) expression);
        } else {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  node.getSource(),
                                  model,
                                  null,
                                  null,
                                  Msg.EXPR_TYPE_NOT_SUPPORTED_IN_NODE,
                                  expression.getClass().getSimpleName(),
                                  node.getIdentifierString());
        }
        return null;
    }

    protected ClassLoader getRootClassLoader() {
        return getDmnCompilerConfig().getRootClassLoader();
    }

    protected DMNCompilerConfigurationImpl getDmnCompilerConfig() {
        return (DMNCompilerConfigurationImpl) compiler.getDmnCompilerConfig();
    }

    private DMNExpressionEvaluator compileInvocation(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                     Invocation expression) {
        Invocation invocation = expression;
        // expression must be a literal text with the name of the function
        if (invocation.getExpression() == null || ((LiteralExpression) invocation.getExpression()).getText().isEmpty()) {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  invocation,
                                  model,
                                  null,
                                  null,
                                  Msg.MISSING_EXPRESSION_FOR_INVOCATION,
                                  node.getIdentifierString());
            return null;
        }
        String functionName = ((LiteralExpression) invocation.getExpression()).getText();
        String[] fnameParts = functionName.split("\\.");
        Optional<DMNNode> findAsDep;
        if (fnameParts.length > 1) {
            findAsDep = node.getDependencies().values().stream()
                    .filter(dmnNode -> dmnNode.getModelImportAliasFor(dmnNode.getModelNamespace(),
                                                                      dmnNode.getModelName()).map(alias -> Objects.equals(functionName, alias + "." + dmnNode.getName())).orElse(false)
                    ).findFirst();
        } else {
            findAsDep =
                    node.getDependencies().values().stream().filter(d -> d.getName().equals(functionName)).findAny();
        }
        boolean findAsBuiltin = RootExecutionFrame.INSTANCE.getValue(functionName) != null;
        boolean findAsCustomFunction =
                ctx.getFeelHelper().newCompilerContext().getFEELFunctions().stream().anyMatch(f -> f.getName().equals(functionName));
        boolean findInContext = ctx.getVariables().get(functionName) != null;
        if (findAsDep.isEmpty() && !findAsBuiltin && !findAsCustomFunction && !findInContext) {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.WARN,
                                  invocation,
                                  model,
                                  null,
                                  null,
                                  Msg.EXPRESSION_FOR_INVOCATION_NOT_RESOLVED,
                                  functionName,
                                  node.getIdentifierString(),
                                  node.getDependencies().values().stream().map(DMNNode::getName).collect(Collectors.toList()));
        }
        DMNInvocationEvaluator invEval = new DMNInvocationEvaluator(node.getName(), node.getSource(), functionName,
                                                                    invocation, null,
                                                                    ctx.getFeelHelper().newFEELInstance());
        for (Binding binding : invocation.getBinding()) {
            if (binding.getParameter() == null) {
                // error, missing binding parameter
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      binding,
                                      model,
                                      null,
                                      null,
                                      Msg.MISSING_PARAMETER_FOR_INVOCATION,
                                      node.getIdentifierString());
                return null;
            }
            if (binding.getExpression() == null) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.WARN,
                                      binding,
                                      model,
                                      null,
                                      null,
                                      Msg.MISSING_EXPRESSION_FOR_PARAM_OF_INVOCATION,
                                      binding.getParameter().getIdentifierString(),
                                      node.getIdentifierString());
                return null;
            }
            invEval.addParameter(
                    binding.getParameter().getName(),
                    compiler.resolveTypeRef(model, binding.getParameter(), binding.getParameter(),
                                            binding.getParameter().getTypeRef()),
                    compileExpression(ctx, model, node, binding.getParameter().getName(), binding.getExpression()));
        }
        return invEval;
    }

    private DMNExpressionEvaluator compileRelation(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                   String relationName, Relation expression) {
        Relation relationDef = expression;
        DMNRelationEvaluator relationEval = new DMNRelationEvaluator(node.getName(), node.getSource(), relationDef);
        for (InformationItem col : relationDef.getColumn()) {
            DMNCompilerHelper.checkVariableName(model, col, col.getName());
            relationEval.addColumn(col.getName());
        }
        for (org.kie.dmn.model.api.List row : relationDef.getRow()) {
            java.util.List<DMNExpressionEvaluator> values = new ArrayList<>();
            for (Expression expr : row.getExpression()) {
                if (expr instanceof LiteralExpression) {
                    // DROOLS-2439
                    LiteralExpression literalExpression = (LiteralExpression) expr;
                    if (literalExpression.getText() == null || literalExpression.getText().isEmpty()) {
                        LiteralExpression nullProxy =
                                (literalExpression instanceof org.kie.dmn.model.v1_1.TLiteralExpression) ?
                                        new org.kie.dmn.model.v1_1.TLiteralExpression() :
                                        new org.kie.dmn.model.v1_2.TLiteralExpression();
                        nullProxy.setText("null");
                        nullProxy.setImportedValues(literalExpression.getImportedValues());
                        nullProxy.setExpressionLanguage(literalExpression.getExpressionLanguage());
                        nullProxy.setTypeRef(literalExpression.getTypeRef());
                        nullProxy.setId(literalExpression.getId());
                        nullProxy.setLabel(literalExpression.getLabel());
                        nullProxy.setDescription(literalExpression.getDescription());
                        nullProxy.setExtensionElements(literalExpression.getExtensionElements());
                        nullProxy.setParent(literalExpression.getParent());
                        nullProxy.getNsContext().putAll(literalExpression.getNsContext());
                        nullProxy.setAdditionalAttributes(literalExpression.getAdditionalAttributes());
                        nullProxy.setLocation(literalExpression.getLocation());
                        // do not add `temp` as child of parent.
                        expr = nullProxy;
                    }
                }
                values.add(compileExpression(ctx, model, node, relationName, expr));
            }
            relationEval.addRow(values);
        }
        return relationEval;
    }

    private DMNExpressionEvaluator compileList(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                               String listName, org.kie.dmn.model.api.List expression) {
        org.kie.dmn.model.api.List listDef = expression;
        DMNListEvaluator listEval = new DMNListEvaluator(node.getName(), node.getSource(), listDef);
        for (Expression expr : listDef.getExpression()) {
            listEval.addElement(compileExpression(ctx, model, node, listName, expr));
        }
        return listEval;
    }

    private DMNExpressionEvaluator compileContext(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                  String contextName, Context expression) {
        Context ctxDef = expression;
        DMNContextEvaluator ctxEval = new DMNContextEvaluator(node.getName(), ctxDef);
        ctx.enterFrame();
        try {
            for (ContextEntry ce : ctxDef.getContextEntry()) {
                if (ce.getVariable() != null) {
                    String entryName = ce.getVariable().getName();
                    DMNCompilerHelper.checkVariableName(model, node.getSource(), entryName);
                    DMNType entryType = compiler.resolveTypeRef(model, ce.getVariable(), ce.getVariable(),
                                                                ce.getVariable().getTypeRef());
                    // add context entry to the list of available variables for the following entries
                    ctx.setVariable(entryName, entryType);
                    DMNExpressionEvaluator evaluator = compileExpression(ctx, model, node, entryName,
                                                                         ce.getExpression());
                    ctxEval.addEntry(
                            entryName,
                            entryType,
                            evaluator,
                            ce);
                } else {
                    // if the variable is not defined, then it should be the last
                    // entry in the context and the result of this context evaluation is the
                    // result of this expression itself

                    // TODO: if it is not the last entry, raise error message
                    DMNType type = null;
                    if (ctxDef.getParent() instanceof ContextEntry && ((ContextEntry) ctxDef.getParent()).getVariable() != null) {
                        ContextEntry parentEntry = (ContextEntry) ctxDef.getParent();
                        type = compiler.resolveTypeRef(model, parentEntry.getVariable(), parentEntry.getVariable(),
                                                       parentEntry.getVariable().getTypeRef());
                    } else if (node instanceof BusinessKnowledgeModelNode) {
                        type = ((BusinessKnowledgeModelNode) node).getResultType();
                    } else if (node instanceof DecisionNode) {
                        type = ((DecisionNode) node).getResultType();
                    }
                    ctxEval.addEntry(
                            DMNContextEvaluator.RESULT_ENTRY,
                            type,
                            compileExpression(ctx, model, node, contextName, ce.getExpression()),
                            ce);
                }
            }
        } finally {
            ctx.exitFrame();
        }
        return ctxEval;
    }

    private DMNExpressionEvaluator compileFunctionDefinition(DMNCompilerContext ctx, DMNModelImpl model,
                                                             DMNBaseNode node, String functionName,
                                                             FunctionDefinition funcDef) {
        FunctionKind kind = funcDef.getKind();
        if (kind == null) {
            // unknown function kind
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  funcDef,
                                  model,
                                  null,
                                  null,
                                  Msg.FUNC_DEF_INVALID_KIND,
                                  kind,
                                  node.getIdentifierString());
            return new DMNFunctionDefinitionEvaluator(node, funcDef);
        } else if (kind.equals(FunctionKind.FEEL)) {
            return compileFunctionDefinitionFEEL(ctx, model, node, functionName, funcDef);
        } else if (kind.equals(FunctionKind.JAVA)) {
            return compileFunctionDefinitionJAVA(ctx, model, node, functionName, funcDef);
        } else if (kind.equals(FunctionKind.PMML)) {
            return compileFunctionDefinitionPMML(ctx, model, node, functionName, funcDef);
        } else {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  funcDef,
                                  model,
                                  null,
                                  null,
                                  Msg.FUNC_DEF_INVALID_KIND,
                                  kind,
                                  node.getIdentifierString());
        }
        return new DMNFunctionDefinitionEvaluator(node, funcDef);
    }

    private DMNExpressionEvaluator compileFunctionDefinitionFEEL(DMNCompilerContext ctx, DMNModelImpl model,
                                                                 DMNBaseNode node, String functionName,
                                                                 FunctionDefinition funcDef) {
        ctx.enterFrame();
        try {
            DMNFunctionDefinitionEvaluator func = new DMNFunctionDefinitionEvaluator(node, funcDef);
            for (InformationItem p : funcDef.getFormalParameter()) {
                DMNCompilerHelper.checkVariableName(model, p, p.getName());
                DMNType dmnType = compiler.resolveTypeRef(model, p, p, p.getTypeRef());
                func.addParameter(p.getName(), dmnType);
                ctx.setVariable(p.getName(), dmnType);
            }

            DMNExpressionEvaluator eval = compileExpression(ctx, model, node, functionName, funcDef.getExpression());
            func.setEvaluator(eval);

            return func;
        } finally {
            ctx.exitFrame();
        }
    }

    private DMNExpressionEvaluator compileFunctionDefinitionJAVA(DMNCompilerContext ctx, DMNModelImpl model,
                                                                 DMNBaseNode node, String functionName,
                                                                 FunctionDefinition funcDef) {
        if (funcDef.getExpression() instanceof Context) {
            // proceed
            Context context = (Context) funcDef.getExpression();
            String clazz = null;
            String method = null;
            for (ContextEntry ce : context.getContextEntry()) {
                if (ce.getVariable() != null && ce.getVariable().getName() != null && ce.getExpression() != null && ce.getExpression() instanceof LiteralExpression) {
                    if (ce.getVariable().getName().equals("class")) {
                        clazz = stripQuotes(((LiteralExpression) ce.getExpression()).getText().trim());
                    } else if (ce.getVariable().getName().equals("method signature")) {
                        method = stripQuotes(((LiteralExpression) ce.getExpression()).getText().trim());
                    }
                }
            }
            if (clazz != null && method != null) {
                String params =
                        funcDef.getFormalParameter().stream().map(p -> p.getName()).collect(Collectors.joining(","));
                String expr = String.format("function(%s) external { java: { class: \"%s\", method signature: \"%s\" " +
                                                    "}}", params, clazz, method);

                try {
                    FEELFunction feelFunction = ctx.getFeelHelper().evaluateFunctionDef(ctx, expr, model, funcDef,
                                                                                        Msg.FUNC_DEF_COMPILATION_ERR,
                                                                                        functionName,
                                                                                        node.getIdentifierString());
                    if (feelFunction != null) {
                        ((BaseFEELFunction) feelFunction).setName(functionName);
                    }

                    DMNInvocationEvaluator invoker = new DMNInvocationEvaluator(node.getName(), node.getSource(),
                                                                                functionName, null,
                                                                                (fctx, fname) -> feelFunction, null); // feel can be null as anyway is hardcoded to `feelFunction`

                    DMNFunctionDefinitionEvaluator func = new DMNFunctionDefinitionEvaluator(node, funcDef);
                    for (InformationItem p : funcDef.getFormalParameter()) {
                        DMNCompilerHelper.checkVariableName(model, p, p.getName());
                        DMNType dmnType = compiler.resolveTypeRef(model, p, p, p.getTypeRef());
                        func.addParameter(p.getName(), dmnType);
                        invoker.addParameter(p.getName(), dmnType,
                                             (em, dr) -> new EvaluatorResultImpl(dr.getContext().get(p.getName()),
                                                                                 EvaluatorResult.ResultType.SUCCESS));
                    }
                    func.setEvaluator(invoker);
                    return func;
                } catch (Throwable e) {
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          funcDef,
                                          model,
                                          e,
                                          null,
                                          Msg.FUNC_DEF_COMPILATION_ERR,
                                          functionName,
                                          node.getIdentifierString(),
                                          "Exception raised: " + e.getClass().getSimpleName());
                }
            } else {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      funcDef,
                                      model,
                                      null,
                                      null,
                                      Msg.FUNC_DEF_MISSING_ENTRY,
                                      functionName,
                                      node.getIdentifierString());
            }
        } else {
            // error, java function definitions require a context
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  funcDef,
                                  model,
                                  null,
                                  null,
                                  Msg.FUNC_DEF_BODY_NOT_CONTEXT,
                                  node.getIdentifierString());
        }
        return new DMNFunctionDefinitionEvaluator(node, funcDef);
    }

    private DMNExpressionEvaluator compileFunctionDefinitionPMML(DMNCompilerContext ctx, DMNModelImpl model,
                                                                 DMNBaseNode node, String functionName,
                                                                 FunctionDefinition funcDef) {
        if (funcDef.getExpression() instanceof Context) {
            Context context = (Context) funcDef.getExpression();
            String pmmlDocument = null;
            String pmmlModel = null;
            for (ContextEntry ce : context.getContextEntry()) {
                if (ce.getVariable() != null && ce.getVariable().getName() != null && ce.getExpression() instanceof LiteralExpression) {
                    LiteralExpression ceLitExpr = (LiteralExpression) ce.getExpression();
                    if (ce.getVariable().getName().equals("document")) {
                        if (ceLitExpr.getText() != null) {
                            pmmlDocument = stripQuotes(ceLitExpr.getText().trim());
                        }
                    } else if (ce.getVariable().getName().equals("model")) {
                        if (ceLitExpr.getText() != null) {
                            pmmlModel = stripQuotes(ceLitExpr.getText().trim());
                        }
                    }
                }
            }
            final String nameLookup = pmmlDocument;
            Optional<Import> lookupImport =
                    model.getDefinitions().getImport().stream().filter(x -> x.getName().equals(nameLookup)).findFirst();
            if (lookupImport.isPresent()) {
                Import theImport = lookupImport.get();
                logger.trace("theImport: {}", theImport);
                Resource pmmlResource = DMNCompilerImpl.resolveRelativeResource(getRootClassLoader(), model,
                                                                                theImport, funcDef,
                                                                                ctx.getRelativeResolver());
                logger.trace("pmmlResource: {}", pmmlResource);
                DMNImportPMMLInfo pmmlInfo = model.getPmmlImportInfo().get(pmmlDocument);
                logger.trace("pmmlInfo: {}", pmmlInfo);
                if (pmmlModel == null || pmmlModel.isEmpty()) {
                    List<String> pmmlModelNames = pmmlInfo.getModels()
                            .stream()
                            .map(PMMLModelInfo::getName)
                            .filter(x -> x != null)
                            .collect(Collectors.toList());
                    if (pmmlModelNames.size() > 0) {
                        MsgUtil.reportMessage(logger,
                                              DMNMessage.Severity.WARN,
                                              funcDef,
                                              model,
                                              null,
                                              null,
                                              Msg.FUNC_DEF_PMML_MISSING_MODEL_NAME,
                                              pmmlModelNames.stream().collect(Collectors.joining(",")));
                    }
                }
                AbstractPMMLInvocationEvaluator invoker = PMMLInvocationEvaluatorFactory.newInstance(model,
                                                                                                     getRootClassLoader(),
                                                                                                     funcDef,
                                                                                                     pmmlResource,
                                                                                                     pmmlModel,
                                                                                                     pmmlInfo);
                DMNFunctionDefinitionEvaluator func = new DMNFunctionDefinitionEvaluator(node, funcDef);
                for (InformationItem p : funcDef.getFormalParameter()) {
                    DMNCompilerHelper.checkVariableName(model, p, p.getName());
                    DMNType dmnType = compiler.resolveTypeRef(model, p, p, p.getTypeRef());
                    func.addParameter(p.getName(), dmnType);
                    invoker.addParameter(p.getName(), dmnType);
                }
                func.setEvaluator(invoker);
                return func;
            } else {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      funcDef,
                                      model,
                                      null,
                                      null,
                                      Msg.FUNC_DEF_PMML_MISSING_ENTRY,
                                      functionName,
                                      node.getIdentifierString());
            }
        } else {
            // error, PMML function definitions require a context
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  funcDef,
                                  model,
                                  null,
                                  null,
                                  Msg.FUNC_DEF_BODY_NOT_CONTEXT,
                                  node.getIdentifierString());
        }
        return new DMNFunctionDefinitionEvaluator(node, funcDef);
    }

    private String stripQuotes(String trim) {
        return trim.startsWith("\"") && trim.endsWith("\"") && trim.length() >= 2 ? trim.substring(1,
                                                                                                   trim.length() - 1) : trim;
    }

    protected DMNExpressionEvaluator compileDecisionTable(DMNCompilerContext ctx, DMNModelImpl model,
                                                          DMNBaseNode node, String dtName, DecisionTable dt) {
        java.util.List<DTInputClause> inputs = new ArrayList<>();
        java.util.List<DMNType> inputTypes = new ArrayList<>();
        int index = 0;
        for (InputClause ic : dt.getInput()) {
            index++;
            String inputExpressionText = ic.getInputExpression().getText();
            String inputValuesText = Optional.ofNullable(ic.getInputValues()).map(UnaryTests::getText).orElse(null);
            java.util.List<UnaryTest> inputValues = null;
            DMNType inputType = model.getTypeRegistry().unknown();
            if (inputValuesText != null) {
                inputValues = textToUnaryTestList(ctx,
                                                  inputValuesText,
                                                  model,
                                                  ic,
                                                  Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_INPUT_CLAUSE_IDX,
                                                  inputValuesText,
                                                  node.getIdentifierString(),
                                                  index);
            } else if (ic.getInputExpression().getTypeRef() != null) {
                QName inputExpressionTypeRef = ic.getInputExpression().getTypeRef();
                QName resolvedInputExpressionTypeRef = NamespaceUtil.getNamespaceAndName(ic.getInputExpression(),
                                                                                         model.getImportAliasesForNS(), inputExpressionTypeRef, model.getNamespace());
                BaseDMNTypeImpl typeRef =
                        (BaseDMNTypeImpl) model.getTypeRegistry().resolveType(resolvedInputExpressionTypeRef.getNamespaceURI(), resolvedInputExpressionTypeRef.getLocalPart());
                inputType = typeRef;
                if (inputType == null) {
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          dt,
                                          model,
                                          null,
                                          null,
                                          Msg.WRONG_TYPEREF_FOR_COLUMN,
                                          index,
                                          inputExpressionText,
                                          inputExpressionTypeRef);
                    inputType = model.getTypeRegistry().unknown();
                } else {
                    inputValues = typeRef.getAllowedValuesFEEL();
                }
            }
            CompiledExpression compiledInput = ctx.getFeelHelper().compileFeelExpression(
                    ctx,
                    inputExpressionText,
                    model,
                    dt,
                    Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_INPUT_CLAUSE_IDX,
                    inputExpressionText,
                    dtName,
                    index);
            inputs.add(new DTInputClause(inputExpressionText, inputValuesText, inputValues, compiledInput,
                                         inputType.isCollection()));
            inputTypes.add(inputType);
        }
        java.util.List<DTOutputClause> outputs = new ArrayList<>();
        index = 0;
        boolean hasOutputValues = false;
        for (OutputClause oc : dt.getOutput()) {
            String outputName = oc.getName();
            if (outputName != null) {
                DMNCompilerHelper.checkVariableName(model, node.getSource(), outputName);
            }
            String id = oc.getId();
            String outputValuesText = Optional.ofNullable(oc.getOutputValues()).map(UnaryTests::getText).orElse(null);
            String defaultValue =
                    Optional.ofNullable(oc.getDefaultOutputEntry()).map(LiteralExpression::getText).filter(t -> !t.isEmpty()).orElse(null);
            BaseDMNTypeImpl typeRef = inferTypeRef(model, dt, oc);
            java.util.List<UnaryTest> outputValues = null;

            if (outputValuesText != null) {
                outputValues = textToUnaryTestList(ctx,
                                                   outputValuesText,
                                                   model,
                                                   oc,
                                                   Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_OUTPUT_CLAUSE_IDX,
                                                   outputValuesText,
                                                   node.getIdentifierString(),
                                                   ++index);
            } else if (typeRef != model.getTypeRegistry().unknown()) {
                outputValues = typeRef.getAllowedValuesFEEL();
            }

            if (outputValues != null && !outputValues.isEmpty()) {
                hasOutputValues = true;
            }
            outputs.add(new DTOutputClause(outputName, id, outputValues, defaultValue, typeRef.getFeelType(),
                                           typeRef.isCollection()));
        }
        if (dt.getHitPolicy().equals(HitPolicy.PRIORITY) && !hasOutputValues) {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  dt.getParent(),
                                  model,
                                  null,
                                  null,
                                  Msg.MISSING_OUTPUT_VALUES,
                                  dtName);
        }
        java.util.List<DTDecisionRule> rules = new ArrayList<>();
        index = 0;
        for (DecisionRule dr : dt.getRule()) {
            DTDecisionRule rule = new DTDecisionRule(index, dr.getId());
            for (int i = 0; i < dr.getInputEntry().size(); i++) {
                UnaryTests ut = dr.getInputEntry().get(i);
                final java.util.List<UnaryTest> tests;
                if (ut == null || ut.getText() == null || ut.getText().isEmpty()) {
                    tests = Collections.emptyList();
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          ut,
                                          model,
                                          null,
                                          null,
                                          Msg.DTABLE_EMPTY_ENTRY,
                                          dt.getRule().indexOf(dr) + 1,
                                          dr.getInputEntry().indexOf(ut) + 1,
                                          dt.getParentDRDElement().getIdentifierString());
                } else {
                    ctx.enterFrame();
                    try {
                        ctx.setVariable("?", inputTypes.get(i));
                        tests = textToUnaryTestList(ctx,
                                                    ut.getText(),
                                                    model,
                                                    dr,
                                                    Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_RULE_IDX,
                                                    ut.getText(),
                                                    node.getIdentifierString(),
                                                    index + 1);
                    } finally {
                        ctx.exitFrame();
                    }
                }
                rule.getInputEntry().add((c, x) -> tests.stream().anyMatch(t -> {
                    Boolean result = t.apply(c, x);
                    return result != null && result;
                }));
            }
            for (LiteralExpression le : dr.getOutputEntry()) {
                String expressionText = le.getText();
                if (expressionText == null || expressionText.isEmpty()) {
                    expressionText = "null"; // addendum to DROOLS-2075 Allow empty output cell on DTs
                }
                CompiledExpression compiledExpression = ctx.getFeelHelper().compileFeelExpression(
                        ctx,
                        expressionText,
                        model,
                        dr,
                        Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_RULE_IDX,
                        expressionText,
                        dtName,
                        index + 1);
                rule.getOutputEntry().add(compiledExpression);
            }
            rules.add(rule);
            index++;
        }
        String policy = dt.getHitPolicy().value() + (dt.getAggregation() != null ? " " + dt.getAggregation().value()
                : "");
        org.kie.dmn.feel.runtime.decisiontables.HitPolicy hp =
                org.kie.dmn.feel.runtime.decisiontables.HitPolicy.fromString(policy);
        java.util.List<String> parameterNames = getParameters(model, node, dt);
        // DROOLS-2799 DMN Optimize DT parameter binding for compilation:
        java.util.List<CompiledExpression> compiledParameterNames = new ArrayList<>();
        for (String pName : parameterNames) {
            CompiledExpression compiledExpression = ctx.getFeelHelper().compileFeelExpression(ctx,
                                                                                              pName,
                                                                                              model,
                                                                                              dt,
                                                                                              Msg.ERR_COMPILING_FEEL_EXPR_ON_DT_PARAM,
                                                                                              pName,
                                                                                              dtName);
            compiledParameterNames.add(compiledExpression);
        }

        // creates a FEEL instance which will be used by the invoker/impl (s)
        FEEL feelInstance = ctx.getFeelHelper().newFEELInstance();

        DecisionTableImpl dti = new DecisionTableImpl(dtName, parameterNames, inputs, outputs, rules, hp, feelInstance);
        dti.setCompiledParameterNames(compiledParameterNames);
        DTInvokerFunction dtf = new DTInvokerFunction(dti);
        DMNDTExpressionEvaluator dtee = new DMNDTExpressionEvaluator(node, feelInstance, dtf);
        dtee.setDtNodeId(dt.getId());
        return dtee;
    }

    public static BaseDMNTypeImpl inferTypeRef(DMNModelImpl model, DecisionTable dt, OutputClause oc) {
        BaseDMNTypeImpl typeRef = (BaseDMNTypeImpl) model.getTypeRegistry().unknown();
        if (oc.getTypeRef() != null) {
            QName outputExpressionTypeRef = oc.getTypeRef();
            QName resolvedOutputExpressionTypeRef = NamespaceUtil.getNamespaceAndName(oc,
                                                                                      model.getImportAliasesForNS(),
                                                                                      outputExpressionTypeRef,
                                                                                      model.getNamespace());
            typeRef =
                    (BaseDMNTypeImpl) model.getTypeRegistry().resolveType(resolvedOutputExpressionTypeRef.getNamespaceURI(), resolvedOutputExpressionTypeRef.getLocalPart());
            if (typeRef == null) {
                typeRef = (BaseDMNTypeImpl) model.getTypeRegistry().unknown();
            }
        } else if (dt.getOutput().size() == 1 && (dt.getParent() instanceof Decision || dt.getParent() instanceof BusinessKnowledgeModel || dt.getParent() instanceof ContextEntry)) {
            QName inferredTypeRef = recurseUpToInferTypeRef(model, oc, dt);
            // if inferredTypeRef is null, a std err will have been reported
            if (inferredTypeRef != null) {
                QName resolvedInferredTypeRef = NamespaceUtil.getNamespaceAndName(oc, model.getImportAliasesForNS(),
                                                                                  inferredTypeRef,
                                                                                  model.getNamespace());
                typeRef =
                        (BaseDMNTypeImpl) model.getTypeRegistry().resolveType(resolvedInferredTypeRef.getNamespaceURI(), resolvedInferredTypeRef.getLocalPart());
            }
        }
        return typeRef;
    }

    public static java.util.List<String> getParameters(DMNModelImpl model, DMNBaseNode node, DecisionTable dt) {
        java.util.List<String> parameterNames = new ArrayList<>();
        if (node instanceof BusinessKnowledgeModelNode) {
            // need to break this statement down and check for nulls
            parameterNames.addAll(((BusinessKnowledgeModelNode) node).getBusinessKnowledModel().getEncapsulatedLogic().getFormalParameter().stream().map(f -> f.getName()).collect(toList()));
        } else {
            for (Entry<String, DMNNode> depEntry : node.getDependencies().entrySet()) { // DROOLS-1663: dependencies
                // names must be prefixed with "alias." for those not coming from this model but DMN Imports instead.
                if (depEntry.getValue().getModelNamespace().equals(node.getModelNamespace())) {
                    parameterNames.add(depEntry.getKey()); // this dependency is from this model, adding parameter
                    // name as-is.
                } else {
                    Optional<String> importAlias = model.getImportAliasFor(depEntry.getValue().getModelNamespace(),
                                                                           depEntry.getValue().getModelName());
                    if (importAlias.isEmpty()) {
                        MsgUtil.reportMessage(logger,
                                              DMNMessage.Severity.ERROR,
                                              dt.getParent(),
                                              model,
                                              null,
                                              null,
                                              Msg.IMPORT_NOT_FOUND_FOR_NODE_MISSING_ALIAS,
                                              new QName(depEntry.getValue().getModelNamespace(),
                                                        depEntry.getValue().getModelName()),
                                              node);
                        return null;
                    }
                    parameterNames.add(importAlias.get() + "." + depEntry.getKey()); // this dependency is from an
                    // imported model, need to add parameter with "alias." DMN Import name prefix.
                }
            }
        }
        return parameterNames;
    }

    /**
     * This method is used to override the feel expression language used by the returned <code>FEEL</code>
     * @param ctx
     * @param expression
     * @param expressionLanguage
     *
     * @throws IllegalArgumentException if the given <code>expressionLanguage</code> is not <code>null</code> and is different from the URIFEEL and the BFEEL
     * @return
     */
    static FEEL getFEELDialectAdaptedFEEL(DMNCompilerContext ctx, LiteralExpression expression, String expressionLanguage) {
        if (expressionLanguage == null ||
                expressionLanguage.equals(expression.getURIFEEL())) {
            return ctx.getFeelHelper().newFEELInstance();
        } else if (expressionLanguage.equals(FEELDialect.BFEEL.getNamespace())) {
            return ctx.getFeelHelper().newFEELInstance(FEELDialect.BFEEL);
        } else {
            String errorMessage = String.format("Unsupported FEEL language '%s'; allowed values are `null`, %s, %s", expressionLanguage, expression.getURIFEEL(), FEELDialect.BFEEL.getNamespace());
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Utility method for DecisionTable with only 1 output, to infer typeRef from parent
     * @param model used for reporting errors
     * @param originalElement the original OutputClause[0] single output for which the DecisionTable parameter
     * recursionIdx is being processed for inferring the typeRef
     * @param recursionIdx start of the recursion is the DecisionTable model node itself
     * @return the inferred `typeRef` or null in case of errors. Errors are reported with standard notification
     * mechanism via MsgUtil.reportMessage
     */
    private static QName recurseUpToInferTypeRef(DMNModelImpl model, OutputClause originalElement,
                                                 DMNElement recursionIdx) {
        if (recursionIdx.getParent() instanceof Decision) {
            InformationItem parentVariable = ((Decision) recursionIdx.getParent()).getVariable();
            if (parentVariable != null) {
                return variableTypeRefOrErrIfNull(model, parentVariable);
            } else {
                return null; // simply to avoid NPE, the proper error is already managed in compilation
            }
        } else if (recursionIdx.getParent() instanceof BusinessKnowledgeModel) {
            InformationItem parentVariable = ((BusinessKnowledgeModel) recursionIdx.getParent()).getVariable();
            if (parentVariable != null) {
                return variableTypeRefOrErrIfNull(model, parentVariable);
            } else {
                return null; // simply to avoid NPE, the proper error is already managed in compilation
            }
        } else if (recursionIdx.getParent() instanceof ContextEntry) {
            ContextEntry parentCtxEntry = (ContextEntry) recursionIdx.getParent();
            if (parentCtxEntry.getVariable() != null) {
                return variableTypeRefOrErrIfNull(model, parentCtxEntry.getVariable());
            } else {
                Context parentCtx = (Context) parentCtxEntry.getParent();
                if (parentCtx.getContextEntry().get(parentCtx.getContextEntry().size() - 1).equals(parentCtxEntry)) {
                    // the ContextEntry is the last one in the Context, so I can recurse up-ward in the DMN model tree
                    // please notice the recursion would be considering the parentCtxEntry's parent, which is the
                    // `parentCtx` so is effectively a 2x jump upward in the model tree
                    return recurseUpToInferTypeRef(model, originalElement, parentCtx);
                } else {
                    // error not last ContextEntry in context
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          parentCtxEntry,
                                          model,
                                          null,
                                          null,
                                          Msg.MISSING_VARIABLE_ON_CONTEXT,
                                          parentCtxEntry);
                    return null;
                }
            }
        } else {
            // this is only for safety in case the recursion is escaping the allowed path for a broken model.
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  originalElement,
                                  model,
                                  null,
                                  null,
                                  Msg.UNKNOWN_OUTPUT_TYPE_FOR_DT_ON_NODE,
                                  originalElement.getParentDRDElement().getIdentifierString());
            return null;
        }
    }

    /**
     * Utility method to have a error message is reported if a DMN Variable is missing typeRef.
     * @param model used for reporting errors
     * @param variable the variable to extract typeRef
     * @return the `variable.typeRef` or null if missing. When missing a WARN is reported with standard notification
     * mechanism via MsgUtil.reportMessage
     */
    private static QName variableTypeRefOrErrIfNull(DMNModelImpl model, InformationItem variable) {
        if (variable.getTypeRef() != null) {
            return variable.getTypeRef();
        } else {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.WARN,
                                  variable,
                                  model,
                                  null,
                                  null,
                                  Msg.MISSING_TYPEREF_FOR_VARIABLE,
                                  variable.getName(),
                                  variable.getParentDRDElement().getIdentifierString());
            return null;
        }
    }

    private DMNExpressionEvaluator compileLiteralExpression(DMNCompilerContext ctx, DMNModelImpl model,
                                                            DMNBaseNode node, String exprName,
                                                            LiteralExpression expression) {
        DMNLiteralExpressionEvaluator evaluator = null;
        String exprText = expression.getText();
        // an empty literal expression is always an error that returns a null evaluator, so better to deal with it here
        if (exprText == null) {
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  expression,
                                  model,
                                  null,
                                  null,
                                  Msg.MISSING_EXPRESSION_FOR_NAME,
                                  exprName,
                                  node.getIdentifierString());
            return null;
        }
        String expressionLanguage = expression.getExpressionLanguage();
        if (expressionLanguage == null ||
                expressionLanguage.equals(expression.getURIFEEL()) ||
                expressionLanguage.equals(FEELDialect.BFEEL.getNamespace())) { // we allow to override
            try {
                CompiledExpression compiledExpression = ctx.getFeelHelper().compileFeelExpression(ctx,
                                                                                                  exprText,
                                                                                                  model,
                                                                                                  expression,
                                                                                                  Msg.ERR_COMPILING_FEEL_EXPR_FOR_NAME_ON_NODE,
                                                                                                  exprText,
                                                                                                  exprName,
                                                                                                  node.getIdentifierString());
                FEEL feelInstance = getFEELDialectAdaptedFEEL(ctx, expression, expressionLanguage);
                evaluator = new DMNLiteralExpressionEvaluator(compiledExpression, expression,
                                                              feelInstance);
            } catch (Throwable e) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      expression,
                                      model,
                                      e,
                                      null,
                                      Msg.ERR_COMPILING_FEEL_EXPR_FOR_NAME_ON_NODE,
                                      exprText,
                                      exprName,
                                      node.getIdentifierString(),
                                      "Exception raised: " + e.getClass().getSimpleName());
            }
        }
        return evaluator;
    }

    private java.util.List<UnaryTest> textToUnaryTestList(DMNCompilerContext ctx, String text, DMNModelImpl model,
                                                          DMNElement element, Msg.Message errorMsg,
                                                          Object... msgParams) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        return ctx.getFeelHelper().evaluateUnaryTests(ctx, text, model, element, errorMsg, msgParams);
    }

    private DMNExpressionEvaluator compileConditional(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                      String exprName, Conditional expression) {
        DMNExpressionEvaluator ifEvaluator = compileExpression(ctx, model, node, exprName + " [if]",
                                                               expression.getIf().getExpression());
        DMNExpressionEvaluator thenEvaluator = compileExpression(ctx, model, node, exprName + " [then]",
                                                                 expression.getThen().getExpression());
        DMNExpressionEvaluator elseEvaluator = compileExpression(ctx, model, node, exprName + " [else]",
                                                                 expression.getElse().getExpression());

        if (ifEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_CONDITION, "if",
                                  node.getIdentifierString());
            return null;
        }

        if (thenEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_CONDITION, "then",
                                  node.getIdentifierString());
            return null;
        }

        if (elseEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_CONDITION, "else",
                                  node.getIdentifierString());
            return null;
        }

        return new DMNConditionalEvaluator(exprName, node.getSource(), ifEvaluator, thenEvaluator, elseEvaluator);
    }

    private DMNExpressionEvaluator compileIterator(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                   String exprName, Iterator expression) {
        if (expression.getIteratorVariable() == null || expression.getIteratorVariable().isEmpty()) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_ITERATOR,
                                  "iteratorVariable", node.getIdentifierString());
            return null;
        }

        DMNExpressionEvaluator inEvaluator;
        DMNExpressionEvaluator returnEvaluator;

        inEvaluator = compileExpression(ctx, model, node, exprName + " [in]", expression.getIn().getExpression());

        try {
            ctx.enterFrame();
            DMNType outputType = compiler.resolveTypeRef(model, null, node.getSource(), expression.getTypeRef());
            DMNType elementType = compiler.resolveTypeRefUsingString(model, null, node.getSource(),
                                                                     expression.getIn().getTypeRef());
            if (elementType != null && elementType.isCollection() && elementType instanceof BaseDMNTypeImpl) {
                elementType = extractOrSynthesizeGeneric(model, (BaseDMNTypeImpl) elementType);
            }

            ctx.setVariable(expression.getIteratorVariable(), elementType != null ? elementType :
                    model.getTypeRegistry().unknown());
            ctx.setVariable("partial", outputType != null ? outputType : model.getTypeRegistry().unknown());
            returnEvaluator = compileExpression(ctx, model, node, exprName + " [return]", expression instanceof For ?
                    ((For) expression).getReturn().getExpression() :
                    ((Quantified) expression).getSatisfies().getExpression());
        } finally {
            ctx.exitFrame();
        }

        if (inEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_ITERATOR, "in",
                                  node.getIdentifierString());
            return null;
        }

        if (returnEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_ITERATOR,
                                  expression instanceof For ? "return" : "satisfies", node.getIdentifierString());
            return null;
        }

        return new DMNIteratorEvaluator(exprName, node.getSource(), expression, expression.getIteratorVariable(),
                                        inEvaluator, returnEvaluator);
    }

    /**
     * extract the generic T from the DMN representation of FEEL:list<T>
     */
    private DMNType extractOrSynthesizeGeneric(DMNModelImpl model, BaseDMNTypeImpl elementType) {
        if (elementType.getBaseType() != null) {
            return elementType.getBaseType();
        } else if (elementType instanceof CompositeTypeImpl) {
            CompositeTypeImpl orig = (CompositeTypeImpl) elementType;
            return new CompositeTypeImpl(orig.getNamespace(),
                                         UUID.randomUUID().toString() + orig.getName(),
                                         UUID.randomUUID().toString() + orig.getId(),
                                         false, // synth T.
                                         orig.getFields(),
                                         null,
                                         null);
        }
        return model.getTypeRegistry().unknown();
    }

    private DMNExpressionEvaluator compileFilter(DMNCompilerContext ctx, DMNModelImpl model, DMNBaseNode node,
                                                 String exprName, Filter expression) {
        DMNExpressionEvaluator inEvaluator = compileExpression(ctx, model, node, exprName + " [in]",
                                                               expression.getIn().getExpression());
        DMNExpressionEvaluator filterEvaluator;

        try {
            ctx.enterFrame();

            DMNType outputType = compiler.resolveTypeRef(model, null, node.getSource(), expression.getTypeRef());
            DMNType elementType = outputType;
            if (elementType != null && elementType.isCollection() && elementType instanceof BaseDMNTypeImpl) {
                elementType = extractOrSynthesizeGeneric(model, (BaseDMNTypeImpl) elementType);
            }

            ctx.setVariable("item", elementType != null ? elementType : model.getTypeRegistry().unknown());
            if (elementType != null && elementType.isComposite()) {
                elementType.getFields().forEach((k, v) -> ctx.setVariable(k, v != null ? v :
                        model.getTypeRegistry().unknown()));
            }
            filterEvaluator = compileExpression(ctx, model, node, exprName + " [filter]",
                                                expression.getMatch().getExpression());
        } finally {
            ctx.exitFrame();
        }

        if (inEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_FILTER, "in",
                                  node.getIdentifierString());
            return null;
        }

        if (filterEvaluator == null) {
            MsgUtil.reportMessage(logger, DMNMessage.Severity.ERROR, node.getSource(), model, null, null,
                                  Msg.MISSING_EXPRESSION_FOR_FILTER, "filter",
                                  node.getIdentifierString());
            return null;
        }

        return new DMNFilterEvaluator(exprName, node.getSource(), inEvaluator, filterEvaluator);
    }
}
