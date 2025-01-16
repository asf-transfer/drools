/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.drl.parser.antlr4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.drools.drl.ast.descr.AccumulateDescr;
import org.drools.drl.ast.descr.AccumulateImportDescr;
import org.drools.drl.ast.descr.AndDescr;
import org.drools.drl.ast.descr.AnnotationDescr;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.BehaviorDescr;
import org.drools.drl.ast.descr.CollectDescr;
import org.drools.drl.ast.descr.ConditionalBranchDescr;
import org.drools.drl.ast.descr.EntryPointDeclarationDescr;
import org.drools.drl.ast.descr.EntryPointDescr;
import org.drools.drl.ast.descr.EnumDeclarationDescr;
import org.drools.drl.ast.descr.EnumLiteralDescr;
import org.drools.drl.ast.descr.EvalDescr;
import org.drools.drl.ast.descr.ExistsDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.ForallDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.FunctionDescr;
import org.drools.drl.ast.descr.GlobalDescr;
import org.drools.drl.ast.descr.GroupByDescr;
import org.drools.drl.ast.descr.ImportDescr;
import org.drools.drl.ast.descr.MVELExprDescr;
import org.drools.drl.ast.descr.NamedConsequenceDescr;
import org.drools.drl.ast.descr.NotDescr;
import org.drools.drl.ast.descr.OrDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.QualifiedName;
import org.drools.drl.ast.descr.QueryDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.ast.descr.TypeDeclarationDescr;
import org.drools.drl.ast.descr.TypeFieldDescr;
import org.drools.drl.ast.descr.WindowDeclarationDescr;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.parser.DroolsError;
import org.drools.drl.parser.DroolsParserException;
import org.drools.drl.parser.impl.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * This test class is ported from org.drools.mvel.compiler.lang.RuleParserTest
 */
class MiscDRLParserTest {

    private DrlParser parser;

    @BeforeEach
    void setUp() {
        parser = ParserTestUtils.getParser();
    }

    private static boolean isNewParser() {
        return DrlParser.ANTLR4_PARSER_ENABLED;
    }

    private static boolean isOldParser() {
        return !DrlParser.ANTLR4_PARSER_ENABLED;
    }

