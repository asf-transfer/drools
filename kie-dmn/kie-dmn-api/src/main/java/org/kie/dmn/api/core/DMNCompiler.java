/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
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

package org.kie.dmn.api.core;

import org.kie.api.io.Resource;
import org.kie.dmn.api.marshalling.v1_1.DMNExtensionElementRegister;
import org.kie.dmn.model.v1_1.Definitions;

import java.io.Reader;

public interface DMNCompiler {

    DMNModel compile( Resource resource );

    DMNModel compile( Resource resource, DMNExtensionElementRegister extensionElementRegister );

    DMNModel compile( Reader source );

    DMNModel compile( Reader source, DMNExtensionElementRegister extensionElementRegister );

    DMNModel compile(Definitions dmndefs);
}
