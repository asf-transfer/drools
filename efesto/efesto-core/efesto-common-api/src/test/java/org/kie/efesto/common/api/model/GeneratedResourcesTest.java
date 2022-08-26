/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.efesto.common.api.model;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.kie.efesto.common.api.identifiers.LocalUri;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;
import org.kie.efesto.common.api.identifiers.ReflectiveAppRoot;
import org.kie.efesto.common.api.identifiers.componentroots.ComponentRootA;
import org.kie.efesto.common.api.identifiers.componentroots.ComponentRootB;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedResourcesTest {

    @Test
    void add() {
        String fullClassName = "full.class.Path";
        GeneratedResource generatedClassResource = new GeneratedClassResource(fullClassName);
        String model = "foo";
        LocalUri localUri = new ReflectiveAppRoot(model)
                .get(ComponentRootB.class)
                .get("this", "is", "localUri")
                .asLocalUri();
        ModelLocalUriId localUriId = new ModelLocalUriId(localUri);
        GeneratedResource generatedFinalResource = new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName));
        GeneratedResources generatedResources = new GeneratedResources();
        generatedResources.add(generatedClassResource);
        generatedResources.add(generatedFinalResource);
        assertThat(generatedResources).hasSize(2);

        generatedResources = new GeneratedResources();
        generatedResources.add(new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName)));
        generatedResources.add(new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName)));
        assertThat(generatedResources).hasSize(1);

        generatedResources = new GeneratedResources();
        generatedResources.add(new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName)));
        generatedResources.add(new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName)));
        assertThat(generatedResources).hasSize(1);

        generatedResources = new GeneratedResources();
        generatedResources.add(new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName)));
        LocalUri localUriDifferent = new ReflectiveAppRoot(model)
                .get(ComponentRootA.class)
                .get("this", "different-localUri")
                .asLocalUri();
        ModelLocalUriId localUriIdDifferent = new ModelLocalUriId(localUriDifferent);
        generatedResources.add(new GeneratedExecutableResource(localUriIdDifferent, Collections.singletonList(fullClassName)));
        assertThat(generatedResources).hasSize(2);

        generatedClassResource = new GeneratedClassResource(fullClassName);
        generatedFinalResource = new GeneratedExecutableResource(localUriId, Collections.singletonList(fullClassName));
        generatedResources = new GeneratedResources();
        generatedResources.add(generatedClassResource);
        generatedResources.add(generatedFinalResource);
        assertThat(generatedResources).hasSize(2);
        assertThat(generatedResources.contains(generatedClassResource)).isTrue();
        assertThat(generatedResources.contains(generatedFinalResource)).isTrue();
    }

}