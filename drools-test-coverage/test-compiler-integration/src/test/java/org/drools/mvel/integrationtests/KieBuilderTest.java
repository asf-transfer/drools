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
package org.drools.mvel.integrationtests;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.drools.mvel.compiler.Message;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.builder.InternalKieBuilder;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class KieBuilderTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        // TODO: EM failed with some tests. File JIRAs
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testResourceInclusion(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.mvel.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String drl2 = "package org.drools.mvel.compiler\n" +
                "rule R2 when\n" +
                "   $m : Message( message == \"Hi Universe\" )\n" +
                "then\n" +
                "end\n";

        final String drl3 = "package org.drools.mvel.compiler\n" +
                "rule R3 when\n" +
                "   $m : Message( message == \"Hello World\" )\n" +
                "then\n" +
                "end\n";

        final String drl4 = "package org.drools.mvel.compiler\n" +
                "rule R4 when\n" +
                "   $m : Message( message == \"Hello Earth\" )\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\" default=\"true\" eventProcessingMode=\"stream\" equalsBehavior=\"identity\" scope=\"jakarta.enterprise.context.ApplicationScoped\">\n" +
                "    <ksession name=\"ksession1\" type=\"stateful\" default=\"true\" clockType=\"realtime\" scope=\"jakarta.enterprise.context.ApplicationScoped\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        // Create an in-memory jar for version 1.0.0
        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );
        final Resource r2 = ResourceFactory.newByteArrayResource( drl2.getBytes() ).setResourceType( ResourceType.GDRL ).setSourcePath( "kbase1/drl2.gdrl" );
        final Resource r3 = ResourceFactory.newByteArrayResource( drl3.getBytes() ).setResourceType( ResourceType.RDRL ).setSourcePath( "kbase1/drl3.rdrl" );
        final Resource r4 = ResourceFactory.newByteArrayResource( drl4.getBytes() ).setResourceType( ResourceType.TDRL ).setSourcePath( "kbase1/drl4.tdrl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1, r2, r3, r4);

        final InternalKieModule ikm = (InternalKieModule) km;
        assertThat(ikm.getResource( r1.getSourcePath())).isNotNull();
        assertThat( ikm.getResource( r2.getSourcePath())).isNotNull();
        assertThat( ikm.getResource( r3.getSourcePath())).isNotNull();
        assertThat( ikm.getResource( r4.getSourcePath())).isNotNull();

        // Create a session and fire rules
        final KieContainer kc = ks.newKieContainer( km.getReleaseId() );
        final KieSession ksession = kc.newKieSession();
        ksession.insert( new Message( "Hello World" ) );
        assertThat(ksession.fireAllRules()).isEqualTo(2);
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testValidXsdTargetNamespace(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.mvel.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1);

        ks.newKieContainer( km.getReleaseId() );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInvalidXsdTargetNamespace(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/doesNotExist\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        // Create an in-memory jar for version 1.0.0
        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );

        assertThatThrownBy(() -> KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("XSD validation failed");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testOldXsdTargetNamespace(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.mvel.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://jboss.org/kie/6.0.0/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        // Create an in-memory jar for version 1.0.0
        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1);

        ks.newKieContainer( km.getReleaseId() );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testGetKieBaseAfterKieSessionCreation(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String KBASE_NAME = "kieBase";
        final String KSESSION_NAME = "kieSession";

        final String drl = "declare TestEvent\n" +
                "    @role( event )\n" +
                "    name : String\n" +
                "end\n" +
                "\n" +
                "declare window DeclaredTimeWindow\n" +
                "    TestEvent ( name == \"timeDec\" ) over window:time( 50ms ) from entry-point EventStream\n" +
                "end";

        final KieServices ks = KieServices.Factory.get();
        final KieFileSystem kfs = ks.newKieFileSystem();

        kfs.write( "src/main/resources/window.drl", drl );

        final KieModuleModel kmoduleModel = ks.newKieModuleModel();
        kmoduleModel.newKieBaseModel( KBASE_NAME )
                .addPackage( "*" )
                .newKieSessionModel( KSESSION_NAME )
                .setDefault( true )
                .setClockType( ClockTypeOption.PSEUDO );

        kfs.writeKModuleXML( kmoduleModel.toXML() );

        final KieBuilder builder = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, false);
        assertThat(builder.getResults().getMessages().size()).isEqualTo(0);
        ks.getRepository().addKieModule( builder.getKieModule() );

        final KieSession kieSession = ks.newKieContainer( ks.getRepository().getDefaultReleaseId() ).newKieSession( KSESSION_NAME );
        assertThat(kieSession).isNotNull();

        final KieBase kieBase = ks.newKieContainer( ks.getRepository().getDefaultReleaseId() ).getKieBase( KBASE_NAME );
        assertThat(kieBase).isNotNull();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testReportKBuilderErrorWhenUsingAJavaClassWithNoPkg(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // BZ-995018
        final String java = "public class JavaClass { }\n";
        final KieServices ks = KieServices.Factory.get();

        final KieFileSystem kfs = ks.newKieFileSystem()
                .write( "src/main/java/JavaClass.java", java );

        final KieBuilder kieBuilder = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, false);
        Results results = kieBuilder.getResults();

        System.out.println( results.getMessages() );

        assertThat(results.getMessages().size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJavaSourceFileAndDrlDeploy(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String java = "package org.drools.mvel.compiler;\n" +
                "public class JavaSourceMessage { }\n";
        final String drl = "package org.drools.mvel.compiler;\n" +
                "import org.drools.mvel.compiler.JavaSourceMessage;" +
                "rule R1 when\n" +
                "   $m : JavaSourceMessage()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource javaResource = ResourceFactory.newByteArrayResource( java.getBytes() ).setResourceType( ResourceType.JAVA )
                .setSourcePath( "org/drools/mvel/compiler/JavaSourceMessage.java" );
        final Resource drlResource = ResourceFactory.newByteArrayResource( drl.getBytes() ).setResourceType( ResourceType.DRL )
                .setSourcePath( "kbase1/drl1.drl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), javaResource, drlResource);

        final KieContainer kieContainer = ks.newKieContainer(km.getReleaseId());
        try {
            final Class<?> messageClass = kieContainer.getClassLoader().loadClass("org.drools.mvel.compiler.JavaSourceMessage");
            assertThat(messageClass).isNotNull();
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Loading the java class failed.", e);
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJavaSourceFileAndDrlDeployWithClassFilter(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String allowedJava = "package org.drools.mvel.compiler;\n" +
                "public class JavaSourceMessage { }\n";
        final String filteredJava = "package org.drools.mvel.compiler;\n" +
                "public class ClassCausingClassNotFoundException { non.existing.Type foo() { return null; } }\n";
        final String drl = "package org.drools.compiler;\n" +
                "import org.drools.mvel.compiler.JavaSourceMessage;" +
                "rule R1 when\n" +
                "   $m : JavaSourceMessage()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource allowedJavaResource = ResourceFactory.newByteArrayResource( allowedJava.getBytes() ).setResourceType( ResourceType.JAVA )
                .setSourcePath( "org/drools/mvel/compiler/JavaSourceMessage.java" );
        final Resource filteredJavaResource = ResourceFactory.newByteArrayResource( filteredJava.getBytes() ).setResourceType( ResourceType.JAVA )
                .setSourcePath( "org/drools/mvel/compiler/ClassCausingClassNotFoundException.java" );
        final Resource drlResource = ResourceFactory.newByteArrayResource( drl.getBytes() ).setResourceType( ResourceType.DRL )
                .setSourcePath( "kbase1/drl1.drl" );

        final Predicate<String> filter = fileName -> !fileName.endsWith( "org/drools/mvel/compiler/ClassCausingClassNotFoundException.java" );

        KieModule km = null;
        try {
            // KieBuilderImpl.buildAll( Predicate<String> classFilter ) only works with KieModuleKieProject. So not parameterized
            KieFileSystem kfs = KieUtil.getKieFileSystemWithKieModule(KieModuleModelImpl.fromXML(kmodule), releaseId1, allowedJavaResource, filteredJavaResource, drlResource);
            KieBuilder kieBuilder = ks.newKieBuilder(kfs);
            ((InternalKieBuilder)kieBuilder).buildAll(filter);
            km = kieBuilder.getKieModule();
            ks.getRepository().addKieModule(km);
        } catch ( final IllegalStateException ise ) {
            if ( ise.getMessage().contains( "org/drools/mvel/compiler/ClassCausingClassNotFoundException.java" ) ) {
                fail( "Build failed because source file was not filtered out." );
            } else {
                throw ise;
            }
        }

        final KieContainer kieContainer = ks.newKieContainer(km.getReleaseId());
        try {
            final Class<?> messageClass = kieContainer.getClassLoader().loadClass("org.drools.mvel.compiler.JavaSourceMessage");
            assertThat(messageClass).isNotNull();
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Loading the java class failed.", e);
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKieBuilderWithDotFiles(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // BZ-1044409
        final String KBASE_NAME = "kieBase";
        final String KSESSION_NAME = "kieSession";

        //This can be any DRL, or any file recognised by drools-compiler
        final String drl = "declare TestEvent\n" +
                "  name : String\n" +
                "end\n";

        final String dotDrl = "Meta-data used by kie-wb";

        final KieServices ks = KieServices.Factory.get();
        final KieFileSystem kfs = ks.newKieFileSystem();

        kfs.write( "src/main/resources/drlFile.drl", drl );
        kfs.write( "src/main/resources/.drlFile.drl", dotDrl );

        final KieModuleModel kmoduleModel = ks.newKieModuleModel();
        kmoduleModel.newKieBaseModel( KBASE_NAME )
                .addPackage( "*" )
                .newKieSessionModel( KSESSION_NAME )
                .setDefault( true );

        kfs.writeKModuleXML( kmoduleModel.toXML() );

        final KieBuilder builder = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, false);

        for ( final org.kie.api.builder.Message m : builder.getResults().getMessages() ) {
            System.out.println( m );
        }
        assertThat(builder.getResults().getMessages().size()).isEqualTo(0);

        ks.getRepository().addKieModule( builder.getKieModule() );

        final KieSession kieSession = ks.newKieContainer( ks.getRepository().getDefaultReleaseId() ).newKieSession( KSESSION_NAME );
        assertThat(kieSession).isNotNull();

        final KieBase kieBase = ks.newKieContainer( ks.getRepository().getDefaultReleaseId() ).getKieBase( KBASE_NAME );
        assertThat(kieBase).isNotNull();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMultipleKBaseWithDrlError(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHBRMS-2651
        final String drl = "package org.drools.compiler;\n" +
                     "rule \"test\"\n" +
                     "  when\n" +
                     "    Smurf\n" +
                     "  then\n" +
                     "end";

        final KieServices ks = KieServices.Factory.get();

        final KieModuleModel kproj = ks.newKieModuleModel();
        kproj.newKieBaseModel( "kbase1" ).newKieSessionModel( "ksession1" ).setDefault( true );
        kproj.newKieBaseModel( "kbase2" ).newKieSessionModel( "ksession2" );

        final ReleaseId releaseId = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final KieFileSystem kfs = ks.newKieFileSystem().generateAndWritePomXML( releaseId ).writeKModuleXML( kproj.toXML() );

        final Resource drlResource = ResourceFactory.newByteArrayResource( drl.getBytes() ).setResourceType( ResourceType.DRL )
                                              .setSourcePath( "kbase1/drl1.drl" );

        kfs.write( "src/main/resources/org/drools/compiler/drl1.drl", drlResource );

        final KieBuilder kb = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, false);

        final List<org.kie.api.builder.Message> messages = kb.getResults().getMessages( org.kie.api.builder.Message.Level.ERROR );
        assertThat(messages.size()).isEqualTo(4);

        assertThat(messages.get(0).toString().contains("kbase1")).isTrue();
        assertThat(messages.get(1).toString().contains("kbase1")).isTrue();
        assertThat(messages.get(2).toString().contains("kbase2")).isTrue();
        assertThat(messages.get(3).toString().contains("kbase2")).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testBuildWithKBaseAndKSessionWithIdenticalNames(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHBRMS-2689
        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                         "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                         "  <kbase name=\"name\">\n" +
                         "    <ksession name=\"name\" default=\"true\"/>\n" +
                         "  </kbase>\n" +
                         "</kmodule>";

        checkKModule( kieBaseTestConfiguration, kmodule, 0 );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testBuildWithDuplicatedKSessionNames(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHBRMS-2689
        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                         "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                         "  <kbase name=\"kbase1\">\n" +
                         "    <ksession name=\"ksessionA\" default=\"true\"/>\n" +
                         "  </kbase>\n" +
                         "  <kbase name=\"kbase2\">\n" +
                         "    <ksession name=\"ksessionA\"/>\n" +
                         "  </kbase>\n" +
                         "</kmodule>";

        checkKModule( kieBaseTestConfiguration, kmodule, 1 );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testBuildWithDuplicatedKBaseNames(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHBRMS-2689
        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                         "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                         "  <kbase name=\"kbase1\">\n" +
                         "    <ksession name=\"ksessionA\" default=\"true\"/>\n" +
                         "  </kbase>\n" +
                         "  <kbase name=\"kbase1\">\n" +
                         "    <ksession name=\"ksessionB\"/>\n" +
                         "  </kbase>\n" +
                         "</kmodule>";

        checkKModule( kieBaseTestConfiguration, kmodule, 1 );
    }

    private void checkKModule(KieBaseTestConfiguration kieBaseTestConfiguration , final String kmodule, final int expectedErrors) {
        final KieServices ks = KieServices.Factory.get();
        final ReleaseId releaseId = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final KieFileSystem kfs = ks.newKieFileSystem().generateAndWritePomXML( releaseId ).writeKModuleXML( kmodule );
        final KieBuilder kieBuilder = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, false);
        final Results results = kieBuilder.getResults();
        assertThat(results.getMessages(org.kie.api.builder.Message.Level.ERROR).size()).isEqualTo(expectedErrors);
        assertThat(((InternalKieBuilder) kieBuilder ).getKieModuleIgnoringErrors()).isNotNull();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testAddMissingResourceToPackageBuilder(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        assertThatThrownBy(() -> kbuilder.add(ResourceFactory.newClassPathResource("some.drl"), ResourceType.DRL))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> kbuilder.add(ResourceFactory.newClassPathResource("some.bpmn"), ResourceType.BPMN2))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeclarativeChannelRegistration(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.mvel.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\">\n" +
                "       <channels>\n" +
                "         <channel name=\"testChannel\" type=\"org.drools.mvel.integrationtests.KieBuilderTest$MockChannel\" />\n" +
                "       </channels>\n" +
                "    </ksession>" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1);

        KieContainer kieContainer = ks.newKieContainer( km.getReleaseId());
        
        KieSession kieSession = kieContainer.newKieSession();
        assertThat(kieSession.getChannels().size()).isEqualTo(1);
        assertThat(kieSession.getChannels().containsKey("testChannel")).isTrue();
    }
    
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testStatelessSessionDeclarativeChannelRegistration(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl1 = "package org.drools.mvel.compiler\n" +
                "rule R1 when\n" +
                "   $m : Message()\n" +
                "then\n" +
                "end\n";

        final String kmodule = "<kmodule xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                "         xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"kbase1\">\n" +
                "    <ksession name=\"ksession1\" default=\"true\" type=\"stateless\">\n" +
                "       <channels>\n" +
                "         <channel name=\"testChannel\" type=\"org.drools.mvel.integrationtests.KieBuilderTest$MockChannel\" />\n" +
                "       </channels>\n" +
                "    </ksession>" +
                "  </kbase>\n" +
                "</kmodule>";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = ks.newReleaseId( "org.kie", "test-kie-builder", "1.0.0" );
        final Resource r1 = ResourceFactory.newByteArrayResource( drl1.getBytes() ).setResourceType( ResourceType.DRL ).setSourcePath( "kbase1/drl1.drl" );

        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, KieModuleModelImpl.fromXML(kmodule), r1);

        KieContainer kieContainer = ks.newKieContainer( km.getReleaseId());
        
        StatelessKieSession statelessKieSession = kieContainer.newStatelessKieSession();
        assertThat(statelessKieSession.getChannels().size()).isEqualTo(1);
        assertThat(statelessKieSession.getChannels().containsKey("testChannel")).isTrue();
    }
    
    public static class MockChannel implements Channel {

        @Override
        public void send(Object object) {
            //NO=OP
        }
        
    }

}
