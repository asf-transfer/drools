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
package org.drools.core.base.accumulators;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;

/**
 * An implementation of an accumulator capable of calculating maximum values
 */
public class BigIntegerMaxAccumulateFunction extends AbstractAccumulateFunction<BigIntegerMaxAccumulateFunction.MaxData> {

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

    }

    public void writeExternal(ObjectOutput out) throws IOException {

    }

    protected static class MaxData implements Externalizable {
        public BigInteger max = null;

        public MaxData() {}

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            max = (BigInteger) in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(max);
        }

        @Override
        public String toString() {
            return "max";
        }
    }

    public MaxData createContext() {
        return new MaxData();
    }

    public void init(MaxData data) {
        data.max = null;
    }

    public void accumulate(MaxData data,
                           Object value) {
        if (value != null) {
            BigInteger biValue = (BigInteger) value;
            data.max = data.max == null || data.max.compareTo(biValue) < 0 ?
                       biValue :
                       data.max;
        }
    }

    public void reverse(MaxData data,
                        Object value) {
    }

    @Override
    public boolean tryReverse( MaxData data, Object value ) {
        if (value != null) {
            return data.max.compareTo((BigInteger) value) > 0;
        }
        return true;
    }

    public Object getResult(MaxData data) {
        return data.max;
    }

    public boolean supportsReverse() {
        return false;
    }

    public Class<?> getResultType() {
        return BigInteger.class;
    }
}
