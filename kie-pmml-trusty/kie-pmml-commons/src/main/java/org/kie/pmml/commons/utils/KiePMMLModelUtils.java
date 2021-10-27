/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.pmml.commons.utils;

import java.util.UUID;

import org.kie.pmml.api.enums.DATA_TYPE;

public class KiePMMLModelUtils {

    private KiePMMLModelUtils() {
    }

    /**
     * Method to be used by <b>every</b> KiePMML implementation to retrieve the <b>package</b> name
     * out of the model name
     * @param modelName
     * @return
     */
    public static String getSanitizedPackageName(String modelName) {
        return modelName.replaceAll("[^A-Za-z0-9.]", "").toLowerCase();
    }

    /**
     * Convert the given <code>String</code> in a valid class name (i.e. no dots, no spaces, first letter upper case)
     * @param input
     * @return
     */
    public static String getSanitizedClassName(String input) {
        String upperCasedInput = input.substring(0, 1).toUpperCase() + input.substring(1);
        return upperCasedInput.replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * Convert the given <code>String</code> in a valid variable name (i.e. no dots, no spaces, first letter lower case)
     * @param input
     * @return
     */
    public static String getSanitizedVariableName(String input) {
        String lowerCasedInput = input.substring(0, 1).toLowerCase() + input.substring(1);
        return lowerCasedInput.replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * Returns an autogenerated classname whose name starts with the given <b>prefix</b>
     * @param prefix
     * @return
     */
    public static String getGeneratedClassName(final String prefix) {
        String rawName = prefix + UUID.randomUUID();
        return getSanitizedClassName(rawName);
    }

    public static Object commonEvaluate(Object rawObject, DATA_TYPE dataType) {
        return dataType != null && rawObject != null ? dataType.getActualValue(rawObject) : rawObject;
    }
}
