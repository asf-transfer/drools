package org.drools.mvelcompiler;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import static org.drools.mvel.parser.printer.PrintUtil.printConstraint;

public class ParsingResult {

    private List<Statement> statements;
    private Set<String> modifyProperties = new HashSet<>();
    private Optional<Type> lastExpressionType;

    public ParsingResult(List<Statement> statements) {
        this.statements = statements;
    }

    public String resultAsString() {
        return printConstraint(statementResults());
    }

    public BlockStmt statementResults() {
        return new BlockStmt(NodeList.nodeList(statements));
    }

    public ParsingResult setModifyProperties(Set<String> modifyProperties) {
        this.modifyProperties = modifyProperties;
        return this;
    }

    public Set<String> getModifyProperties() {
        return modifyProperties;
    }

    public ParsingResult setLastExpressionType(Optional<Type> lastExpressionType) {
        this.lastExpressionType = lastExpressionType;
        return this;
    }

    public Optional<Type> lastExpressionType() {
        return lastExpressionType;
    }

    @Override
    public String toString() {
        return "ParsingResult{" +
                "statements='" + resultAsString() + '\'' +
                '}';
    }
}
