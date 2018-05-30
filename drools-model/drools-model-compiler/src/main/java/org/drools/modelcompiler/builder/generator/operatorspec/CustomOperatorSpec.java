package org.drools.modelcompiler.builder.generator.operatorspec;

import java.util.Optional;

import org.drools.javaparser.ast.drlx.expr.PointFreeExpr;
import org.drools.javaparser.ast.expr.Expression;
import org.drools.javaparser.ast.expr.MethodCallExpr;
import org.drools.javaparser.ast.expr.StringLiteralExpr;
import org.drools.javaparser.ast.expr.UnaryExpr;
import org.drools.model.functions.Operator;
import org.drools.modelcompiler.builder.generator.RuleContext;
import org.drools.modelcompiler.builder.generator.TypedExpression;
import org.drools.modelcompiler.builder.generator.expressiontyper.ExpressionTyper;

import static org.drools.modelcompiler.builder.generator.DslMethodNames.EVAL_CALL;
import static org.drools.modelcompiler.builder.generator.drlxparse.ConstraintParser.coerceRightExpression;

public class CustomOperatorSpec implements OperatorSpec {
    public static final CustomOperatorSpec INSTANCE = new CustomOperatorSpec();

    public Expression getExpression(RuleContext context, PointFreeExpr pointFreeExpr, TypedExpression left, ExpressionTyper expressionTyper) {
        MethodCallExpr methodCallExpr = new MethodCallExpr( null, EVAL_CALL );

        String opName = pointFreeExpr.getOperator().asString();
        Operator operator = Operator.Register.getOperator( opName );
        try {
            // if the operator has an INSTANCE field avoid the operator lookup at runtime
            operator.getClass().getField( "INSTANCE" );
            methodCallExpr.addArgument( operator.getClass().getCanonicalName() + ".INSTANCE" );
        } catch (NoSuchFieldException e) {
            methodCallExpr.addArgument( new StringLiteralExpr( opName ) );
        }

        methodCallExpr.addArgument( left.getExpression() );
        for (Expression rightExpr : pointFreeExpr.getRight()) {
            final Optional<TypedExpression> optionalRight = expressionTyper.toTypedExpression(rightExpr).getTypedExpression();
            optionalRight.ifPresent( right -> {
                if (operator.requiresCoercion()) {
                    System.out.println("\n\n");
                    System.out.println("YYY left = " + left);
                    System.out.println("YYY right = " + right);
                    coerceRightExpression(left, right);

                    System.out.println("YYY left = " + left);
                    System.out.println("YYY right = " + right);
                    System.out.println("\n\n");

                }
                methodCallExpr.addArgument(right.getExpression() );
            });
        }

        return pointFreeExpr.isNegated() ?
                new UnaryExpr( methodCallExpr, UnaryExpr.Operator.LOGICAL_COMPLEMENT ) :
                methodCallExpr;
    }

    @Override
    public boolean isStatic() {
        return false;
    }
}
