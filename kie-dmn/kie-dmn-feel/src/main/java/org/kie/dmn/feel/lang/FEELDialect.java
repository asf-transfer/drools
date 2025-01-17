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
package org.kie.dmn.feel.lang;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


public enum FEELDialect {

    FEEL(""),
    BFEEL("https://www.omg.org/spec/DMN/20240513/B-FEEL/");

    private final String namespace;

    FEELDialect(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {return namespace;}

    public final static Set<String> STANDARD_FEEL_URIS = getStandardFEELDialectURIS();

    /**
     * It returns <code>BFEEL</code> if provided namespace matches,
     * or <code>FEEL</code> if it matches any of the <code>STANDARD_FEEL_URIS</code>,
     * or throws an <code>IllegalArgumentException</code> if no match is found
     *
     * @param namespace
     * @return
     */
    public static FEELDialect fromNamespace(String namespace) {
        Optional<FEELDialect> byNamespace = Arrays.stream(FEELDialect.values())
                .filter(dialect -> dialect.getNamespace().equals(namespace))
                .findFirst();
        if (byNamespace.isPresent()) {
            return byNamespace.get();
        }
        Optional<FEELDialect> fromStandardFeelUris = getStandardFeelDialect(namespace);
        if (fromStandardFeelUris.isPresent()) {
            return fromStandardFeelUris.get();
        }
        throw new IllegalArgumentException("Unknown FEEL dialect '" + namespace + "'");
    }

    static Optional<FEELDialect> getStandardFeelDialect(String namespace) {
        return STANDARD_FEEL_URIS.contains(namespace) ? Optional.of(FEEL) : Optional.empty();
    }

    private static Set<String> getStandardFEELDialectURIS() {
        Set<String> toReturn = new HashSet<>();
        toReturn.add(org.kie.dmn.model.v1_1.KieDMNModelInstrumentedBase.URI_FEEL);
        toReturn.add(org.kie.dmn.model.v1_2.KieDMNModelInstrumentedBase.URI_FEEL);
        toReturn.add(org.kie.dmn.model.v1_3.KieDMNModelInstrumentedBase.URI_FEEL);
        toReturn.add(org.kie.dmn.model.v1_4.KieDMNModelInstrumentedBase.URI_FEEL);
        toReturn.add(org.kie.dmn.model.v1_5.KieDMNModelInstrumentedBase.URI_FEEL);
        return toReturn;
    }
}