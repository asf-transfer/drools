package org.drools.mvelcompiler;

import java.util.Map;

import org.drools.Person;
import org.junit.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class MvelCompilerTest implements CompilerTest {

    @Test
    public void testConvertPropertyToAccessor() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.parent.getParent().name; } ",
             "{ $p.getParent().getParent().getName(); }");

        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.getParent().parent.name; } ",
             "{ $p.getParent().getParent().getName(); }");

        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.parent.parent.name; } ",
             "{ $p.getParent().getParent().getName(); }");

        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.getParent().getParent().getName(); } ",
             "{ $p.getParent().getParent().getName(); }");
    }

    @Test
    public void testAccessorInArguments() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ insert(\"Modified person age to 1 for: \" + $p.name); }",
             "{ insert(\"Modified person age to 1 for: \" + $p.getName()); } ");
    }

    @Test
    public void testPublicField() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.parentPublic.getParent().name; } ",
             "{ $p.parentPublic.getParent().getName(); }");

        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.getParent().parentPublic.name; } ",
             "{ $p.getParent().parentPublic.getName(); }");
    }

    @Test
    public void testUncompiledMethod() {
        test("{ System.out.println(\"Hello World\"); }",
             "{ System.out.println(\"Hello World\"); }");
    }


    @Test
    public void testStringLength() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.name.length; }",
             "{ $p.getName().length(); }");
    }

    @Test
    public void testAssignment() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ Person np = $p; np = $p; }",
             "{ org.drools.Person np = $p; np = $p; }");
    }

    @Test
    public void testSetter() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ $p.name = \"Luca\"; }",
             "{ $p.setName(\"Luca\"); }");
    }

    @Test
    public void withoutSemicolonAndComment() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{             " +
                     "delete($person) // some comment\n" +
                     "delete($pet) // another comment\n" +
                     "}",
             "{             " +
                     "delete($person);\n" +
                     "delete($pet);\n" +
                     "}");
    }


    @Test
    public void testInitializerArrayAccess() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ " +
                     "l = new ArrayList(); " +
                     "l.add(\"first\"); " +
                     "System.out.println(l[0]); " +
                     "}",
             "{ " +
                     "java.util.ArrayList l = new ArrayList(); " +
                     "l.add(\"first\"); " +
                     "System.out.println(l.get(0)); " +
                     "}");
    }

    @Test
    public void testInitializerMap() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ " +
                     "m = new HashMap();\n" +
                     "m.put(\"key\", 2);\n" +
                     "System.out.println(m[\"key\"]);\n" +
                     "}",
             "{ " +
                     "java.util.HashMap m = new HashMap();\n" +
                     "m.put(\"key\", 2);\n" +
                     "System.out.println(m.get(\"key\"));\n" +
                     "}");
    }

    @Test
    public void testMixArrayMap() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ " +
                     "    m = new HashMap();\n" +
                     "    l = new ArrayList();\n" +
                     "    l.add(\"first\");\n" +
                     "    m.put(\"content\", l);\n" +
                     "    System.out.println(((ArrayList)m[\"content\"])[0]);\n" +
                     "    list.add(((ArrayList)m[\"content\"])[0]);\n" +
                     "}",
             "{ " +
                     "    java.util.HashMap m = new HashMap();\n" +
                     "    java.util.ArrayList l = new ArrayList();\n" +
                     "    l.add(\"first\");\n" +
                     "    m.put(\"content\", l);\n" +
                     "    System.out.println(((java.util.ArrayList) m.get(\"content\")).get(0));\n" +
                     "    list.add(((java.util.ArrayList) m.get(\"content\")).get(0));\n" +
                     "}");
    }

    @Test
    public void testModify() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ modify ( $p )  { name = \"Luca\", age = 35 }; }",
             "{ $p.setName(\"Luca\"); $p.setAge(35); }",
             result -> assertThat(allModifiedProperties(result), containsInAnyOrder("name", "age")));
    }

    @Test
    public void testModifySemiColon() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ modify($p) { setAge(1); }; }",
             "{ $p.setAge(1); }",
             result -> assertThat(allModifiedProperties(result), containsInAnyOrder("age")));
    }

    @Test
    public void testModifyWithAssignment() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ modify($p) { age = $p.age+1 }; }",
             "{ $p.setAge($p.getAge() + 1); }",
             result -> assertThat(allModifiedProperties(result), containsInAnyOrder("age")));
    }

    @Test
    public void testWithSemiColon() {
        test("{ with( $l = new ArrayList()) { $l.add(2); }; }",
             "{ java.util.ArrayList $l = new ArrayList(); $l.add(2); }",
             result -> assertThat(allModifiedProperties(result), is(empty())));
    }

    @Test
    public void testWithWithAssignment() {
        test(ctx -> ctx.addDeclaration("$p", Person.class),
             "{ with($p = new Person()) { age = $p.age+1 }; }",
             "{ org.drools.Person $p = new Person(); $p.setAge($p.getAge() + 1); }",
             result -> assertThat(allModifiedProperties(result), is(empty())));
    }

    @Test
    public void testVariableDeclarationUntyped() {
        test(ctx -> ctx.addDeclaration("$map", Map.class),
             " { Map pMap = map.get( $r.getName() ); }",
             " { java.util.Map pMap = (java.util.Map) (map.get($r.getName())); }" );
    }

    @Test
    public void testSimpleVariableDeclaration() {
        test(" { int i; }",
             " { int i; }" );
    }

    @Test
    public void testNestedModify() {
        test(            "{    if ($fact.getResult() != null) {\n" +
                                 "        $fact.setResult(\"OK\");\n" +
                                 "    } else {\n" +
                                 "        modify ($fact) {\n" +
                                 "            result = \"FIRST\"" +
                                 "        }\n" +
                                 "    }}",
                         " { " +
                                 "if ($fact.getResult() != null) { " +
                                 "  $fact.setResult(\"OK\"); " +
                                 "} else { " +
                                 "($fact).setResult(\"FIRST\"); " +
                                 "update($fact); " +
                                 "} " +
                                 "} ",
                         result -> assertThat(allModifiedProperties(result), containsInAnyOrder("result")));
    }
}