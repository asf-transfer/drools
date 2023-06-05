/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.drlonyaml.model;

import java.util.Objects;

import org.drools.drl.ast.descr.GlobalDescr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"type", "id"})
public class Global {
    @JsonProperty(required = true)
    private String type;
    @JsonProperty(required = true)
    private String id;

    public static Global from(GlobalDescr r) {
        Objects.requireNonNull(r);
        Global result = new Global();
        result.type = r.getType();
        result.id = r.getIdentifier();
        return result;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }
}
