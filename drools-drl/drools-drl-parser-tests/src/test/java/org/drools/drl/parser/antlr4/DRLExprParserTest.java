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
package org.drools.drl.parser.antlr4;

import java.util.Arrays;
import java.util.List;

import org.drools.drl.ast.descr.AtomicExprDescr;
import org.drools.drl.ast.descr.BindingDescr;
import org.drools.drl.ast.descr.ConnectiveType;
import org.drools.drl.ast.descr.ConstraintConnectiveDescr;
import org.drools.drl.ast.descr.RelationalExprDescr;
import org.drools.drl.parser.DrlExprParser;
import org.drools.drl.parser.DrlExprParserFactory;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.parser.DroolsParserException;
import org.drools.drl.parser.impl.Operator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.internal.builder.conf.LanguageLevelOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DRLExprTreeTest
 */
class DRLExprParserTest {

    DrlExprParser parser;

    @BeforeEach
    void setUp() {
        this.parser = DrlExprParserFactory.getDrlExprParser(LanguageLevelOption.DRL6);
    }

    @AfterEach
    void tearDown() {
        this.parser = null;
    }

    @Test
    void simpleExpression() {
        String source = "a > b";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo(">");

        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();

        assertThat(left.getExpression()).isEqualTo("a");
        assertThat(right.getExpression()).isEqualTo("b");
    }

    @Test
    void andConnective() {
        String source = "a > b && 10 != 20";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo(">");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("a");
        assertThat(right.getExpression()).isEqualTo("b");

        expr = (RelationalExprDescr) result.getDescrs().get( 1 );
        assertThat(expr.getOperator()).isEqualTo("!=");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("10");
        assertThat(right.getExpression()).isEqualTo("20");
    }

    @Test
    void connective2() {
        String source = "(a > b || 10 != 20) && someMethod(10) == 20";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        ConstraintConnectiveDescr or = (ConstraintConnectiveDescr) result.getDescrs().get( 0 );
        assertThat(or.getConnective()).isEqualTo(ConnectiveType.OR);
        assertThat(or.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) or.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo(">");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("a");
        assertThat(right.getExpression()).isEqualTo("b");

        expr = (RelationalExprDescr) or.getDescrs().get( 1 );
        assertThat(expr.getOperator()).isEqualTo("!=");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("10");
        assertThat(right.getExpression()).isEqualTo("20");

        expr = (RelationalExprDescr) result.getDescrs().get( 1 );
        assertThat(expr.getOperator()).isEqualTo("==");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("someMethod(10)");
        assertThat(right.getExpression()).isEqualTo("20");

    }

    @Test
    void binding() {
        String source = "$x : property";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        BindingDescr bind = (BindingDescr) result.getDescrs().get( 0 );
        assertThat(bind.getVariable()).isEqualTo("$x");
        assertThat(bind.getExpression()).isEqualTo("property");
    }

    @Test
    void bindingConstraint() {
        String source = "$x : property > value";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        RelationalExprDescr rel = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(rel.getOperator()).isEqualTo(">");

        BindingDescr bind = (BindingDescr) rel.getLeft();
        assertThat(bind.getVariable()).isEqualTo("$x");
        assertThat(bind.getExpression()).isEqualTo("property");

        AtomicExprDescr right = (AtomicExprDescr) rel.getRight();
        assertThat(right.getExpression()).isEqualTo("value");
    }

    @Test
    void bindingWithRestrictions() {
        String source = "$x : property > value && < 20";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        RelationalExprDescr rel = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(rel.getOperator()).isEqualTo(">");

        BindingDescr bind = (BindingDescr) rel.getLeft();
        assertThat(bind.getVariable()).isEqualTo("$x");
        assertThat(bind.getExpression()).isEqualTo("property");

        AtomicExprDescr right = (AtomicExprDescr) rel.getRight();
        assertThat(right.getExpression()).isEqualTo("value");

        rel = (RelationalExprDescr) result.getDescrs().get( 1 );
        assertThat(rel.getOperator()).isEqualTo("<");

        AtomicExprDescr left = (AtomicExprDescr) rel.getLeft();
        assertThat(left.getExpression()).isEqualTo("property");

        right = (AtomicExprDescr) rel.getRight();
        assertThat(right.getExpression()).isEqualTo("20");
    }

    @Test
    void doubleBinding() {
        String source = "$x : x.m( 1, a ) && $y : y[z].foo";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        BindingDescr bind = (BindingDescr) result.getDescrs().get( 0 );
        assertThat(bind.getVariable()).isEqualTo("$x");
        assertThat(bind.getExpression()).isEqualTo("x.m( 1, a )");

        bind = (BindingDescr) result.getDescrs().get( 1 );
        assertThat(bind.getVariable()).isEqualTo("$y");
        assertThat(bind.getExpression()).isEqualTo("y[z].foo");
    }

