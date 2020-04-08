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

package org.kie.pmml.models.tree.compiler.factories;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.IntStream;

import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.junit.Test;
import org.kie.pmml.commons.model.enums.DATA_TYPE;
import org.kie.pmml.models.drooled.ast.KiePMMLDrooledType;
import org.kie.pmml.models.drooled.tuples.KiePMMLOriginalTypeGeneratedType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.kie.pmml.commons.utils.DrooledModelUtils.getSanitizedClassName;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLTreeModelASTTestUtils.getDottedTypeDataField;
import static org.kie.pmml.models.tree.compiler.factories.KiePMMLTreeModelASTTestUtils.getTypeDataField;

public class KiePMMLTreeModelDataDictionaryASTFactoryTest {

    @Test
    public void declareTypes() {
        List<DataField> dataFields = Arrays.asList(getTypeDataField(), getDottedTypeDataField(), getTypeDataField(), getDottedTypeDataField());
        DataDictionary dataDictionary = new DataDictionary(dataFields);
        final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap = new HashMap<>();
        Queue<KiePMMLDrooledType> retrieved = KiePMMLTreeModelDataDictionaryASTFactory.factory(fieldTypeMap).declareTypes(dataDictionary);
        assertNotNull(retrieved);
        assertEquals(dataFields.size(), retrieved.size());
        IntStream.range(0, dataFields.size()).forEach(i -> commonVerifyTypeDeclarationDescr(dataFields.get(i), fieldTypeMap, retrieved.poll()));
    }

    @Test
    public void declareType() {
        DataField dataField = getTypeDataField();
        final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap = new HashMap<>();
        KiePMMLDrooledType retrieved = KiePMMLTreeModelDataDictionaryASTFactory.factory(fieldTypeMap).declareType(dataField);
        assertNotNull(retrieved);
        commonVerifyTypeDeclarationDescr(dataField, fieldTypeMap, retrieved);
    }

    private void commonVerifyTypeDeclarationDescr(DataField dataField, Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap, final KiePMMLDrooledType kiePMMLDrooledType) {
        String expectedGeneratedType = getSanitizedClassName(dataField.getName().getValue().toUpperCase());
        String expectedMappedOriginalType = DATA_TYPE.byName(dataField.getDataType().value()).getMappedClass().getSimpleName();
        assertEquals(expectedGeneratedType, kiePMMLDrooledType.getName());
        assertEquals(expectedMappedOriginalType, kiePMMLDrooledType.getType());
        assertTrue(fieldTypeMap.containsKey(dataField.getName().getValue()));
        KiePMMLOriginalTypeGeneratedType kiePMMLOriginalTypeGeneratedType = fieldTypeMap.get(dataField.getName().getValue());
        assertEquals(dataField.getDataType().value(), kiePMMLOriginalTypeGeneratedType.getOriginalType());
        assertEquals(expectedGeneratedType, kiePMMLOriginalTypeGeneratedType.getGeneratedType());
    }
}