    private String readResource(final String filename) {
        Path path;
        try {
            path = Paths.get(getClass().getResource(filename).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (String line; (line = br.readLine()) != null; ) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    private PackageDescr parseAndGetPackageDescr(String drl) {
        try {
            PackageDescr pkg = parser.parse(null, drl);
            assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();
            return pkg;
        } catch (DroolsParserException e) {
            throw new RuntimeException(e);
        }
    }

    private PackageDescr parseAndGetPackageDescrWithoutErrorCheck(String drl) {
        try {
            return parser.parse(null, drl);
        } catch (DroolsParserException e) {
            throw new RuntimeException(e);
        }
    }

    private PackageDescr parseAndGetPackageDescrWithDsl(String dslrFileName, String dslFileName) {
        try (InputStreamReader dslr = new InputStreamReader(getClass().getResourceAsStream(dslrFileName));
             InputStreamReader dsl = new InputStreamReader(getClass().getResourceAsStream(dslFileName))) {
            PackageDescr pkg = parser.parse(dslr, dsl);
            assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();
            return pkg;
        } catch (DroolsParserException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuleDescr parseAndGetFirstRuleDescr(String drl) {
        PackageDescr pkg = parseAndGetPackageDescr(drl);
        assertThat(pkg.getRules()).isNotEmpty();
        return pkg.getRules().get(0);
    }

    private ExprConstraintDescr parseAndGetFirstConstraintDescr(String drl) {
        RuleDescr rule = parseAndGetFirstRuleDescr(drl);
        assertThat(rule.getLhs().getDescrs().get(0)).isInstanceOf(PatternDescr.class);
        PatternDescr patternDescr = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOf(ExprConstraintDescr.class);
        return (ExprConstraintDescr) patternDescr.getConstraint().getDescrs().get(0);
    }

    private PackageDescr parseAndGetPackageDescrFromFile(String filename) {
        return parseAndGetPackageDescr(readResource(filename));
    }

    private RuleDescr parseAndGetFirstRuleDescrFromFile(String filename) {
        return parseAndGetFirstRuleDescr(readResource(filename));
    }

    private QueryDescr parseAndGetFirstQueryDescr(String drl) {
        PackageDescr pkg = parseAndGetPackageDescr(drl);
        assertThat(pkg.getRules()).isNotEmpty();
        Optional<QueryDescr> optQuery = pkg.getRules().stream().filter(QueryDescr.class::isInstance).map(QueryDescr.class::cast).findFirst();
        assertThat(optQuery).isPresent();
        return optQuery.get();
    }

    private QueryDescr parseAndGetFirstQueryDescrFromFile(String filename) {
        return parseAndGetFirstQueryDescr(readResource(filename));
    }

    @Test
    void emptySource() {
        final String source = "";
        final PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEmpty();
    }

    @Test
    void validPackage() {
        final String source = "package foo.bar.baz";
        final PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEqualTo("foo.bar.baz");
    }

    @Test
    void packageWithErrorNode() {
        final String source = "package 12 foo.bar.baz";
        final PackageDescr pkg = parseAndGetPackageDescrWithoutErrorCheck(source);
        assertThat(parser.hasErrors()).isTrue();
        assertThat(pkg).isNull();
    }

    @Test
    void packageWithAllErrorNode() {
        final String source = "package 12 12312 231";
        final PackageDescr pkg = parseAndGetPackageDescrWithoutErrorCheck(source);
        assertThat(parser.hasErrors()).isTrue();
        assertThat(pkg).isNull();
    }

    @Test
    void importDef() {
        final String source = "package foo; import com.foo.Bar; import com.foo.Baz;";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEqualTo("foo");
        assertThat(pkg.getImports()).hasSize(2);
        ImportDescr impdescr = pkg.getImports().get(0);
        assertThat(impdescr.getTarget()).isEqualTo("com.foo.Bar");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()) + ("import " + impdescr.getTarget()).length());

        impdescr = pkg.getImports().get(1);
        assertThat(impdescr.getTarget()).isEqualTo("com.foo.Baz");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()) + ("import " + impdescr.getTarget()).length());
    }

    @Test
    void functionImport() {
        final String source = "package foo\n" +
                "import function java.lang.Math.max\n" +
                "import function java.lang.Math.min;\n" +
                "import foo.bar.*\n" +
                "import baz.Baz";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEqualTo("foo");
        assertThat(pkg.getImports()).hasSize(2);
        ImportDescr impdescr = pkg.getImports().get(0);
        assertThat(impdescr.getTarget()).isEqualTo("foo.bar.*");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()));

        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()) + ("import " + impdescr.getTarget()).length());

        impdescr = pkg.getImports().get(1);
        assertThat(impdescr.getTarget()).isEqualTo("baz.Baz");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()) + ("import " + impdescr.getTarget()).length());

        assertThat(pkg.getFunctionImports()).hasSize(2);
        impdescr = pkg.getFunctionImports().get(0);
        assertThat(impdescr.getTarget()).isEqualTo("java.lang.Math.max");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import function " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import function " + impdescr.getTarget()) + ("import function " + impdescr.getTarget()).length());

        impdescr = pkg.getFunctionImports().get(1);
        assertThat(impdescr.getTarget()).isEqualTo("java.lang.Math.min");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import function " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import function " + impdescr.getTarget()) + ("import function " + impdescr.getTarget()).length());
    }

    @Test
    void globalWithComplexType() {
        final String source = "package foo.bar.baz\n" +
                "import com.foo.Bar\n" +
                "global java.util.List<java.util.Map<String,Integer>> aList;\n" +
                "global Integer aNumber";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEqualTo("foo.bar.baz");
        assertThat(pkg.getImports()).hasSize(1);

        ImportDescr impdescr = pkg.getImports().get(0);
        assertThat(impdescr.getTarget()).isEqualTo("com.foo.Bar");
        assertThat(impdescr.getStartCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()));
        assertThat(impdescr.getEndCharacter()).isEqualTo(source.indexOf("import " + impdescr.getTarget()) + ("import " + impdescr.getTarget()).length());

        assertThat(pkg.getGlobals()).hasSize(2);

        GlobalDescr global = pkg.getGlobals().get(0);
        assertThat(global.getType()).isEqualTo("java.util.List<java.util.Map<String,Integer>>");
        assertThat(global.getIdentifier()).isEqualTo("aList");
        assertThat(global.getStartCharacter()).isEqualTo(source.indexOf("global " + global.getType()));
        assertThat(global.getEndCharacter()).isEqualTo(source.indexOf("global " + global.getType() + " " + global.getIdentifier()) +
                                                               ("global " + global.getType() + " " + global.getIdentifier()).length());

        global = pkg.getGlobals().get(1);
        assertThat(global.getType()).isEqualTo("Integer");
        assertThat(global.getIdentifier()).isEqualTo("aNumber");
        assertThat(global.getStartCharacter()).isEqualTo(source.indexOf("global " + global.getType()));
        assertThat(global.getEndCharacter()).isEqualTo(source.indexOf("global " + global.getType() + " " + global.getIdentifier()) +
                                                               ("global " + global.getType() + " " + global.getIdentifier()).length());
    }

    @Test
    void globalWithOrWithoutSemi() {
        String source = readResource("globals.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        assertThat(pkg.getImports()).hasSize(1);
        assertThat(pkg.getGlobals()).hasSize(2);

        final GlobalDescr foo = pkg.getGlobals().get(0);
        assertThat(foo.getType()).isEqualTo("java.lang.String");
        assertThat(foo.getIdentifier()).isEqualTo("foo");
        final GlobalDescr bar = pkg.getGlobals().get(1);
        assertThat(bar.getType()).isEqualTo("java.lang.Integer");
        assertThat(bar.getIdentifier()).isEqualTo("bar");
    }

    @Test
    void functionImportWithNotExist() {
        String source = readResource("test_FunctionImport.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getFunctionImports()).hasSize(2);

        assertThat(pkg.getFunctionImports().get(0).getTarget()).isEqualTo("abd.def.x");
        assertThat(pkg.getFunctionImports().get(0).getStartCharacter()).isNotSameAs(-1);
        assertThat(pkg.getFunctionImports().get(0).getEndCharacter()).isNotSameAs(-1);
        assertThat(pkg.getFunctionImports().get(1).getTarget()).isEqualTo("qed.wah.*");
        assertThat(pkg.getFunctionImports().get(1).getStartCharacter()).isNotSameAs(-1);
        assertThat(pkg.getFunctionImports().get(1).getEndCharacter()).isNotSameAs(-1);
    }

    @Test
    void fromComplexAccessor() {
        String source = "rule \"Invalid customer id\" ruleflow-group \"validate\" lock-on-active true \n" +
                " when \n" +
                "     o: Order( ) \n" +
                "     not( Customer( ) from customerService.getCustomer(o.getCustomerId()) ) \n" +
                " then \n" +
                "     System.err.println(\"Invalid customer id found!\"); " +
                "\n" +
                "     o.addError(\"Invalid customer id\"); \n" +
                "end \n";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("Invalid customer id");

        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        NotDescr not = (NotDescr) rule.getLhs().getDescrs().get(1);
        PatternDescr customer = (PatternDescr) not.getDescrs().get(0);

        assertThat(customer.getObjectType()).isEqualTo("Customer");
        assertThat(((FromDescr) customer.getSource()).getDataSource().getText()).isEqualTo("customerService.getCustomer(o.getCustomerId())");
    }

    @Test
    void fromWithInlineList() {
        String source = "rule XYZ \n" +
                " when \n" +
                " o: Order( ) \n" +
                " not( Number( ) from [1, 2, 3] ) \n" +
                " then \n" +
                " System.err.println(\"Invalid customer id found!\"); \n" +
                " o.addError(\"Invalid customer id\"); \n" +
                "end \n";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("XYZ");

        PatternDescr number = (PatternDescr) ((NotDescr) rule.getLhs().getDescrs().get(1)).getDescrs().get(0);
        assertThat(((FromDescr) number.getSource()).getDataSource().toString()).isEqualToIgnoringWhitespace("[1, 2, 3]");
    }

    @Test
    void fromWithInlineListMethod() {
        String source = "rule XYZ \n" +
                " when \n" +
                " o: Order( ) \n" +
                " Number( ) from [1, 2, 3].sublist(1, 2) \n" +
                " then \n" +
                " System.err.println(\"Invalid customer id found!\"); \n" +
                " o.addError(\"Invalid customer id\"); \n" +
                "end \n";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("XYZ");

        PatternDescr number = (PatternDescr) rule.getLhs().getDescrs().get(1);

        assertThat(((FromDescr) number.getSource()).getDataSource().toString()).isEqualToIgnoringWhitespace("[1, 2, 3].sublist(1, 2)");
    }

    @Test
    void fromWithInlineListIndex() {
        String source = "rule XYZ \n" +
                " when \n" +
                " o: Order( ) \n" +
                " Number( ) from [1, 2, 3][1] \n" +
                " then \n" +
                " System.err.println(\"Invalid customer id found!\"); \n" +
                " o.addError(\"Invalid customer id\"); \n" +
                "end \n";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("XYZ");

        PatternDescr number = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(((FromDescr) number.getSource()).getDataSource().toString()).isEqualToIgnoringWhitespace("[1, 2, 3][1]");
    }

    @Test
    void ruleWithoutEnd() {
        String source = "rule \"Invalid customer id\" \n" +
                " when \n" +
                " o: Order( ) \n" +
                " then \n" +
                " System.err.println(\"Invalid customer id found!\"); \n";
        parseAndGetPackageDescrWithoutErrorCheck(source);
        assertThat(parser.hasErrors()).isTrue();
    }

    @Test
    void orWithSpecialBind() {
        String source = "rule \"A and (B or C or D)\" \n" +
                "    when \n" +
                "        pdo1 : ParametricDataObject( paramID == 101, stringValue == \"1000\" ) and \n" +
                "        pdo2 :(ParametricDataObject( paramID == 101, stringValue == \"1001\" ) or \n" +
                "               ParametricDataObject( paramID == 101, stringValue == \"1002\" ) or \n" +
                "               ParametricDataObject( paramID == 101, stringValue == \"1003\" )) \n" +
                "    then \n" +
                "        System.out.println( \"Rule: A and (B or C or D) Fired. pdo1: \" + pdo1 +  \" pdo2: \"+ pdo2); \n" +
                "end\n";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        AndDescr lhs = rule.getLhs();
        assertThat(lhs.getDescrs()).hasSize(2);

        PatternDescr pdo1 = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(pdo1.getIdentifier()).isEqualTo("pdo1");

        OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(1);
        assertThat(or.getDescrs()).hasSize(3);
        for (BaseDescr pdo2 : or.getDescrs()) {
            assertThat(((PatternDescr) pdo2).getIdentifier()).isEqualTo("pdo2");
        }
    }

    @Test
    void compatibleRestriction() {
        String source = "package com.sample  rule test  when  Test( ( text == null || text2 matches \"\" ) )  then  end";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getName()).isEqualTo("com.sample");
        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("test");
        ExprConstraintDescr expr = (ExprConstraintDescr) ((PatternDescr) rule.getLhs().getDescrs().get(0)).getDescrs().get(0);
        assertThat(expr.getText()).isEqualTo("( text == null || text2 matches \"\" )");
    }

    @Test
    void simpleConstraint() {
        String source = "package com.sample  rule test  when  Cheese( type == 'stilton', price > 10 )  then  end";
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getName()).isEqualTo("com.sample");
        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("test");

        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);

        AndDescr constraint = (AndDescr) pattern.getConstraint();
        assertThat(constraint.getDescrs()).hasSize(2);
        assertThat(constraint.getDescrs().get(0)).hasToString("type == \"stilton\"");
        assertThat(constraint.getDescrs().get(1)).hasToString("price > 10");
    }

    @Test
    void stringEscapes() {
        String source = "package com.sample  rule test  when  Cheese( type matches \"\\..*\\\\.\" )  then  end";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        assertThat(pkg.getName()).isEqualTo("com.sample");
        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("test");

        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);

        AndDescr constraint = (AndDescr) pattern.getConstraint();
        assertThat(constraint.getDescrs()).hasSize(1);
        assertThat(constraint.getDescrs().get(0)).hasToString("type matches \"\\..*\\\\.\"");
    }

    @Test
    void dialectWithSingleQuotation() {
        final String source = "dialect 'mvel'";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        AttributeDescr attr = pkg.getAttributes().get(0);
        assertThat(attr.getName()).isEqualTo("dialect");
        assertThat(attr.getValue()).isEqualTo("mvel");
    }

    @Test
    void dialectWithDoubleQuotation() {
        final String source = "dialect \"mvel\"";
        PackageDescr pkg = parseAndGetPackageDescr(source);
        AttributeDescr attr = pkg.getAttributes().get(0);
        assertThat(attr.getName()).isEqualTo("dialect");
        assertThat(attr.getValue()).isEqualTo("mvel");
    }

    @Test
    void emptyRuleWithoutWhen() {
        String source = readResource("empty_rule.drl"); // without WHEN
        RuleDescr ruleDescr = parseAndGetFirstRuleDescr(source);

        assertThat(ruleDescr).isNotNull();

        assertThat(ruleDescr.getName()).isEqualTo("empty");
        assertThat(ruleDescr.getLhs()).isNotNull();
        assertThat(ruleDescr.getConsequence()).isNotNull();
    }

    @Test
    void keywordCollisions() {
        String source = readResource("eol_funny_business.drl"); // keywords everywhere

        // Note: eol_funny_business.drl is modified from the one under drools-test-coverage to be more realistic.
        // e.g. "package" is not allowed in a package value in Java, so it doesn't make sense to test. (Right to raise a parser error)

        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getRules()).hasSize(1);
    }

    @Test
    void ternaryExpression() {
        String source = readResource("ternary_expression.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(pkg.getRules()).hasSize(1);

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("if (speed > speedLimit ? true : false;) pullEmOver();");
    }

    @Test
    void functionWithArrays() {
        String source = readResource("function_arrays.drl");

        // Note: function_arrays.drl is modified from the one under drools-test-coverage to be more realistic.
        // new String[3] {"a","b","c"} is invalid in Java (Cannot define dimension expressions when an array initializer is provided)
        // , so it doesn't make sense to test. (Right to raise a parser error)

        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getName()).isEqualTo("foo");
        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule = pkg.getRules().get(0);

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("yourFunction(new String[] {\"a\",\"b\",\"c\"});");

        final FunctionDescr func = pkg.getFunctions().get(0);

        assertThat(func.getReturnType()).isEqualTo("String[]");
        assertThat(func.getParameterNames().get(0)).isEqualTo("args[]");
        assertThat(func.getParameterTypes().get(0)).isEqualTo("String");
    }

    @Test
    void almostEmptyRule() {
        String source = readResource("almost_empty_rule.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg).isNotNull();

        RuleDescr rule = pkg.getRules().get(0);

        assertThat(rule.getName()).isEqualTo("almost_empty");
        assertThat(rule.getLhs()).isNotNull();
        assertThat(((String) rule.getConsequence())).isBlank();
    }

    @Test
    void quotedStringNameRule() {
        String source = readResource("quoted_string_name_rule.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("quoted string name");
        assertThat(rule.getLhs()).isNotNull();
        assertThat(((String) rule.getConsequence())).isBlank();
    }

    @Test
    void noLoop() {
        String source = readResource("no-loop.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("rule1");
        final AttributeDescr att = rule.getAttributes().get("no-loop");
        assertThat(att.getValue()).isEqualTo("false");
        assertThat(att.getName()).isEqualTo("no-loop");
    }

    @Test
    void autofocus() {
        String source = readResource("autofocus.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("rule1");
        final AttributeDescr att = rule.getAttributes().get("auto-focus");
        assertThat(att.getValue()).isEqualTo("true");
        assertThat(att.getName()).isEqualTo("auto-focus");
    }

    @Test
    void ruleFlowGroup() {
        String source = readResource("ruleflowgroup.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("rule1");
        final AttributeDescr att = rule.getAttributes().get("ruleflow-group");
        assertThat(att.getValue()).isEqualTo("a group");
        assertThat(att.getName()).isEqualTo("ruleflow-group");
    }

    @Test
    void consequenceWithDeclaration() {
        String source = readResource("declaration-in-consequence.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        // Note : Removed "i\i;" from the original declaration-in-consequence.drl under drools-test-coverage
        // because it's not a valid java expression and doesn't make sense to test. (Right to raise a parser error)

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("myrule");

        final String expected = "int i = 0; i = 1; i / 1; i == 1; i(i); i = 'i'; i.i.i; i<i; i>i; i=\"i\";  ++i;" +
                "i++; --i; i--; i += i; i -= i; i *= i; i /= i;" +
                "int i = 5;" + "for(int j; j<i; ++j) {" +
                "System.out.println(j);}" +
                "Object o = new String(\"Hello\");" +
                "String s = (String) o;";

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace(expected);
        assertThat(((String) rule.getConsequence()).indexOf("++") > 0).isTrue();
        assertThat(((String) rule.getConsequence()).indexOf("--") > 0).isTrue();
        assertThat(((String) rule.getConsequence()).indexOf("+=") > 0).isTrue();
        assertThat(((String) rule.getConsequence()).indexOf("==") > 0).isTrue();
        assertThat(((String) rule.getConsequence()).indexOf("i++") > 0).isTrue();
        // note, need to assert that "i++" is preserved as is, no extra spaces.
    }

    @Test
    void or() {
        final String text = "rule X when Person(age < 42, location==\"atlanta\") \nor\nPerson(name==\"bob\") then end";
        PackageDescr pkg = parseAndGetPackageDescr(text);
        RuleDescr rule = pkg.getRules().get(0);

        assertThat(rule).isNotNull();

        AndDescr lhs = rule.getLhs();
        assertThat(lhs.getDescrs()).hasSize(1);
        assertThat(((OrDescr) lhs.getDescrs().get(0)).getDescrs()).hasSize(2);
    }

    @Test
    void lhsWithStringQuotes() {
        final String text = "rule X when Person( location==\"atlanta\\\"\") then end\n";
        PackageDescr pkg = parseAndGetPackageDescr(text);
        RuleDescr rule = pkg.getRules().get(0);

        assertThat(rule).isNotNull();

        AndDescr lhs = rule.getLhs();
        ExprConstraintDescr constr = (ExprConstraintDescr) ((PatternDescr) lhs.getDescrs().get(0)).getDescrs().get(0);

        assertThat(constr.getText()).isEqualToIgnoringWhitespace("location==\"atlanta\\\"\"");
    }

    @Test
    void lhsWithStringQuotesEscapeChars() {
        final String text = "rule X when Cheese( $x: type, type == \"s\\tti\\\"lto\\nn\" ) then end\n";
        PackageDescr pkg = parseAndGetPackageDescr(text);
        RuleDescr rule = pkg.getRules().get(0);

        assertThat(rule).isNotNull();

        AndDescr lhs = rule.getLhs();
        ExprConstraintDescr constr = (ExprConstraintDescr) ((PatternDescr) lhs.getDescrs().get(0)).getDescrs().get(1);

        assertThat(constr.getText()).isEqualToIgnoringWhitespace("type == \"s\\tti\\\"lto\\nn\"");
    }

    @Test
    void literalBoolAndNegativeNumbersRule() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("literal_bool_and_negative.drl");

        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat(rule.getLhs()).isNotNull();

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("cons();");

        final AndDescr lhs = rule.getLhs();
        assertThat(lhs.getDescrs()).hasSize(3);

        PatternDescr pattern = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);
        AndDescr fieldAnd = (AndDescr) pattern.getConstraint();
        ExprConstraintDescr fld = (ExprConstraintDescr) fieldAnd.getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("bar == false");

        pattern = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        fieldAnd = (AndDescr) pattern.getConstraint();
        fld = (ExprConstraintDescr) fieldAnd.getDescrs().get(0);

        assertThat(fld.getText()).isEqualToIgnoringWhitespace("boo > -42");

        pattern = (PatternDescr) lhs.getDescrs().get(2);
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        fieldAnd = (AndDescr) pattern.getConstraint();
        fld = (ExprConstraintDescr) fieldAnd.getDescrs().get(0);

        assertThat(fld.getText()).isEqualToIgnoringWhitespace("boo > -42.42");
    }

    @Test
    void emptyPattern() {
        String source = readResource("test_EmptyPattern.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr ruleDescr = pkg.getRules().get(0);
        assertThat(ruleDescr.getName()).isEqualTo("simple rule");
        assertThat(ruleDescr.getLhs()).isNotNull();
        assertThat(ruleDescr.getLhs().getDescrs()).hasSize(1);
        final PatternDescr patternDescr = (PatternDescr) ruleDescr.getLhs().getDescrs().get(0);
        assertThat(patternDescr.getConstraint().getDescrs()).isEmpty(); // this
        assertThat(patternDescr.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void simpleMethodCallWithFrom() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("test_SimpleMethodCallWithFrom.drl");
        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final FromDescr from = (FromDescr) pattern.getSource();
        final MVELExprDescr method = (MVELExprDescr) from.getDataSource();

        assertThat(method.getExpression()).isEqualToIgnoringWhitespace("something.doIt( foo,bar,42,\"hello\",[ a : \"b\", \"something\" : 42, \"a\" : foo, x : [x:y]],\"end\", [a, \"b\", 42] )");
    }

    @Test
    void simpleFunctionCallWithFrom() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("test_SimpleFunctionCallWithFrom.drl");
        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final FromDescr from = (FromDescr) pattern.getSource();
        final MVELExprDescr func = (MVELExprDescr) from.getDataSource();

        assertThat(func.getExpression()).isEqualToIgnoringWhitespace("doIt( foo,bar,42,\"hello\",[ a : \"b\", \"something\" : 42, \"a\" : foo, x : [x:y]],\"end\", [a, \"b\", 42] )");
    }

    @Test
    void simpleAccessorWithFrom() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("test_SimpleAccessorWithFrom.drl");
        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final FromDescr from = (FromDescr) pattern.getSource();
        final MVELExprDescr accessor = (MVELExprDescr) from.getDataSource();

        assertThat(accessor.getExpression()).isEqualTo("something.doIt");
    }

    @Test
    void simpleAccessorAndArgWithFrom() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("test_SimpleAccessorArgWithFrom.drl");
        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final FromDescr from = (FromDescr) pattern.getSource();
        final MVELExprDescr accessor = (MVELExprDescr) from.getDataSource();

        assertThat(accessor.getExpression()).isEqualTo("something.doIt[\"key\"]");
    }

    @Test
    void complexChainedAccessor() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("test_ComplexChainedCallWithFrom.drl");

        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final FromDescr from = (FromDescr) pattern.getSource();
        final MVELExprDescr accessor = (MVELExprDescr) from.getDataSource();

        assertThat(accessor.getExpression()).isEqualToIgnoringWhitespace("doIt1( foo,bar,42,\"hello\",[ a : \"b\"], [a, \"b\", 42] ).doIt2(bar, [a, \"b\", 42]).field[\"key\"]");
    }

    @Test
    void from() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("from.drl");
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("using_from");

        assertThat(rule.getLhs().getDescrs()).hasSize(9);
    }

    @Test
    void simpleRuleWithBindings() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("simple_rule.drl");
        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("simple_rule");

        assertThat(rule.getStartCharacter()).isEqualTo(803);
        assertThat(rule.getEndCharacter()).isEqualTo(996);

        assertThat(rule.getConsequenceLine()).isEqualTo(22);
        assertThat(rule.getConsequencePattern()).isEqualTo(2);

        final AndDescr lhs = rule.getLhs();

        assertThat(lhs).isNotNull();

        assertThat(lhs.getDescrs()).hasSize(3);

        // Check first pattern
        final PatternDescr first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getIdentifier()).isEqualTo("foo3");
        assertThat(first.getObjectType()).isEqualTo("Bar");

        assertThat(first.getConstraint().getDescrs()).hasSize(1);

        AndDescr fieldAnd = (AndDescr) first.getConstraint();
        ExprConstraintDescr constraint = (ExprConstraintDescr) fieldAnd.getDescrs().get(0);
        assertThat(constraint).isNotNull();

        assertThat(constraint.getExpression()).isEqualToIgnoringWhitespace("a==3");

        // Check second pattern
        final PatternDescr second = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(second.getIdentifier()).isEqualTo("foo4");
        assertThat(second.getObjectType()).isEqualTo("Bar");

        // no constraints, only a binding
        fieldAnd = (AndDescr) second.getConstraint();
        assertThat(fieldAnd.getDescrs()).hasSize(1);

        final ExprConstraintDescr binding = (ExprConstraintDescr) second.getConstraint().getDescrs().get(0);
        assertThat(binding.getExpression()).isEqualToIgnoringWhitespace("a4:a==4");

        // Check third pattern
        final PatternDescr third = (PatternDescr) lhs.getDescrs().get(2);
        assertThat(third.getIdentifier()).isNull();
        assertThat(third.getObjectType()).isEqualTo("Baz");

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("if ( a == b ) { " + "  assert( foo3 );" + "} else {" + "  retract( foo4 );" + "}" + "  System.out.println( a4 );");
    }

    @Test
    void multipleRestrictionsConstraint() {
        RuleDescr rule = parseAndGetFirstRuleDescrFromFile("restrictions_test.drl");
        assertThat(rule).isNotNull();

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("consequence();");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        // The first pattern, with 2 restrictions on a single field (plus a
        // connective)
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        AndDescr and = (AndDescr) pattern.getConstraint();
        ExprConstraintDescr fld = (ExprConstraintDescr) and.getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualTo("age > 30 && < 40");

        // the second col, with 2 fields, the first with 2 restrictions, the
        // second field with one
        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Vehicle");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(2);

        and = (AndDescr) pattern.getConstraint();
        fld = (ExprConstraintDescr) and.getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("type == \"sedan\" || == \"wagon\"");

        // now the second field
        fld = (ExprConstraintDescr) and.getDescrs().get(1);
        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("age < 3");
    }

    @Test
    void lineNumberInAST() {
        // also see testSimpleExpander to see how this works with an expander
        // (should be the same).

        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "simple_rule.drl");

        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("simple_rule");

        assertThat(rule.getConsequenceLine()).isEqualTo(22);
        assertThat(rule.getConsequencePattern()).isEqualTo(2);

        final AndDescr lhs = rule.getLhs();

        assertThat(lhs).isNotNull();

        assertThat(lhs.getDescrs()).hasSize(3);

        // Check first pattern
        final PatternDescr first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getIdentifier()).isEqualTo("foo3");
        assertThat(first.getObjectType()).isEqualTo("Bar");
        assertThat(first.getConstraint().getDescrs()).hasSize(1);

        // Check second pattern
        final PatternDescr second = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(second.getIdentifier()).isEqualTo("foo4");
        assertThat(second.getObjectType()).isEqualTo("Bar");

        final PatternDescr third = (PatternDescr) lhs.getDescrs().get(2);
        assertThat(third.getObjectType()).isEqualTo("Baz");

        assertThat(first.getLine()).isEqualTo(19);
        assertThat(second.getLine()).isEqualTo(20);
        assertThat(third.getLine()).isEqualTo(21);
    }

    @Test
    void lineNumberIncludingCommentsInRHS() {
        PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "test_CommentLineNumbersInConsequence.drl");

        final String rhs = (String) ((RuleDescr) pkg.getRules().get(0)).getConsequence();
        String expected = "\\s*//woot$\\s*first;$\\s*$\\s*//$\\s*$\\s*/\\* lala$\\s*$\\s*\\*/$\\s*second;$\\s*";
        assertThat(Pattern.compile(expected,
                                   Pattern.DOTALL | Pattern.MULTILINE).matcher(rhs).matches()).isTrue();
    }

    @Test
    void lhsSemicolonDelim() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "lhs_semicolon_delim.drl");

        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("simple_rule");

        final AndDescr lhs = rule.getLhs();

        assertThat(lhs).isNotNull();

        assertThat(lhs.getDescrs()).hasSize(3);

        // System.err.println( lhs.getDescrs() );

        // Check first pattern
        final PatternDescr first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getIdentifier()).isEqualTo("foo3");
        assertThat(first.getObjectType()).isEqualTo("Bar");

        assertThat(first.getConstraint().getDescrs()).hasSize(1);

        // LiteralDescr constraint = (LiteralDescr) first.getDescrs().get( 0 );
        AndDescr and = (AndDescr) first.getConstraint();
        ExprConstraintDescr fld = (ExprConstraintDescr) and.getDescrs().get(0);
        assertThat(fld).isNotNull();

        assertThat(fld.getExpression()).isEqualTo("a==3");

        // Check second pattern
        final PatternDescr second = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(second.getIdentifier()).isEqualTo("foo4");
        assertThat(second.getObjectType()).isEqualTo("Bar");

        assertThat(second.getDescrs()).hasSize(1);

        final ExprConstraintDescr fieldBindingDescr = (ExprConstraintDescr) second.getDescrs().get(0);
        assertThat(fieldBindingDescr.getExpression()).isEqualTo("a4:a==4");

        // Check third pattern
        final PatternDescr third = (PatternDescr) lhs.getDescrs().get(2);
        assertThat(third.getIdentifier()).isNull();
        assertThat(third.getObjectType()).isEqualTo("Baz");

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("if ( a == b ) { " + "  assert( foo3 );" + "} else {" + "  retract( foo4 );" + "}" + "  System.out.println( a4 );");
    }

    @Test
    void notNode() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_not.drl");

        assertThat(rule).isNotNull();
        assertThat(rule.getName()).isEqualTo("simple_rule");

        final AndDescr lhs = rule.getLhs();
        assertThat(lhs.getDescrs()).hasSize(1);
        final NotDescr not = (NotDescr) lhs.getDescrs().get(0);
        assertThat(not.getDescrs()).hasSize(1);
        final PatternDescr pattern = (PatternDescr) not.getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        final AndDescr and = (AndDescr) pattern.getConstraint();
        final ExprConstraintDescr fld = (ExprConstraintDescr) and.getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualTo("type == \"stilton\"");
    }

    @Test
    void notExistWithBrackets() {

        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "not_exist_with_brackets.drl");

        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        assertThat(rule).isNotNull();
        assertThat(rule.getName()).isEqualTo("simple_rule");

        final AndDescr lhs = rule.getLhs();
        assertThat(lhs.getDescrs()).hasSize(2);
        final NotDescr not = (NotDescr) lhs.getDescrs().get(0);
        assertThat(not.getDescrs()).hasSize(1);
        final PatternDescr pattern = (PatternDescr) not.getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("Cheese");

        final ExistsDescr ex = (ExistsDescr) lhs.getDescrs().get(1);
        assertThat(ex.getDescrs()).hasSize(1);
        final PatternDescr exPattern = (PatternDescr) ex.getDescrs().get(0);
        assertThat(exPattern.getObjectType()).isEqualTo("Foo");
    }

    @Test
    void simpleQuery() {
        final QueryDescr query = parseAndGetFirstQueryDescrFromFile(
                "simple_query.drl");

        assertThat(query).isNotNull();

        assertThat(query.getName()).isEqualTo("simple_query");

        final AndDescr lhs = query.getLhs();

        assertThat(lhs).isNotNull();

        assertThat(lhs.getDescrs()).hasSize(3);

        // Check first pattern
        final PatternDescr first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getIdentifier()).isEqualTo("foo3");
        assertThat(first.getObjectType()).isEqualTo("Bar");

        assertThat(first.getConstraint().getDescrs()).hasSize(1);

        AndDescr and = (AndDescr) first.getConstraint();
        ExprConstraintDescr fld = (ExprConstraintDescr) and.getDescrs().get(0);
        assertThat(fld).isNotNull();

        assertThat(fld.getExpression()).isEqualTo("a==3");

        // Check second pattern
        final PatternDescr second = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(second.getIdentifier()).isEqualTo("foo4");
        assertThat(second.getObjectType()).isEqualTo("Bar");

        assertThat(second.getDescrs()).hasSize(1);
        // check it has field bindings.
        final ExprConstraintDescr bindingDescr = (ExprConstraintDescr) second.getDescrs().get(0);
        assertThat(bindingDescr.getExpression()).isEqualTo("a4:a==4");
    }

    @Test
    void queryRuleMixed() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "query_and_rule.drl");

        assertThat(pkg.getRules()).hasSize(4); // as queries are rules
        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("bar");

        QueryDescr query = (QueryDescr) pkg.getRules().get(1);
        assertThat(query.getName()).isEqualTo("simple_query");

        rule = (RuleDescr) pkg.getRules().get(2);
        assertThat(rule.getName()).isEqualTo("bar2");

        query = (QueryDescr) pkg.getRules().get(3);
        assertThat(query.getName()).isEqualTo("simple_query2");
    }

    @Test
    void multipleRules() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "multiple_rules.drl");

        final List<RuleDescr> rules = pkg.getRules();

        assertThat(rules).hasSize(2);

        final RuleDescr rule0 = rules.get(0);
        assertThat(rule0.getName()).isEqualTo("Like Stilton");

        final RuleDescr rule1 = rules.get(1);
        assertThat(rule1.getName()).isEqualTo("Like Cheddar");

        // checkout the first rule
        AndDescr lhs = rule1.getLhs();
        assertThat(lhs).isNotNull();
        assertThat(lhs.getDescrs()).hasSize(1);
        assertThat((String) rule0.getConsequence()).isEqualToIgnoringWhitespace("System.out.println(\"I like \" + t);");

        // Check first pattern
        PatternDescr first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getObjectType()).isEqualTo("Cheese");

        // checkout the second rule
        lhs = rule1.getLhs();
        assertThat(lhs).isNotNull();
        assertThat(lhs.getDescrs()).hasSize(1);
        assertThat((String) rule1.getConsequence()).isEqualToIgnoringWhitespace("System.out.println(\"I like \" + t);");

        // Check first pattern
        first = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(first.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void expanderLineSpread() {
        final PackageDescr pkg = parseAndGetPackageDescrWithDsl("expander_spread_lines.dslr", "complex.dsl");

        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);
        assertThat((String) rule.getConsequence()).isNotNull();
    }

    @Test
    void expanderMultipleConstraints() {
        final PackageDescr pkg = parseAndGetPackageDescrWithDsl("expander_multiple_constraints.dslr", "multiple_constraints.dsl");

        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Person");

        assertThat(pattern.getConstraint().getDescrs()).hasSize(2);
        assertThat(((ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0)).getExpression()).isEqualTo("age < 42");
        assertThat(((ExprConstraintDescr) pattern.getConstraint().getDescrs().get(1)).getExpression()).isEqualTo("location==atlanta");

        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Bar");

        assertThat((String) rule.getConsequence()).isNotNull();
    }

    @Test
    void expanderMultipleConstraintsFlush() {
        // this is similar to the other test, but it requires a flush to add the
        // constraints
        final PackageDescr pkg = parseAndGetPackageDescrWithDsl("expander_multiple_constraints_flush.dslr", "multiple_constraints.dsl");

        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Person");

        assertThat(pattern.getConstraint().getDescrs()).hasSize(2);
        assertThat(((ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0)).getExpression()).isEqualTo("age < 42");
        assertThat(((ExprConstraintDescr) pattern.getConstraint().getDescrs().get(1)).getExpression()).isEqualTo("location==atlanta");

        assertThat((String) rule.getConsequence()).isNotNull();
    }

    @Test
    void basicBinding() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "basic_binding.drl");

        final RuleDescr ruleDescr = (RuleDescr) pkg.getRules().get(0);

        final AndDescr lhs = ruleDescr.getLhs();
        assertThat(lhs.getDescrs()).hasSize(1);
        final PatternDescr cheese = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
        assertThat(cheese.getConstraint().getDescrs()).hasSize(1);
        final ExprConstraintDescr fieldBinding = (ExprConstraintDescr) cheese.getDescrs().get(0);
        assertThat(fieldBinding.getExpression()).isEqualToIgnoringWhitespace("$type:type");
    }

    @Test
    void boundVariables() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "bindings.drl");

        final RuleDescr ruleDescr = (RuleDescr) pkg.getRules().get(0);

        final AndDescr lhs = ruleDescr.getLhs();
        assertThat(lhs.getDescrs()).hasSize(2);
        final PatternDescr cheese = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
        assertThat(cheese.getDescrs()).hasSize(1);
        ExprConstraintDescr fieldBinding = (ExprConstraintDescr) cheese.getDescrs().get(0);
        assertThat(fieldBinding.getExpression()).isEqualTo("$type : type == \"stilton\"");

        final PatternDescr person = (PatternDescr) lhs.getDescrs().get(1);
        assertThat(person.getDescrs()).hasSize(2);
        fieldBinding = (ExprConstraintDescr) person.getDescrs().get(0);
        assertThat(fieldBinding.getExpression()).isEqualTo("$name : name == \"bob\"");

        ExprConstraintDescr fld = (ExprConstraintDescr) person.getDescrs().get(1);
        assertThat(fld.getExpression()).isEqualTo("likes == $type");
    }

    @Test
    void orNesting() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "or_nesting.drl");

        assertThat(pkg).isNotNull();
        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("simple_rule");

        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);

        final PatternDescr first = (PatternDescr) or.getDescrs().get(0);
        assertThat(first.getObjectType()).isEqualTo("Person");

        final AndDescr and = (AndDescr) or.getDescrs().get(1);
        assertThat(and.getDescrs()).hasSize(2);

        final PatternDescr left = (PatternDescr) and.getDescrs().get(0);
        assertThat(left.getObjectType()).isEqualTo("Person");

        final PatternDescr right = (PatternDescr) and.getDescrs().get(1);
        assertThat(right.getObjectType()).isEqualTo("Cheese");
    }

    /**
     * Test that explicit "&&", "||" works as expected
     */
    @Test
    void andOrRules() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "and_or_rule.drl");

        assertThat(pkg).isNotNull();
        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("simple_rule");

        // we will have 3 children under the main And node
        final AndDescr and = rule.getLhs();
        assertThat(and.getDescrs()).hasSize(3);

        PatternDescr left = (PatternDescr) and.getDescrs().get(0);
        PatternDescr right = (PatternDescr) and.getDescrs().get(1);
        assertThat(left.getObjectType()).isEqualTo("Person");
        assertThat(right.getObjectType()).isEqualTo("Cheese");

        assertThat(left.getConstraint().getDescrs()).hasSize(1);

        ExprConstraintDescr fld = (ExprConstraintDescr) left.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualTo("name == \"mark\"");

        assertThat(right.getConstraint().getDescrs()).hasSize(1);

        fld = (ExprConstraintDescr) right.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualTo("type == \"stilton\"");

        // now the "||" part
        final OrDescr or = (OrDescr) and.getDescrs().get(2);
        assertThat(or.getDescrs()).hasSize(2);
        left = (PatternDescr) or.getDescrs().get(0);
        right = (PatternDescr) or.getDescrs().get(1);
        assertThat(left.getObjectType()).isEqualTo("Person");
        assertThat(right.getObjectType()).isEqualTo("Cheese");
        assertThat(left.getConstraint().getDescrs()).hasSize(1);

        fld = (ExprConstraintDescr) left.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualTo("name == \"mark\"");

        assertThat(right.getConstraint().getDescrs()).hasSize(1);

        fld = (ExprConstraintDescr) right.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualTo("type == \"stilton\"");

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println( \"Mark and Michael\" );");
    }

    /**
     * test basic foo : Fact() || Fact() stuff
     */
    @Test
    void orWithBinding() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "or_binding.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);

        final PatternDescr leftPattern = (PatternDescr) or.getDescrs().get(0);
        assertThat(leftPattern.getObjectType()).isEqualTo("Person");
        assertThat(leftPattern.getIdentifier()).isEqualTo("foo");

        final PatternDescr rightPattern = (PatternDescr) or.getDescrs().get(1);
        assertThat(rightPattern.getObjectType()).isEqualTo("Person");
        assertThat(rightPattern.getIdentifier()).isEqualTo("foo");

        final PatternDescr cheeseDescr = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(cheeseDescr.getObjectType()).isEqualTo("Cheese");
        assertThat(cheeseDescr.getIdentifier()).isEqualTo(null);

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println( \"Mark and Michael\" + bar );");
    }

    /**
     * test basic foo : Fact() || Fact() stuff binding to an "or"
     */
    @Test
    void orBindingComplex() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "or_binding_complex.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);

        // first fact
        final PatternDescr firstFact = (PatternDescr) or.getDescrs().get(0);
        assertThat(firstFact.getObjectType()).isEqualTo("Person");
        assertThat(firstFact.getIdentifier()).isEqualTo("foo");

        // second "option"
        final PatternDescr secondFact = (PatternDescr) or.getDescrs().get(1);
        assertThat(secondFact.getObjectType()).isEqualTo("Person");
        assertThat(secondFact.getConstraint().getDescrs()).hasSize(1);
        assertThat(secondFact.getIdentifier()).isEqualTo("foo");

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println( \"Mark and Michael\" + bar );");
    }

    @Test
    void orBindingWithBrackets() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "or_binding_with_brackets.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);

        // first fact
        final PatternDescr firstFact = (PatternDescr) or.getDescrs().get(0);
        assertThat(firstFact.getObjectType()).isEqualTo("Person");
        assertThat(firstFact.getIdentifier()).isEqualTo("foo");

        // second "option"
        final PatternDescr secondFact = (PatternDescr) or.getDescrs().get(0);
        assertThat(secondFact.getObjectType()).isEqualTo("Person");
        assertThat(secondFact.getIdentifier()).isEqualTo("foo");

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println( \"Mark and Michael\" + bar );");
    }

    @Test
    void parenthesesOrAndOr() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "brackets_precedence.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        final AndDescr rootAnd = (AndDescr) rule.getLhs();

        assertThat(rootAnd.getDescrs()).hasSize(2);

        final OrDescr leftOr = (OrDescr) rootAnd.getDescrs().get(0);

        assertThat(leftOr.getDescrs()).hasSize(2);
        final NotDescr not = (NotDescr) leftOr.getDescrs().get(0);
        final PatternDescr foo1 = (PatternDescr) not.getDescrs().get(0);
        assertThat(foo1.getObjectType()).isEqualTo("Foo");
        final PatternDescr foo2 = (PatternDescr) leftOr.getDescrs().get(1);
        assertThat(foo2.getObjectType()).isEqualTo("Foo");

        final OrDescr rightOr = (OrDescr) rootAnd.getDescrs().get(1);

        assertThat(rightOr.getDescrs()).hasSize(2);
        final PatternDescr shoes = (PatternDescr) rightOr.getDescrs().get(0);
        assertThat(shoes.getObjectType()).isEqualTo("Shoes");
        final PatternDescr butt = (PatternDescr) rightOr.getDescrs().get(1);
        assertThat(butt.getObjectType()).isEqualTo("Butt");
    }

    @Test
    void parenthesesAndOrOr() {
        final String drl = "rule and_or_or\n" +
                "  when\n" +
                "     (Foo(x == 1) and Bar(x == 2)) or (Foo(x == 3) or Bar(x == 4))\n" +
                "  then\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(drl);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final AndDescr rootAnd = (AndDescr) rule.getLhs();
        assertThat(rootAnd.getDescrs()).hasSize(1);

        final OrDescr topOr = (OrDescr) rootAnd.getDescrs().get(0);
        assertThat(topOr.getDescrs()).hasSize(2);

        final AndDescr leftAnd = (AndDescr) topOr.getDescrs().get(0);
        assertThat(leftAnd.getDescrs()).hasSize(2);
        final PatternDescr foo1 = (PatternDescr) leftAnd.getDescrs().get(0);
        assertThat(foo1.getObjectType()).isEqualTo("Foo");
        final PatternDescr bar1 = (PatternDescr) leftAnd.getDescrs().get(1);
        assertThat(bar1.getObjectType()).isEqualTo("Bar");

        final OrDescr rightOr = (OrDescr) topOr.getDescrs().get(1);
        assertThat(rightOr.getDescrs()).hasSize(2);
        final PatternDescr foo2 = (PatternDescr) rightOr.getDescrs().get(0);
        assertThat(foo2.getObjectType()).isEqualTo("Foo");
        final PatternDescr bar2 = (PatternDescr) rightOr.getDescrs().get(1);
        assertThat(bar2.getObjectType()).isEqualTo("Bar");
    }

    @Test
    void parenthesesOrAndAnd() {
        final String drl = "rule or_and_and\n" +
                "  when\n" +
                "     (Foo(x == 1) or Bar(x == 2)) and (Foo(x == 3) and Bar(x == 4))\n" +
                "  then\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(drl);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final AndDescr rootAnd = (AndDescr) rule.getLhs();
        assertThat(rootAnd.getDescrs()).hasSize(2);

        final OrDescr leftOr = (OrDescr) rootAnd.getDescrs().get(0);
        assertThat(leftOr.getDescrs()).hasSize(2);
        final PatternDescr foo1 = (PatternDescr) leftOr.getDescrs().get(0);
        assertThat(foo1.getObjectType()).isEqualTo("Foo");
        final PatternDescr bar1 = (PatternDescr) leftOr.getDescrs().get(1);
        assertThat(bar1.getObjectType()).isEqualTo("Bar");

        final AndDescr rightAnd = (AndDescr) rootAnd.getDescrs().get(1);
        assertThat(rightAnd.getDescrs()).hasSize(2);
        final PatternDescr foo2 = (PatternDescr) rightAnd.getDescrs().get(0);
        assertThat(foo2.getObjectType()).isEqualTo("Foo");
        final PatternDescr bar2 = (PatternDescr) rightAnd.getDescrs().get(1);
        assertThat(bar2.getObjectType()).isEqualTo("Bar");
    }

    @Test
    void multipleLevelNestAndOrOrOrAnd() {
        final String drl = "rule and_or_or_or_and\n" +
                "  when\n" +
                "     (Foo(x == 1) and (Bar(x == 2) or Foo(x == 3))) or (Bar(x == 4) or (Foo(x == 5) and Bar(x == 6)))\n" +
                "  then\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(drl);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final AndDescr rootAnd = (AndDescr) rule.getLhs();
        assertThat(rootAnd.getDescrs()).hasSize(1);

        final OrDescr topOr = (OrDescr) rootAnd.getDescrs().get(0);
        assertThat(topOr.getDescrs()).hasSize(2);

        final AndDescr leftAnd = (AndDescr) topOr.getDescrs().get(0);
        assertThat(leftAnd.getDescrs()).hasSize(2);
        final PatternDescr foo1 = (PatternDescr) leftAnd.getDescrs().get(0);
        assertThat(foo1.getObjectType()).isEqualTo("Foo");
        final OrDescr leftOr = (OrDescr) leftAnd.getDescrs().get(1);
        assertThat(leftOr.getDescrs()).hasSize(2);
        final PatternDescr bar1 = (PatternDescr) leftOr.getDescrs().get(0);
        assertThat(bar1.getObjectType()).isEqualTo("Bar");
        final PatternDescr foo2 = (PatternDescr) leftOr.getDescrs().get(1);
        assertThat(foo2.getObjectType()).isEqualTo("Foo");

        final OrDescr rightOr = (OrDescr) topOr.getDescrs().get(1);
        assertThat(rightOr.getDescrs()).hasSize(2);
        final PatternDescr bar2 = (PatternDescr) rightOr.getDescrs().get(0);
        assertThat(bar2.getObjectType()).isEqualTo("Bar");
        final AndDescr rightAnd = (AndDescr) rightOr.getDescrs().get(1);
        assertThat(rightAnd.getDescrs()).hasSize(2);
        final PatternDescr foo3 = (PatternDescr) rightAnd.getDescrs().get(0);
        assertThat(foo3.getObjectType()).isEqualTo("Foo");
        final PatternDescr bar3 = (PatternDescr) rightAnd.getDescrs().get(1);
        assertThat(bar3.getObjectType()).isEqualTo("Bar");
    }

    @Test
    void multipleLevelNestWithThreeOrSiblings() {
        final String drl = "rule nest_or_siblings\n" +
                "  when\n" +
                "     (A() or (B() or C() or (D() and E())))\n" +
                "  then\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(drl);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final AndDescr rootAnd = (AndDescr) rule.getLhs();
        assertThat(rootAnd.getDescrs()).hasSize(1);

        final OrDescr topOr = (OrDescr) rootAnd.getDescrs().get(0);
        assertThat(topOr.getDescrs()).hasSize(2);

        final PatternDescr leftPattern = (PatternDescr) topOr.getDescrs().get(0);
        assertThat(leftPattern.getObjectType()).isEqualTo("A");

        final OrDescr rightOr = (OrDescr) topOr.getDescrs().get(1);
        assertThat(rightOr.getDescrs()).as("top level Or has 3 sibling children").hasSize(3);
        final PatternDescr bPattern = (PatternDescr) rightOr.getDescrs().get(0);
        assertThat(bPattern.getObjectType()).isEqualTo("B");
        final PatternDescr cPattern = (PatternDescr) rightOr.getDescrs().get(1);
        assertThat(cPattern.getObjectType()).isEqualTo("C");
        final AndDescr deAnd = (AndDescr) rightOr.getDescrs().get(2);
        assertThat(deAnd.getDescrs()).hasSize(2);

        final PatternDescr dPattern = (PatternDescr) deAnd.getDescrs().get(0);
        assertThat(dPattern.getObjectType()).isEqualTo("D");
        final PatternDescr ePattern = (PatternDescr) deAnd.getDescrs().get(1);
        assertThat(ePattern.getObjectType()).isEqualTo("E");
    }

    @Test
    void existsMultipleLevelNestWithThreeOrSiblings() {
        final String drl = "rule nest_or_siblings\n" +
                "  when\n" +
                "     exists(A() or (B() or C() or (D() and E())))\n" +
                "  then\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(drl);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final AndDescr rootAnd = (AndDescr) rule.getLhs();
        assertThat(rootAnd.getDescrs()).hasSize(1);

        final ExistsDescr topExists = (ExistsDescr) rootAnd.getDescrs().get(0);
        assertThat(topExists.getDescrs()).hasSize(1);

        final OrDescr topOr = (OrDescr) topExists.getDescrs().get(0);
        assertThat(topOr.getDescrs()).hasSize(2);

        final PatternDescr leftPattern = (PatternDescr) topOr.getDescrs().get(0);
        assertThat(leftPattern.getObjectType()).isEqualTo("A");

        final OrDescr rightOr = (OrDescr) topOr.getDescrs().get(1);
        assertThat(rightOr.getDescrs()).hasSize(3);
        final PatternDescr bPattern = (PatternDescr) rightOr.getDescrs().get(0);
        assertThat(bPattern.getObjectType()).isEqualTo("B");
        final PatternDescr cPattern = (PatternDescr) rightOr.getDescrs().get(1);
        assertThat(cPattern.getObjectType()).isEqualTo("C");
        final AndDescr deAnd = (AndDescr) rightOr.getDescrs().get(2);
        assertThat(deAnd.getDescrs()).hasSize(2);

        final PatternDescr dPattern = (PatternDescr) deAnd.getDescrs().get(0);
        assertThat(dPattern.getObjectType()).isEqualTo("D");
        final PatternDescr ePattern = (PatternDescr) deAnd.getDescrs().get(1);
        assertThat(ePattern.getObjectType()).isEqualTo("E");
    }

    @Test
    void evalMultiple() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "eval_multiple.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(4);

        final EvalDescr eval = (EvalDescr) rule.getLhs().getDescrs().get(0);
        assertThat((String) eval.getContent()).isEqualToIgnoringWhitespace("abc(\"foo\") + 5");

        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Foo");
    }

    @Test
    void withEval() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "with_eval.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(3);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Foo");
        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Bar");

        final EvalDescr eval = (EvalDescr) rule.getLhs().getDescrs().get(2);
        assertThat((String) eval.getContent()).isEqualToIgnoringWhitespace("abc(\"foo\")");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("Kapow");
    }

    @Test
    void withRetval() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "with_retval.drl");

        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        final PatternDescr col = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(col.getConstraint().getDescrs()).hasSize(1);
        assertThat(col.getObjectType()).isEqualTo("Foo");
        final ExprConstraintDescr fld = (ExprConstraintDescr) col.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("name== (a + b)");
    }

    @Test
    void withPredicate() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "with_predicate.drl");

        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        final PatternDescr col = (PatternDescr) rule.getLhs().getDescrs().get(0);
        AndDescr and = (AndDescr) col.getConstraint();
        assertThat(and.getDescrs()).hasSize(2);

        final ExprConstraintDescr field = (ExprConstraintDescr) col.getDescrs().get(0);
        final ExprConstraintDescr pred = (ExprConstraintDescr) and.getDescrs().get(1);
        assertThat(field.getExpression()).isEqualToIgnoringWhitespace("$age2:age");
        assertThat(pred.getExpression()).isEqualToIgnoringWhitespace("$age2 == $age1+2");
    }

    @Test
    void notWithConstraint() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "not_with_constraint.drl");

        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final ExprConstraintDescr fieldBinding = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fieldBinding.getExpression()).isEqualToIgnoringWhitespace("$likes:like");

        final NotDescr not = (NotDescr) rule.getLhs().getDescrs().get(1);
        pattern = (PatternDescr) not.getDescrs().get(0);

        final ExprConstraintDescr fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("type == $likes");
    }

    @Test
    void functions() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "functions.drl");

        assertThat(pkg.getRules()).hasSize(2);

        final List<FunctionDescr> functions = pkg.getFunctions();
        assertThat(functions).hasSize(2);

        FunctionDescr func = functions.get(0);
        assertThat(func.getName()).isEqualTo("functionA");
        assertThat(func.getReturnType()).isEqualTo("String");
        assertThat(func.getParameterNames()).hasSize(2);
        assertThat(func.getParameterTypes()).hasSize(2);
        assertThat(func.getLine()).isEqualTo(21);
        assertThat(func.getColumn()).isEqualTo(0);

        assertThat(func.getParameterTypes().get(0)).isEqualTo("String");
        assertThat(func.getParameterNames().get(0)).isEqualTo("s");

        assertThat(func.getParameterTypes().get(1)).isEqualTo("Integer");
        assertThat(func.getParameterNames().get(1)).isEqualTo("i");

        assertThat(func.getBody()).isEqualToIgnoringWhitespace("foo();");

        func = functions.get(1);
        assertThat(func.getName()).isEqualTo("functionB");
        assertThat(func.getText()).isEqualToIgnoringWhitespace("bar();");
    }

    @Test
    void comment() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "comment.drl");

        assertThat(pkg).isNotNull();

        assertThat(pkg.getName()).isEqualTo("foo.bar");
    }

    @Test
    void attributes() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_attributes.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(6);

        AttributeDescr at = (AttributeDescr) attrs.get("salience");
        assertThat(at.getName()).isEqualTo("salience");
        assertThat(at.getValue()).isEqualTo("42");

        at = (AttributeDescr) attrs.get("agenda-group");
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("my_group");

        at = (AttributeDescr) attrs.get("no-loop");
        assertThat(at.getName()).isEqualTo("no-loop");
        assertThat(at.getValue()).isEqualTo("true");

        at = (AttributeDescr) attrs.get("duration");
        assertThat(at.getName()).isEqualTo("duration");
        assertThat(at.getValue()).isEqualTo("42");

        at = (AttributeDescr) attrs.get("activation-group");
        assertThat(at.getName()).isEqualTo("activation-group");
        assertThat(at.getValue()).isEqualTo("my_activation_group");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void attributes2() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "rule_attributes2.drl");

        List<RuleDescr> rules = pkg.getRules();
        assertThat(rules).hasSize(3);

        RuleDescr rule = rules.get(0);
        assertThat(rule.getName()).isEqualTo("rule1");
        Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);
        AttributeDescr at = (AttributeDescr) attrs.get("salience");
        assertThat(at.getName()).isEqualTo("salience");
        assertThat(at.getValue()).isEqualTo("(42)");
        at = (AttributeDescr) attrs.get("agenda-group");
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("my_group");

        rule = rules.get(1);
        assertThat(rule.getName()).isEqualTo("rule2");
        attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);
        at = (AttributeDescr) attrs.get("salience");
        assertThat(at.getName()).isEqualTo("salience");
        assertThat(at.getValue()).isEqualTo("(Integer.MIN_VALUE)");
        at = (AttributeDescr) attrs.get("no-loop");
        assertThat(at.getName()).isEqualTo("no-loop");

        rule = rules.get(2);
        assertThat(rule.getName()).isEqualTo("rule3");
        attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);
        at = (AttributeDescr) attrs.get("enabled");
        assertThat(at.getName()).isEqualTo("enabled");
        assertThat(at.getValue()).isEqualTo("(Boolean.TRUE)");
        at = (AttributeDescr) attrs.get("activation-group");
        assertThat(at.getName()).isEqualTo("activation-group");
        assertThat(at.getValue()).isEqualTo("my_activation_group");
    }

    @Test
    void attributeRefract() {
        final String source = "rule Test refract when Person() then end";

        PackageDescr pkg = parseAndGetPackageDescr(
                source);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        assertThat(rule.getName()).isEqualTo("Test");
        Map<String, AttributeDescr> attributes = rule.getAttributes();
        assertThat(attributes).hasSize(1);
        AttributeDescr refract = attributes.get("refract");
        assertThat(refract).isNotNull();
        assertThat(refract.getValue()).isEqualTo("true");
    }

    @Test
    void enabledExpression() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_enabled_expression.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(3);

        AttributeDescr at = (AttributeDescr) attrs.get("enabled");
        assertThat(at.getName()).isEqualTo("enabled");
        assertThat(at.getValue()).isEqualTo("( 1 + 1 == 2 )");

        at = (AttributeDescr) attrs.get("salience");
        assertThat(at.getName()).isEqualTo("salience");
        assertThat(at.getValue()).isEqualTo("( 1+2 )");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void durationExpression() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_duration_expression.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);

        AttributeDescr at = (AttributeDescr) attrs.get("duration");
        assertThat(at.getName()).isEqualTo("duration");
        assertThat(at.getValue()).isEqualTo("1h30m");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void calendars() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_calendars_attribute.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);

        AttributeDescr at = (AttributeDescr) attrs.get("calendars");
        assertThat(at.getName()).isEqualTo("calendars");
        assertThat(at.getValue()).isEqualTo("[ \"cal1\" ]");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void calendars2() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_calendars_attribute2.drl");

        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);

        AttributeDescr at = (AttributeDescr) attrs.get("calendars");
        assertThat(at.getName()).isEqualTo("calendars");
        assertThat(at.getValue()).isEqualTo("[ \"cal 1\", \"cal 2\", \"cal 3\" ]");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void timer() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile("rule_timer_attribute.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(2);

        AttributeDescr at = (AttributeDescr) attrs.get("timer");
        assertThat(at.getName()).isEqualTo("timer");
        assertThat(at.getValue()).isEqualTo("int: 0 1; start=1_000_000, repeat-limit=0");

        at = (AttributeDescr) attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");
    }

    @Test
    void attributes_alternateSyntax() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "rule_attributes_alt.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("bar();");

        final Map<String, AttributeDescr> attrs = rule.getAttributes();
        assertThat(attrs).hasSize(6);

        AttributeDescr at = attrs.get("salience");
        assertThat(at.getName()).isEqualTo("salience");
        assertThat(at.getValue()).isEqualTo("42");

        at = attrs.get("agenda-group");
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("my_group");

        at = attrs.get("no-loop");
        assertThat(at.getName()).isEqualTo("no-loop");
        assertThat(at.getValue()).isEqualTo("true");

        at = attrs.get("lock-on-active");
        assertThat(at.getName()).isEqualTo("lock-on-active");
        assertThat(at.getValue()).isEqualTo("true");

        at = attrs.get("duration");
        assertThat(at.getName()).isEqualTo("duration");
        assertThat(at.getValue()).isEqualTo("42");

        at = attrs.get("activation-group");
        assertThat(at.getName()).isEqualTo("activation-group");
        assertThat(at.getValue()).isEqualTo("my_activation_group");
    }

    @Test
    void enumeration() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "enumeration.drl");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        final PatternDescr col = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(col.getObjectType()).isEqualTo("Foo");
        assertThat(col.getConstraint().getDescrs()).hasSize(1);
        final ExprConstraintDescr fld = (ExprConstraintDescr) col.getConstraint().getDescrs().get(0);

        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("bar == Foo.BAR");
    }

    @Test
    void extraLhsNewline() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "extra_lhs_newline.drl");
    }

    @Test
    void soundsLike() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "soundslike_operator.drl");

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        PatternDescr pat = (PatternDescr) rule.getLhs().getDescrs().get(0);

        pat.getConstraint();
    }

    @Test
    void packageAttributes() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "package_attributes.drl");

        AttributeDescr at = (AttributeDescr) pkg.getAttributes().get(0);
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("x");
        at = (AttributeDescr) pkg.getAttributes().get(1);
        assertThat(at.getName()).isEqualTo("dialect");
        assertThat(at.getValue()).isEqualTo("java");

        assertThat(pkg.getRules()).hasSize(2);

        assertThat(pkg.getImports()).hasSize(2);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("bar");
        at = (AttributeDescr) rule.getAttributes().get("agenda-group");
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("x");
        at = (AttributeDescr) rule.getAttributes().get("dialect");
        assertThat(at.getName()).isEqualTo("dialect");
        assertThat(at.getValue()).isEqualTo("java");

        rule = (RuleDescr) pkg.getRules().get(1);
        assertThat(rule.getName()).isEqualTo("baz");
        at = (AttributeDescr) rule.getAttributes().get("dialect");
        assertThat(at.getName()).isEqualTo("dialect");
        assertThat(at.getValue()).isEqualTo("mvel");
        at = (AttributeDescr) rule.getAttributes().get("agenda-group");
        assertThat(at.getName()).isEqualTo("agenda-group");
        assertThat(at.getValue()).isEqualTo("x");
    }

    @Test
    void statementOrdering1() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "statement_ordering_1.drl");

        assertThat(pkg.getRules()).hasSize(2);

        assertThat(((RuleDescr) pkg.getRules().get(0)).getName()).isEqualTo("foo");
        assertThat(((RuleDescr) pkg.getRules().get(1)).getName()).isEqualTo("bar");

        assertThat(pkg.getFunctions()).hasSize(2);

        assertThat(((FunctionDescr) pkg.getFunctions().get(0)).getName()).isEqualTo("cheeseIt");
        assertThat(((FunctionDescr) pkg.getFunctions().get(1)).getName()).isEqualTo("uncheeseIt");

        assertThat(pkg.getImports()).hasSize(4);
        assertThat(((ImportDescr) pkg.getImports().get(0)).getTarget()).isEqualTo("im.one");
        assertThat(((ImportDescr) pkg.getImports().get(1)).getTarget()).isEqualTo("im.two");
        assertThat(((ImportDescr) pkg.getImports().get(2)).getTarget()).isEqualTo("im.three");
        assertThat(((ImportDescr) pkg.getImports().get(3)).getTarget()).isEqualTo("im.four");
    }

    @Test
    void ruleNamesStartingWithNumbers() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "rule_names_number_prefix.drl");

        assertThat(pkg.getRules()).hasSize(2);

        assertThat(((RuleDescr) pkg.getRules().get(0)).getName()).isEqualTo("1. Do Stuff!");
        assertThat(((RuleDescr) pkg.getRules().get(1)).getName()).isEqualTo("2. Do More Stuff!");
    }

    @Test
    void evalWithNewline() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "eval_with_newline.drl");
    }

    @Test
    void endPosition() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "test_EndPosition.drl");
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        final PatternDescr col = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(col.getLine()).isEqualTo(23);
        assertThat(col.getEndLine()).isEqualTo(25);
    }

    @Test
    void groupBy() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile("groupBy.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final GroupByDescr groupBy = (GroupByDescr) outPattern.getSource();
        assertThat(groupBy.getGroupingKey()).isEqualToIgnoringWhitespace("$initial");
        assertThat(groupBy.getGroupingFunction()).isEqualToIgnoringWhitespace("$p.getName().substring(0, 1)");
        assertThat(groupBy.getActionCode()).isNull();
        assertThat(groupBy.getReverseCode()).isNull();
        assertThat(groupBy.getFunctions()).hasSize(2);
        assertThat(groupBy.getFunctions().get(0).getFunction()).isEqualToIgnoringWhitespace("sum");
        assertThat(groupBy.getFunctions().get(1).getFunction()).isEqualToIgnoringWhitespace("count");

        assertThat(groupBy.getFunctions().get(0).getParams()).hasSize(1);
        assertThat(groupBy.getFunctions().get(0).getParams()[0]).isEqualToIgnoringWhitespace("$age");

        assertThat(groupBy.getFunctions().get(1).getParams()).hasSize(0);

        assertThat(groupBy.isExternalFunction()).isTrue();

        final PatternDescr pattern = groupBy.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);
        assertThat(pattern.getConstraint().getDescrs().get(0).getText()).isEqualToIgnoringWhitespace("$age : age < 30");

        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);
        assertThat(pattern.getConstraint().getDescrs().get(0).getText()).isEqualToIgnoringWhitespace("$age : age < 30");

        assertThat(outPattern.getConstraint().getDescrs()).hasSize(1);
        assertThat(outPattern.getConstraint().getDescrs().get(0).getText()).isEqualToIgnoringWhitespace("$sumOfAges > 10");
    }

    @Test
    void qualifiedClassname() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "qualified_classname.drl");

        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        final PatternDescr p = (PatternDescr) rule.getLhs().getDescrs().get(0);

        assertThat(p.getObjectType()).isEqualTo("com.cheeseco.Cheese");
    }

    @Test
    void accumulate() {
        final String drl = "rule R\n" +
                "when\n" +
                "     accumulate( Person( $age : age );\n" +
                "                 $avg : average( $age ) );\n" +
                "then\n" +
                "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(drl);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Object");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(1);
        assertThat(functions.get(0).getFunction()).isEqualTo("average");
        assertThat(functions.get(0).getBind()).isEqualTo("$avg");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$age");

        final PatternDescr pattern = accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");

        // accum.getInput() is always AndDescr
        assertThat(accum.getInput()).isInstanceOfSatisfying(AndDescr.class, and -> {
            assertThat(and.getDescrs()).hasSize(1);
            assertThat(and.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("Person");
            });
        });
    }

    @Test
    void fromAccumulate() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile("from_accumulate.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(accum.getInitCode()).isEqualTo("int x = 0;");
        assertThat(accum.getActionCode()).isEqualTo("x++;");
        assertThat(accum.getReverseCode()).isNull();
        assertThat(accum.getResultCode()).isEqualTo("new Integer(x)");

        assertThat(accum.isExternalFunction()).isFalse();

        final PatternDescr pattern = accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");

        // accum.getInput() is always AndDescr
        assertThat(accum.getInput()).isInstanceOfSatisfying(AndDescr.class, and -> {
            assertThat(and.getDescrs()).hasSize(1);
            assertThat(and.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("Person");
            });
        });
    }

    @Test
    void accumulateWithBindings() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulate_with_bindings.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(outPattern.getIdentifier()).isEqualTo("$counter");
        assertThat(accum.getInitCode()).isEqualTo("int x = 0;");
        assertThat(accum.getActionCode()).isEqualTo("x++;");
        assertThat(accum.getResultCode()).isEqualTo("new Integer(x)");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
    }

    /**
     * - Optional semicolon at the end of statements (int x = 0).
     * - Optional comma delimiting init, action, and result.
     */
    @Test
    void accumulateWithoutOptionalDelimiters() {
        String source = "rule \"AccumulateParserTest\"\n"
                + "when\n"
                + "     $counter:Integer() from accumulate( $person : Person( age > 21 ),\n"
                + "                                         init( int x = 0 )\n"
                + "                                         action( x++ )\n"
                + "                                         result( new Integer(x) ) );\n"
                + "then\n"
                + "end\n";
        final PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(outPattern.getIdentifier()).isEqualTo("$counter");
        assertThat(accum.getInitCode()).isEqualTo("int x = 0");
        assertThat(accum.getActionCode()).isEqualTo("x++");
        assertThat(accum.getResultCode()).isEqualTo("new Integer(x)");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
    }

    /**
     * When the accumulate function (e.g. count()) has no arguments.
     */
    @Test
    void accumulateCount() {
        String source = "rule R when\n" +
                "   accumulate (\n" +
                "       Person(), $result : count() " +
                "         )" +
                "then\n" +
                "end";
        final PackageDescr pkg = parseAndGetPackageDescr(source);

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(accum).isNotNull();

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");

        assertThat(accum.getFunctions()).hasSize(1);
        AccumulateDescr.AccumulateFunctionCallDescr accumulateFunction = accum.getFunctions().get(0);
        assertThat(accumulateFunction.getBind()).isEqualTo("$result");
        assertThat(accumulateFunction.getFunction()).isEqualTo("count");
        assertThat(accumulateFunction.getParams()).isEmpty();
    }

    @Test
    void collect() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "collect.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final CollectDescr collect = (CollectDescr) outPattern.getSource();

        final PatternDescr pattern = (PatternDescr) collect.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
    }

    @Test
    void predicate2() {
        // predicates are also prefixed by the eval keyword
        final RuleDescr rule = parseAndGetFirstRuleDescr(
                "rule X when Foo(eval( $var.equals(\"xyz\") )) then end");

        final PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final List<?> constraints = pattern.getConstraint().getDescrs();
        assertThat(constraints).hasSize(1);

        final ExprConstraintDescr predicate = (ExprConstraintDescr) constraints.get(0);
        assertThat(predicate.getExpression()).isEqualToIgnoringWhitespace("eval( $var.equals(\"xyz\") )");
    }

    @Test
    void escapedStrings() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "escaped-string.drl");

        assertThat(rule).isNotNull();

        assertThat(rule.getName()).isEqualTo("test_Quotes");

        final String expected = "String s = \"\\\"\\n\\t\\\\\";";

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void nestedCEs() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "nested_conditional_elements.drl");

        assertThat(rule).isNotNull();

        final AndDescr root = rule.getLhs();
        final NotDescr not1 = (NotDescr) root.getDescrs().get(0);
        final AndDescr and1 = (AndDescr) not1.getDescrs().get(0);

        final PatternDescr state = (PatternDescr) and1.getDescrs().get(0);
        final NotDescr not2 = (NotDescr) and1.getDescrs().get(1);
        final AndDescr and2 = (AndDescr) not2.getDescrs().get(0);
        final PatternDescr person = (PatternDescr) and2.getDescrs().get(0);
        final PatternDescr cheese = (PatternDescr) and2.getDescrs().get(1);

        final PatternDescr person2 = (PatternDescr) root.getDescrs().get(1);
        final OrDescr or = (OrDescr) root.getDescrs().get(2);
        final PatternDescr cheese2 = (PatternDescr) or.getDescrs().get(0);
        final PatternDescr cheese3 = (PatternDescr) or.getDescrs().get(1);

        assertThat("State").isEqualTo(state.getObjectType());
        assertThat("Person").isEqualTo(person.getObjectType());
        assertThat("Cheese").isEqualTo(cheese.getObjectType());
        assertThat("Person").isEqualTo(person2.getObjectType());
        assertThat("Cheese").isEqualTo(cheese2.getObjectType());
        assertThat("Cheese").isEqualTo(cheese3.getObjectType());
    }

    @Test
    void forall() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "forall.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final ForallDescr forall = (ForallDescr) rule.getLhs().getDescrs().get(0);

        assertThat(forall.getDescrs()).hasSize(2);
        final PatternDescr pattern = forall.getBasePattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        final List<BaseDescr> remaining = forall.getRemainingPatterns();
        assertThat(remaining).hasSize(1);
        final PatternDescr cheese = (PatternDescr) remaining.get(0);
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void forallWithFrom() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "forallwithfrom.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final ForallDescr forall = (ForallDescr) rule.getLhs().getDescrs().get(0);

        assertThat(forall.getDescrs()).hasSize(2);
        final PatternDescr pattern = forall.getBasePattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(((FromDescr) pattern.getSource()).getDataSource().toString()).isEqualTo("$village");
        final List<BaseDescr> remaining = forall.getRemainingPatterns();
        assertThat(remaining).hasSize(1);
        final PatternDescr cheese = (PatternDescr) remaining.get(0);
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
        assertThat(((FromDescr) cheese.getSource()).getDataSource().toString()).isEqualTo("$cheesery");
    }

    @Test
    void memberof() {
        final String text = "rule X when Country( $cities : city )\nPerson( city memberOf $cities )\n then end";
        AndDescr descrs = parseAndGetFirstRuleDescr(
                text).getLhs();

        assertThat(descrs.getDescrs()).hasSize(2);
        PatternDescr pat = (PatternDescr) descrs.getDescrs().get(1);
        ExprConstraintDescr fieldConstr = (ExprConstraintDescr) pat.getConstraint().getDescrs().get(0);

        assertThat(fieldConstr.getExpression()).isEqualTo("city memberOf $cities");
    }

    @Test
    void notMemberof() {
        final String text = "rule X when Country( $cities : city )\nPerson( city not memberOf $cities ) then end\n";
        AndDescr descrs = parseAndGetFirstRuleDescr(
                text).getLhs();

        assertThat(descrs.getDescrs()).hasSize(2);
        PatternDescr pat = (PatternDescr) descrs.getDescrs().get(1);
        ExprConstraintDescr fieldConstr = (ExprConstraintDescr) pat.getConstraint().getDescrs().get(0);

        assertThat(fieldConstr.getExpression()).isEqualTo("city not memberOf $cities");
    }

    @Test
    void inOperator() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "in_operator_test.drl");

        assertThat(rule).isNotNull();

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("consequence();");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        // The first pattern, with 2 restrictions on a single field (plus a
        // connective)
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        ExprConstraintDescr fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualTo("age > 30 && < 40");

        // the second col, with 2 fields, the first with 2 restrictions, the
        // second field with one
        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Vehicle");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(2);

        fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("type in ( \"sedan\", \"wagon\" )");

        // now the second field
        fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(1);
        assertThat(fld.getExpression()).isEqualTo("age < 3");
    }

    @Test
    void notInOperator() {
        final RuleDescr rule = parseAndGetFirstRuleDescrFromFile(
                "notin_operator_test.drl");

        assertThat(rule).isNotNull();

        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("consequence();");
        assertThat(rule.getName()).isEqualTo("simple_rule");
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        // The first pattern, with 2 restrictions on a single field (plus a
        // connective)
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(1);

        ExprConstraintDescr fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualTo("age > 30 && < 40");

        // the second col, with 2 fields, the first with 2 restrictions, the
        // second field with one
        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("Vehicle");
        assertThat(pattern.getConstraint().getDescrs()).hasSize(2);

        fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(fld.getExpression()).isEqualToIgnoringWhitespace("type not in ( \"sedan\", \"wagon\" )");

        // now the second field
        fld = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(1);
        assertThat(fld.getExpression()).isEqualTo("age < 3");
    }

    @Test
    void checkOrDescr() {
        final String text = "rule X when Person( eval( age == 25 ) || ( eval( name.equals( \"bob\" ) ) && eval( age == 30 ) ) ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        assertThat(AndDescr.class).isEqualTo(pattern.getConstraint().getClass());

        assertThat(pattern.getConstraint().getDescrs().get(0).getClass()).isEqualTo(ExprConstraintDescr.class);
    }

    @Test
    void constraintAndConnective() {
        final String text = "rule X when Person( age < 42 && location==\"atlanta\") then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualToIgnoringWhitespace("age < 42 && location==\"atlanta\"");
    }

    @Test
    void constraintOrConnective() {
        final String text = "rule X when Person( age < 42 || location==\"atlanta\") then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualToIgnoringWhitespace("age < 42 || location==\"atlanta\"");
    }

    @Test
    void restrictions() {
        final String text = "rule X when Foo( bar > 1 || == 1 ) then end\n";

        AndDescr descrs = (AndDescr) parseAndGetFirstRuleDescr(
                text).getLhs();

        assertThat(descrs.getDescrs()).hasSize(1);
        PatternDescr pat = (PatternDescr) descrs.getDescrs().get(0);
        ExprConstraintDescr fieldConstr = (ExprConstraintDescr) pat.getConstraint().getDescrs().get(0);

        assertThat(fieldConstr.getExpression()).isEqualTo("bar > 1 || == 1");
    }

    @Test
    void semicolon() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "semicolon.drl");

        assertThat(pkg.getName()).isEqualTo("org.drools.mvel.compiler");
        assertThat(pkg.getGlobals()).hasSize(1);
        assertThat(pkg.getRules()).hasSize(3);

        final RuleDescr rule1 = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule1.getLhs().getDescrs()).hasSize(2);

        final RuleDescr query1 = (RuleDescr) pkg.getRules().get(1);
        assertThat(query1.getLhs().getDescrs()).hasSize(3);

        final RuleDescr rule2 = (RuleDescr) pkg.getRules().get(2);
        assertThat(rule2.getLhs().getDescrs()).hasSize(2);
    }

    @Test
    void eval() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "eval_parsing.drl");

        assertThat(pkg.getName()).isEqualTo("org.drools.mvel.compiler");
        assertThat(pkg.getRules()).hasSize(1);

        final RuleDescr rule1 = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule1.getLhs().getDescrs()).hasSize(1);
    }

    @Test
    void accumulateReverse() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulateReverse.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.getInitCode()).isEqualToIgnoringWhitespace("int x = 0 ;"
        );
        assertThat(accum.getActionCode()).isEqualToIgnoringWhitespace("x++;"
        );
        assertThat(accum.getReverseCode()).isEqualToIgnoringWhitespace("x--;"
        );
        assertThat(accum.getResultCode()).isEqualToIgnoringWhitespace("new Integer(x)"
        );
        assertThat(accum.isExternalFunction()).isFalse();

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
    }

    @Test
    void accumulateExternalFunction() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulateExternalFunction.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.getFunctions().get(0).getParams()[0]).isEqualToIgnoringWhitespace("$age"
        );
        assertThat(accum.getFunctions().get(0).getFunction()).isEqualToIgnoringWhitespace("average"
        );
        assertThat(accum.isExternalFunction()).isTrue();

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Person");
    }

    @Test
    void collectWithNestedFrom() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "collect_with_nested_from.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final CollectDescr collect = (CollectDescr) out.getSource();

        PatternDescr person = (PatternDescr) collect.getInputPattern();
        assertThat(person.getObjectType()).isEqualTo("Person");

        final CollectDescr collect2 = (CollectDescr) person.getSource();

        final PatternDescr people = collect2.getInputPattern();
        assertThat(people.getObjectType()).isEqualTo("People");
    }

    @Test
    void accumulateWithNestedFrom() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulate_with_nested_from.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accumulate = (AccumulateDescr) out.getSource();

        PatternDescr person = (PatternDescr) accumulate.getInputPattern();
        assertThat(person.getObjectType()).isEqualTo("Person");

        final CollectDescr collect2 = (CollectDescr) person.getSource();

        final PatternDescr people = collect2.getInputPattern();
        assertThat(people.getObjectType()).isEqualTo("People");
    }

    @Test
    void accumulateMultipleFunctions() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulateMultipleFunctions.drl");

        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Object");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(3);
        assertThat(functions.get(0).getFunction()).isEqualTo("average");
        assertThat(functions.get(0).getBind()).isEqualTo("$a1");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$price");

        assertThat(functions.get(1).getFunction()).isEqualTo("min");
        assertThat(functions.get(1).getBind()).isEqualTo("$m1");
        assertThat(functions.get(1).getParams()[0]).isEqualTo("$price");

        assertThat(functions.get(2).getFunction()).isEqualTo("max");
        assertThat(functions.get(2).getBind()).isEqualTo("$M1");
        assertThat(functions.get(2).getParams()[0]).isEqualTo("$price");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void accumulateMnemonic() {
        String drl = "package org.drools.mvel.compiler\n" +
                "rule \"Accumulate 1\"\n" +
                "when\n" +
                "     acc( Cheese( $price : price ),\n" +
                "          $a1 : average( $price ) )\n" +
                "then\n" +
                "end\n";
        PackageDescr pkg = parseAndGetPackageDescr(
                drl);

        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Object");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(1);
        assertThat(functions.get(0).getFunction()).isEqualTo("average");
        assertThat(functions.get(0).getBind()).isEqualTo("$a1");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$price");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void accumulateMnemonic2() {
        String drl = "package org.drools.mvel.compiler\n" +
                "rule \"Accumulate 1\"\n" +
                "when\n" +
                "     Number() from acc( Cheese( $price : price ),\n" +
                "                        average( $price ) )\n" +
                "then\n" +
                "end\n";
        PackageDescr pkg = parseAndGetPackageDescr(
                drl);

        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Number");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(1);
        assertThat(functions.get(0).getFunction()).isEqualTo("average");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$price");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void importAccumulate() {
        String drl = "package org.drools.mvel.compiler\n" +
                "import acc foo.Bar baz\n" +
                "import accumulate foo.Bar2 baz2\n" +
                "rule \"Accumulate 1\"\n" +
                "when\n" +
                "     acc( Cheese( $price : price ),\n" +
                "          $v1 : baz( $price ), \n" +
                "          $v2 : baz2( $price ) )\n" +
                "then\n" +
                "end\n";
        PackageDescr pkg = parseAndGetPackageDescr(
                drl);

        assertThat(pkg.getAccumulateImports()).hasSize(2);
        AccumulateImportDescr imp = (AccumulateImportDescr) pkg.getAccumulateImports().get(0);
        assertThat(imp.getTarget()).isEqualTo("foo.Bar");
        assertThat(imp.getFunctionName()).isEqualTo("baz");

        imp = (AccumulateImportDescr) pkg.getAccumulateImports().get(1);
        assertThat(imp.getTarget()).isEqualTo("foo.Bar2");
        assertThat(imp.getFunctionName()).isEqualTo("baz2");

        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Object");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(2);
        assertThat(functions.get(0).getFunction()).isEqualTo("baz");
        assertThat(functions.get(0).getBind()).isEqualTo("$v1");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$price");

        assertThat(functions.get(1).getFunction()).isEqualTo("baz2");
        assertThat(functions.get(1).getBind()).isEqualTo("$v2");
        assertThat(functions.get(1).getParams()[0]).isEqualTo("$price");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void accumulateMultipleFunctionsConstraint() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulateMultipleFunctionsConstraint.drl");

        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        PatternDescr out = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(out.getObjectType()).isEqualTo("Object");
        assertThat(out.getConstraint().getDescrs()).hasSize(2);
        assertThat(out.getConstraint().getDescrs().get(0).toString()).isEqualTo("$a1 > 10 && $M1 <= 100");
        assertThat(out.getConstraint().getDescrs().get(1).toString()).isEqualTo("$m1 == 5");
        AccumulateDescr accum = (AccumulateDescr) out.getSource();
        assertThat(accum.isExternalFunction()).isTrue();

        List<AccumulateDescr.AccumulateFunctionCallDescr> functions = accum.getFunctions();
        assertThat(functions).hasSize(3);
        assertThat(functions.get(0).getFunction()).isEqualTo("average");
        assertThat(functions.get(0).getBind()).isEqualTo("$a1");
        assertThat(functions.get(0).getParams()[0]).isEqualTo("$price");

        assertThat(functions.get(1).getFunction()).isEqualTo("min");
        assertThat(functions.get(1).getBind()).isEqualTo("$m1");
        assertThat(functions.get(1).getParams()[0]).isEqualTo("$price");

        assertThat(functions.get(2).getFunction()).isEqualTo("max");
        assertThat(functions.get(2).getBind()).isEqualTo("$M1");
        assertThat(functions.get(2).getParams()[0]).isEqualTo("$price");

        final PatternDescr pattern = (PatternDescr) accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void orCE() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "or_ce.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        final PatternDescr person = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(person.getObjectType()).isEqualTo("Person");
        assertThat(person.getIdentifier()).isEqualTo("$p");

        final OrDescr or = (OrDescr) rule.getLhs().getDescrs().get(1);
        assertThat(or.getDescrs()).hasSize(2);

        final PatternDescr cheese1 = (PatternDescr) or.getDescrs().get(0);
        assertThat(cheese1.getObjectType()).isEqualTo("Cheese");
        assertThat(cheese1.getIdentifier()).isEqualTo("$c");
        final PatternDescr cheese2 = (PatternDescr) or.getDescrs().get(1);
        assertThat(cheese2.getObjectType()).isEqualTo("Cheese");
        assertThat(cheese2.getIdentifier()).isNull();
    }

    @Test
    void ruleSingleLine() {
        final String text = "rule \"another test\" salience 10 when eval( true ) then System.out.println(1); end";
        RuleDescr rule = parseAndGetFirstRuleDescr(
                text);

        assertThat(rule.getName()).isEqualTo("another test");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println(1); ");
    }

    @Test
    void ruleTwoLines() {
        final String text = "rule \"another test\" salience 10 when eval( true ) then System.out.println(1);\n end";
        RuleDescr rule = parseAndGetFirstRuleDescr(
                text);

        assertThat(rule.getName()).isEqualTo("another test");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println(1);\n ");
    }

    @Test
    void ruleParseLhs3() {
        final String text = "rule X when (or\nnot Person()\n(and Cheese()\nMeat()\nWine())) then end";
        AndDescr pattern = parseAndGetFirstRuleDescr(
                text).getLhs();

        assertThat(pattern.getDescrs()).hasSize(1);
        OrDescr or = (OrDescr) pattern.getDescrs().get(0);
        assertThat(or.getDescrs()).hasSize(2);
        NotDescr not = (NotDescr) or.getDescrs().get(0);
        AndDescr and = (AndDescr) or.getDescrs().get(1);
        assertThat(not.getDescrs()).hasSize(1);
        PatternDescr person = (PatternDescr) not.getDescrs().get(0);
        assertThat(person.getObjectType()).isEqualTo("Person");
        assertThat(and.getDescrs()).hasSize(3);
        PatternDescr cheese = (PatternDescr) and.getDescrs().get(0);
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
        PatternDescr meat = (PatternDescr) and.getDescrs().get(1);
        assertThat(meat.getObjectType()).isEqualTo("Meat");
        PatternDescr wine = (PatternDescr) and.getDescrs().get(2);
        assertThat(wine.getObjectType()).isEqualTo("Wine");
    }

    @Test
    void accumulateMultiPattern() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "accumulate_multi_pattern.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(1);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(outPattern.getIdentifier()).isEqualToIgnoringWhitespace("$counter"
        );
        assertThat(accum.getInitCode()).isEqualToIgnoringWhitespace("int x = 0 ;"
        );
        assertThat(accum.getActionCode()).isEqualToIgnoringWhitespace("x++;"
        );
        assertThat(accum.getResultCode()).isEqualToIgnoringWhitespace("new Integer(x)"
        );

        final AndDescr and = (AndDescr) accum.getInput();
        assertThat(and.getDescrs()).hasSize(2);
        final PatternDescr person = (PatternDescr) and.getDescrs().get(0);
        final PatternDescr cheese = (PatternDescr) and.getDescrs().get(1);
        assertThat(person.getObjectType()).isEqualTo("Person");
        assertThat(cheese.getObjectType()).isEqualTo("Cheese");
    }

    @Test
    void pluggableOperators() {

        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "pluggable_operators.drl");

        assertThat(pkg.getRules()).hasSize(1);
        final RuleDescr rule = (RuleDescr) pkg.getRules().get(0);
        assertThat(rule.getLhs().getDescrs()).hasSize(5);

        final PatternDescr eventA = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(eventA.getIdentifier()).isEqualTo("$a");
        assertThat(eventA.getObjectType()).isEqualTo("EventA");

        final PatternDescr eventB = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(eventB.getIdentifier()).isEqualTo("$b");
        assertThat(eventB.getObjectType()).isEqualTo("EventB");
        assertThat(eventB.getConstraint().getDescrs()).hasSize(1);
        assertThat(eventB.getConstraint().getDescrs()).hasSize(1);

        final ExprConstraintDescr fcdB = (ExprConstraintDescr) eventB.getConstraint().getDescrs().get(0);
        assertThat(fcdB.getExpression()).isEqualTo("this after[1,10] $a || this not after[15,20] $a");

        final PatternDescr eventC = (PatternDescr) rule.getLhs().getDescrs().get(2);
        assertThat(eventC.getIdentifier()).isEqualTo("$c");
        assertThat(eventC.getObjectType()).isEqualTo("EventC");
        assertThat(eventC.getConstraint().getDescrs()).hasSize(1);
        final ExprConstraintDescr fcdC = (ExprConstraintDescr) eventC.getConstraint().getDescrs().get(0);
        assertThat(fcdC.getExpression()).isEqualTo("this finishes $b");

        final PatternDescr eventD = (PatternDescr) rule.getLhs().getDescrs().get(3);
        assertThat(eventD.getIdentifier()).isEqualTo("$d");
        assertThat(eventD.getObjectType()).isEqualTo("EventD");
        assertThat(eventD.getConstraint().getDescrs()).hasSize(1);
        final ExprConstraintDescr fcdD = (ExprConstraintDescr) eventD.getConstraint().getDescrs().get(0);
        assertThat(fcdD.getExpression()).isEqualTo("this not starts $a");

        final PatternDescr eventE = (PatternDescr) rule.getLhs().getDescrs().get(4);
        assertThat(eventE.getIdentifier()).isEqualTo("$e");
        assertThat(eventE.getObjectType()).isEqualTo("EventE");
        assertThat(eventE.getConstraint().getDescrs()).hasSize(1);

        ExprConstraintDescr fcdE = (ExprConstraintDescr) eventE.getConstraint().getDescrs().get(0);
        assertThat(fcdE.getExpression()).isEqualTo("this not before[1, 10] $b || after[1, 10] $c && this after[1, 5] $d");
    }

    @Test
    void ruleMetadata() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "Rule_with_Metadata.drl");

        // @fooAttribute(barValue)
        // @fooAtt2(barVal2)
        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getAnnotationNames()).contains("fooMeta1");
        assertThat(rule.getAnnotation("fooMeta1").getValue()).isEqualTo("barVal1");
        assertThat(rule.getAnnotationNames()).contains("fooMeta2");
        assertThat(rule.getAnnotation("fooMeta2").getValue()).isEqualTo("barVal2");
        assertThat((String) rule.getConsequence()).isEqualToIgnoringWhitespace("System.out.println(\"Consequence\");"
        );
    }

    @Test
    void ruleExtends() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "Rule_with_Extends.drl");

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getParentName() != null).isTrue();
        assertThat(rule.getParentName()).isEqualTo("rule1");

        AndDescr lhs = rule.getLhs();
        assertThat(lhs).isNotNull();
        assertThat(lhs.getDescrs()).hasSize(1);

        PatternDescr pattern = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("foo");
        assertThat(pattern.getIdentifier()).isEqualTo("$foo");
    }

    @Test
    void typeDeclarationWithFields() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "declare_type_with_fields.drl");

        List<TypeDeclarationDescr> td = pkg.getTypeDeclarations();
        assertThat(td).hasSize(3);

        TypeDeclarationDescr d = td.get(0);
        assertThat(d.getTypeName()).isEqualTo("SomeFact");
        assertThat(d.getFields()).hasSize(2);
        assertThat(d.getFields()).containsKey("name");
        assertThat(d.getFields()).containsKey("age");

        TypeFieldDescr f = d.getFields().get("name");
        assertThat(f.getPattern().getObjectType()).isEqualTo("String");

        f = d.getFields().get("age");
        assertThat(f.getPattern().getObjectType()).isEqualTo("Integer");

        d = td.get(1);
        assertThat(d.getTypeName()).isEqualTo("AnotherFact");

        TypeDeclarationDescr type = td.get(2);
        assertThat(type.getTypeName()).isEqualTo("Person");

        assertThat(type.getAnnotation("role").getValue()).isEqualTo("fact");
        assertThat(type.getAnnotation("doc").getValue("descr")).isEqualTo("\"Models a person\"");
        assertThat(type.getAnnotation("doc").getValue("author")).isEqualTo("\"Bob\"");
        assertThat(type.getAnnotation("doc").getValue("date")).isEqualTo("Calendar.getInstance().getDate()");

        assertThat(type.getFields()).hasSize(2);
        TypeFieldDescr field = type.getFields().get("name");
        assertThat(field.getFieldName()).isEqualTo("name");
        assertThat(field.getPattern().getObjectType()).isEqualTo("String");
        assertThat(field.getInitExpr()).isEqualTo("\"John Doe\"");
        assertThat(field.getAnnotation("length").getValue("max")).isEqualTo("50");
        assertThat(field.getAnnotation("key")).isNotNull();

        field = type.getFields().get("age");
        assertThat(field.getFieldName()).isEqualTo("age");
        assertThat(field.getPattern().getObjectType()).isEqualTo("int");
        assertThat(field.getInitExpr()).isEqualTo("-1");
        assertThat(field.getAnnotation("ranged").getValue("min")).isEqualTo("0");
        assertThat(field.getAnnotation("ranged").getValue("max")).isEqualTo("150");
        assertThat(field.getAnnotation("ranged").getValue("unknown")).isEqualTo("-1");
    }

    @Test
    void qualifiedTypeDeclaration() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "qualified_type_declaration.drl");

        TypeDeclarationDescr someFact = pkg.getTypeDeclarations().get(0);
        assertThat(someFact.getTypeName()).isEqualTo("SomeFact");
        assertThat(someFact.getNamespace()).isEqualTo("com.sample1");

        EnumDeclarationDescr color = pkg.getEnumDeclarations().get(0);
        assertThat(color.getTypeName()).isEqualTo("Color");
        assertThat(color.getNamespace()).isEqualTo("com.sample2");
    }

    @Test
    void parenthesesOneLevelNestWithThreeSiblings() {
        final PackageDescr pkg = parseAndGetPackageDescrFromFile("Rule_with_nested_LHS.drl");

        RuleDescr rule = pkg.getRules().get(0);
        assertThat(rule.getName()).isEqualTo("test");

        AndDescr lhs = rule.getLhs();
        assertThat(lhs).isNotNull();
        assertThat(lhs.getDescrs()).hasSize(2);

        PatternDescr a = (PatternDescr) lhs.getDescrs().get(0);
        assertThat(a.getObjectType()).isEqualTo("A");

        OrDescr or = (OrDescr) lhs.getDescrs().get(1);
        assertThat(or.getDescrs()).hasSize(3);

        AndDescr and1 = (AndDescr) or.getDescrs().get(0);
        assertThat(and1.getDescrs()).hasSize(2);
        PatternDescr b = (PatternDescr) and1.getDescrs().get(0);
        PatternDescr c = (PatternDescr) and1.getDescrs().get(1);
        assertThat(b.getObjectType()).isEqualTo("B");
        assertThat(c.getObjectType()).isEqualTo("C");

        AndDescr and2 = (AndDescr) or.getDescrs().get(1);
        assertThat(and2.getDescrs()).hasSize(2);
        PatternDescr d = (PatternDescr) and2.getDescrs().get(0);
        PatternDescr e = (PatternDescr) and2.getDescrs().get(1);
        assertThat(d.getObjectType()).isEqualTo("D");
        assertThat(e.getObjectType()).isEqualTo("E");

        AndDescr and3 = (AndDescr) or.getDescrs().get(2);
        assertThat(and3.getDescrs()).hasSize(2);
        PatternDescr f = (PatternDescr) and3.getDescrs().get(0);
        PatternDescr g = (PatternDescr) and3.getDescrs().get(1);
        assertThat(f.getObjectType()).isEqualTo("F");
        assertThat(g.getObjectType()).isEqualTo("G");
    }

    @Test
    void entryPoint() {
        final String text = "rule X when StockTick( symbol==\"ACME\") from entry-point StreamA then end";

        PackageDescr pkg = parseAndGetPackageDescr(
                text);

        RuleDescr rule = pkg.getRules().get(0);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("symbol==\"ACME\"");

        assertThat(pattern.getSource()).isNotNull();
        EntryPointDescr entry = (EntryPointDescr) pattern.getSource();
        assertThat(entry.getEntryId()).isEqualTo("StreamA");
    }

    @Test
    void entryPoint2() {
        final String text = "rule X when StockTick( symbol==\"ACME\") from entry-point \"StreamA\" then end";

        PackageDescr pkg = parseAndGetPackageDescr(
                text);

        RuleDescr rule = pkg.getRules().get(0);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("symbol==\"ACME\"");

        assertThat(pattern.getSource()).isNotNull();
        EntryPointDescr entry = (EntryPointDescr) pattern.getSource();
        assertThat(entry.getEntryId()).isEqualTo("StreamA");
    }

    @Test
    void slidingWindow() {
        final String text = "rule X when StockTick( symbol==\"ACME\") over window:length(10) then end";

        PackageDescr pkg = parseAndGetPackageDescr(text);

        RuleDescr rule = pkg.getRules().get(0);
        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("symbol==\"ACME\"");

        List<BehaviorDescr> behaviors = pattern.getBehaviors();
        assertThat(behaviors).isNotNull();
        assertThat(behaviors).hasSize(1);
        BehaviorDescr descr = behaviors.get(0);
        assertThat(descr.getType()).isEqualTo("window");
        assertThat(descr.getSubType()).isEqualTo("length");
        assertThat(descr.getParameters().get(0)).isEqualTo("10");
    }

    @Test
    void ruleOldSyntax1() {
        final String source = "rule \"Test\" when ( not $r :LiteralRestriction( operator == Operator.EQUAL ) ) then end";

        PackageDescr pkg = parseAndGetPackageDescr(
                source);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        assertThat(rule.getName()).isEqualTo("Test");
        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        assertThat(((NotDescr) rule.getLhs().getDescrs().get(0)).getDescrs()).hasSize(1);
        NotDescr notDescr = (NotDescr) rule.getLhs().getDescrs().get(0);
        PatternDescr patternDescr = (PatternDescr) notDescr.getDescrs().get(0);
        assertThat(patternDescr.getIdentifier()).isEqualTo("$r");
        assertThat(patternDescr.getDescrs()).hasSize(1);
        ExprConstraintDescr fieldConstraintDescr = (ExprConstraintDescr) patternDescr.getDescrs().get(0);
        assertThat(fieldConstraintDescr.getExpression()).isEqualToIgnoringWhitespace("operator == Operator.EQUAL");
    }

    @Test
    void ruleOldSyntax2() {
        final String source = "rule \"Test\" when ( $r :LiteralRestriction( operator == Operator.EQUAL ) ) then end";

        PackageDescr pkg = parseAndGetPackageDescr(
                source);

        RuleDescr rule = (RuleDescr) pkg.getRules().get(0);

        assertThat(rule.getName()).isEqualTo("Test");
        assertThat(rule.getLhs().getDescrs()).hasSize(1);
        PatternDescr patternDescr = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(patternDescr.getIdentifier()).isEqualTo("$r");
        assertThat(patternDescr.getDescrs()).hasSize(1);
        ExprConstraintDescr fieldConstraintDescr = (ExprConstraintDescr) patternDescr.getDescrs().get(0);
        assertThat(fieldConstraintDescr.getExpression()).isEqualToIgnoringWhitespace("operator == Operator.EQUAL");
    }

    @Test
    void typeWithMetaData() {

        PackageDescr pkg = parseAndGetPackageDescrFromFile(
                "type_with_meta.drl");

        final List<TypeDeclarationDescr> declarations = pkg.getTypeDeclarations();

        assertThat(declarations).hasSize(3);
    }

    @Test
    void nullConstraints() {
        final String text = "rule X when Person( name == null ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(1);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("name == null");
        assertThat(fcd.getPosition()).isEqualTo(0);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.NAMED);
    }

    @Test
    void positionalConstraintsOnly() {
        final String text = "rule X when Person( \"Mark\", 42; ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(2);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("\"Mark\"");
        assertThat(fcd.getPosition()).isEqualTo(0);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
        fcd = (ExprConstraintDescr) pattern.getDescrs().get(1);
        assertThat(fcd.getExpression()).isEqualTo("42");
        assertThat(fcd.getPosition()).isEqualTo(1);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
    }

    @Test
    void isQuery() {
        final String text = "rule X when ?person( \"Mark\", 42; ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.isQuery()).isTrue();

        assertThat(pattern.getDescrs()).hasSize(2);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("\"Mark\"");
        assertThat(fcd.getPosition()).isEqualTo(0);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
        fcd = (ExprConstraintDescr) pattern.getDescrs().get(1);
        assertThat(fcd.getExpression()).isEqualTo("42");
        assertThat(fcd.getPosition()).isEqualTo(1);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
    }

    @Test
    void fromFollowedByQuery() {
        // the 'from' expression requires a ";" to disambiguate the "?"
        // prefix for queries from the ternary operator "? :"
        final String text = "rule X when Cheese() from $cheesery ?person( \"Mark\", 42; ) then end";
        RuleDescr rule = parseAndGetFirstRuleDescr(
                text);

        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
        assertThat(pattern.getSource().getText()).isEqualTo("from $cheesery");
        assertThat(pattern.isQuery()).isFalse();

        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("person");
        assertThat(pattern.isQuery()).isTrue();
    }

    @Test
    void fromWithTernaryFollowedByQuery() {
        // the 'from' expression requires a ";" to disambiguate the "?"
        // prefix for queries from the ternary operator "? :"
        final String text = "rule X when Cheese() from (isFull ? $cheesery : $market) ?person( \"Mark\", 42; ) then end";
        RuleDescr rule = parseAndGetFirstRuleDescr(
                text);

        PatternDescr pattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        assertThat(pattern.getObjectType()).isEqualTo("Cheese");
        assertThat(pattern.getSource().getText()).isEqualToIgnoringWhitespace("from (isFull ? $cheesery : $market)");
        assertThat(pattern.isQuery()).isFalse();

        pattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        assertThat(pattern.getObjectType()).isEqualTo("person");
        assertThat(pattern.isQuery()).isTrue();
    }

    @Test
    void multiValueAnnotationsBackwardCompatibility() {
        // multiple values with no keys are parsed as a single value
        final String text = "rule X @ann1( val1, val2 ) @ann2( \"val1\", \"val2\" ) when then end";
        RuleDescr rule = parseAndGetFirstRuleDescr(
                text);

        AnnotationDescr ann = rule.getAnnotation("ann1");
        assertThat(ann).isNotNull();
        assertThat(ann.getValue()).isEqualTo("val1, val2");

        ann = rule.getAnnotation("ann2");
        assertThat(ann).isNotNull();
        assertThat(ann.getValue()).isEqualTo("\"val1\", \"val2\"");
        assertThat(ann.getSingleValueAsString()).isEqualTo("\"val1\", \"val2\"");
    }

    @Test
    void positionalsAndNamedConstraints() {
        final String text = "rule X when Person( \"Mark\", 42; location == \"atlanta\" ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(3);
        ExprConstraintDescr fcd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(fcd.getExpression()).isEqualTo("\"Mark\"");
        assertThat(fcd.getPosition()).isEqualTo(0);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
        fcd = (ExprConstraintDescr) pattern.getDescrs().get(1);
        assertThat(fcd.getExpression()).isEqualTo("42");
        assertThat(fcd.getPosition()).isEqualTo(1);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);

        fcd = (ExprConstraintDescr) pattern.getDescrs().get(2);
        assertThat(fcd.getExpression()).isEqualTo("location == \"atlanta\"");
        assertThat(fcd.getPosition()).isEqualTo(2);
        assertThat(fcd.getType()).isEqualTo(ExprConstraintDescr.Type.NAMED);
    }

    @Test
    void unificationBinding() {
        final String text = "rule X when $p := Person( $name := name, $loc : location ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getIdentifier()).isEqualTo("$p");
        assertThat(pattern.isUnification()).isTrue();

        assertThat(pattern.getDescrs()).hasSize(2);
        ExprConstraintDescr bindingDescr = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(bindingDescr.getExpression()).isEqualTo("$name := name");

        bindingDescr = (ExprConstraintDescr) pattern.getDescrs().get(1);
        assertThat(bindingDescr.getExpression()).isEqualTo("$loc : location");
    }

    @Test
    void bigLiterals() {
        final String text = "rule X when Primitives( bigInteger == (10I), " +
                "                        bigDecimal == (10B), " +
                "                        bigInteger < 50I, " +
                "                        bigDecimal < 50.2B ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getDescrs()).hasSize(4);
        ExprConstraintDescr ecd = (ExprConstraintDescr) pattern.getDescrs().get(0);
        assertThat(ecd.getExpression()).isEqualTo("bigInteger == (10I)");

        ecd = (ExprConstraintDescr) pattern.getDescrs().get(1);
        assertThat(ecd.getExpression()).isEqualTo("bigDecimal == (10B)");

        ecd = (ExprConstraintDescr) pattern.getDescrs().get(2);
        assertThat(ecd.getExpression()).isEqualTo("bigInteger < 50I");

        ecd = (ExprConstraintDescr) pattern.getDescrs().get(3);
        assertThat(ecd.getExpression()).isEqualTo("bigDecimal < 50.2B");
    }

    @Test
    void bindingComposite() {
        final String text = "rule X when Person( $name : name == \"Bob\" || $loc : location == \"Montreal\" ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.isUnification()).isFalse();

        // embedded bindings are extracted at compile time
        List<?> constraints = pattern.getDescrs();
        assertThat(constraints).hasSize(1);
        assertThat(((ExprConstraintDescr) constraints.get(0)).getExpression()).isEqualTo("$name : name == \"Bob\" || $loc : location == \"Montreal\"");
    }

    @Test
    void bindingCompositeWithMethods() {
        final String text = "rule X when Person( $name : name.toUpperCase() == \"Bob\" || $loc : location[0].city == \"Montreal\" ) then end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("Person");
        assertThat(pattern.isUnification()).isFalse();

        // embedded bindings are extracted at compile time
        List<?> constraints = pattern.getDescrs();
        assertThat(constraints).hasSize(1);
        assertThat(((ExprConstraintDescr) constraints.get(0)).getExpression()).isEqualTo("$name : name.toUpperCase() == \"Bob\" || $loc : location[0].city == \"Montreal\"");
    }

    @Test
    void pluggableOperators2() {
        final String text = "rule \"tt\"\n" +
                "    dialect \"mvel\"\n" +
                "when\n" +
                "    exists (TelephoneCall( this finishes [1m] \"25-May-2011\" ))\n" +
                "then\n" +
                "end";
        PatternDescr pattern = (PatternDescr) ((ExistsDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0)).getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("TelephoneCall");
        ExprConstraintDescr constr = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(constr.getText()).isEqualTo("this finishes [1m] \"25-May-2011\"");
    }

    @Test
    void inlineEval() {
        final String text = "rule \"inline eval\"\n" +
                "when\n" +
                "    Person( eval( name.startsWith(\"b\") && name.finishesWith(\"b\")) )\n" +
                "then\n" +
                "end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("Person");
        ExprConstraintDescr constr = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(constr.getText()).isEqualToIgnoringWhitespace("eval( name.startsWith(\"b\") && name.finishesWith(\"b\"))");
    }

    @Test
    void infinityLiteral() {
        final String text = "rule \"infinity\"\n" +
                "when\n" +
                "    StockTick( this after[-*,*] $another )\n" +
                "then\n" +
                "end";
        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(
                text).getLhs().getDescrs().get(0);

        assertThat(pattern.getObjectType()).isEqualTo("StockTick");
        ExprConstraintDescr constr = (ExprConstraintDescr) pattern.getConstraint().getDescrs().get(0);
        assertThat(constr.getText()).isEqualTo("this after[-*,*] $another");
    }

    static Stream<Arguments> entryPointIds() {
        return Stream.of(
                Arguments.of("eventStream", "eventStream"),
                Arguments.of("\"My entry-point 'ID'\"", "My entry-point 'ID'")
        );
    }

    @ParameterizedTest
    @MethodSource("entryPointIds")
    void parse_EntryPointDeclaration(String sourceId, String expectedId) {
        final String text = "package org.drools\n" +
                "declare entry-point " + sourceId + "\n" +
                "    @source(\"jndi://queues/events\")\n" +
                "    @foo( true )\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(
                text);

        assertThat(pkg.getName()).isEqualTo("org.drools");
        assertThat(pkg.getEntryPointDeclarations()).hasSize(1);

        EntryPointDeclarationDescr epd = pkg.getEntryPointDeclarations().iterator().next();

        assertThat(epd.getEntryPointId()).isEqualTo(expectedId);
        assertThat(epd.getAnnotations()).hasSize(2);
        assertThat(epd.getAnnotation("source").getValue()).isEqualTo("\"jndi://queues/events\"");
        assertThat(epd.getAnnotation("foo").getValue()).isEqualTo("true");
    }

    @Test
    void windowDeclaration() {
        final String text = "package org.drools\n" +
                "declare window Ticks\n" +
                "    @doc(\"last 10 stock ticks\")\n" +
                "    $s : StockTick( source == \"NYSE\" )\n" +
                "        over window:length( 10, $s.symbol )\n" +
                "        from entry-point stStream\n" +
                "end";
        PackageDescr pkg = parseAndGetPackageDescr(
                text);

        assertThat(pkg.getName()).isEqualTo("org.drools");
        assertThat(pkg.getWindowDeclarations()).hasSize(1);

        WindowDeclarationDescr wdd = pkg.getWindowDeclarations().iterator().next();

        assertThat(wdd.getName()).isEqualTo("Ticks");
        assertThat(wdd.getAnnotations()).hasSize(1);
        assertThat(wdd.getAnnotation("doc").getValue()).isEqualTo("\"last 10 stock ticks\"");

        PatternDescr pd = wdd.getPattern();
        assertThat(pd).isNotNull();
        assertThat(pd.getIdentifier()).isEqualTo("$s");
        assertThat(pd.getObjectType()).isEqualTo("StockTick");
        assertThat(pd.getSource().getText()).isEqualTo("stStream");

        assertThat(pd.getBehaviors()).hasSize(1);
        BehaviorDescr bd = pd.getBehaviors().get(0);
        assertThat(bd.getType()).isEqualTo("window");
        assertThat(bd.getSubType()).isEqualTo("length");
        assertThat(bd.getParameters()).hasSize(2);
        assertThat(bd.getParameters().get(0)).isEqualTo("10");
        assertThat(bd.getParameters().get(1)).isEqualTo("$s.symbol");
    }

    @Test
    void windowUsage() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    StockTick() from window Y\n" +
                "then\n" +
                "end\n";
        PackageDescr pkg = parseAndGetPackageDescr(
                text);

        assertThat(pkg.getName()).isEqualTo("org.drools");
        assertThat(pkg.getRules()).hasSize(1);

        RuleDescr rd = pkg.getRules().get(0);

        assertThat(rd.getName()).isEqualTo("X");
        assertThat(rd.getLhs().getDescrs()).hasSize(1);

        PatternDescr pd = (PatternDescr) rd.getLhs().getDescrs().get(0);
        assertThat(pd).isNotNull();
        assertThat(pd.getObjectType()).isEqualTo("StockTick");
        assertThat(pd.getSource().getText()).isEqualTo("Y");
    }

    @Test
    void endInRhs() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $s : String()\n" +
                "then\n" +
                "    System.out.println($s.endsWith(\"xyz\"));\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("System.out.println($s.endsWith(\"xyz\"));");
    }

    @Test
    void endTokenInRhs() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $s : String()\n" +
                "then\n" +
                "    int end = 10;\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("int end = 10;");
    }

    @Test
    void ruleTokenInRhs() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $s : String()\n" +
                "then\n" +
                "    int rule = 10;\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("int rule = 10;");
    }

    @Test
    void semicolonEnd() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $s : String()\n" +
                "then\n" +
                "    delete($s);end\n"; // no space after semicolon
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("delete($s);");
    }

    @Test
    void braceEnd() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    modify($p) { setAge(2) }end\n"; // no space after right brace
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("modify($p) { setAge(2) }");
    }

    @Test
    void parenthesisEnd() {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p)end\n"; // no space after right parenthesis
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getConsequence().toString()).isEqualToIgnoringWhitespace("retract($p)");
    }

    @Test
    void endAndRuleInSameLine() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p)\n" +
                "end rule R2 when Person() then end"; // 'rule' after 'end' in the same line
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndAttributeAndRuleInSameLine() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p)\n" +
                "end no-loop true rule R2 when Person() then end"; // 'end', attribute, 'rule' in the same line
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
        assertThat(packageDescr.getAttributes().get(0).getName()).isEqualTo("no-loop"); // package level attribute
    }

    @Test
    void endAndSingleLineCommentAndRule() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p)\n" +
                "end\n" +
                "// comment\n" +
                "rule R2 when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndMultiLineCommentAndRule() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p)\n" +
                "end\n" +
                "/* comment\n" +
                "comment */\n" +
                "rule R2 when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndNonPairingDoubleQuoteInSingleLineCommentInRHS() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    //System.out.println(\");\n" + // non-pairing double quote in comment
                "    retract($p)\n" +
                "end\n" +
                "rule \"R2\" when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndNonPairingDoubleQuoteInMultiLineCommentInRHS() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    /*System.out.println\n" +
                "          (\");*/\n" + // non-pairing double quote in comment
                "    retract($p)\n" +
                "end\n" +
                "rule \"R2\" when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndDoubleQuotationsInRHS() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    System.out.println(\"Draw \"+$p1+\" \"+$p2);\n" +
                "    retract($p)\n" +
                "end\n" +
                "rule \"R2\" when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void endAndSingleQuotationsInRHS() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    System.out.println('Draw '+$p1+' '+$p2);\n" +
                "    retract($p)\n" +
                "end\n" +
                "rule \"R2\" when Person() then end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        List<RuleDescr> ruleDescrList = packageDescr.getRules();
        assertThat(ruleDescrList).hasSize(2);
        assertThat(ruleDescrList.get(0).getName()).isEqualTo("R1");
        assertThat(ruleDescrList.get(1).getName()).isEqualTo("R2");
    }

    @Test
    void singleQuoteInRhsWithSpace() {
        String consequence = getResultConsequence("    System.out.println( 'singleQuoteInRhs' );\n");
        assertThat(consequence)
                .as("Single quote should be converted to double quote")
                .isEqualToIgnoringWhitespace("System.out.println( \"singleQuoteInRhs\" );");
    }

    @Test
    void singleQuoteInRhsWithoutSpace() {
        String consequence = getResultConsequence("    System.out.println('singleQuoteInRhs');\n");
        assertThat(consequence)
                .as("Single quote should be converted to double quote")
                .isEqualToIgnoringWhitespace("System.out.println( \"singleQuoteInRhs\" );");
    }

    @Test
    void singleQuoteInDoubleQuoteInRhsWithoutSpace() {
        String consequence = getResultConsequence("    System.out.println(\"There is '\" + $s + \"' in the workspace.\");\n");
        assertThat(consequence)
                .as("Single quote should not be converted to double quote in case of inside double quotes")
                .isEqualToIgnoringWhitespace("System.out.println(\"There is '\" + $s + \"' in the workspace.\");");
    }

    private String getResultConsequence(String rhs) {
        final String text = "package org.drools\n" +
                "rule X\n" +
                "when\n" +
                "    $s : String()\n" +
                "then\n" +
                rhs +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        return ruleDescr.getConsequence().toString();
    }

    @Test
    void ruleDescrProperties() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    retract($p);\n" +
                "end\n";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);

        assertThat(ruleDescr.getName()).isEqualTo("R1");
        assertThat(ruleDescr.getNamespace()).isEqualTo("org.drools");
    }

    /**
     * Each test input is a constraint expression covering one of the existing DRL operators. The test is successful if the parser has
     * no errors and the descriptor's expression string is equal to the input.
     *
     * @param constraint expression using an operator
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "country matches \"[a-z]*\"",
            "country not matches \"[a-z]*\"",
            "person memberOf $europeanDescendants",
            "person not memberOf $europeanDescendants",
            "countries contains \"UK\"",
            "countries not contains \"UK\"",
            "countries excludes \"UK\"",
            "countries not excludes \"UK\"",
            "firstName soundslike \"John\"",
            "firstName not soundslike \"John\"",
            "routingValue str[startsWith] \"R1\"",
            "routingValue not str[startsWith] \"R1\""
    })
    void constraintOperators(String constraint) {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person(" + constraint + ")\n" +
                "then\n" +
                "end\n";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        AndDescr lhs = ruleDescr.getLhs();
        PatternDescr patternDescr = (PatternDescr) lhs.getDescrs().get(0);
        ExprConstraintDescr exprConstraintDescr = (ExprConstraintDescr) patternDescr.getConstraint().getDescrs().get(0);
        assertThat(exprConstraintDescr.getExpression()).isEqualToIgnoringWhitespace(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "country matches \"[a-z]*\" || matches \"[A-Z]*\"",
            "country not matches \"[a-z]*\" || not matches \"[A-Z]*\"",
            "person memberOf $europeanDescendants || memberOf $africanDescendants",
            "person not memberOf $europeanDescendants || not memberOf $africanDescendants",
            "countries contains \"UK\" || contains \"US\"",
            "countries not contains \"UK\" || not contains \"US\"",
            "countries excludes \"UK\" || excludes \"US\"",
            "countries not excludes \"UK\" || not excludes \"US\"",
            "firstName soundslike \"John\" || soundslike \"Paul\"",
            "firstName not soundslike \"John\" && not soundslike \"Paul\"",
            "routingValue str[startsWith] \"R1\" || str[startsWith] \"R2\"",
            "routingValue not str[startsWith] \"R1\" && not str[startsWith] \"R2\""
    })
    void halfConstraintOperators(String constraint) {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person(" + constraint + ")\n" +
                "then\n" +
                "end\n";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        AndDescr lhs = ruleDescr.getLhs();
        PatternDescr patternDescr = (PatternDescr) lhs.getDescrs().get(0);
        ExprConstraintDescr exprConstraintDescr = (ExprConstraintDescr) patternDescr.getConstraint().getDescrs().get(0);
        assertThat(exprConstraintDescr.getExpression()).isEqualToIgnoringWhitespace(constraint);
    }

    @Test
    void nullSafeDereferencing() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person(address!.city == $city)\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("address!.city == $city");
    }

    @Test
    void nullSafeDereferencingMethodCall() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person(address!.city!.startsWith(\"M\"))\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("address!.city!.startsWith(\"M\")");
    }

    @Test
    void nullSafeDereferencingMethodCallBindVariable() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person( $containsL : address!.city.contains(\"L\") )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("$containsL : address!.city.contains(\"L\")");
    }

    @Test
    void groupedConstraints() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person( address.(city.startsWith(\"I\") && city.length() == 5  ) )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .as("prefix should be appended to each element")
                .isEqualToIgnoringWhitespace("address.city.startsWith(\"I\") && address.city.length() == 5");
    }

    @Test
    void groupedConstraintsWithNullSafeDereferencing() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person( address!.(city!.startsWith(\"I\") && city!.length() == 5  ) )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .as("prefix should be appended to each element")
                .isEqualToIgnoringWhitespace("address!.city!.startsWith(\"I\") && address!.city!.length() == 5");
    }

    @Test
    void functionWithStringLiteral() {
        final String text = "function String star(String s) { return \"*\"; }";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        FunctionDescr function = packageDescr.getFunctions().get(0);

        assertThat(function.getName()).isEqualTo("star");
        assertThat(function.getReturnType()).isEqualTo("String");
        assertThat(function.getParameterTypes().get(0)).isEqualTo("String");
        assertThat(function.getParameterNames().get(0)).isEqualTo("s");
        assertThat(function.getBody()).isEqualToIgnoringWhitespace("return \"*\";");
    }

    @Test
    void functionWithStringLiteralAddition() {
        final String text = "function String addStar(String s) { return s + \"*\"; }";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        FunctionDescr function = packageDescr.getFunctions().get(0);

        assertThat(function.getName()).isEqualTo("addStar");
        assertThat(function.getReturnType()).isEqualTo("String");
        assertThat(function.getParameterTypes().get(0)).isEqualTo("String");
        assertThat(function.getParameterNames().get(0)).isEqualTo("s");
        assertThat(function.getBody()).isEqualToIgnoringWhitespace("return s + \"*\";");
    }

    @Test
    void functionWithMultipleBlockStatements() {
        final String text = "function String star(String s) {\n" +
                "    String result = s + \"*\";\n" +
                "    return result;\n" +
                "}";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        FunctionDescr function = packageDescr.getFunctions().get(0);

        assertThat(function.getName()).isEqualTo("star");
        assertThat(function.getReturnType()).isEqualTo("String");
        assertThat(function.getParameterTypes().get(0)).isEqualTo("String");
        assertThat(function.getParameterNames().get(0)).isEqualTo("s");
        assertThat(function.getBody()).isEqualToIgnoringWhitespace("String result = s + \"*\"; return result;");
    }

    @Test
    void lhsPatternAnnotation() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person( name == \"Mario\" ) @watch(!*, age)\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        PatternDescr patternDescr = (PatternDescr) ruleDescr.getLhs().getDescrs().get(0);
        AnnotationDescr annotationDescr = patternDescr.getAnnotations().iterator().next();

        assertThat(annotationDescr.getName()).isEqualTo("watch");
        assertThat(annotationDescr.getValue()).isEqualTo("!*, age");
        assertThat(annotationDescr.getSingleValueAsString()).isEqualTo("!*, age");
    }

    @Test
    void prefixAndDescrAnnotation() {
        final String text =
                "rule R\n" +
                        "    when\n" +
                        "       ( and @Annot \n" +
                        "         String() \n" +
                        "         Integer() ) \n" +
                        "    then\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        AndDescr andDescr = ruleDescr.getLhs();
        AnnotationDescr annotationDescr = andDescr.getAnnotations().iterator().next();
        assertThat(annotationDescr.getName()).isEqualTo("Annot");
        assertThat(annotationDescr.hasValue()).isFalse();

        assertThat(andDescr.getDescrs()).hasSize(2);
    }

    @Test
    void prefixOrDescrAnnotation() {
        final String text =
                "rule R\n" +
                        "    when\n" +
                        "       ( or @Annot \n" +
                        "         String() \n" +
                        "         Integer() ) \n" +
                        "    then\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        OrDescr orDescr = (OrDescr) ruleDescr.getLhs().getDescrs().get(0);
        AnnotationDescr annotationDescr = orDescr.getAnnotations().iterator().next();

        assertThat(annotationDescr.getName()).isEqualTo("Annot");
        assertThat(annotationDescr.hasValue()).isFalse();

        assertThat(orDescr.getDescrs()).hasSize(2);
    }

    @Test
    void infixAndDescrAnnotation() {
        final String text =
                "rule R\n" +
                        "    when\n" +
                        "       ( Double() \n" +
                        "         and @Annot1 String() \n" +
                        "         and @Annot2 Integer() ) " +
                        "    then\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        AndDescr andDescr = ruleDescr.getLhs();
        Collection<AnnotationDescr> annotationDescrs = andDescr.getAnnotations();
        assertThat(annotationDescrs).extracting(AnnotationDescr::getName).containsExactlyInAnyOrder("Annot1", "Annot2");

        assertThat(andDescr.getDescrs()).hasSize(3);
    }

    @Test
    void infixOrDescrAnnotation() {
        final String text =
                "rule R\n" +
                        "    when\n" +
                        "       ( Double() \n" +
                        "         or @Annot1 String() \n" +
                        "         or @Annot2 Integer() ) " +
                        "    then\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        OrDescr orDescr = (OrDescr) ruleDescr.getLhs().getDescrs().get(0);
        Collection<AnnotationDescr> annotationDescrs = orDescr.getAnnotations();
        assertThat(annotationDescrs).extracting(AnnotationDescr::getName).containsExactlyInAnyOrder("Annot1", "Annot2");

        assertThat(orDescr.getDescrs()).hasSize(3);
    }

    @Test
    void annotationWithEmptyParentheses() {
        final String text = "package org.drools;\n" +
                "import java.util.*;\n" +
                "import org.drools.base.factmodel.traits.*;\n" +
                "declare HashMap @Traitable() end \n" +
                "declare trait PersonMap\n" +
                "@propertyReactive  \n" +
                "   age  : Integer  @Alias( \"years\" ) \n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        AnnotationDescr annotationDescr = packageDescr.getTypeDeclarations().get(0).getAnnotations().iterator().next();

        assertThat(annotationDescr.getName()).isEqualTo("Traitable");
        assertThat(annotationDescr.getValue()).isEqualTo(Collections.emptyMap());
        assertThat(annotationDescr.getSingleValueAsString()).isNull();
    }

    @Test
    void fromNew() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "    $p : Person() from new Person(\"John\", 30)\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        PatternDescr patternDescr = (PatternDescr) ruleDescr.getLhs().getDescrs().get(0);
        FromDescr fromDescr = (FromDescr) patternDescr.getSource();
        assertThat(fromDescr.getDataSource().toString()).isEqualTo("new Person(\"John\", 30)");
    }

    @Test
    void expiresWithTimeLiteralValue() {
        String text = "package org.drools\n" +
                "declare StockFact\n" +
                "    @role( value = event )\n" +
                "    @expires( value = 2s, policy = TIME_SOFT )\n" +
                "end";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);

        AnnotationDescr annotationDescr = packageDescr
                .getTypeDeclarations().get(0)
                .getAnnotation("expires");

        assertThat(annotationDescr.getSingleValueAsString()).isEqualTo("2s");
        assertThat(annotationDescr.getValueAsString("value")).isEqualTo("2s");
        assertThat(annotationDescr.getValueAsString("policy")).isEqualTo("TIME_SOFT");
    }

    @Test
    void ooPath() {
        final String text = "package org.drools\n" +
                "rule R when\n" +
                " $man: Man( /wife/children[age > 10] )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .isEqualToIgnoringWhitespace("/wife/children[age > 10]");
    }

    private static ExprConstraintDescr getFirstExprConstraintDescr(PackageDescr packageDescr) {
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        PatternDescr patternDescr = (PatternDescr) ruleDescr.getLhs().getDescrs().get(0);
        ExprConstraintDescr constraintDescr = (ExprConstraintDescr) patternDescr.getConstraint().getDescrs().get(0);
        return constraintDescr;
    }

    @Test
    void ooPathWithBindingInBrackets() {
        final String text = "package org.drools\n" +
                "rule R when\n" +
                " Man( /wife[$age : age] )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .isEqualToIgnoringWhitespace("/wife[$age : age]");
    }

    @Test
    void ooPathWithBindingInParentheses() {
        final String text = "package org.drools\n" +
                "rule R when\n" +
                " Man( $toy: /wife/children[age > 10]/toys )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .isEqualToIgnoringWhitespace("$toy: /wife/children[age > 10]/toys");
    }

    @Test
    void ooPathWithBackReference() {
        final String text = "package org.drools\n" +
                "rule R when\n" +
                "  Man( $toy: /wife/children/toys[ name.length == ../name.length ] )\n" +
                "then\n" +
                "end\n";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .isEqualToIgnoringWhitespace("$toy: /wife/children/toys[ name.length == ../name.length ]");
    }

    @Test
    void ooPathMixedWithStandardConstraint() {
        final String text = "package org.drools\n" +
                "rule R when\n" +
                "  Man( /wife[$age : age] && age > $age )\n" +
                "then\n" +
                "end\n";

        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString())
                .isEqualToIgnoringWhitespace("/wife[$age : age] && age > $age");
    }

    @Test
    void ooPathLhsPattern() {
        final String text = "package org.drools\n"
                + "rule PlainNot when\n"
                + "    not( /strings [ this == \"It Does Work\" ] )\n"
                + "then\n"
                + "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(NotDescr.class, notDescr -> {
            assertThat(notDescr.getDescrs()).hasSize(1);
            assertThat(notDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getConstraint().getDescrs()).hasSize(1);
                assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                    assertThat(exprConstraintDescr.getExpression()).isEqualTo("/strings [ this == \"It Does Work\" ]");
                    assertThat(exprConstraintDescr.getType()).isEqualTo(ExprConstraintDescr.Type.NAMED);
                    assertThat(exprConstraintDescr.getPosition()).isEqualTo(0);
                });
            });
        });
    }

    @Test
    void inlineCast() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "  $a : ICA( someB#ICB.onlyConcrete() == \"Hello\" )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("someB#ICB.onlyConcrete() == \"Hello\"");
    }

    @Test
    void inlineCastMultiple() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "  $a : ICA( someB#ICB.someC#ICC.onlyConcrete() == \"Hello\" )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("someB#ICB.someC#ICC.onlyConcrete() == \"Hello\"");
    }

    @Test
    void inlineCastThis() {
        final String text = "package org.drools\n" +
                "rule R1\n" +
                "when\n" +
                "  $o : Object( this#Person.name == \"Mark\" )\n" +
                "then\n" +
                "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        ExprConstraintDescr constraintDescr = getFirstExprConstraintDescr(packageDescr);
        assertThat(constraintDescr.toString()).isEqualToIgnoringWhitespace("this#Person.name == \"Mark\"");
    }

    @Test
    void queryArgumentWithoutType() {
        final String text = "package org.drools\n" +
                "query olderThan( $age )\n" +
                "    $p : Person(age > (Integer)$age)\n" +
                "end ";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("olderThan");
        assertThat(query.getParameterTypes()).containsExactly("Object");
        assertThat(query.getParameters()).containsExactly("$age");
    }

    @Test
    void queryMultipleArguments() {
        final String text = "package org.drools\n" +
                "query olderThan( String $name, int $age )\n" +
                "    $p : Person(age > $age)\n" +
                "end ";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("olderThan");
        assertThat(query.getParameterTypes()).containsExactly("String", "int");
        assertThat(query.getParameters()).containsExactly("$name", "$age");
    }

    @Test
    void queryArrayArgument() {
        final String text = "package org.drools\n" +
                "query olderThan( int[] $ages )\n" +
                "    $p : Person(age > $ages[0])\n" +
                "end ";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("olderThan");
        assertThat(query.getParameterTypes()).containsExactly("int[]");
        assertThat(query.getParameters()).containsExactly("$ages");
    }

    @Test
    void queryZeroArgument() {
        final String text = "package org.drools\n" +
                "query olderThan()\n" +
                "    $p : Person()\n" +
                "end ";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("olderThan");
        assertThat(query.getParameterTypes()).isEmpty();
        assertThat(query.getParameters()).isEmpty();
    }

    @Test
    void queryNoArgument() {
        final String text = "package org.drools\n" +
                "query olderThan\n" +
                "    $p : Person()\n" +
                "end ";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("olderThan");
        assertThat(query.getParameterTypes()).isEmpty();
        assertThat(query.getParameters()).isEmpty();
    }

    @Test
    void traitExtendsMultiple() {
        final String source = "declare trait FatherTrait extends com.sample.ParentTrait, UncleTrait, org.test.GrandParentTrait end";

        PackageDescr pkg = parseAndGetPackageDescr(source);

        final List<TypeDeclarationDescr> declarations = pkg.getTypeDeclarations();

        assertThat(declarations).hasSize(1);
        TypeDeclarationDescr trait = declarations.get(0);
        assertThat(trait.getSuperTypeName()).isEqualTo("ParentTrait");
        assertThat(trait.getSuperTypeNamespace()).isEqualTo("com.sample");
        assertThat(trait.getSuperTypes())
                .map(QualifiedName::getFullName)
                .containsExactlyInAnyOrder("com.sample.ParentTrait", "UncleTrait", "org.test.GrandParentTrait");
    }

    @EnabledIf("isOldParser")
    @Test
    void pluggableEvaluatorOldParser() {
        final String source = "package org.drools\n" +
                "rule R\n" +
                "when\n" +
                "   $t : Thing( $c : core, this not isA t.x.E.class, this isA t.x.D.class )\n" +
                "then\n" +
                "   list.add( \"E\" ); \n" +
                "   don( $t, E.class ); \n" +
                "end\n";

        Operator.addOperatorToRegistry("isA", false);
        Operator.addOperatorToRegistry("isA", true);

        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(source).getLhs().getDescrs().get(0);
        assertThat(pattern.getConstraint().getDescrs())
                .extracting(Object::toString)
                .containsExactly("$c : core", "this not isA t.x.E.class", "this isA t.x.D.class");
    }

    @EnabledIf("isNewParser")
    @Test
    void pluggableEvaluatorNewParser() {
        final String source = "package org.drools\n" +
                "rule R\n" +
                "when\n" +
                "   $t : Thing( $c : core, this not ##isA t.x.E.class, this ##isA t.x.D.class )\n" +
                "then\n" +
                "   list.add( \"E\" ); \n" +
                "   don( $t, E.class ); \n" +
                "end\n";

        Operator.addOperatorToRegistry("isA", false);
        Operator.addOperatorToRegistry("isA", true);

        PatternDescr pattern = (PatternDescr) parseAndGetFirstRuleDescr(source).getLhs().getDescrs().get(0);
        assertThat(pattern.getConstraint().getDescrs())
                .extracting(Object::toString)
                .containsExactly("$c : core", "this not ##isA t.x.E.class", "this ##isA t.x.D.class");
    }

    @Test
    void namedConsequenceDo() {
        final String text =
                "rule R when\n" +
                        "  $r : Result()\n" +
                        "  $p1 : Person(name == \"Mark\")\n" +
                        "  do[FoundMark]\n" +
                        "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                        "then\n" +
                        "  $r.addValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                        "then[FoundMark]\n" +
                        "  $r.addValue(\"Found \" + $p1.getName());\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        NamedConsequenceDescr namedConsequenceDescr = (NamedConsequenceDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(namedConsequenceDescr.getName()).isEqualTo("FoundMark");

        assertThat(ruleDescr.getConsequence().toString().trim()).isEqualTo("$r.addValue($p2.getName() + \" is older than \" + $p1.getName());");
        assertThat(ruleDescr.getNamedConsequences().get("FoundMark").toString().trim()).isEqualTo("$r.addValue(\"Found \" + $p1.getName());");
    }

    @Test
    void namedConsequenceIfElse() {
        final String text =
                "rule R1 dialect \"mvel\" when\n" +
                        "    $a: Cheese ( type == \"stilton\" )\n" +
                        "    if ( $a.price > Cheese.BASE_PRICE ) do[t1] else do[t2]\n" +
                        "    $b: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $b.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $a.getType() );\n" +
                        "then[t2]\n" +
                        "    results.add( $a.getType().toUpperCase() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        ConditionalBranchDescr conditionalBranchDescr = (ConditionalBranchDescr) ruleDescr.getLhs().getDescrs().get(1);
        assertThat(conditionalBranchDescr.getCondition().getContent().toString()).isEqualTo("$a.price > Cheese.BASE_PRICE");
        assertThat(conditionalBranchDescr.getConsequence().getName()).isEqualTo("t1");
        assertThat(conditionalBranchDescr.getElseBranch().getConsequence().getName()).isEqualTo("t2");
    }

    @Test
    void namedConsequenceIfElseBreak() {
        final String text =
                "rule R1 dialect \"mvel\" when\n" +
                        "    $a: Cheese ( type == \"stilton\" )\n" +
                        "    if ( $a.price > Cheese.BASE_PRICE ) break[t1] else break[t2]\n" +
                        "    $b: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $b.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $a.getType() );\n" +
                        "then[t2]\n" +
                        "    results.add( $a.getType().toUpperCase() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        ConditionalBranchDescr conditionalBranchDescr = (ConditionalBranchDescr) ruleDescr.getLhs().getDescrs().get(1);
        assertThat(conditionalBranchDescr.getCondition().getContent().toString()).isEqualTo("$a.price > Cheese.BASE_PRICE");
        assertThat(conditionalBranchDescr.getConsequence().getName()).isEqualTo("t1");
        assertThat(conditionalBranchDescr.getElseBranch().getConsequence().getName()).isEqualTo("t2");
    }

    @Test
    void namedConsequenceNestedIf() {
        final String text =
                "rule R1 dialect \"mvel\" when\n" +
                        "    $a: Cheese ( type == \"stilton\" )\n" +
                        "    if ( $a.price > Cheese.BASE_PRICE ) do[t1] else if ($a.price == Cheese.BASE_PRICE ) do[t2]\n" +
                        "    $b: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $b.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $a.getType() );\n" +
                        "then[t2]\n" +
                        "    results.add( $a.getType().toUpperCase() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        ConditionalBranchDescr conditionalBranchDescr = (ConditionalBranchDescr) ruleDescr.getLhs().getDescrs().get(1);
        assertThat(conditionalBranchDescr.getCondition().getContent().toString()).isEqualTo("$a.price > Cheese.BASE_PRICE");
        assertThat(conditionalBranchDescr.getConsequence().getName()).isEqualTo("t1");
        ConditionalBranchDescr elseBranch = conditionalBranchDescr.getElseBranch();
        assertThat(elseBranch.getCondition().getContent().toString()).isEqualTo("$a.price == Cheese.BASE_PRICE");
        assertThat(elseBranch.getConsequence().getName()).isEqualTo("t2");
    }

    @Test
    void namedConsequenceAfterExists() {
        final String text =
                "rule R when\n" +
                        "  $r : Result()\n" +
                        "  exists( Person(name == \"Mark\") )\n" +
                        "  do[FoundMark]\n" +
                        "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                        "then\n" +
                        "  $r.addValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                        "then[FoundMark]\n" +
                        "  $r.addValue(\"Found \" + $p1.getName());\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        NamedConsequenceDescr namedConsequenceDescr = (NamedConsequenceDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(namedConsequenceDescr.getName()).isEqualTo("FoundMark");
    }

    @Test
    void namedConsequenceAfterNot() {
        final String text =
                "rule R when\n" +
                        "  $r : Result()\n" +
                        "  not( Person(name == \"Mark\") )\n" +
                        "  do[NotFoundMark]\n" +
                        "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                        "then\n" +
                        "  $r.addValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                        "then[FoundMark]\n" +
                        "  $r.addValue(\"NotFound \" + $p1.getName());\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        NamedConsequenceDescr namedConsequenceDescr = (NamedConsequenceDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(namedConsequenceDescr.getName()).isEqualTo("NotFoundMark");
    }

    @Test
    void namedConsequenceIfElseAfterEval() {
        final String text =
                "rule R1 dialect \"mvel\" when\n" +
                        "    $a: Cheese ( type == \"stilton\" )\n" +
                        "    eval($a.price > 0) if ( $a.price > Cheese.BASE_PRICE ) do[t1] else do[t2]\n" +
                        "    $b: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $b.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $a.getType() );\n" +
                        "then[t2]\n" +
                        "    results.add( $a.getType().toUpperCase() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        ConditionalBranchDescr conditionalBranchDescr = (ConditionalBranchDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(conditionalBranchDescr.getCondition().getContent().toString()).isEqualTo("$a.price > Cheese.BASE_PRICE");
        assertThat(conditionalBranchDescr.getConsequence().getName()).isEqualTo("t1");
        assertThat(conditionalBranchDescr.getElseBranch().getConsequence().getName()).isEqualTo("t2");
    }

    @Test
    void namedConsequenceAfterEnclosed() {
        final String text =
                "rule R when\n" +
                        "  $r : Result()\n" +
                        "  ( Person(name == \"Mark\") or Person(name == \"Mario\") )\n" +
                        "  do[FoundMarkOrMario]\n" +
                        "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                        "then\n" +
                        "  $r.addValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                        "then[FoundMark]\n" +
                        "  $r.addValue(\"Found \" + $p1.getName());\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        NamedConsequenceDescr namedConsequenceDescr = (NamedConsequenceDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(namedConsequenceDescr.getName()).isEqualTo("FoundMarkOrMario");
    }

    @Test
    void namedConsequenceOrWithBindVariables() {
        final String text =
                "rule R when\n" +
                        "  $r : Result()\n" +
                        "  ( $p1 : Person(name == \"Mark\") or $p1 : Person(name == \"Mario\") )\n" +
                        "  do[FoundMarkOrMario]\n" +
                        "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                        "then\n" +
                        "  $r.addValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                        "then[FoundMark]\n" +
                        "  $r.addValue(\"Found \" + $p1.getName());\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);

        OrDescr orDescr = (OrDescr) ruleDescr.getLhs().getDescrs().get(1);
        PatternDescr patternDescr1 = (PatternDescr) orDescr.getDescrs().get(0);
        assertThat(patternDescr1.getIdentifier()).isEqualTo("$p1");
        assertThat(((ExprConstraintDescr) patternDescr1.getConstraint().getDescrs().get(0)).getExpression()).isEqualTo("name == \"Mark\"");
        PatternDescr patternDescr2 = (PatternDescr) orDescr.getDescrs().get(1);
        assertThat(patternDescr2.getIdentifier()).isEqualTo("$p1");
        assertThat(((ExprConstraintDescr) patternDescr2.getConstraint().getDescrs().get(0)).getExpression()).isEqualTo("name == \"Mario\"");

        NamedConsequenceDescr namedConsequenceDescr = (NamedConsequenceDescr) ruleDescr.getLhs().getDescrs().get(2);
        assertThat(namedConsequenceDescr.getName()).isEqualTo("FoundMarkOrMario");
    }

    @Test
    void namedConsequencesInsideOR1() {
        final String text =
                "import org.drools.mvel.compiler.Cheese;\n " +
                        "global java.util.List results;\n" +
                        "\n" +
                        "rule R1 when\n" +
                        "    ( $a: Cheese ( type == \"stilton\" ) do[t1]\n" +
                        "    or\n" +
                        "    $b: Cheese ( type == \"gorgonzola\" ) )\n" +
                        "    $c: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $c.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $a.getType() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);

        assertThat(ruleDescr.getLhs().getDescrs()).hasSize(2);
        assertThat(ruleDescr.getLhs().getDescrs()).first().isInstanceOfSatisfying(OrDescr.class, stiltonOrGorgonzola -> {
            assertThat(stiltonOrGorgonzola.getDescrs()).hasSize(2);
            assertThat(stiltonOrGorgonzola.getDescrs()).first().isInstanceOfSatisfying(AndDescr.class, andDescr -> {
                assertThat(andDescr.getDescrs()).hasSize(2);
                assertThat(andDescr.getDescrs()).first().isInstanceOfSatisfying(PatternDescr.class, stilton -> {
                    assertThat(stilton.getIdentifier()).isEqualTo("$a");
                    assertThat(stilton.getObjectType()).isEqualTo("Cheese");
                    assertThat(stilton.getConstraint().toString()).contains("stilton");
                });
                assertThat(andDescr.getDescrs()).last().isInstanceOfSatisfying(NamedConsequenceDescr.class, namedConsequenceDescr -> {
                    assertThat(namedConsequenceDescr.getName()).isEqualTo("t1");
                });
            });
            assertThat(stiltonOrGorgonzola.getDescrs()).last().isInstanceOfSatisfying(PatternDescr.class, gorgonzola -> {
                assertThat(gorgonzola.getIdentifier()).isEqualTo("$b");
                assertThat(gorgonzola.getObjectType()).isEqualTo("Cheese");
                assertThat(gorgonzola.getConstraint().toString()).contains("gorgonzola");
            });
        });
        assertThat(ruleDescr.getLhs().getDescrs()).last().isInstanceOfSatisfying(PatternDescr.class, cheddar -> {
            assertThat(cheddar.getIdentifier()).isEqualTo("$c");
            assertThat(cheddar.getObjectType()).isEqualTo("Cheese");
            assertThat(cheddar.getConstraint().toString()).contains("cheddar");
        });
    }

    @Test
    void namedConsequencesInsideOR2() {
        final String text =
                "import org.drools.mvel.compiler.Cheese;\n " +
                        "global java.util.List results;\n" +
                        "\n" +
                        "rule R1 when\n" +
                        "    ( $a: Cheese ( type == \"stilton\" )\n" +
                        "    or\n" +
                        "    $b: Cheese ( type == \"gorgonzola\" ) do[t1] )\n" +
                        "    $c: Cheese ( type == \"cheddar\" )\n" +
                        "then\n" +
                        "    results.add( $c.getType() );\n" +
                        "then[t1]\n" +
                        "    results.add( $b.getType() );\n" +
                        "end\n";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);

        assertThat(ruleDescr.getLhs().getDescrs()).hasSize(2);
        assertThat(ruleDescr.getLhs().getDescrs()).first().isInstanceOfSatisfying(OrDescr.class, stiltonOrGorgonzola -> {
            assertThat(stiltonOrGorgonzola.getDescrs()).hasSize(2);
            assertThat(stiltonOrGorgonzola.getDescrs()).first().isInstanceOfSatisfying(PatternDescr.class, stilton -> {
                assertThat(stilton.getIdentifier()).isEqualTo("$a");
                assertThat(stilton.getObjectType()).isEqualTo("Cheese");
                assertThat(stilton.getConstraint().toString()).contains("stilton");
            });
            assertThat(stiltonOrGorgonzola.getDescrs()).last().isInstanceOfSatisfying(AndDescr.class, andDescr -> {
                assertThat(andDescr.getDescrs()).hasSize(2);
                assertThat(andDescr.getDescrs()).first().isInstanceOfSatisfying(PatternDescr.class, gorgonzola -> {
                    assertThat(gorgonzola.getIdentifier()).isEqualTo("$b");
                    assertThat(gorgonzola.getObjectType()).isEqualTo("Cheese");
                    assertThat(gorgonzola.getConstraint().toString()).contains("gorgonzola");
                });
                assertThat(andDescr.getDescrs()).last().isInstanceOfSatisfying(NamedConsequenceDescr.class, namedConsequenceDescr -> {
                    assertThat(namedConsequenceDescr.getName()).isEqualTo("t1");
                });
            });
        });
        assertThat(ruleDescr.getLhs().getDescrs()).last().isInstanceOfSatisfying(PatternDescr.class, cheddar -> {
            assertThat(cheddar.getIdentifier()).isEqualTo("$c");
            assertThat(cheddar.getObjectType()).isEqualTo("Cheese");
            assertThat(cheddar.getConstraint().toString()).contains("cheddar");
        });
    }

    @Test
    void queryComplexLhs() {
        final String text = "query isContainedIn(String x, String y)\n" +
                "    Location (x, y;)\n" +
                "    or\n" +
                "    ( Location (z, y;) and ?isContainedIn(x, z;))\n" +
                "end\n";
        final QueryDescr query = parseAndGetFirstQueryDescr(text);

        assertThat(query).isNotNull();
        AndDescr lhs = query.getLhs();
        assertThat(lhs.getDescrs()).hasSize(1);

        assertThat(lhs.getDescrs().get(0))
                .as("Top level node is OR")
                .isInstanceOfSatisfying(OrDescr.class, orDescr -> {
                    assertThat(orDescr.getDescrs()).hasSize(2);
                    assertThat(orDescr.getDescrs().get(0))
                            .as("Left node of OR is Pattern")
                            .isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                                assertThat(patternDescr.getObjectType()).isEqualTo("Location");
                                assertThat(patternDescr.getConstraint().getDescrs().get(0))
                                        .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                            assertThat(constraint.getExpression()).isEqualTo("x");
                                            assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                            assertThat(constraint.getPosition()).isEqualTo(0);
                                        });
                                assertThat(patternDescr.getConstraint().getDescrs().get(1))
                                        .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                            assertThat(constraint.getExpression()).isEqualTo("y");
                                            assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                            assertThat(constraint.getPosition()).isEqualTo(1);
                                        });
                            });
                    assertThat(orDescr.getDescrs().get(1))
                            .as("Right node of OR is AND")
                            .isInstanceOfSatisfying(AndDescr.class, andDescr -> {
                                assertThat(andDescr.getDescrs().get(0))
                                        .as("Left node of AND is Pattern")
                                        .isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                                            assertThat(patternDescr.getObjectType()).isEqualTo("Location");
                                            assertThat(patternDescr.getConstraint().getDescrs().get(0))
                                                    .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                                        assertThat(constraint.getExpression()).isEqualTo("z");
                                                        assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                                        assertThat(constraint.getPosition()).isEqualTo(0);
                                                    });
                                            assertThat(patternDescr.getConstraint().getDescrs().get(1))
                                                    .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                                        assertThat(constraint.getExpression()).isEqualTo("y");
                                                        assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                                        assertThat(constraint.getPosition()).isEqualTo(1);
                                                    });
                                        });
                                assertThat(andDescr.getDescrs().get(1))
                                        .as("Right node of AND is Query Pattern")
                                        .isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                                            assertThat(patternDescr.isQuery()).isTrue();
                                            assertThat(patternDescr.getObjectType()).isEqualTo("isContainedIn");
                                            assertThat(patternDescr.getConstraint().getDescrs().get(0))
                                                    .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                                        assertThat(constraint.getExpression()).isEqualTo("x");
                                                        assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                                        assertThat(constraint.getPosition()).isEqualTo(0);
                                                    });
                                            assertThat(patternDescr.getConstraint().getDescrs().get(1))
                                                    .isInstanceOfSatisfying(ExprConstraintDescr.class, constraint -> {
                                                        assertThat(constraint.getExpression()).isEqualTo("z");
                                                        assertThat(constraint.getType()).isEqualTo(ExprConstraintDescr.Type.POSITIONAL);
                                                        assertThat(constraint.getPosition()).isEqualTo(1);
                                                    });
                                        });
                            });
                });
    }

    @Test
    void notWithPrefixAnd() {
        final String text =
                "package org.drools.compiler\n" +
                        "rule R when\n" +
                        "  (not (and Integer( $i : intValue )\n" +
                        "            String( length > $i ) \n" +
                        "       )\n" +
                        "  )\n" +
                        "then\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(NotDescr.class, notDescr -> {
            assertThat(notDescr.getDescrs().get(0)).isInstanceOfSatisfying(AndDescr.class, andDescr -> {
                assertThat(andDescr.getDescrs()).hasSize(2);
                assertThat(andDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                    assertThat(patternDescr.getObjectType()).isEqualTo("Integer");
                    assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                        assertThat(exprConstraintDescr.getExpression()).isEqualTo("$i : intValue");
                    });
                });
                assertThat(andDescr.getDescrs().get(1)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                    assertThat(patternDescr.getObjectType()).isEqualTo("String");
                    assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                        assertThat(exprConstraintDescr.getExpression()).isEqualTo("length > $i");
                    });
                });
            });
        });
    }

    @Test
    void existsWithPrefixAnd() {
        final String text =
                "package org.drools.compiler\n" +
                        "rule R when\n" +
                        "  (exists (and Integer( $i : intValue )\n" +
                        "            String( length > $i ) \n" +
                        "       )\n" +
                        "  )\n" +
                        "then\n" +
                        "end";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(ExistsDescr.class, existsDescr -> {
            assertThat(existsDescr.getDescrs().get(0)).isInstanceOfSatisfying(AndDescr.class, andDescr -> {
                assertThat(andDescr.getDescrs()).hasSize(2);
                assertThat(andDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                    assertThat(patternDescr.getObjectType()).isEqualTo("Integer");
                    assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                        assertThat(exprConstraintDescr.getExpression()).isEqualTo("$i : intValue");
                    });
                });
                assertThat(andDescr.getDescrs().get(1)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                    assertThat(patternDescr.getObjectType()).isEqualTo("String");
                    assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                        assertThat(exprConstraintDescr.getExpression()).isEqualTo("length > $i");
                    });
                });
            });
        });
    }

    @Test
    void enumDeclaration() {
        final String text =
                "declare enum PersonAge\n" +
                        "    @doc(author=\"Bob\")\n" +
                        "    ELEVEN(11, \"XI\"), TWELVE(12, \"XII\");\n" +
                        "\n" +
                        "    key: int\n" +
                        "    romanStr : String\n" +
                        "end";
        PackageDescr pkg = parseAndGetPackageDescr(text);

        List<EnumDeclarationDescr> descrList = pkg.getEnumDeclarations();
        EnumDeclarationDescr enumDeclarationDescr = descrList.get(0);
        assertThat(enumDeclarationDescr.getTypeName()).isEqualTo("PersonAge");

        assertThat(enumDeclarationDescr.getAnnotation("doc").getValue("author")).isEqualTo("\"Bob\"");

        List<EnumLiteralDescr> literals = enumDeclarationDescr.getLiterals();
        EnumLiteralDescr enumLiteralDescr0 = literals.get(0);
        assertThat(enumLiteralDescr0.getName()).isEqualTo("ELEVEN");
        assertThat(enumLiteralDescr0.getConstructorArgs()).containsExactly("11", "\"XI\"");
        EnumLiteralDescr enumLiteralDescr1 = literals.get(1);
        assertThat(enumLiteralDescr1.getName()).isEqualTo("TWELVE");
        assertThat(enumLiteralDescr1.getConstructorArgs()).containsExactly("12", "\"XII\"");

        TypeFieldDescr key = enumDeclarationDescr.getFields().get("key");
        assertThat(key.getPattern().getObjectType()).isEqualTo("int");

        TypeFieldDescr romanStr = enumDeclarationDescr.getFields().get("romanStr");
        assertThat(romanStr.getPattern().getObjectType()).isEqualTo("String");
    }

    @Test
    void packageChildrenNamespaceAndUnitProperties() {
        String namespace = "org.drools.compiler.test";
        String source = readResource("package_children.drl");
        PackageDescr pkg = parseAndGetPackageDescr(source);

        // Package and Rule Unit's namespace.
        assertThat(pkg.getName()).isEqualTo(namespace);
        assertThat(pkg.getUnit().getNamespace()).isEqualTo(namespace);

        // Children that are expected to have the package name as their namespace.
        assertNamespace(pkg.getImports(), namespace);
        assertNamespace(pkg.getFunctionImports(), namespace);
        assertNamespace(pkg.getAccumulateImports(), namespace);
        assertNamespace(pkg.getGlobals(), namespace);
        assertNamespace(pkg.getFunctions(), namespace);
        assertNamespace(pkg.getRules(), namespace);
        assertNamespace(pkg.getAttributes(), namespace);

        // Children that are expected to have no namespace.
        assertNamespace(pkg.getTypeDeclarations(), "");
        assertNamespace(pkg.getEnumDeclarations(), "");
        assertNamespace(pkg.getEntryPointDeclarations(), "");
        assertNamespace(pkg.getWindowDeclarations(), "");

        assertThat(pkg.getRules())
                .allSatisfy(ruleDescr -> assertThat(ruleDescr.getUnit()).isNotNull())
                .allSatisfy(ruleDescr -> assertThat(ruleDescr.getUnit().getTarget()).isEqualTo("TestUnit"));

        assertThat(pkg.getRules().get(0).getUnitQualifiedName()).isEqualTo("TestUnit.MyQuery");
        assertThat(pkg.getRules().get(1).getUnitQualifiedName()).isEqualTo("TestUnit.My Rule");
    }

    static void assertNamespace(Collection<? extends BaseDescr> children, String namespace) {
        assertThat(children).isNotEmpty(); // Make sure that every child type is represented.
        assertThat(children).allSatisfy(baseDescr -> assertThat(baseDescr.getNamespace()).isEqualTo(namespace));
    }

    @Test
    void noWhitespaceBetweenRuleKeywordAndName() {
        final String text = "rule X when then end rule\"Y\" when then end rule'Z'when then end";

        PackageDescr pkg = parseAndGetPackageDescr(text);

        assertThat(pkg.getRules())
                .map(RuleDescr::getName)
                .containsExactly("X", "Y", "Z");
    }

    @Test
    void orWithMethodCall() {
        final String text =
                "rule R1\n" +
                        "when\n" +
                        "  MyFact( value == 10 || someMethod() == 4 )\n" +
                        "then\n" +
                        "end";
        ExprConstraintDescr exprConstraintDescr = parseAndGetFirstConstraintDescr(text);
        assertThat(exprConstraintDescr.getExpression()).isEqualTo("value == 10 || someMethod() == 4");
    }

    @Test
    void orWithMethodCallWithArg() {
        final String text =
                "rule R1\n" +
                        "when\n" +
                        "  MyFact( value == 10 || someMethod(value) == 4 )\n" +
                        "then\n" +
                        "end";
        ExprConstraintDescr exprConstraintDescr = parseAndGetFirstConstraintDescr(text);
        assertThat(exprConstraintDescr.getExpression()).isEqualTo("value == 10 || someMethod(value) == 4");
    }

    @Test
    void andWithMethodCall() {
        final String text =
                "rule R1\n" +
                        "when\n" +
                        "  MyFact( value == 10 && someMethod() == 4 )\n" +
                        "then\n" +
                        "end";
        ExprConstraintDescr exprConstraintDescr = parseAndGetFirstConstraintDescr(text);
        assertThat(exprConstraintDescr.getExpression()).isEqualTo("value == 10 && someMethod() == 4");
    }

    @Test
    void andWithMethodCallWithArg() {
        final String text =
                "rule R1\n" +
                        "when\n" +
                        "  MyFact( value == 10 && someMethod(value) == 4 )\n" +
                        "then\n" +
                        "end";
        ExprConstraintDescr exprConstraintDescr = parseAndGetFirstConstraintDescr(text);
        assertThat(exprConstraintDescr.getExpression()).isEqualTo("value == 10 && someMethod(value) == 4");
    }

    @Test
    void unificationInAccumulateRule() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    MyFact($i : currentValue)\n" +
                        "    accumulate( $p : Person( name == \"John\" ),\n" +
                        "                $i := min( $p.getAge() ) )\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getLhs().getDescrs()).hasSize(2);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(1);
        AccumulateDescr accumulateDescr = (AccumulateDescr) outPattern.getSource();
        assertThat(accumulateDescr.getInputPattern()).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
            assertThat(patternDescr.getIdentifier()).isEqualTo("$p");
            assertThat(patternDescr.getObjectType()).isEqualTo("Person");
        });

        AccumulateDescr.AccumulateFunctionCallDescr accumulateFunction = accumulateDescr.getFunctions().get(0);
        assertThat(accumulateFunction.getBind()).isEqualTo("$i");
        assertThat(accumulateFunction.isUnification()).isTrue();
        assertThat(accumulateFunction.getFunction()).isEqualTo("min");
        assertThat(accumulateFunction.getParams()).containsExactly("$p.getAge()");
    }

    @Test
    void existsOrNot() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    exists(not(Integer()) or not(Double()))\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(ExistsDescr.class, existsDescr -> {
            assertThat(existsDescr.getDescrs().get(0)).isInstanceOfSatisfying(OrDescr.class, orDescr -> {
                assertThat(orDescr.getDescrs()).hasSize(2);
                assertThat(orDescr.getDescrs().get(0)).isInstanceOfSatisfying(NotDescr.class, notDescr -> {
                    assertThat(notDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                        assertThat(patternDescr.getObjectType()).isEqualTo("Integer");
                    });
                });
                assertThat(orDescr.getDescrs().get(1)).isInstanceOfSatisfying(NotDescr.class, notDescr -> {
                    assertThat(notDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                        assertThat(patternDescr.getObjectType()).isEqualTo("Double");
                    });
                });
            });
        });
    }

    @Test
    void nestedNot() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    not ( not ( Cheese() ) )\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(NotDescr.class, notDescr -> {
            assertThat(notDescr.getDescrs().get(0)).isInstanceOfSatisfying(NotDescr.class, nestedNotDescr -> {
                assertThat(nestedNotDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                    assertThat(patternDescr.getObjectType()).isEqualTo("Cheese");
                });
            });
        });
    }

    @Test
    void errorMessage_shouldNotContainEmptyString() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    foo\n" + // parse error
                        "then\n" +
                        "end";
        PackageDescr pkg = parseAndGetPackageDescrWithoutErrorCheck(text);
        assertThat(pkg).isNull();
        assertThat(parser.hasErrors()).isTrue();
        assertThat(parser.getErrors()).extracting(DroolsError::getMessage).doesNotContain("");
    }

    @ParameterizedTest
    @MethodSource("org.drools.drl.parser.antlr4.ParserTestUtils#javaKeywords")
    void javaKeywordsInPackage(String keyword) {
        String pkgName = "org.drools." + keyword;
        String text = "package " + pkgName + "\n" +
                "rule R\n" +
                "when\n" +
                "  $p : Person()\n" +
                "then\n" +
                "end\n";

        PackageDescr pkg = parseAndGetPackageDescr(text);

        assertThat(pkg.getName()).isEqualTo(pkgName);
        assertThat(pkg.getRules()).hasSize(1);

        assertThat(pkg.getRules().get(0).getName()).isEqualTo("R");
    }

    @Test
    void accumulateEmptyChunks() {
        String text = "rule R\n" +
                "when\n" +
                "        $totalAmount : Number() from accumulate( Cheese( $price : price ),\n" +
                "                                                 init( ),\n" +
                "                                                 action( ),\n" +
                "                                                 result( null ) );\n" +
                "then\n" +
                "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accumulateDescr = (AccumulateDescr) outPattern.getSource();
        assertThat(accumulateDescr.getInitCode()).isEmpty();
        assertThat(accumulateDescr.getActionCode()).isEmpty();
        assertThat(accumulateDescr.getReverseCode()).isNull();
        assertThat(accumulateDescr.getResultCode()).isEqualTo("null");
    }

    @Test
    void doublePipeInfixOr() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    Person()\n" +
                        "      ||\n" +
                        "    Address()\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(OrDescr.class, orDescr -> {
            assertThat(orDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("Person");
            });
            assertThat(orDescr.getDescrs().get(1)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("Address");
            });
        });
    }

    @Test
    void doubleAmpersandInfixAnd() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    Person()\n" +
                        "      &&\n" +
                        "    Address()\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getLhs().getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
            assertThat(patternDescr.getObjectType()).isEqualTo("Person");
        });
        assertThat(rule.getLhs().getDescrs().get(1)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
            assertThat(patternDescr.getObjectType()).isEqualTo("Address");
        });
    }

    @Test
    void doubleAmpersandInfixAndInAccumulate() {
        final String text =
                "rule R\n" +
                        "when\n" +
                        "    accumulate( FactA($a : value) && FactB($b : value);\n" +
                        "      $avg : average($a + $b))\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        AccumulateDescr accumulateDescr = (AccumulateDescr) outPattern.getSource();
        assertThat(accumulateDescr.getInput()).isInstanceOfSatisfying(AndDescr.class, andDescr -> {
            assertThat(andDescr.getDescrs()).hasSize(2);
            assertThat(andDescr.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("FactA");
                assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                    assertThat(exprConstraintDescr.getExpression()).isEqualTo("$a : value");
                });
            });
            assertThat(andDescr.getDescrs().get(1)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("FactB");
                assertThat(patternDescr.getConstraint().getDescrs().get(0)).isInstanceOfSatisfying(ExprConstraintDescr.class, exprConstraintDescr -> {
                    assertThat(exprConstraintDescr.getExpression()).isEqualTo("$b : value");
                });
            });
        });

        AccumulateDescr.AccumulateFunctionCallDescr accumulateFunction = accumulateDescr.getFunctions().get(0);
        assertThat(accumulateFunction.getBind()).isEqualTo("$avg");
        assertThat(accumulateFunction.getFunction()).isEqualTo("average");
        assertThat(accumulateFunction.getParams()).containsExactly("$a + $b");
    }

    @Test
    void durationChunk() {
        final String text =
                "rule R\n" +
                        "  duration (wrong input) \n" +
                        "when\n" +
                        "then\n" +
                        "end";
        RuleDescr rule = parseAndGetFirstRuleDescr(text);
        assertThat(rule.getAttributes()).containsKey("duration");
        assertThat(rule.getAttributes().get("duration").getType()).isEqualTo(AttributeDescr.Type.EXPRESSION);

        // At the moment, the parser accepts any input and let the compile phase validate it.
        assertThat(rule.getAttributes().get("duration").getValue()).isEqualTo("wrong input");
    }

    @Test
    void accumulateWithEmptyActionAndReverse() {
        final String drl = "rule R when\n" +
                "    Number() from accumulate( Number(),\n" +
                "        init( double total = 0; ),\n" +
                "        action( ),\n" +
                "        reverse( ),\n" +
                "        result( new Double( total ) )\n" +
                "    )\n" +
                "then end";
        RuleDescr rule = parseAndGetFirstRuleDescr(drl);

        final PatternDescr outPattern = (PatternDescr) rule.getLhs().getDescrs().get(0);
        final AccumulateDescr accum = (AccumulateDescr) outPattern.getSource();
        assertThat(accum.getInitCode()).isEqualTo("double total = 0;");
        assertThat(accum.getActionCode()).isEmpty();
        assertThat(accum.getReverseCode()).isEmpty();
        assertThat(accum.getResultCode()).isEqualTo("new Double( total )");

        assertThat(accum.isExternalFunction()).isFalse();

        final PatternDescr pattern = accum.getInputPattern();
        assertThat(pattern.getObjectType()).isEqualTo("Number");

        assertThat(accum.getInput()).isInstanceOfSatisfying(AndDescr.class, and -> {
            assertThat(and.getDescrs()).hasSize(1);
            assertThat(and.getDescrs().get(0)).isInstanceOfSatisfying(PatternDescr.class, patternDescr -> {
                assertThat(patternDescr.getObjectType()).isEqualTo("Number");
            });
        });
    }

    @Test
    void functionWithAnonymousClass() {
        final String text = "function Function<String, Integer> f() {\n" +
                "    return new Function<String, Integer>() {\n" +
                "        public Integer apply(String s) {\n" +
                "            return s.length();\n" +
                "        }\n" +
                "    };\n" +
                "}";
        PackageDescr packageDescr = parseAndGetPackageDescr(text);
        FunctionDescr function = packageDescr.getFunctions().get(0);

        assertThat(function.getName()).isEqualTo("f");
        assertThat(function.getReturnType()).isEqualToIgnoringWhitespace("Function<String, Integer>");
        assertThat(function.getParameterTypes()).isEmpty();
        assertThat(function.getParameterNames()).isEmpty();
        assertThat(function.getBody()).isEqualToIgnoringWhitespace("return new Function<String, Integer>() {\n" +
                                                                           "        public Integer apply(String s) {\n" +
                                                                           "            return s.length();\n" +
                                                                           "        }\n" +
                                                                           "    };");
    }

    @Test
    void typeDeclarationWithTypeToken() {
        final String drl = "declare type Foo\n" + // "type" is just optional
                "  id : int\n" +
                "end";
        final PackageDescr pkg = parseAndGetPackageDescr(drl);

        TypeDeclarationDescr typeDeclarationDescr = pkg.getTypeDeclarations().get(0);
        assertThat(typeDeclarationDescr.getTypeName()).isEqualTo("Foo");
        TypeFieldDescr typeFieldDescr = typeDeclarationDescr.getFields().get("id");
        assertThat(typeFieldDescr.getPattern().getObjectType()).isEqualTo("int");
    }
}
