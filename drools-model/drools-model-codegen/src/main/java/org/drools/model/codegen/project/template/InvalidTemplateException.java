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
package org.drools.model.codegen.project.template;

import java.text.MessageFormat;

public class InvalidTemplateException extends RuntimeException {

    public InvalidTemplateException(TemplatedGenerator generator) {
        this(generator, "Wrong template");
    }

    public InvalidTemplateException(TemplatedGenerator generator, String errorMessage) {
        this(generator.targetTypeName(), generator.uncheckedTemplatePath(), errorMessage);
    }

    public InvalidTemplateException(String classType, String templateName, String errorMessage) {
        super(MessageFormat.format(
                "Invalid template for ''{0}'', file ''{1}'': {2}", classType, templateName, errorMessage));
    }

}