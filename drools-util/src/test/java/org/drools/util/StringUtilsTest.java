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
package org.drools.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.util.StringUtils.getPkgUUID;
import static org.drools.util.StringUtils.indexOfOutOfQuotes;
import static org.drools.util.StringUtils.md5Hash;
import static org.drools.util.StringUtils.splitStatements;
import static org.drools.util.StringUtils.splitStatementsAcrossBlocks;

public class StringUtilsTest {

    @Test
    public void testFindEndOfMethodArgsIndex() {
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"myId\")", 12);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"myId\").call()", 12);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('myId')", 12);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('myId').call()", 12);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id\")", 13);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id\").call()", 13);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id'\")", 14);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id'\").call()", 14);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('my\"Id\"')", 14);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('my\"Id\"').call()", 14);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('my\"Id')", 13);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('my\"Id').call()", 13);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my\\\"Id\")", 14);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my\\\"Id\").call()", 14);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId('myId', 'something')", 25);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"myId\", \"something\")", 25);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id\", \"somet'hing\")", 27);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'Id'\", \"somet'hing\")", 28);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setId(\"my'(Id\", \"somet'(hing'\")", 30);

        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setObject(new Object())", 22);
        findEndOfMethodArgsIndexAndAssertItEqualsToExpected("setObject(new Object(\"string param\"))", 36);
    }

    private void findEndOfMethodArgsIndexAndAssertItEqualsToExpected(String strExpr, int expectedIndex) {
        int actualIndex = StringUtils.findEndOfMethodArgsIndex(strExpr, strExpr.indexOf('('));
        assertThat(actualIndex).as("Expected and actual end of method args index for expr '" + strExpr + "' are not equal!").isEqualTo(expectedIndex);
    }
    
    @Test
    public void test_codeAwareEqualsIgnoreSpaces() {
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(null, null)).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces("", "")).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces("", null)).isFalse();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(null, "")).isFalse();

        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(" ", "")).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces("", " ")).isTrue();

        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(" ", "  ")).isTrue();

        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "rule Rx when then end",
                " rule Rx  when then end " // <<- DIFF 3x 
        )).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "rule Rx when then end\n",
                " rule Rx  when then end\n " // <<- DIFF, both terminate with whitespace but different types
        )).isTrue();

        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n",

                "package org.drools.compiler\n " +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n"
        )).isFalse();

        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n " +  // <<- DIFF
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n"
        )).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n",

                " package org.drools.compiler\n" +  // <<- DIFF (at beginning of this line)
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n"
        )).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n",

                " package org.drools.compiler\n " +  // <<- DIFF 2x
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n " // <<- DIFF 
        )).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\"  )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isTrue();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello World\" )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello    World\" )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isFalse();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello' World\" )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == \"Hello'    World\" )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isFalse();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello World' )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello    World' )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isFalse();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello\" World' )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello\"    World' )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isFalse();
        assertThat(StringUtils.codeAwareEqualsIgnoreSpaces(
                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello\\' World' )\n" +
                        "then\n" +
                        "end\n",

                "package org.drools.compiler\n" +
                        "rule Rx when\n" +
                        "   $m : Message( message == 'Hello\\'    World' )\n" + // <<- DIFF
                        "then\n" +
                        "end\n"
        )).isFalse();
    }

    @Test
    public void test_indexOfOutOfQuotes() {
        assertThat(indexOfOutOfQuotes("bla\"bla\"bla", "bla")).isEqualTo(0);
        assertThat(indexOfOutOfQuotes("\"bla\"bla", "bla")).isEqualTo(5);
        assertThat(indexOfOutOfQuotes("\"bla\"", "bla")).isEqualTo(-1);
        assertThat(indexOfOutOfQuotes("bla\"bla\"bla", "bla", 0)).isEqualTo(0);
        assertThat(indexOfOutOfQuotes("bla\"bla\"bla", "bla", 1)).isEqualTo(8);
        assertThat(indexOfOutOfQuotes("bla\"bla\"bla", "bla", 9)).isEqualTo(-1);
    }

    @Test
    public void getPkgUUIDFromGAV() {
        String gav = "group:artifact:version";
        String packageName = "apackage";
        String retrieved = getPkgUUID(gav, packageName);
        String expected = md5Hash(gav+packageName);
        assertThat(retrieved).isEqualTo(expected);
    }

    @Test
    public void testExtractFirstIdentifierWithStringBuilder() {
        // Not-quoted string, interpreted as identifier
        String string = "IDENTIFIER";
        String expected = string;
        StringBuilder builder = new StringBuilder();
        int start = 0;
        int retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // retrieved size is equals to the length of given string
        assertThat(builder.toString()).isEqualTo(expected);

        // Quoted string, not interpreted as identifier
        string = "\"IDENTIFIER\"";
        expected = "";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length());
        assertThat(builder.toString()).isEqualTo(expected);

        // Only the not-quoted string, and its size, is returned
        string = "IDENTIFIER \"";
        expected = "IDENTIFIER";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(expected.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        string = "IDENTIFIER \"the_identifier";
        expected = "IDENTIFIER";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(expected.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        string = "\"the_identifier\" IDENTIFIER";
        expected = "IDENTIFIER";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        // Quoted string, not interpreted as identifier, starting at arbitrary position
        string = "THIS IS BEFORE \"IDENTIFIER\"";
        expected = "";
        builder = new StringBuilder();
        start = 14;
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length());
        assertThat(builder.toString()).isEqualTo(expected);

        // Only the not-quoted string, and its size, is returned, starting at arbitrary position
        string = "THIS IS BEFORE IDENTIFIER \"";
        expected = "IDENTIFIER";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(25); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        string = "IDENTIFIER \"the_identifier";
        expected = "";
        builder = new StringBuilder();
        start = 10;
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        string = "IDENTIFIER \"the_identifier";
        expected = "";
        builder = new StringBuilder();
        start = 10;
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

        string = "\"not an ' identifier\"";
        expected = "";
        builder = new StringBuilder();
        start = 0;
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the whole string length
        assertThat(builder.toString()).isEqualTo(expected);

        string = "'not an \" identifier'";
        expected = "";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the whole string length
        assertThat(builder.toString()).isEqualTo(expected);

        string = "'not an \" identifier\"'";
        expected = "";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the whole string length
        assertThat(builder.toString()).isEqualTo(expected);

        string = "\"an \" IDENTIFIER";
        expected = "IDENTIFIER";
        builder = new StringBuilder();
        retrieved = StringUtils.extractFirstIdentifier(string, builder, start);
        assertThat(retrieved).isEqualTo(string.length()); // it returns the index where the identifier ends
        assertThat(builder.toString()).isEqualTo(expected);

    }

    @Test
    public void testSplitStatements() {
        String text =
                "System.out.println(\"'\");" +
                "$visaApplication.setValidation( Validation.FAILED );" +
                "drools.update($visaApplication);";
        List<String> statements = splitStatements(text);
        assertThat(statements.size()).isEqualTo(3);
        assertThat(statements.get(0)).isEqualTo("System.out.println(\"'\")");
        assertThat(statements.get(1)).isEqualTo("$visaApplication.setValidation( Validation.FAILED )");
        assertThat(statements.get(2)).isEqualTo("drools.update($visaApplication)");
    }

    @Test
    public void splitStatementsAcrossBlocksIf() {
        String text = "if (true) {\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "}";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("if (true)");
        assertThat(statements.get(1)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(2)).isEqualTo("drools.update($fact)");
    }

    @Test
    public void splitStatementsAcrossBlocksDoWhile() {
        String text = "do {\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "} while (false);";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("do");
        assertThat(statements.get(1)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(2)).isEqualTo("drools.update($fact)");
        assertThat(statements.get(3)).isEqualTo("while (false)");
    }

    @Test
    public void splitStatementsAcrossBlocksFor() {
        String text = "for (int i = 0; i < 1; i++) {\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "}";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("for (int i = 0; i < 1; i++)");
        assertThat(statements.get(1)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(2)).isEqualTo("drools.update($fact)");
    }

    @Test
    public void splitStatementsAcrossBlocksCommentedIf() {
        String text = "// if (true) {\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "// }";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(1)).isEqualTo("drools.update($fact)");
    }

    @Test
    public void splitStatementsAcrossBlocksCommentedIfMissingStartingBrace() {
        String text = "// if (true)\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "// }";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(1)).isEqualTo("drools.update($fact)");
    }

    @Test
    public void splitStatementsAcrossBlocksCommentedIfMissingEndingBrace() {
        String text = "// if (true) {\n" +
                      "  $fact.value1 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "//";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(1)).isEqualTo("drools.update($fact)");
    }

    @Test
    public void splitStatementsAcrossBlocksIfInsideAndOutsideAssginment() {
        String text = "$fact.value1 = 2;\n" +
                      "if (true) {\n" +
                      "  $fact.value2 = 2;\n" +
                      "  drools.update($fact);\n" +
                      "}";
        List<String> statements = splitStatementsAcrossBlocks(text);
        assertThat(statements.get(0)).isEqualTo("$fact.value1 = 2");
        assertThat(statements.get(1)).isEqualTo("if (true)");
        assertThat(statements.get(2)).isEqualTo("$fact.value2 = 2");
        assertThat(statements.get(3)).isEqualTo("drools.update($fact)");
    }
}
