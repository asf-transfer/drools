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
package org.kie.pmml.api.model;

import java.util.Objects;
import java.util.function.Supplier;

import org.kie.pmml.api.model.abstracts.KiePMMLIDedNamed;
import org.kie.pmml.api.model.enums.MINING_FUNCTION;
import org.kie.pmml.api.model.enums.PMML_MODEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KIE representation of PMML model that use <b>drool</b> for implementation
 */
public abstract class KiePMMLDrooledModel extends KiePMMLModel {

    private static final long serialVersionUID = -6845971260164057040L;
    private static final Logger logger = LoggerFactory.getLogger(KiePMMLDrooledModel.class.getName());

    // Using Object to avoid add dependency on drools
    protected Object droolContent;;

    protected KiePMMLDrooledModel() {
    }

    public Object getDroolContent() {
        return droolContent;
    }

    @Override
    public String toString() {
        return "KiePMMLDrooledModel{" +
                "droolContent=" + droolContent +
                ", pmmlMODEL=" + pmmlMODEL +
                ", miningFunction=" + miningFunction +
                ", targetField='" + targetField + '\'' +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        KiePMMLDrooledModel that = (KiePMMLDrooledModel) o;
        return Objects.equals(droolContent, that.droolContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), droolContent);
    }

    public abstract static class Builder<T extends KiePMMLDrooledModel> extends KiePMMLIDedNamed.Builder<T> {

        protected Builder(String name, String prefix, PMML_MODEL pmmlMODEL, MINING_FUNCTION miningFunction, Supplier<T> supplier) {
            super(name, prefix, supplier);
            toBuild.pmmlMODEL = pmmlMODEL;
            toBuild.miningFunction = miningFunction;
        }

        public Builder<T> withTargetField(String targetField) {
            toBuild.targetField = targetField;
            return this;
        }

        public Builder<T> withContent(Object content) {
            toBuild.droolContent = content;
            return this;
        }
    }


}
