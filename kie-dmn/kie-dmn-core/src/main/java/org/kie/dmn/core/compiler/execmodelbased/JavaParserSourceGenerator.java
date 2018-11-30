package org.kie.dmn.core.compiler.execmodelbased;

import java.util.EnumSet;
import java.util.List;

import org.drools.javaparser.JavaParser;
import org.drools.javaparser.ast.ArrayCreationLevel;
import org.drools.javaparser.ast.CompilationUnit;
import org.drools.javaparser.ast.Modifier;
import org.drools.javaparser.ast.NodeList;
import org.drools.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.drools.javaparser.ast.body.ConstructorDeclaration;
import org.drools.javaparser.ast.body.FieldDeclaration;
import org.drools.javaparser.ast.body.VariableDeclarator;
import org.drools.javaparser.ast.expr.ArrayCreationExpr;
import org.drools.javaparser.ast.expr.ArrayInitializerExpr;
import org.drools.javaparser.ast.expr.Expression;
import org.drools.javaparser.ast.expr.ObjectCreationExpr;
import org.drools.javaparser.ast.type.ArrayType;
import org.drools.javaparser.ast.type.ClassOrInterfaceType;

public class JavaParserSourceGenerator {

    private ClassOrInterfaceDeclaration firstClass;
    private CompilationUnit compilationUnit;

    public static EnumSet<Modifier> PUBLIC_STATIC_FINAL = EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

    public JavaParserSourceGenerator(String className, String namespace, String packageName) {
        this.compilationUnit = JavaParser.parse("public class " + className + namespace + "{ }");
        this.compilationUnit.setPackageDeclaration(packageName);
        firstClass = this.compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow(() -> new RuntimeException("Cannot find Class"));
        String className1 = className;
    }

    public void addImports(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            compilationUnit.addImport(clazz);
        }
    }

    public void addInnerClassWithName(ClassOrInterfaceDeclaration feelExpressionSource, String name) {
        renameFeelExpressionClass(name, feelExpressionSource);
        firstClass.addMember(feelExpressionSource);
    }

    public void addMember(FieldDeclaration feelExpressionSource) {
        firstClass.addMember(feelExpressionSource);
    }

    public void addField(String testClass, Class<?> type, String instanceName) {
        ClassOrInterfaceType innerClassType = getType(testClass);
        ObjectCreationExpr newInstanceOfInnerClass = new ObjectCreationExpr(null, innerClassType, NodeList.nodeList());
        VariableDeclarator variableDeclarator = new VariableDeclarator(getType(type), instanceName, newInstanceOfInnerClass);
        firstClass.addMember(new FieldDeclaration(PUBLIC_STATIC_FINAL, variableDeclarator));
    }

    public void addTwoDimensionalArray(List<Expression> arrayInitializer, String arrayName, Class<?> type) {
        NodeList<ArrayCreationLevel> arrayCreationLevels = NodeList.nodeList(new ArrayCreationLevel(), new ArrayCreationLevel());
        ArrayInitializerExpr initializerMainArray = new ArrayInitializerExpr(NodeList.nodeList(arrayInitializer));
        ArrayCreationExpr arrayCreationExpr = new ArrayCreationExpr(getType(type), arrayCreationLevels, initializerMainArray);
        VariableDeclarator variable = new VariableDeclarator(new ArrayType(new ArrayType(getType(type))), arrayName, arrayCreationExpr);
        addMember(new FieldDeclaration(PUBLIC_STATIC_FINAL, variable));
    }


    private ClassOrInterfaceType getType(String canonicalName) {
        return JavaParser.parseClassOrInterfaceType(canonicalName);
    }

    private ClassOrInterfaceType getType(Class<?> clazz) {
        return JavaParser.parseClassOrInterfaceType(clazz.getCanonicalName());
    }

    private void renameFeelExpressionClass(String testClass, ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
        final String finalTestClass = testClass;
        classOrInterfaceDeclaration
                .setName(finalTestClass);

        classOrInterfaceDeclaration.findAll(ConstructorDeclaration.class)
                .forEach(n -> n.replace(new ConstructorDeclaration(finalTestClass)));
    }

    public String getSource() {
        return compilationUnit.toString();
    }
}
