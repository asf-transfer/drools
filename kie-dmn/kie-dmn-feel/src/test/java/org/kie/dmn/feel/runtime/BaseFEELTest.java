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
package org.kie.dmn.feel.runtime;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.ObjectAssert;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.FEELProfile;
import org.kie.dmn.feel.lang.impl.FEELBuilder;
import org.kie.dmn.feel.parser.feel11.profiles.DoCompileFEELProfile;
import org.kie.dmn.feel.parser.feel11.profiles.KieExtendedFEELProfile;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public abstract class BaseFEELTest {

    public enum FEEL_TARGET {
        AST_INTERPRETED,
        JAVA_TRANSLATED
    }

    private FEEL feel = null; // due to @Parameter injection by JUnit framework, need to defer FEEL init to actual instance method, to have the opportunity for the JUNit framework to initialize all the @Parameters

    public String expression;

    public Object result;

    public FEELEvent.Severity severity;

    public FEEL_TARGET testFEELTarget;

    public boolean useExtendedProfile;

    public void expression(String expression, Object result, FEELEvent.Severity severity, FEEL_TARGET testFEELTarget,
                           Boolean useExtendedProfile, FEELDialect dialect) {
        this.expression = expression;
        this.result = result;
        this.severity = severity;
        this.testFEELTarget = testFEELTarget;
        this.useExtendedProfile = useExtendedProfile;

        final List<FEELProfile> profiles = getFEELProfilesForTests();
        feel = FEELBuilder.builder().withProfiles(profiles).withFEELDialect(dialect).build();
        final FEELEventListener listener = mock(FEELEventListener.class );
        feel.addListener( listener );
        feel.addListener(System.out::println);
        assertResult( expression, result );

        if( severity != null ) {
            final ArgumentCaptor<FEELEvent> captor = ArgumentCaptor.forClass(FEELEvent.class );
            verify( listener , atLeastOnce()).onEvent( captor.capture() );
            assertThat(captor.getValue().getSeverity()).isEqualTo(severity);
        } else {
            verify( listener, never() ).onEvent( any(FEELEvent.class) );
        }
    }

    protected abstract void instanceTest(String expression, Object result, FEELEvent.Severity severity,
                                         FEEL_TARGET testFEELTarget, Boolean useExtendedProfile, FEELDialect dialect);

    protected void assertResult(final String expression, final Object expected ) {
        Object retrieved = feel.evaluate( expression );
        String description = String.format("Evaluating: '%s'", expression);
        ObjectAssert<Object> assertion = assertThat(retrieved).as(description);
        if( expected == null ) {
        	assertion.isNull();
        } else if( expected instanceof Class<?> ) {
        	assertion.isInstanceOf((Class<?>) expected);
        } else {
        	assertion.isEqualTo(expected);
        }
    }

    protected static List<Object[]> addAdditionalParameters(final Object[][] cases, final boolean useExtendedProfile) {
        return addAdditionalParameters(cases, useExtendedProfile, FEELDialect.FEEL);
    }

    protected static List<Object[]> addAdditionalParameters(final Object[][] cases, final boolean useExtendedProfile, FEELDialect feelDialect) {
        final List<Object[]> toReturn = new ArrayList<>();
        for (final Object[] c : cases) {
            toReturn.add(new Object[]{c[0], c[1], c[2], FEEL_TARGET.AST_INTERPRETED, useExtendedProfile, feelDialect});
            toReturn.add(new Object[]{c[0], c[1], c[2], FEEL_TARGET.JAVA_TRANSLATED, useExtendedProfile, feelDialect});
        }
        return toReturn;
    }

    protected static List<Object[]> addAdditionalParametersForBothProfiles(final Object[][] cases, FEELDialect feelDialect) {
        final List<Object[]> toReturn = new ArrayList<>();
        final boolean[] useExtendedProfiles = { false, true };
        for (final Object[] c : cases) {
            for (boolean useExtendedProfile : useExtendedProfiles) {
                toReturn.add(new Object[]{c[0], c[1], c[2], FEEL_TARGET.AST_INTERPRETED, useExtendedProfile, feelDialect});
                toReturn.add(new Object[]{c[0], c[1], c[2], FEEL_TARGET.JAVA_TRANSLATED, useExtendedProfile, feelDialect});
            }
        }
        return toReturn;
    }

    private List<FEELProfile> getFEELProfilesForTests() {
        final List<FEELProfile> profiles = new ArrayList<>();
        if (testFEELTarget == FEEL_TARGET.JAVA_TRANSLATED) {
            profiles.add(new DoCompileFEELProfile());
        }
        if (useExtendedProfile) {
            profiles.add(new KieExtendedFEELProfile());
        }
        return profiles;
    }
}
