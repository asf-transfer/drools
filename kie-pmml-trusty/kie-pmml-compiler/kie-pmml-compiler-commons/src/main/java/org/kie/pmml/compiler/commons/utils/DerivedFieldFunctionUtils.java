/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.pmml.compiler.commons.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.dmg.pmml.Aggregate;
import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Discretize;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Lag;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.NormContinuous;
import org.dmg.pmml.NormDiscrete;
import org.dmg.pmml.TextIndex;
import org.kie.pmml.commons.model.enums.DATA_TYPE;
import org.kie.pmml.commons.model.tuples.KiePMMLNameValue;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static org.kie.pmml.compiler.commons.utils.CommonCodegenUtils.OPTIONAL_FILTERED_KIEPMMLNAMEVALUE_NAME;
import static org.kie.pmml.compiler.commons.utils.CommonCodegenUtils.getFilteredKiePMMLNameValueExpression;
import static org.kie.pmml.compiler.commons.utils.CommonCodegenUtils.getMethodDeclaration;
import static org.kie.pmml.compiler.commons.utils.CommonCodegenUtils.getTypedClassOrInterfaceType;

/**
 * Class meant to provide <i>helper</i> methods to retrieve <code>Function</code> code-generators
 * out of <code>DerivedField</code>s
 */
public class DerivedFieldFunctionUtils {

    static final String KIEPMMLNAMEVALUE_LIST_PARAM = "param1"; // it is the first parameter

    private DerivedFieldFunctionUtils() {
        // Avoid instantiation
    }

    static Map<String, MethodDeclaration> getDerivedFieldsMethodMap(final List<DerivedField> derivedFields, final AtomicInteger arityCounter) {
        Map<String, MethodDeclaration> toReturn = new HashMap<>();
        derivedFields.forEach(derivedField ->
                                      toReturn.put(derivedField.getName().getValue(),
                                                   getDerivedFieldMethodDeclaration(derivedField, arityCounter)));
        return toReturn;
    }

    static MethodDeclaration getDerivedFieldMethodDeclaration(final DerivedField derivedField, final AtomicInteger arityCounter) {
        final Expression expression = derivedField.getExpression();
        if (expression != null) {
            return getExpressionMethodDeclaration(expression, arityCounter);
        } else {
            // TODO {gcardosi}
            return new MethodDeclaration();
        }
    }

    static MethodDeclaration getExpressionMethodDeclaration(final Expression expression, final AtomicInteger arityCounter) {
        int methodArity = arityCounter.addAndGet(1);
        if (expression instanceof Aggregate) {
            return getAggregatefMethodDeclaration((Aggregate) expression, methodArity);
        } else if (expression instanceof Apply) {
            return getApplyMethodDeclaration((Apply)expression, methodArity);
        } else if (expression instanceof Constant) {
            return getConstantMethodDeclaration((Constant)expression, methodArity);
        } else if (expression instanceof Discretize) {
            return getDiscretizeMethodDeclaration((Discretize)expression, methodArity);
        } else if (expression instanceof FieldRef) {
            return getFieldRefMethodDeclaration((FieldRef)expression, methodArity);
        } else if (expression instanceof Lag) {
            return getLagMethodDeclaration((Lag)expression, methodArity);
        } else if (expression instanceof MapValues) {
            return getMapValuesMethodDeclaration((MapValues)expression, methodArity);
        } else if (expression instanceof NormContinuous) {
            return getNormContinuousMethodDeclaration((NormContinuous)expression, methodArity);
        } else if (expression instanceof NormDiscrete) {
            return getNormDiscreteMethodDeclaration((NormDiscrete)expression, methodArity);
        } else if (expression instanceof TextIndex) {
            return getTextIndexMethodDeclaration((TextIndex)expression, methodArity);
        } else {
            throw new IllegalArgumentException(String.format("Expression %s not managed", expression.getClass()));
        }
    }

    static MethodDeclaration getAggregatefMethodDeclaration(final Aggregate aggregate, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(aggregate, methodArity);
        return toReturn;
    }

    static MethodDeclaration getApplyMethodDeclaration(final Apply apply, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(apply, methodArity);
        return toReturn;
    }

