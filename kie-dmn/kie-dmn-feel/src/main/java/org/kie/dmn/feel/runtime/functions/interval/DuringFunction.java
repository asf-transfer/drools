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
package org.kie.dmn.feel.runtime.functions.interval;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELBooleanFunction;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.Range.RangeBoundary;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.runtime.functions.BaseFEELFunction;
import org.kie.dmn.feel.runtime.functions.FEELFnResult;
import org.kie.dmn.feel.runtime.functions.ParameterName;

public class DuringFunction
        extends BaseFEELFunction implements FEELBooleanFunction {

    public static final DuringFunction INSTANCE = new DuringFunction();

    private DuringFunction() {
        super( "during" );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "point" ) Comparable point, @ParameterName( "range" ) Range range) {
        if ( point == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "point", "cannot be null"));
        }
        if ( range == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range", "cannot be null"));
        }
        try {
            boolean result = (range.getLowEndPoint().compareTo(point) < 0 && range.getHighEndPoint().compareTo(point) > 0) ||
                             (range.getLowEndPoint().compareTo(point) == 0 && range.getLowBoundary() == RangeBoundary.CLOSED) ||
                             (range.getHighEndPoint().compareTo(point) == 0 && range.getHighBoundary() == RangeBoundary.CLOSED);
            return FEELFnResult.ofResult( result );
        } catch( Exception e ) {
            // points are not comparable
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "point", "cannot be compared to range"));
        }
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "range1" ) Range range1, @ParameterName( "range2" ) Range range2) {
        if ( range1 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range1", "cannot be null"));
        }
        if ( range2 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range2", "cannot be null"));
        }
        try {
            boolean result = (range2.getLowEndPoint().compareTo(range1.getLowEndPoint()) < 0 ||
                              (range2.getLowEndPoint().compareTo(range1.getLowEndPoint()) == 0 &&
                               (range2.getLowBoundary() == RangeBoundary.CLOSED ||
                                range1.getLowBoundary() == RangeBoundary.OPEN))) &&
                             (range2.getHighEndPoint().compareTo(range1.getHighEndPoint()) > 0 ||
                              (range2.getHighEndPoint().compareTo(range1.getHighEndPoint()) == 0 &&
                               (range2.getHighBoundary() == RangeBoundary.CLOSED ||
                                range1.getHighBoundary() == RangeBoundary.OPEN)));
            return FEELFnResult.ofResult( result );
        } catch( Exception e ) {
            // points are not comparable
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range1", "cannot be compared to range2"));
        }
    }

}