    private static final List<Operator.BuiltInOperator> nonKeywordBuiltInOperators = Arrays.asList(
            Operator.BuiltInOperator.EQUAL,
            Operator.BuiltInOperator.NOT_EQUAL,
            Operator.BuiltInOperator.LESS,
            Operator.BuiltInOperator.LESS_OR_EQUAL,
            Operator.BuiltInOperator.GREATER,
            Operator.BuiltInOperator.GREATER_OR_EQUAL
    );

    @ParameterizedTest
    @EnumSource(Operator.BuiltInOperator.class)
    void drlKeywordMethodCall(Operator.BuiltInOperator operator) {
        // Skip operators that cannot be used as method names (==, !=, <, etc.).
        assumeFalse(nonKeywordBuiltInOperators.contains(operator));

        String source = String.format("x.%s( 1, a )", operator.getSymbol());
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        AtomicExprDescr descr = (AtomicExprDescr) result.getDescrs().get( 0 );
        assertThat(descr.getExpression()).isEqualTo(source);
    }

    @ParameterizedTest
    @EnumSource(Operator.BuiltInOperator.class)
    void drlKeywordInChainedMethodCallWithBinding(Operator.BuiltInOperator operator) {
        // Skip operators that cannot be used as method names (==, !=, <, etc.).
        assumeFalse(nonKeywordBuiltInOperators.contains(operator));

        String expressionSource = String.format("x.%s( 1, a ).%s(\"\")", operator.getSymbol(), operator.getSymbol());
        String bindingVariableSource = "$x";
        String source = bindingVariableSource + " : " + expressionSource;
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        BindingDescr bind = (BindingDescr) result.getDescrs().get( 0 );
        assertThat(bind.getVariable()).isEqualTo(bindingVariableSource);
        assertThat(bind.getExpression()).isEqualTo(expressionSource);
    }

    @Test
    void deepBinding() {
        String source = "($a : a > $b : b[10].prop || 10 != 20) && $x : someMethod(10) == 20";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        ConstraintConnectiveDescr or = (ConstraintConnectiveDescr) result.getDescrs().get( 0 );
        assertThat(or.getConnective()).isEqualTo(ConnectiveType.OR);
        assertThat(or.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) or.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo(">");
        BindingDescr leftBind = (BindingDescr) expr.getLeft();
        BindingDescr rightBind = (BindingDescr) expr.getRight();
        assertThat(leftBind.getVariable()).isEqualTo("$a");
        assertThat(leftBind.getExpression()).isEqualTo("a");
        assertThat(rightBind.getVariable()).isEqualTo("$b");
        assertThat(rightBind.getExpression()).isEqualTo("b[10].prop");

        expr = (RelationalExprDescr) or.getDescrs().get( 1 );
        assertThat(expr.getOperator()).isEqualTo("!=");
        AtomicExprDescr leftExpr = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr rightExpr = (AtomicExprDescr) expr.getRight();
        assertThat(leftExpr.getExpression()).isEqualTo("10");
        assertThat(rightExpr.getExpression()).isEqualTo("20");

        expr = (RelationalExprDescr) result.getDescrs().get( 1 );
        assertThat(expr.getOperator()).isEqualTo("==");
        leftBind = (BindingDescr) expr.getLeft();
        rightExpr = (AtomicExprDescr) expr.getRight();
        assertThat(leftBind.getVariable()).isEqualTo("$x");
        assertThat(leftBind.getExpression()).isEqualTo("someMethod(10)");
        assertThat(rightExpr.getExpression()).isEqualTo("20");

    }

    @Test
    @Timeout(10000L)
    void nestedExpression() {
        // DROOLS-982
        String source = "(((((((((((((((((((((((((((((((((((((((((((((((((( a > b ))))))))))))))))))))))))))))))))))))))))))))))))))";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo(">");

        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();

        assertThat(left.getExpression()).isEqualTo("a");
        assertThat(right.getExpression()).isEqualTo("b");
    }

