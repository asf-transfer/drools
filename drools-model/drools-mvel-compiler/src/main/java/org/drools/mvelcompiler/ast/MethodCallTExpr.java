package org.drools.mvelcompiler.ast;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import static com.github.javaparser.ast.NodeList.nodeList;
import static org.drools.constraint.parser.printer.PrintUtil.printConstraint;

public class MethodCallTExpr extends TypedExpression {

    private final TypedExpression scope;
    private final Type type;
    private final Method accessor;

    private final List<TypedExpression> arguments;

    public MethodCallTExpr(Node expression, TypedExpression scope, Method accessor, List<TypedExpression> arguments) {
        super(expression);
        this.scope = scope;
        this.accessor = accessor;
        this.type = accessor.getGenericReturnType();
        this.arguments = arguments;
    }

    public MethodCallTExpr(Node expression, TypedExpression scope, Method accessor) {
        super(expression);
        this.scope = scope;
        this.accessor = accessor;
        this.type = accessor.getGenericReturnType();
        this.arguments = Collections.emptyList();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Node toJavaExpression() {
        List<Expression> arguments = this.arguments.stream()
                .map(a -> (Expression)(a.toJavaExpression()))
                .collect(Collectors.toList());

        return new MethodCallExpr((Expression) scope.toJavaExpression(), accessor.getName(), nodeList(arguments));
    }

    @Override
    public String toString() {
        return "MethodCallTExpr{" +
                "expression=" + printConstraint(originalExpression) +
                ", scope=" + scope.toString() +
                ", type=" + type +
                ", accessor=" + accessor +
                '}';
    }
}