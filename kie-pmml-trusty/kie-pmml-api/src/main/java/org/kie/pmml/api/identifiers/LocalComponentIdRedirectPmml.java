/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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
package org.kie.pmml.api.identifiers;

import java.util.Objects;

import org.kie.efesto.common.api.identifiers.LocalUri;

public class LocalComponentIdRedirectPmml extends AbstractModelLocalUriIdPmml {

    private static final long serialVersionUID = -4610916178245973385L;

    public LocalComponentIdRedirectPmml(String redirectModel, String fileName, String name) {
        super(LocalUri.Root.append(redirectModel).append(fileName).append(name), fileName, name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        LocalComponentIdRedirectPmml that = (LocalComponentIdRedirectPmml) o;
        return Objects.equals(fileName, that.fileName) && Objects.equals(name, that.name);
    }

}
