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
package org.kie.scanner.concurrent;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.core.util.FileManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.scanner.AbstractKieCiTest;
import org.kie.scanner.KieMavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.scanner.KieMavenRepository.getKieMavenRepository;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class ConcurrentBuildTest extends AbstractKieCiTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentBuildTest.class);

    private FileManager fileManager;

    @BeforeEach
    public void setUp() throws Exception {
        this.fileManager = new FileManager();
        this.fileManager.setUp();
        ReleaseId releaseId = KieServices.Factory.get().newReleaseId("org.kie", "scanner-test", "1.0-SNAPSHOT");
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.fileManager.tearDown();
    }

    // This is TurtleTest. You can run this test with -PrunTurtleTests
    @Test
    @Timeout(600000)
    public void concurrentBuildWithDependency() throws Exception {
        KieServices ks = KieServices.Factory.get();
        KieMavenRepository repository = getKieMavenRepository();

        final int testLoop = 10;
        for (int m = 0; m < testLoop; m++) {
            System.out.println("===== test loop " + m + " start");

            // test-dep-a exists in KieRepositoryImpl$KieModuleRepo
            // To resolve test-dep-a, KieRepositoryImpl$KieModuleRepo.load -> KieRepositoryScannerImpl.getArtifactVersion
            //    , so the lock order is kieModuleRepo -> kieScanner
            System.out.println("===== dep A start");
            ReleaseId releaseIdDepA = ks.newReleaseId("org.kie", "test-dep-a", "1.0-SNAPSHOT");
            InternalKieModule kJarDepA = createKieJar(ks, releaseIdDepA, false, "ruleA");
            repository.installArtifact(releaseIdDepA, kJarDepA, createKPom(fileManager, releaseIdDepA));

            // test-dep-b does not exist in KieRepositoryImpl$KieModuleRepo. Instead, it is installed in local Maven repository
            // To resolve test-dep-b, KieRepositoryImpl.loadKieModuleFromMavenRepo -> KieRepositoryImpl$KieModuleRepo.load
            //    , so the lock order is kieScanner -> kieModuleRepo
            // Note: This deadlock scenario happens only in 7.x (before RHDM-2028), because since Drools 8,
            //    ReleaseIdImpl usage is refactored and we no longer use ReleaseIdImpl.setSnapshotVersion.
            //    But we keep this test to detect a regression.
            System.out.println("===== dep B start");
            ReleaseId releaseIdDepB = ks.newReleaseId("org.kie", "test-dep-b", "1.0-SNAPSHOT");
            InternalKieModule kJarDepB = createKieJarWithDependencies(ks, releaseIdDepB, false, "ruleB", releaseIdDepA); // test-dep-b depends on test-dep-a
            repository.installArtifact(releaseIdDepB, kJarDepB, createKPom(fileManager, releaseIdDepB, releaseIdDepA));
            KieServices.Factory.get().getRepository().removeKieModule(releaseIdDepB);

            System.out.println("===== dep artifacts are ready. Start concurrent build");

            final int maxThread = 20;
            ExecutorService executor = Executors.newFixedThreadPool(maxThread);

            for (int n = 0; n < maxThread; n++) {
                final int i = n;
                executor.execute(() -> {
                    ReleaseId releaseId = ks.newReleaseId("org.kie", "test-" + i, "1.0-SNAPSHOT");
                    try {
                        ReleaseId myDependencyId = i % 2 == 0 ? releaseIdDepA : releaseIdDepB;
                        InternalKieModule kJar = createKieJarWithDependencies(ks, releaseId, false, "rule" + i, myDependencyId);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(300, TimeUnit.SECONDS);

            // cleanup
            KieServices.Factory.get().getRepository().removeKieModule(releaseIdDepA);
            KieServices.Factory.get().getRepository().removeKieModule(releaseIdDepB);
        }
        assertThat(true).isTrue(); // no deadlock
    }
}