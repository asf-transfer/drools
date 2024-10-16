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
package org.drools.mvel.compiler.rule.builder.dialect.mvel;

import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.Dialect;
import org.drools.compiler.compiler.DialectCompiletimeRegistry;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.drools.core.reteoo.MockLeftTupleSink;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.parser.DroolsParserException;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.rule.builder.RuleBuildContext;
import org.drools.compiler.rule.builder.RuleBuilder;
import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContextFactory;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.MockTupleSource;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.Pattern;
import org.drools.base.base.ObjectType;
import org.drools.base.rule.accessor.PatternExtractor;
import org.drools.core.common.PropagationContext;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.kiesession.consequence.DefaultKnowledgeHelper;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.mvel.MVELDialectRuntimeData;
import org.drools.mvel.builder.MVELConsequenceBuilder;
import org.drools.mvel.builder.MVELDialect;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.expr.MVELConsequence;
import org.drools.mvel.expr.MVELDebugHandler;
import org.junit.Test;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.conf.LanguageLevelOption;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.debug.DebugTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class MVELConsequenceBuilderTest {

    @Test
    public void testSimpleExpression() throws Exception {
        PackageDescr pkgDescr = new PackageDescr( "pkg1" );
        KnowledgeBuilderImpl pkgBuilder = new KnowledgeBuilderImpl();
        pkgBuilder.addPackage( pkgDescr );

        InternalKnowledgePackage pkg = pkgBuilder.getPackageRegistry( "pkg1" ).getPackage();
        final RuleDescr ruleDescr = new RuleDescr( "rule 1" );
        ruleDescr.setNamespace( "pkg1" );
        ruleDescr.setConsequence( "modify (cheese) {price = 5 };\nretract (cheese)" );

        DialectCompiletimeRegistry dialectRegistry = pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectCompiletimeRegistry();

        MVELDialect mvelDialect = (MVELDialect) dialectRegistry.getDialect( "mvel" );

        final RuleBuildContext context = new RuleBuildContext( pkgBuilder,
                                                               ruleDescr,
                                                               dialectRegistry,
                                                               pkg,
                                                               mvelDialect );

        final InstrumentedDeclarationScopeResolver declarationResolver = new InstrumentedDeclarationScopeResolver();

        final ObjectType cheeseObjeectType = new ClassObjectType( Cheese.class );

        final Pattern pattern = new Pattern( 0,
                                             cheeseObjeectType,
                                             "cheese" );

        final GroupElement subrule = new GroupElement( GroupElement.AND );
        subrule.addChild( pattern );
        final Map<String, Declaration> map = new HashMap<String, Declaration>();
        map.put( "cheese",
                 pattern.getDeclaration() );
        declarationResolver.setDeclarations( map );
        context.setDeclarationResolver( declarationResolver );

        final MVELConsequenceBuilder builder = new MVELConsequenceBuilder();
        builder.build( context, RuleImpl.DEFAULT_CONSEQUENCE_NAME );

        InternalKnowledgeBase kBase = KnowledgeBaseFactory.newKnowledgeBase();

        PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
        kBase.addPackage(pkg);

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        BuildContext buildContext = new BuildContext(kBase, Collections.emptyList());
        MockTupleSource      source       = new MockTupleSource(1, buildContext);
        source.setObjectCount(1);
        RuleTerminalNode rtn = new RuleTerminalNode(0, source, context.getRule(), subrule, 0, buildContext);

        final Cheese cheddar = new Cheese( "cheddar", 10 );
        final InternalFactHandle f0 = (InternalFactHandle) ksession.insert( cheddar );
        final LeftTuple tuple = new LeftTuple( f0, rtn, true );
        f0.removeLeftTuple(tuple);

        final RuleTerminalNodeLeftTuple item = (RuleTerminalNodeLeftTuple) TupleFactory.createLeftTuple(tuple, rtn, pctxFactory.createPropagationContext(1, PropagationContext.Type.DELETION, null, null, null), true);
        item.init(0, 0, item.getPropagationContext(), null, null);


        final DefaultKnowledgeHelper kbHelper = new DefaultKnowledgeHelper( ksession );
        kbHelper.setActivation( item );
        (( MVELConsequence ) context.getRule().getConsequence()).compile(  ( MVELDialectRuntimeData ) pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectRuntimeRegistry().getDialectData( "mvel" ));
        context.getRule().getConsequence().evaluate( kbHelper,
                                                     ksession );

        assertThat(cheddar.getPrice()).isEqualTo(5);
    }

    @Test
    public void testImperativeCodeError() throws Exception {
        InternalKnowledgePackage pkg = CoreComponentFactory.get().createKnowledgePackage( "pkg1" );
        final RuleDescr ruleDescr = new RuleDescr( "rule 1" );
        ruleDescr.setConsequence( "if (cheese.price == 10) { cheese.price = 5; }" );

        Properties properties = new Properties();
        properties.setProperty( "drools.dialect.default",
                                "mvel" );
        KnowledgeBuilderConfigurationImpl cfg1 = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(properties, null).as(KnowledgeBuilderConfigurationImpl.KEY);

        KnowledgeBuilderImpl pkgBuilder = new KnowledgeBuilderImpl( pkg, cfg1 );
        PackageRegistry pkgRegistry = pkgBuilder.getPackageRegistry( pkg.getName() );
        DialectCompiletimeRegistry dialectRegistry = pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectCompiletimeRegistry();
        MVELDialect mvelDialect = (MVELDialect) dialectRegistry.getDialect( pkgRegistry.getDialect() );

        final RuleBuildContext context = new RuleBuildContext( pkgBuilder,
                                                               ruleDescr,
                                                               dialectRegistry,
                                                               pkg,
                                                               mvelDialect );

        final InstrumentedDeclarationScopeResolver declarationResolver = new InstrumentedDeclarationScopeResolver();

        final ObjectType cheeseObjeectType = new ClassObjectType( Cheese.class );

        final Pattern pattern = new Pattern( 0,
                                             cheeseObjeectType );

        final PatternExtractor extractor = new PatternExtractor( cheeseObjeectType );

        final Declaration declaration = new Declaration( "cheese",
                                                         extractor,
                                                         pattern );
        final Map<String, Declaration> map = new HashMap<String, Declaration>();
        map.put( "cheese",
                 declaration );
        declarationResolver.setDeclarations( map );
        context.setDeclarationResolver( declarationResolver );

        final MVELConsequenceBuilder builder = new MVELConsequenceBuilder();
        builder.build( context, RuleImpl.DEFAULT_CONSEQUENCE_NAME );

        InternalKnowledgeBase kBase = KnowledgeBaseFactory.newKnowledgeBase();
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        final Cheese cheddar = new Cheese( "cheddar",
                                           10 );
        final InternalFactHandle f0 = (InternalFactHandle) ksession.insert( cheddar );
        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("id0"), Collections.emptyList());
        final LeftTuple tuple = new LeftTuple( f0,
                                               new MockLeftTupleSink(0, bctx),
                                               true );

        RuleTerminalNode rtn = new RuleTerminalNode();
        final RuleTerminalNodeLeftTuple item = (RuleTerminalNodeLeftTuple) TupleFactory.createLeftTuple(tuple, rtn, null, true);
        item.init(0, 0, item.getPropagationContext(), null, null);

        final DefaultKnowledgeHelper kbHelper = new DefaultKnowledgeHelper( ksession );
        kbHelper.setActivation( item );
        try {
            ((MVELConsequence) context.getRule().getConsequence()).compile( (MVELDialectRuntimeData) pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectRuntimeRegistry().getDialectData( "mvel" ) );
            context.getRule().getConsequence().evaluate( kbHelper,
                                                         ksession );
            fail( "should throw an exception, as 'if' is not allowed" );
        } catch ( Exception e ) {
        }

        assertThat(cheddar.getPrice()).isEqualTo(10);
    }

    /**
     * Just like MVEL command line, we can allow expressions to span lines, with optional ";"
     * seperating expressions. If its needed a ";" can be thrown in, but if not, a new line is fine.
     *
     * However, when in the middle of unbalanced brackets, a new line means nothing.
     *
     * @throws Exception
     */
    @Test
    public void testLineSpanOptionalSemis() throws Exception {

        String simpleEx = "foo\nbar\nbaz";
        assertThat(MVELConsequenceBuilder.delimitExpressions(simpleEx)).isEqualTo("foo;\nbar;\nbaz");

        String ex = "foo (\n bar \n)\nbar;\nyeah;\nman\nbaby";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("foo ( bar );\n\n\nbar;\nyeah;\nman;\nbaby");

        ex = "foo {\n bar \n}\nbar;   \nyeah;\nman\nbaby";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("foo { bar };\n\n\nbar;   \nyeah;\nman;\nbaby");

        ex = "foo [\n bar \n]\nbar;  x\nyeah();\nman[42]\nbaby;ca chiga;\nend";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("foo [ bar ];\n\n\nbar;  x;\nyeah();\nman[42];\nbaby;ca chiga;\nend");

        ex = "   \n\nfoo [\n bar \n]\n\n\nbar;  x\n  \nyeah();\nman[42]\nbaby;ca chiga;\nend";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("   \n\nfoo [ bar ];\n\n\n\n\nbar;  x;\n  \nyeah();\nman[42];\nbaby;ca chiga;\nend");

        ex = "   retract(f1) // some comment\n   retract(f2)\nend";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("   retract(f1) ;// some comment\n   retract(f2);\nend");

        ex = "   retract(f1 /* inline comment */) /* some\n comment\n*/   retract(f2)\nend";
        assertThat(MVELConsequenceBuilder.delimitExpressions(ex)).isEqualTo("   retract(f1 /* inline comment */) ;/* some\n comment\n*/   retract(f2);\nend");

    }

    @Test
    public void testMVELDebugSymbols() throws DroolsParserException {

        MVELDebugHandler.setDebugMode( true );

        try {
            final DrlParser parser = new DrlParser(LanguageLevelOption.DRL5);
            final PackageDescr pkgDescr = parser.parse( new InputStreamReader( getClass().getResourceAsStream( "mvel_rule.drl" ) ) );

            // just checking there is no parsing errors
            assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();

            InternalKnowledgePackage pkg = CoreComponentFactory.get().createKnowledgePackage( "org.drools" );

            final RuleDescr ruleDescr = pkgDescr.getRules().get( 0 );

            final KnowledgeBuilderImpl pkgBuilder = new KnowledgeBuilderImpl( pkg );
            DialectCompiletimeRegistry dialectRegistry = pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectCompiletimeRegistry();
            Dialect dialect = dialectRegistry.getDialect( "mvel" );

            RuleBuildContext context = new RuleBuildContext( pkgBuilder,
                                                             ruleDescr,
                                                             dialectRegistry,
                                                             pkg,
                                                             dialect );

            RuleBuilder.build( context );

            assertThat(context.getErrors().isEmpty()).as(context.getErrors().toString()).isTrue();

            final RuleImpl rule = context.getRule();

            MVELConsequence mvelCons = (MVELConsequence) rule.getConsequence();
            mvelCons.compile( (MVELDialectRuntimeData) pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectRuntimeRegistry().getDialectData( "mvel" ) );
            String s = DebugTools.decompile( mvelCons.getCompExpr() );

            int fromIndex = 0;
            int count = 0;
            while ( (fromIndex = s.indexOf( "DEBUG_SYMBOL",
                                            fromIndex + 1 )) > -1 ) {
                count++;
            }
            assertThat(count).isEqualTo(4);
        } finally {
            MVELDebugHandler.setDebugMode( false );
        }

    }

    @Test
    public void testDebugSymbolCount() {
        String expr = "System.out.println( \"a1\" );\n" + "System.out.println( \"a2\" );\n" + "System.out.println( \"a3\" );\n" + "System.out.println( \"a4\" );\n";

        ParserContext context = new ParserContext();
        context.setDebugSymbols( true );
        context.addImport( "System",
                           System.class );
        context.setStrictTypeEnforcement( true );
        //context.setDebugSymbols( true );
        context.setSourceFile( "mysource" );

        ExpressionCompiler compiler = new ExpressionCompiler( expr, context );
        Serializable compiledExpression = compiler.compile();

        String s = DebugTools.decompile( compiledExpression );

        System.out.println( "s " + s );

        int fromIndex = 0;
        int count = 0;
        while ( (fromIndex = s.indexOf( "DEBUG_SYMBOL",
                                        fromIndex + 1 )) > -1 ) {
            count++;
        }
        assertThat(count).isEqualTo(4);

    }
    
    private RuleBuildContext        context;
    private RuleDescr               ruleDescr;
    private MVELConsequenceBuilder  builder;
    
    private void setupTest(String consequence, Map<String, Object> namedConsequences) {
        builder = new MVELConsequenceBuilder();

        InternalKnowledgePackage pkg = CoreComponentFactory.get().createKnowledgePackage( "org.drools.mvel.compiler.test" );
        pkg.addImport( new ImportDeclaration( Cheese.class.getCanonicalName() ) );

        KnowledgeBuilderConfigurationImpl conf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration().as(KnowledgeBuilderConfigurationImpl.KEY);
        KnowledgeBuilderImpl pkgBuilder = new KnowledgeBuilderImpl( pkg, conf );

        ruleDescr = new RuleDescr( "test consequence builder" );
        ruleDescr.setConsequence( consequence );
        ruleDescr.addAttribute( new AttributeDescr("dialect", "mvel") );
        
        for ( Entry<String, Object> entry : namedConsequences.entrySet() ) {
            ruleDescr.addNamedConsequences( entry.getKey(), entry.getValue() );
        }

        RuleImpl rule = new RuleImpl( ruleDescr.getName() );
        rule.addPattern( new Pattern( 0,
                                      new ClassObjectType( Cheese.class ),
                                      "$cheese" ) );
        
        rule.addPattern( new Pattern( 0,
                                      new ClassObjectType( Map.class ),
                                      "$map" ) );        

        PackageRegistry pkgRegistry = pkgBuilder.getPackageRegistry( pkg.getName() );
        DialectCompiletimeRegistry reg = pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectCompiletimeRegistry();
        context = new RuleBuildContext( pkgBuilder,
                                        ruleDescr,
                                        reg,
                                        pkg,
                                        reg.getDialect( pkgRegistry.getDialect() ) );
        context.getDeclarationResolver().pushOnBuildStack( rule.getLhs() );
        
        context.getDialect().getConsequenceBuilder().build( context, RuleImpl.DEFAULT_CONSEQUENCE_NAME );
        for ( String name : namedConsequences.keySet() ) {
            context.getDialect().getConsequenceBuilder().build( context, name );
        }
        
        context.getDialect().addRule( context );
        pkgRegistry.getPackage().addRule( context.getRule() );
        pkgBuilder.compileAll();
        pkgBuilder.reloadAll();
        if ( pkgBuilder.hasErrors() ) {
            fail(pkgBuilder.getErrors().toString());
        }
    }
    

    @Test
    public void testDefaultConsequenceCompilation() {
        String consequence = " System.out.println(\"this is a test:\" + $cheese);\n " +
        		"c1 = new Cheese().{ type = $cheese.type };" +
        		"c2 = new Cheese().{ type = $map[$cheese.type] };" +
        		"c3 = new Cheese().{ type = $map['key'] };";
        setupTest( consequence, new HashMap<String, Object>() );
         assertThat(context.getRule().getConsequence()).isNotNull();
        assertThat(context.getRule().hasNamedConsequences()).isFalse();
        assertThat(context.getRule().getConsequence() instanceof MVELConsequence).isTrue();
    }
    
    @Test
    public void testDefaultConsequenceWithSingleNamedConsequenceCompilation() {
        String defaultCon = " System.out.println(\"this is a test\" + $cheese);\n ";
        
        Map<String, Object> namedConsequences = new HashMap<String, Object>();
        String name1 =  " System.out.println(\"this is a test name1\" + $cheese);\n ";
        namedConsequences.put( "name1", name1 );
        
        setupTest( defaultCon, namedConsequences);

        assertThat(context.getRule().getConsequence() instanceof MVELConsequence).isTrue();

        assertThat(context.getRule().getNamedConsequence("name1") instanceof MVELConsequence).isTrue();
        
        assertThat(context.getRule().getNamedConsequence( "name1")).isNotSameAs(context.getRule().getConsequence());
    }
    
    @Test
    public void testDefaultConsequenceWithMultipleNamedConsequenceCompilation() {
        String defaultCon = " System.out.println(\"this is a test\" + $cheese);\n ";
        
        Map<String, Object> namedConsequences = new HashMap<String, Object>();
        String name1 =  " System.out.println(\"this is a test name1\" + $cheese);\n ";
        namedConsequences.put( "name1", name1 );
        String name2 =  " System.out.println(\"this is a test name2\" + $cheese);\n ";
        namedConsequences.put( "name2", name2 );
        
        setupTest( defaultCon, namedConsequences);

        assertThat(context.getRule().getConsequence() instanceof MVELConsequence).isTrue();

        assertThat(context.getRule().getNamedConsequence("name1") instanceof MVELConsequence).isTrue();

        assertThat(context.getRule().getNamedConsequence("name2") instanceof MVELConsequence).isTrue();
        
        assertThat(context.getRule().getNamedConsequence("name1")).isNotSameAs(context.getRule().getConsequence());
        assertThat(context.getRule().getNamedConsequence("name2")).isNotSameAs(context.getRule().getConsequence());
        assertThat(context.getRule().getNamedConsequence("name2")).isNotSameAs(context.getRule().getNamedConsequence( "name1"));
    }
}