    static MethodDeclaration getConstantMethodDeclaration(final Constant constant, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(constant, methodArity);
        Class<?> returnedType = DATA_TYPE.byName(constant.getDataType().value()).getMappedClass();
        ClassOrInterfaceType classOrInterfaceType = parseClassOrInterfaceType(returnedType.getName());
        toReturn.setType(classOrInterfaceType);
        final BlockStmt body = new BlockStmt();
        ReturnStmt returnStmt = new ReturnStmt();
        returnStmt.setExpression(new NameExpr(constant.getValue().toString()));
        body.addStatement(returnStmt);
        toReturn.setBody(body);
        return toReturn;
    }

    static MethodDeclaration getDiscretizeMethodDeclaration(final Discretize discretize, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(discretize, methodArity);
        return toReturn;
    }

    static MethodDeclaration getFieldRefMethodDeclaration(final FieldRef fieldRef, final int methodArity) {
        final BlockStmt body = new BlockStmt();
        String fieldNameToRef = fieldRef.getField().getValue();
        ExpressionStmt filteredOptionalExpr = getFilteredKiePMMLNameValueExpression(KIEPMMLNAMEVALUE_LIST_PARAM, fieldNameToRef);
        body.addStatement(filteredOptionalExpr);
        //KiePMMLNameValue::getValue
        MethodReferenceExpr methodReferenceExpr = new MethodReferenceExpr();
        methodReferenceExpr.setScope(new TypeExpr(parseClassOrInterfaceType(KiePMMLNameValue.class.getName())));
        methodReferenceExpr.setIdentifier("getValue");
        // kiePMMLNameValue.map
        MethodCallExpr expressionScope = new MethodCallExpr("map");
        expressionScope.setScope(new NameExpr(OPTIONAL_FILTERED_KIEPMMLNAMEVALUE_NAME));
        // kiePMMLNameValue.map(KiePMMLNameValue::getValue)
        expressionScope.setArguments(NodeList.nodeList(methodReferenceExpr));
        // kiePMMLNameValue.map(KiePMMLNameValue::getValue).orElse( (fieldRef.getMapMissingTo() )
        MethodCallExpr expression = new MethodCallExpr("orElse");
        expression.setScope(expressionScope);
        com.github.javaparser.ast.expr.Expression orElseExpression =  fieldRef.getMapMissingTo() != null ? new StringLiteralExpr(fieldRef.getMapMissingTo()) : new NullLiteralExpr();
        expression.setArguments(NodeList.nodeList(orElseExpression));
        // return kiePMMLNameValue.map(KiePMMLNameValue::getValue).orElse( (fieldRef.getMapMissingTo() )
        ReturnStmt returnStmt = new ReturnStmt();
        returnStmt.setExpression(expression);
        body.addStatement(returnStmt);
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(fieldRef, methodArity);
        ClassOrInterfaceType classOrInterfaceType = parseClassOrInterfaceType(Object.class.getName());
        toReturn.setType(classOrInterfaceType);
        toReturn.setBody(body);
        return toReturn;
    }

    static MethodDeclaration getLagMethodDeclaration(final Lag lag, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(lag, methodArity);
        return toReturn;
    }

    static MethodDeclaration getMapValuesMethodDeclaration(final MapValues mapValues, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(mapValues, methodArity);
        return toReturn;
    }

    static MethodDeclaration getNormContinuousMethodDeclaration(final NormContinuous normContinuous, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(normContinuous, methodArity);
        return toReturn;
    }

    static MethodDeclaration getNormDiscreteMethodDeclaration(final NormDiscrete normDiscrete, final int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(normDiscrete, methodArity);
        return toReturn;
    }

    static MethodDeclaration getTextIndexMethodDeclaration(final TextIndex textIndex, final  int methodArity) {
        MethodDeclaration toReturn = getDerivedFieldsMethodDeclaration(textIndex, methodArity);
        return toReturn;
    }
    
    static MethodDeclaration getDerivedFieldsMethodDeclaration(final Expression expression, final int methodArity) {
        ClassOrInterfaceType parameter = getTypedClassOrInterfaceType(List.class.getName(), Collections.singletonList(KiePMMLNameValue.class.getName()));
        return getMethodDeclaration(expression, methodArity, Collections.singletonList(parameter));
    }

}
