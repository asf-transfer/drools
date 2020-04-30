package org.drools.modelcompiler.builder.generator.visitor.accumulate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.drools.modelcompiler.builder.generator.RuleContext;

import static java.util.stream.Collectors.toList;
import static org.drools.modelcompiler.builder.generator.DslMethodNames.PATTERN_CALL;
import static org.drools.modelcompiler.builder.generator.expression.PatternExpressionBuilder.BIND_CALL;

public class PatternToReplace {
    final RuleContext context;
    final Collection<String> patternBindings;
    final List<Expression> expressions;

    public PatternToReplace(RuleContext context, Collection<String> patternBindings) {
        this.context = context;
        this.patternBindings = patternBindings;
        expressions = context.getExpressions();
    }

    public Optional<MethodCallExpr> findFromPattern() {
        return expressions.stream().flatMap((Expression e) -> {
            final Optional<MethodCallExpr> pattern = e.findFirst(MethodCallExpr.class, expr -> {
                boolean isPatternExpr = expr.getName().asString().equals(PATTERN_CALL);
                List<Expression> bindingExprsVars = patternBindings.stream().map(context::getVarExpr).collect(Collectors.toList());
                boolean hasBindingHasArgument = !Collections.disjoint(bindingExprsVars, expr.getArguments());
                return isPatternExpr && hasBindingHasArgument;
            });
            return pattern.map(Stream::of).orElse(Stream.empty());
        }).findFirst();
    }

    public Optional<MethodCallExpr> findFromBinding() {
        return expressions.stream().flatMap((Expression e) -> {
            final Optional<MethodCallExpr> bind = e.findFirst(MethodCallExpr.class, expr -> {
                boolean isBindCall = expr.getName().asString().equals(BIND_CALL);
                List<Expression> bindingExprsVars = patternBindings.stream().map(context::getVarExpr).collect(toList());
                boolean hasBindingHasArgument = !Collections.disjoint(bindingExprsVars, expr.getArguments());
                return isBindCall && hasBindingHasArgument;
            });
            return bind
                    .flatMap( b -> b.getScope().map(Expression::asMethodCallExpr))
                    .map(Stream::of).orElse(Stream.empty());
        }).findFirst();
    }

}
