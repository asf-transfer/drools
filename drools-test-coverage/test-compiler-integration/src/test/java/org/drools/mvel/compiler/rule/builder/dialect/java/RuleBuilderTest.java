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
package org.drools.mvel.compiler.rule.builder.dialect.java;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.List;

import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.ast.descr.AndDescr;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.compiler.rule.builder.RuleBuildContext;
import org.drools.compiler.rule.builder.RuleBuilder;
import org.drools.base.base.EnabledBoolean;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.Pattern;
import org.drools.base.time.TimeUtils;
import org.drools.core.time.impl.IntervalTimer;
import org.drools.drl.parser.DroolsParserException;
import org.drools.util.DateUtils;
import org.junit.jupiter.api.Test;
import org.drools.mvel.MVELConstraint;
import org.kie.internal.builder.conf.LanguageLevelOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuleBuilderTest {

    @Test
    public void testBuild() {
        final DrlParser parser = new DrlParser(LanguageLevelOption.DRL5);

        final KnowledgeBuilderImpl kBuilder = new KnowledgeBuilderImpl();
        kBuilder.addPackage(new PackageDescr("org.drools"));
        InternalKnowledgePackage pkg = kBuilder.getPackage("org.drools");

        final PackageDescr pkgDescr;
        try {
            pkgDescr = parser.parse( new InputStreamReader( getClass().getResourceAsStream( "nestedConditionalElements.drl" ) ) );
        } catch (DroolsParserException e) {
            throw new RuntimeException(e);
        }

        // just checking there is no parsing errors
        assertThat(parser.hasErrors()).as(parser.getErrors().toString()).isFalse();
        
        pkg.addGlobal("results", List.class);

        final RuleDescr ruleDescr = pkgDescr.getRules().get( 0 );
        final String ruleClassName = "RuleClassName.java";
        ruleDescr.setClassName( ruleClassName );
        ruleDescr.addAttribute(new AttributeDescr("dialect",
                                                  "java"));
        
        kBuilder.addPackage(pkgDescr);

        assertThat(kBuilder.getErrors().isEmpty()).as(kBuilder.getErrors().toString()).isTrue();

        final RuleImpl rule = kBuilder.getPackage("org.drools.mvel.compiler").getRule( "test nested CEs" );

        assertThat(rule.getDeclarations().size()).as("There should be 2 rule level declarations").isEqualTo(2);

        // second GE should be a not
        final GroupElement not = (GroupElement) rule.getLhs().getChildren().get( 1 );
        assertThat(not.isNot()).isTrue();
        // not has no outer declarations
        assertThat(not.getOuterDeclarations().isEmpty()).isTrue();
        assertThat(not.getInnerDeclarations().size()).isEqualTo(1);
        assertThat(not.getInnerDeclarations().containsKey("$state")).isTrue();

        // second not
        final GroupElement not2 = (GroupElement) ((GroupElement) not.getChildren().get( 0 )).getChildren().get( 1 );
        assertThat(not2.isNot()).isTrue();
        // not has no outer declarations
        assertThat(not2.getOuterDeclarations().isEmpty()).isTrue();
        assertThat(not2.getInnerDeclarations().size()).isEqualTo(1);
        assertThat(not2.getInnerDeclarations().containsKey("$likes")).isTrue();
    }

    @Test
    public void testBuildAttributes() {
        // creates mock objects
        final RuleBuildContext context = mock( RuleBuildContext.class );
        final RuleImpl rule = mock( RuleImpl.class );

        // creates input object
        final RuleDescr ruleDescr = new RuleDescr( "my rule" );
        ruleDescr.addAttribute( new AttributeDescr( "no-loop",
                                                    "true" ) );
        ruleDescr.addAttribute( new AttributeDescr( "auto-focus",
                                                    "false" ) );
        ruleDescr.addAttribute( new AttributeDescr( "agenda-group",
                                                    "my agenda" ) );
        ruleDescr.addAttribute( new AttributeDescr( "activation-group",
                                                    "my activation" ) );
        ruleDescr.addAttribute( new AttributeDescr( "lock-on-active",
                                                    "" ) );
        ruleDescr.addAttribute( new AttributeDescr( "enabled",
                                                    "false" ) );
        ruleDescr.addAttribute( new AttributeDescr( "duration",
                                                    "60" ) );
        ruleDescr.addAttribute( new AttributeDescr( "calendars",
                                                    "\"cal1\"" ) );
        ruleDescr.addAttribute( new AttributeDescr( "date-effective",
                                                    "10-Jul-1974" ) );
        ruleDescr.addAttribute( new AttributeDescr( "date-expires",
                                                    "10-Jul-2040" ) );

        // creates expected results
        final Calendar effective = Calendar.getInstance();
        effective.setTime( DateUtils.parseDate( "10-Jul-1974" ) );
        final Calendar expires = Calendar.getInstance();
        expires.setTime( DateUtils.parseDate( "10-Jul-2040" ) );

        // defining expectations on the mock object
        when( context.getRule() ).thenReturn( rule );
        when( context.getRuleDescr() ).thenReturn( ruleDescr );
        when( context.getKnowledgeBuilder() ).thenReturn( new KnowledgeBuilderImpl() );

        // calling the build method
        RuleBuilder.buildAttributes( context );

        // check expectations
        verify( rule ).setNoLoop( true );
        verify( rule ).setAutoFocus( false );
        verify( rule ).setAgendaGroup( "my agenda" );
        verify( rule ).setActivationGroup( "my activation" );
        verify( rule ).setLockOnActive( true );
        verify( rule ).setEnabled( EnabledBoolean.ENABLED_FALSE );
        verify( rule ).setTimer( new IntervalTimer( null,
                                                    null,
                                                    -1,
                                                    TimeUtils.parseTimeString( "60" ),
                                                    0 ) );
        verify( rule ).setCalendars( new String[]{"cal1"} );
        verify( rule ).setDateEffective( effective );
        verify( rule ).setDateExpires( expires );
    }

    @Test
    public void testBuildMetaAttributes() {
        // creates mock objects
        final RuleBuildContext context = mock( RuleBuildContext.class );
        final RuleImpl rule = mock( RuleImpl.class );

        // creates input object
        final RuleDescr ruleDescr = new RuleDescr( "my rule" );
        ruleDescr.addAnnotation( "ruleId",
                                 "123" );
        ruleDescr.addAnnotation( "author",
                                 "Bob Doe" );
        ruleDescr.addAnnotation( "text",
                                 "\"It's a quoted\\\" string\"" );

        // creates expected results
        // defining expectations on the mock object
        when( context.getRule() ).thenReturn( rule );
        when( context.getRuleDescr() ).thenReturn( ruleDescr );
        when( context.getKnowledgeBuilder() ).thenReturn( new KnowledgeBuilderImpl() );

        // calling the build method
        RuleBuilder.buildMetaAttributes( context );

        // check expectations
        verify( rule ).addMetaAttribute( "ruleId",
                                         123 );
        verify( rule ).addMetaAttribute( "author",
                                         "Bob Doe" );
        verify( rule ).addMetaAttribute( "text",
                                         "It's a quoted\" string" );
    }

    @Test
    public void testBuildDurationExpression() {
        // creates mock objects
        final RuleBuildContext context = mock( RuleBuildContext.class );
        final RuleImpl rule = mock( RuleImpl.class );

        // creates input object
        final RuleDescr ruleDescr = new RuleDescr( "my rule" );
        ruleDescr.addAttribute( new AttributeDescr( "duration",
                                                    "( 1h30m )" ) );
        ruleDescr.addAttribute( new AttributeDescr( "calendars",
                                                    "[\"cal1\", \"cal2\"]" ) );

        // defining expectations on the mock object
        when( context.getRule() ).thenReturn( rule );
        when( context.getRuleDescr() ).thenReturn( ruleDescr );

        // calling the build method
        RuleBuilder.buildAttributes( context );

        // check expectations
        verify( rule ).setTimer( new IntervalTimer( null,
                                                    null,
                                                    -1,
                                                    TimeUtils.parseTimeString( "1h30m" ),
                                                    0 ) );
        verify( rule ).setCalendars( new String[]{"cal1", "cal2"} );
    }

    @Test
    public void testBuildBigDecimalLiteralConstraint() {
        final PackageDescr pkgDescr = new PackageDescr( "org.drools" );
        final RuleDescr ruleDescr = new RuleDescr( "Test Rule" );
        AndDescr andDescr = new AndDescr();
        PatternDescr patDescr = new PatternDescr( "java.math.BigDecimal",
                                                  "$bd" );
        ExprConstraintDescr fcd = new ExprConstraintDescr( "this == 10" );
        patDescr.addConstraint( fcd );
        andDescr.addDescr( patDescr );
        ruleDescr.setLhs( andDescr );
        ruleDescr.setConsequence( "" );
        pkgDescr.addRule( ruleDescr );

        final KnowledgeBuilderImpl kBuilder = new KnowledgeBuilderImpl();
        kBuilder.addPackage(pkgDescr);

        assertThat(kBuilder.getErrors().isEmpty()).as(kBuilder.getErrors().toString()).isTrue();

        final RuleImpl rule = kBuilder.getPackages()[0].getRule( "Test Rule" );
        final GroupElement and = rule.getLhs();
        final Pattern pat = (Pattern) and.getChildren().get( 0 );
        if (pat.getConstraints().get(0) instanceof MVELConstraint) {
            final MVELConstraint fc = (MVELConstraint) pat.getConstraints().get( 0 );
            assertThat(fc.getField().getValue() instanceof BigDecimal).as("Wrong class. Expected java.math.BigDecimal. Found: " + fc.getField().getValue().getClass()).isTrue();
        }
    }

    @Test
    public void testInvalidDialect() {
        final PackageDescr pkgDescr = new PackageDescr( "org.drools" );
        final RuleDescr ruleDescr = new RuleDescr( "Test Rule" );
        ruleDescr.addAttribute( new AttributeDescr( "dialect", "mvl" ) );
        ruleDescr.setConsequence( "" );
        pkgDescr.addRule( ruleDescr );

        final KnowledgeBuilderImpl kBuilder = new KnowledgeBuilderImpl();
        try {
            kBuilder.addPackage(pkgDescr);
            fail("Use of an invalid dialect should cause an exception");
        } catch (Exception e) {
            // ignore
        }
    }
    
    @Test
    public void testBuildBigIntegerLiteralConstraint() {
        final PackageDescr pkgDescr = new PackageDescr( "org.drools" );
        final RuleDescr ruleDescr = new RuleDescr( "Test Rule" );
        AndDescr andDescr = new AndDescr();
        PatternDescr patDescr = new PatternDescr( "java.math.BigInteger",
                                                  "$bd" );
        ExprConstraintDescr fcd = new ExprConstraintDescr( "this==10" );
        patDescr.addConstraint( fcd );
        andDescr.addDescr( patDescr );
        ruleDescr.setLhs( andDescr );
        ruleDescr.setConsequence( "" );
        pkgDescr.addRule( ruleDescr );

        final KnowledgeBuilderImpl kBuilder = new KnowledgeBuilderImpl();
        kBuilder.addPackage(pkgDescr);

        assertThat(kBuilder.getErrors().isEmpty()).as(kBuilder.getErrors().toString()).isTrue();

        final RuleImpl rule = kBuilder.getPackages()[0].getRule( "Test Rule" );
        final GroupElement and = rule.getLhs();
        final Pattern pat = (Pattern) and.getChildren().get( 0 );
        if (pat.getConstraints().get(0) instanceof MVELConstraint) {
            final MVELConstraint fc = (MVELConstraint) pat.getConstraints().get( 0 );
            assertThat(fc.getField().getValue() instanceof BigInteger).as("Wrong class. Expected java.math.BigInteger. Found: " + fc.getField().getValue().getClass()).isTrue();
        }
    }

}
