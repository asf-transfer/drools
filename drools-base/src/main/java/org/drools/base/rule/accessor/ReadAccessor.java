/*
 * Copyright 2005 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.base.rule.accessor;

import java.lang.reflect.Method;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;

/**
 * A public interface for Read accessors
 */
public interface ReadAccessor {

    Object getValue(Object object);

    ValueType getValueType();

    Class< ? > getExtractToClass();

    String getExtractToClassName();

    Method getNativeReadMethod();

    String getNativeReadMethodName();

    int getHashCode(Object object);

    int getIndex();

    Object getValue(ValueResolver valueResolver, Object object);

    char getCharValue(ValueResolver valueResolver, Object object);

    int getIntValue(ValueResolver valueResolver, Object object);

    byte getByteValue(ValueResolver valueResolver, Object object);

    short getShortValue(ValueResolver valueResolver, Object object);

    long getLongValue(ValueResolver valueResolver, Object object);

    float getFloatValue(ValueResolver valueResolver, Object object);

    double getDoubleValue(ValueResolver valueResolver, Object object);

    boolean getBooleanValue(ValueResolver valueResolver, Object object);

    boolean isNullValue(ValueResolver valueResolver, Object object);

    int getHashCode(ValueResolver valueResolver, Object object);

    boolean isGlobal();

    boolean isSelfReference();
}
