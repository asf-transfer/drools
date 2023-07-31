/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.reliability.core;

import java.io.Serializable;

public class SerializableStoredObject extends BaseStoredObject {

    protected final Serializable object;

    public SerializableStoredObject(Object object, boolean propagated) {
        super(propagated);
        if (!(object instanceof Serializable)) {
            throw new IllegalArgumentException("Object must be serializable : " + object.getClass().getCanonicalName());
        }
        this.object = (Serializable) object;
    }

    @Override
    public Serializable getObject() {
        return object;
    }

    @Override
    public String toString() {
        return "SerializableStoredObject{" +
                "object=" + object +
                ", propagated=" + propagated +
                '}';
    }
}