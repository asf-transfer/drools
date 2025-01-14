/*
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
package org.kie.yard.api.model;

import java.util.ArrayList;
import java.util.List;

import org.kie.j2cl.tools.yaml.mapper.api.YAMLDeserializer;
import org.kie.j2cl.tools.yaml.mapper.api.YAMLSerializer;
import org.kie.j2cl.tools.yaml.mapper.api.exception.YAMLDeserializationException;
import org.kie.j2cl.tools.yaml.mapper.api.internal.deser.YAMLDeserializationContext;
import org.kie.j2cl.tools.yaml.mapper.api.internal.ser.YAMLSerializationContext;
import org.kie.j2cl.tools.yaml.mapper.api.node.YamlMapping;
import org.kie.j2cl.tools.yaml.mapper.api.node.YamlNode;
import org.kie.j2cl.tools.yaml.mapper.api.node.YamlSequence;

public class RuleDefSerializer
        implements YAMLSerializer<Object>,
                   YAMLDeserializer<Object> {

    private int rowNumber = 1;

    @Override
    public Object deserialize(YamlMapping yamlMapping,
                              String s,
                              YAMLDeserializationContext yamlDeserializationContext) throws YAMLDeserializationException {
        return deserialize(yamlMapping.getNode(s), yamlDeserializationContext);
    }

    @Override
    public Object deserialize(YamlNode yamlNode,
                              YAMLDeserializationContext yamlDeserializationContext) {
        if (yamlNode instanceof YamlSequence) {
            final List<Comparable> items = getItems(yamlNode);
            return new InlineRule(rowNumber++, items);
        } else if (yamlNode instanceof YamlMapping) {
            final WhenThenRule whenThenRule = new WhenThenRule(rowNumber++);
            final YamlNode when = ((YamlMapping) yamlNode).getNode("when");
            final YamlNode then = ((YamlMapping) yamlNode).getNode("then");
            whenThenRule.setWhen(getItems(when));
            whenThenRule.setThen(then.asScalar().value());
            return whenThenRule;
        }
        return new IllegalArgumentException("Unknown rule format.");
    }

    private List<Comparable> getItems(final YamlNode yamlNode) {
        final List<Comparable> result = new ArrayList<>();
        if (yamlNode instanceof YamlSequence) {
            ((YamlSequence) yamlNode).iterator().forEachRemaining(x -> {
                final Comparable value = (Comparable) x.asScalar().value();
                result.add(value);
            });
        }
        return result;
    }

    @Override
    public void serialize(YamlMapping yamlMapping,
                          String s,
                          Object objects,
                          YAMLSerializationContext yamlSerializationContext) {
        // Not needed, we never serialize.
    }

    @Override
    public void serialize(YamlSequence yamlSequence,
                          Object objects,
                          YAMLSerializationContext yamlSerializationContext) {
        // Not needed, we never serialize.
    }
}
