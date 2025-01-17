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
package org.kie.dmn.feel.codegen.feel11;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.util.PortablePath;
import org.kie.dmn.feel.util.ClassLoaderUtil;
import org.kie.memorycompiler.CompilationResult;
import org.kie.memorycompiler.JavaCompiler;
import org.kie.memorycompiler.resources.MemoryResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.drools.compiler.compiler.JavaDialectConfiguration.createNativeCompiler;
import static org.kie.dmn.feel.codegen.feel11.DMNCodegenConstants.CREATEBASENODE_S;

public class CompilerBytecodeLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CompilerBytecodeLoader.class);

    public interface GenerateClassListener {
        void generatedClass(CompilationUnit cu);
    }

    public static GenerateClassListener generateClassListener;

    public static class TemplateLoader extends ClassLoader {

        public TemplateLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> load(String name, byte[] b) {
            if (!ClassLoaderUtil.CAN_PLATFORM_CLASSLOAD) {
                throw new UnsupportedOperationException("Cannot jit classload on this platform.");
            }
            return defineClass(name, b, 0, b.length);
        }

        public Class<?> load(MemoryFileSystem pStore, String string) {
            if (!ClassLoaderUtil.CAN_PLATFORM_CLASSLOAD) {
                throw new UnsupportedOperationException("Cannot jit classload on this platform.");
            }
            Class<?> loadedClass = null;
            for (Entry<PortablePath, byte[]> kv : pStore.getMap().entrySet() ) {
                final String className = kv.getKey().asClassName();
                final Class<?> definedClass = defineClass(className, kv.getValue(), 0, kv.getValue().length);
                if (string.equals(className)) {
                    loadedClass = definedClass;
                }
            }
            return loadedClass;
        }

    }

    public  <T> T compileUnit(String cuPackage, String cuClass, CompilationUnit cu) {
        try {
            MemoryResourceReader pReader = new MemoryResourceReader();
            pReader.add(cuPackage.replaceAll("\\.", "/") + "/" + cuClass + ".java", cu.toString().getBytes());
            JavaCompiler compiler = createNativeCompiler();
            MemoryFileSystem pStore = new MemoryFileSystem();
            CompilationResult compilationResult = compiler.compile(new String[]{cuPackage.replaceAll("\\.", "/") + "/" + cuClass + ".java"},
                                                                   pReader,
                                                                   pStore,
                                                                   Thread.currentThread().getContextClassLoader());
            if (compilationResult.getErrors().length > 0) {
                LOG.error("{}", Arrays.asList(compilationResult.getErrors()));
            }
            if (compilationResult.getWarnings().length > 0) {
                LOG.warn("{}", Arrays.asList(compilationResult.getWarnings()));
            }

            String fqnClassName = cuPackage + "." + cuClass;
            Class<T> loaded =
                    (Class<T>) new TemplateLoader(Thread.currentThread().getContextClassLoader()).load(pStore,
                                                                                                       fqnClassName);
            return loaded.newInstance();
        } catch (Exception e) {
            LOG.error("Exception", e);
        }
        return null;
    }

    public <T> CompilationUnit getCompilationUnit(String templateResourcePath, String cuPackage,
                                                  String cuClass, String feelExpression,
                                                  BlockStmt directCodegenResult,
                                                  String lastVariableName) {
        CompilationUnit toReturn = getCompilationUnit(templateResourcePath, cuPackage, cuClass);
        populateFirstMethodJavadoc(toReturn, feelExpression);
        MethodDeclaration createBaseNodeMethodDeclaration = getCreateBaseNodeMethodDeclaration(toReturn);
        ReturnStmt returnStmt = getReturnStmt(createBaseNodeMethodDeclaration, directCodegenResult);
        Optional<Statement> lastStatement = directCodegenResult.getStatements().getLast();
        if (lastStatement.isPresent() && lastStatement.get() instanceof ThrowStmt) {
            directCodegenResult.remove(returnStmt);
            createBaseNodeMethodDeclaration.remove(returnStmt);
        } else {
            Expression returnExpression = getReturnExpression(lastVariableName, lastStatement,
                                                              directCodegenResult);
            returnStmt.setExpression(returnExpression);
            directCodegenResult.addStatement(returnStmt);
        }
        List<ClassOrInterfaceDeclaration> classDecls = toReturn.findAll(ClassOrInterfaceDeclaration.class);
        if (classDecls.size() != 1) {
            throw new RuntimeException("Something unexpected changed in the template.");
        }

        if (generateClassListener != null) {
            generateClassListener.generatedClass(toReturn);
        }

        LOG.debug("{}", toReturn);
        return toReturn;
    }

    private CompilationUnit getCompilationUnit(String templateResourcePath, String cuPackage, String cuClass) {
        CompilationUnit cu = parse(CompilerBytecodeLoader.class.getResourceAsStream(templateResourcePath));
        cu.setPackageDeclaration(cuPackage);
        final String className = templateResourcePath.substring(1, templateResourcePath.length() - 5);
        ClassOrInterfaceDeclaration classSource =
                cu.getClassByName(className).orElseThrow(() -> new IllegalArgumentException("Cannot find class by " +
                                                                                                    "name " + className));
        classSource.setName(cuClass);
        return cu;
    }

    private void populateFirstMethodJavadoc(CompilationUnit cu, String feelExpression) {
        MethodDeclaration applyMethodDEclaration = cu
                .findFirst(MethodDeclaration.class)
                .orElseThrow(() -> new RuntimeException("Something unexpected changed in the template."));
        applyMethodDEclaration.setComment(new JavadocComment("   FEEL: " + feelExpression + "   "));
    }

    private MethodDeclaration getCreateBaseNodeMethodDeclaration(CompilationUnit cu) {
        return cu
                .findFirst(MethodDeclaration.class,
                           methodDeclaration -> methodDeclaration.getName().asString().equals(CREATEBASENODE_S))
                .orElseThrow(() -> new RuntimeException("Something unexpected changed in the template."));
    }

    private ReturnStmt getReturnStmt(MethodDeclaration lookupMethod, BlockStmt directCodegenResult) {
        ReturnStmt toReturn =
                lookupMethod.findFirst(ReturnStmt.class)
                        .orElseThrow(() -> new RuntimeException("Something unexpected changed in the template."));
        lookupMethod.setBody(directCodegenResult);
        return toReturn;
    }

    private Expression getReturnExpression(String lastVariableName,
                                           Optional<Statement> lastStatement,
                                           BlockStmt directCodegenResult) {
        Expression toReturn;
        if (lastVariableName != null) {
            toReturn = new NameExpr(lastVariableName);
        } else {
            if (lastStatement.isPresent()) {
                Statement lastStmt = lastStatement.get();
                toReturn = lastStmt.asExpressionStmt().getExpression();
                directCodegenResult.remove(lastStmt);
            } else {
                toReturn = new NullLiteralExpr();
            }
        }
        return toReturn;
    }

    private boolean isUnaryTest(FieldDeclaration fd) {
        return fd.getVariable(0).getName().asString().startsWith("UT");
    }

    public String generateRandomPackage() {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return this.getClass().getPackage().getName() + ".gen" + uuid;
    }

    private static class SortFieldDeclarationStrategy implements Comparator<FieldDeclaration> {

        @Override
        public int compare(FieldDeclaration o1, FieldDeclaration o2) {
            String s1 = o1.getVariable(0).getNameAsString();
            String s2 = o2.getVariable(0).getNameAsString();
            // heuristic to sort longest field names at the bottom.
            // Should be substituted with proper dependency tracking
            return s1.length() < 5 && s2.length() < 5 ? s1.compareTo(s2) : s1.length() - s2.length() ;
        }

    }
}