    /**
     * Each test input is a simple expression covering one of the existing keywords. The test is successful if the parser has
     * no errors and the descriptor's expression string is equal to the input.
     *
     * @param source expression using a keyword
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "(X<? extends Number>) x",
            "SomeClass.super.getData()",
            "(boolean) b",
            "(char) c",
            "(byte) b",
            "(short) s",
            "(int) i",
            "(long) l",
            "(float) f",
            "(double) d",
            "this",
            "<Type>this()",
            "Object[][].class.getName()",
            "new<Integer>ArrayList<Integer>()"
    })
    void keywords(String source) {
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        AtomicExprDescr expr = (AtomicExprDescr) result.getDescrs().get( 0 );

        assertThat(expr.getExpression()).isEqualTo(source);
    }

    @Test
    void keywordInstanceof() {
        String source = "a instanceof A";
        ConstraintConnectiveDescr result = parser.parse( source );
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(1);

        // Unlike the other keywords, instanceof can only be used in a relational expression,
        // so it needs to be tested differently.
        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get( 0 );
        assertThat(expr.getOperator()).isEqualTo("instanceof");

        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();

        assertThat(left.getExpression()).isEqualTo("a");
        assertThat(right.getExpression()).isEqualTo("A");
    }

    @Test
    void mismatchedInput() {
        String source = "+";
        parser.parse(source);
        assertThat(parser.hasErrors()).isTrue();
        assertThat(parser.getErrors()).hasSize(1);
        DroolsParserException exception = parser.getErrors().get(0);

        // Backward Compatibility Notes:
        //   Antlr4 gives a different error code/message from antlr3 for this case.
        //   Backward compatibility doesn't seem to be required in this case.
        if (DrlParser.ANTLR4_PARSER_ENABLED) {
            assertThat(exception.getErrorCode()).isEqualTo("ERR 102");
            assertThat(exception.getLineNumber()).isEqualTo(1);
            assertThat(exception.getColumn()).isEqualTo(1);
            assertThat(exception.getOffset()).isEqualTo(1);
            assertThat(exception.getMessage())
                    .startsWithIgnoringCase("[ERR 102] Line 1:1 mismatched input '<EOF>' expecting ")
                    .contains("TIME_INTERVAL", "DRL_STRING_LITERAL", "?/", "boolean", "byte", "char", "double", "float", "int", "long", "new", "short", "super", "DECIMAL_LITERAL", "HEX_LITERAL", "FLOAT_LITERAL", "BOOL_LITERAL", "STRING_LITERAL", "null", "(", "[", ".", "<", "!", "~", "++", "--", "+", "-", "*", "/", "IDENTIFIER");
        } else {
            assertThat(exception.getErrorCode()).isEqualTo("ERR 101");
            assertThat(exception.getLineNumber()).isEqualTo(1);
            assertThat(exception.getColumn()).isEqualTo(1);
            assertThat(exception.getOffset()).isEqualTo(1);
            assertThat(exception.getMessage())
                    .isEqualToIgnoringCase("[ERR 101] Line 1:1 no viable alternative at input '<eof>'");
        }
    }

    @Test
    void extraneousInput() {
        String source = "a +; b";
        parser.parse(source);
        assertThat(parser.hasErrors()).isTrue();
        assertThat(parser.getErrors()).hasSize(1);
        DroolsParserException exception = parser.getErrors().get(0);

        // Backward Compatibility Notes:
        //   Antlr4 gives a different error code/message from antlr3 for this case.
        //   Backward compatibility doesn't seem to be required in this case.
        if (DrlParser.ANTLR4_PARSER_ENABLED) {
            assertThat(exception.getErrorCode()).isEqualTo("ERR 109");
            assertThat(exception.getLineNumber()).isEqualTo(1);
            assertThat(exception.getColumn()).isEqualTo(3);
            assertThat(exception.getOffset()).isEqualTo(3);
            assertThat(exception.getMessage())
                    .startsWithIgnoringCase("[ERR 109] Line 1:3 extraneous input ';' expecting ")
                    .contains("TIME_INTERVAL", "DRL_STRING_LITERAL", "?/", "boolean", "byte", "char", "double", "float", "int", "long", "new", "short", "super", "DECIMAL_LITERAL", "HEX_LITERAL", "FLOAT_LITERAL", "BOOL_LITERAL", "STRING_LITERAL", "null", "(", "[", ".", "<", "!", "~", "++", "--", "+", "-", "*", "/", "IDENTIFIER");
        } else {
            assertThat(exception.getErrorCode()).isEqualTo("ERR 101");
            assertThat(exception.getLineNumber()).isEqualTo(1);
            assertThat(exception.getColumn()).isEqualTo(3);
            assertThat(exception.getOffset()).isEqualTo(3);
            assertThat(exception.getMessage())
                    .isEqualToIgnoringCase("[ERR 101] Line 1:3 no viable alternative at input ';'");
        }
    }

    @Test
    void noViableAlt() {
        String source = "a~a";
        parser.parse(source);

        // Backward Compatibility Notes:
        //   Old expr parser (DRL6Expressions) allows this expression and only takes "a" ignoring the invalid part "~a" without emitting an error.
        //   This is rather a bug in the old parser, and the new parser (ANTLR4) correctly emits an error for this case.
        //   Backward compatibility doesn't seem to be required in this case.
        if (DrlParser.ANTLR4_PARSER_ENABLED) {
            assertThat(parser.hasErrors()).isTrue();
            assertThat(parser.getErrors()).hasSize(1);
            DroolsParserException exception = parser.getErrors().get(0);
            assertThat(exception.getErrorCode()).isEqualTo("ERR 101");
            assertThat(exception.getLineNumber()).isEqualTo(1);
            assertThat(exception.getColumn()).isEqualTo(2);
            assertThat(exception.getOffset()).isEqualTo(2);
            assertThat(exception.getMessage())
                    .isEqualToIgnoringCase("[ERR 101] Line 1:2 no viable alternative at input '~a'");
        } else {
            assertThat(parser.hasErrors()).isFalse();
        }
    }

    @Test
    void orWithMethodCall() {
        String source = "value == 10 || someMethod() == 4";
        ConstraintConnectiveDescr result = parser.parse(source);
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND); // root is AND
        assertThat(result.getDescrs()).hasSize(1);
        ConstraintConnectiveDescr or = (ConstraintConnectiveDescr) result.getDescrs().get(0);
        assertThat(or.getConnective()).isEqualTo(ConnectiveType.OR);
        assertThat(or.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) or.getDescrs().get(0);
        assertThat(expr.getOperator()).isEqualTo("==");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("value");
        assertThat(right.getExpression()).isEqualTo("10");

        expr = (RelationalExprDescr) or.getDescrs().get(1);
        assertThat(expr.getOperator()).isEqualTo("==");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("someMethod()");
        assertThat(right.getExpression()).isEqualTo("4");
    }

    @Test
    void orWithMethodCallWithArg() {
        String source = "value == 10 || someMethod(value) == 4";
        ConstraintConnectiveDescr result = parser.parse(source);
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND); // root is AND
        assertThat(result.getDescrs()).hasSize(1);
        ConstraintConnectiveDescr or = (ConstraintConnectiveDescr) result.getDescrs().get(0);
        assertThat(or.getConnective()).isEqualTo(ConnectiveType.OR);
        assertThat(or.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) or.getDescrs().get(0);
        assertThat(expr.getOperator()).isEqualTo("==");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("value");
        assertThat(right.getExpression()).isEqualTo("10");

        expr = (RelationalExprDescr) or.getDescrs().get(1);
        assertThat(expr.getOperator()).isEqualTo("==");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("someMethod(value)");
        assertThat(right.getExpression()).isEqualTo("4");
    }

    @Test
    void andWithMethodCall() {
        String source = "value == 10 && someMethod() == 4";
        ConstraintConnectiveDescr result = parser.parse(source);
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get(0);
        assertThat(expr.getOperator()).isEqualTo("==");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("value");
        assertThat(right.getExpression()).isEqualTo("10");

        expr = (RelationalExprDescr) result.getDescrs().get(1);
        assertThat(expr.getOperator()).isEqualTo("==");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("someMethod()");
        assertThat(right.getExpression()).isEqualTo("4");
    }

    @Test
    void andWithMethodCallWithArg() {
        String source = "value == 10 && someMethod(value) == 4";
        ConstraintConnectiveDescr result = parser.parse(source);
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getConnective()).isEqualTo(ConnectiveType.AND);
        assertThat(result.getDescrs()).hasSize(2);

        RelationalExprDescr expr = (RelationalExprDescr) result.getDescrs().get(0);
        assertThat(expr.getOperator()).isEqualTo("==");
        AtomicExprDescr left = (AtomicExprDescr) expr.getLeft();
        AtomicExprDescr right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("value");
        assertThat(right.getExpression()).isEqualTo("10");

        expr = (RelationalExprDescr) result.getDescrs().get(1);
        assertThat(expr.getOperator()).isEqualTo("==");
        left = (AtomicExprDescr) expr.getLeft();
        right = (AtomicExprDescr) expr.getRight();
        assertThat(left.getExpression()).isEqualTo("someMethod(value)");
        assertThat(right.getExpression()).isEqualTo("4");
    }

    @Test
    void newBigDecimal() {
        String source = "$bd : new BigDecimal(30)";
        ConstraintConnectiveDescr result = parser.parse(source);
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

        assertThat(result.getDescrs()).hasSize(1);

        BindingDescr bind = (BindingDescr) result.getDescrs().get(0);
        assertThat(bind.getVariable()).isEqualTo("$bd");
        assertThat(bind.getExpression()).isEqualTo("new BigDecimal(30)");
    }
}
