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
package org.kie.dmn.feel.runtime.functions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELCollectionFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class IndexOfFunction
        extends BaseFEELFunction implements FEELCollectionFunction {

    public static final IndexOfFunction INSTANCE = new IndexOfFunction();

    private IndexOfFunction() {
        super( "index of" );
    }

    public FEELFnResult<List<BigDecimal>> invoke(@ParameterName( "list" ) List list, @ParameterName( "match" ) Object match) {
        if ( list == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }

        final List<BigDecimal> result = new ArrayList<>();
        for( int i = 0; i < list.size(); i++ ) {
            Object o = list.get( i );
            if ( o == null && match == null) {
                result.add( BigDecimal.valueOf( i+1L ) );
            } else if ( o != null && match != null ) {
                if ( equalsAsBigDecimals(o, match) || o.equals(match) ) {
                    result.add( BigDecimal.valueOf( i+1L ) );
                }
            }
        }
        return FEELFnResult.ofResult( result );
    }

    private boolean equalsAsBigDecimals(final Object object, final Object match) {
        return (object instanceof BigDecimal)
                && (match instanceof BigDecimal)
                && ((BigDecimal) object).compareTo((BigDecimal) match) == 0;
    }
}
