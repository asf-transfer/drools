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
package org.kie.dmn.core.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.xml.namespace.QName;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.antlr.v4.runtime.CommonToken;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.core.impl.BaseDMNTypeImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.util.Msg;
import org.kie.dmn.core.util.MsgUtil;
import org.kie.dmn.core.util.NamespaceUtil;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.codegen.feel11.ProcessedUnaryTest;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.dmn.feel.lang.CompilerContext;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.FEELProfile;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.impl.EvaluationContextImpl;
import org.kie.dmn.feel.lang.impl.FEELBuilder;
import org.kie.dmn.feel.lang.impl.FEELEventListenersManager;
import org.kie.dmn.feel.lang.impl.FEELImpl;
import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.runtime.events.ASTHeuristicCheckEvent;
import org.kie.dmn.feel.runtime.events.SyntaxErrorEvent;
import org.kie.dmn.feel.runtime.events.UnknownVariableErrorEvent;
import org.kie.dmn.feel.util.ClassLoaderUtil;
import org.kie.dmn.model.api.DMNElement;
import org.kie.dmn.model.api.ItemDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DMNFEELHelper {

    private static final Logger logger = LoggerFactory.getLogger( DMNFEELHelper.class );

    private final ClassLoader classLoader;
    private final FEEL                   feel;
    private final FEELEventsListenerImpl listener;
    private final List<FEELProfile> feelProfiles = new ArrayList<>();
    private final FEELDialect feelDialect;

    public DMNFEELHelper(List<FEELProfile> feelProfiles, FEELDialect feelDialect) {
        this(ClassLoaderUtil.findDefaultClassLoader(), feelProfiles, feelDialect);
    }

    public DMNFEELHelper(ClassLoader classLoader, List<FEELProfile> feelProfiles, FEELDialect feelDialect) {
        this.classLoader = classLoader;
        this.feelProfiles.addAll(feelProfiles);
        this.listener = new FEELEventsListenerImpl();
        this.feelDialect = feelDialect;
        this.feel = createFEELInstance();
    }

    private FEEL createFEELInstance() {
        FEEL toReturn = newFEELInstance();
        toReturn.addListener( listener );
        return toReturn;
    }

    /**
     * Return a FEEL instance to be used in invokers/impls, which is however configured correctly accordingly to profiles
     * This FEEL instance is potentially not the same shared by the compiler during the compilation phase.
     */
    public FEEL newFEELInstance() {
        return FEELBuilder.builder().withClassloader(classLoader)
                .withProfiles(feelProfiles)
                .withFEELDialect(feelDialect)
                .build();
    }

    /**
     * Return a FEEL instance to be used in invokers/impls, which is however configured correctly accordingly to profiles
     * but overrides the used FEELDialect
     * This FEEL instance is potentially not the same shared by the compiler during the compilation phase.
     *
     */
    public FEEL newFEELInstance(FEELDialect overridingFeelDialect) {
        return FEELBuilder.builder().withClassloader(classLoader)
                .withProfiles(feelProfiles)
                .withFEELDialect(overridingFeelDialect)
                .build();
    }

    public static boolean valueMatchesInUnaryTests(List<UnaryTest> unaryTests, Object value, DMNContext dmnContext) {
        FEELEventListenersManager manager = new FEELEventListenersManager();
        FEELEventsListenerImpl listener = new FEELEventsListenerImpl();
        manager.addListener( listener );
        // Defaulting FEELDialect to FEEL
        EvaluationContextImpl ctx = new EvaluationContextImpl(ClassLoaderUtil.findDefaultClassLoader(), manager, FEELDialect.FEEL);
        try {
            ctx.enterFrame();
            if ( dmnContext != null ) {
                // need to set the values for in context variables...
                for ( Map.Entry<String,Object> entry : dmnContext.getAll().entrySet() ) {
                    ctx.setValue( entry.getKey(), entry.getValue() );
                }
            }

            for ( UnaryTest t : unaryTests ) {
                try {
                    // allow usage of ? as place-holder inside UnaryTest
                    if (!ctx.isDefined("?")) {
                        ctx.setValue("?", value);
                    }
                    Boolean applyT = t.apply( ctx, value );
                    // the unary test above can actually return null, so we have to handle it here
                    if ( applyT == null ) {
                        return false;
                    } else if ( applyT ) {
                        return true;
                    }
                } catch ( Throwable e ) {
                    StringBuilder message = new StringBuilder(  );
                    for( FEELEvent feelEvent : listener.getFeelEvents() ) {
                        message.append( feelEvent.getMessage() );
                        message.append( "\n" );
                    }
                    throw new RuntimeException( message.toString(), e );
                }
            }
        } finally {
            ctx.exitFrame();
        }
        return false;
    }

    public CompiledExpression compileFeelExpression(DMNCompilerContext ctx, String expression, DMNModelImpl model, DMNElement element, Msg.Message errorMsg, Object... msgParams) {
        CompilerContext feelctx = feel.newCompilerContext();

        for ( Map.Entry<String, DMNType> entry : ctx.getVariables().entrySet() ) {
            feelctx.addInputVariableType( entry.getKey(), ((BaseDMNTypeImpl) entry.getValue()).getFeelType() );
        }
        feelctx.setFEELTypeRegistry(model.getTypeRegistry());
        CompiledExpression ce = feel.compile( expression, feelctx );
        processEvents( model, element, errorMsg, msgParams );
        return ce;
    }

    public FEELFunction evaluateFunctionDef(DMNCompilerContext ctx, String expression, DMNModelImpl model, DMNElement element, Msg.Message errorMsg, Object... msgParams) {
        FEELFunction function = null;
        try {
            function = (FEELFunction) feel.evaluate( expression );
        } catch( Throwable t ) {
            logger.error( "Error evaluating function definition. Error will be reported in the model.", t );
        }
        processEvents( model, element, errorMsg, msgParams );
        return function;
    }

    public FEELFunction evaluateFunctionDef(DMNCompilerContext ctx, CompiledExpression expression, DMNModelImpl model, DMNElement element, Msg.Message errorMsg, Object... msgParams) {
        FEELFunction function = null;
        try {
            function = (FEELFunction) feel.evaluate( expression, Collections.emptyMap() );
        } catch( Throwable t ) {
            logger.error( "Error evaluating function definition. Error will be reported in the model.", t );
        }
        processEvents( model, element, errorMsg, msgParams );
        return function;
    }

    public List<UnaryTest> evaluateUnaryTests(DMNCompilerContext ctx, String unaryTests, DMNModelImpl model, DMNElement element, Msg.Message errorMsg, Object... msgParams) {
        List<UnaryTest> result = Collections.emptyList();
        try {
            Map<String, Type> variableTypes = new HashMap<>();
            for ( Map.Entry<String, DMNType> entry : ctx.getVariables().entrySet() ) {
                variableTypes.put( entry.getKey(), ((BaseDMNTypeImpl) entry.getValue()).getFeelType() );
            }
            // allow usage of ? as place-holder inside UnaryTest
            if (!variableTypes.containsKey("?") && element instanceof ItemDefinition itemDef) {
                String nameSpace;
                String name;
                if (itemDef.isIsCollection()) {
                    nameSpace = model.getTypeRegistry().feelNS();
                    name = "list";
                } else {
                    QName typeRef = itemDef.getTypeRef();
                    QName nsAndName = NamespaceUtil.getNamespaceAndName(element, model.getImportAliasesForNS(), typeRef,
                                                                        model.getNamespace());
                    nameSpace = nsAndName.getNamespaceURI();
                    name = nsAndName.getLocalPart();
                }
                BaseDMNTypeImpl toSet = (BaseDMNTypeImpl) model.getTypeRegistry().resolveType(nameSpace, name);
                variableTypes.put("?", toSet.getFeelType());
            }

            result = feel.evaluateUnaryTests( unaryTests, variableTypes );
        } catch( Throwable t ) {
            logger.error( "Error evaluating unary tests. Error will be reported in the model.", t );
        }
        processEvents( model, element, errorMsg, msgParams );
        return result;
    }

    public void processEvents(DMNModelImpl model, DMNElement element, Msg.Message msg, Object... msgParams) {
        Queue<FEELEvent> feelEvents = listener.getFeelEvents();
        while ( !feelEvents.isEmpty() ) {
            FEELEvent event = feelEvents.remove();
            if ( !isDuplicateEvent( model, msg, element ) ) {
                if (event instanceof SyntaxErrorEvent || event instanceof ASTHeuristicCheckEvent || event.getSeverity() == FEELEvent.Severity.ERROR) {
                    DMNMessage.Severity severity = event instanceof ASTHeuristicCheckEvent ? DMNMessage.Severity.WARN : DMNMessage.Severity.ERROR;
                    if ( msg instanceof Msg.Message2 ) {
                        MsgUtil.reportMessage(
                                logger,
                                severity,
                                element,
                                model,
                                null,
                                event,
                                (Msg.Message2) msg,
                                msgParams[0],
                                msgParams[1] );
                    } else if ( msg instanceof Msg.Message3 ) {
                        Object message3;
                        if ( msgParams.length == 3 ) {
                            message3 = msgParams[2];
                        } else {
                            message3 = event.getMessage(); // wrap the originating FEEL error as the last message
                        }
                        MsgUtil.reportMessage(
                                logger,
                                severity,
                                element,
                                model,
                                null,
                                event,
                                (Msg.Message3) msg,
                                msgParams[0],
                                msgParams[1],
                                message3 );
                    } else if ( msg instanceof Msg.Message4 ) {
                        String message;
                        if (event instanceof ASTHeuristicCheckEvent) {
                            message = event.getMessage();
                        } else if (event.getOffendingSymbol() == null) {
                            message = "";
                        } else if( event instanceof UnknownVariableErrorEvent ) {
                            message = event.getMessage();
                        } else if( event.getOffendingSymbol() instanceof CommonToken ) {
                            message = "syntax error near '" + ((CommonToken)event.getOffendingSymbol()).getText() + "'";
                        } else {
                            message = "syntax error near '" + event.getOffendingSymbol() + "'";
                        }
                        MsgUtil.reportMessage(
                                logger,
                                severity,
                                element,
                                model,
                                null,
                                event,
                                (Msg.Message4) msg,
                                msgParams[0],
                                msgParams[1],
                                msgParams[2],
                                message );
                    }
                }
            }
        }
    }

    private boolean isDuplicateEvent(DMNModelImpl model, Msg.Message error, DMNElement element) {
        return model.getMessages().stream().anyMatch( msg -> msg.getMessageType() == error.getType() &&
                                                             (msg.getSourceId() == null && element.getId() == null
                                                                     || (msg.getSourceId() != null && element.getId() != null && msg.getSourceId().equals(element.getId()))) );
    }

    public ClassOrInterfaceDeclaration generateUnaryTestsSource(CompilerContext compilerContext, String unaryTests, Type inputColumnType, boolean isStatic) {
        compilerContext.addInputVariableType("?", inputColumnType);

        ProcessedUnaryTest compiledUnaryTest = ((FEELImpl) feel).processUnaryTests(unaryTests, compilerContext);
        CompilationUnit compilationUnit = compiledUnaryTest.getSourceCode().clone();
        return compilationUnit.getType(0)
                .asClassOrInterfaceDeclaration()
                .setStatic(isStatic);
    }

    public ClassOrInterfaceDeclaration generateStaticUnaryTestsSource(CompilerContext compilerContext, String unaryTests, Type inputColumnType) {
        return generateUnaryTestsSource(compilerContext, unaryTests, inputColumnType, true);
    }

    public static class FEELEventsListenerImpl implements FEELEventListener {
        private final Queue<FEELEvent> feelEvents = new LinkedList<>();

        @Override
        public void onEvent(FEELEvent event) {
            feelEvents.add( event );
        }

        public Queue<FEELEvent> getFeelEvents() {
            return feelEvents;
        }
    }

    public EvaluationContextImpl newEvaluationContext( Collection<FEELEventListener> listeners, Map<String, Object> inputVariables) {
        return (( FEELImpl ) feel).newEvaluationContext(listeners, inputVariables);
    }

    public List<UnaryTest> evaluateUnaryTests(String expression, Map<String, Type> variableTypes) {
        return feel.evaluateUnaryTests( expression, variableTypes );
    }

    public CompilerContext newCompilerContext() {
        return feel.newCompilerContext();
    }

    @Deprecated
    public CompiledExpression compile( DMNModelImpl model, DMNElement element, Msg.Message msg, String dtableName, String expr, CompilerContext feelctx, int index ) {
        CompiledExpression compiled = feel.compile( expr, feelctx );
        processEvents( model, element, msg, expr, dtableName, index );
        return compiled;
    }

    public ClassOrInterfaceDeclaration generateFeelExpressionSource(String input, CompilerContext compilerContext1) {
        return generateFeelExpressionCompilationUnit(input, compilerContext1)
                .getType(0)
                .asClassOrInterfaceDeclaration().setStatic(true);
    }

    public CompilationUnit generateFeelExpressionCompilationUnit(String input, CompilerContext compilerContext1) {
        return feel.processExpression(input, compilerContext1).getSourceCode();
    }
}
