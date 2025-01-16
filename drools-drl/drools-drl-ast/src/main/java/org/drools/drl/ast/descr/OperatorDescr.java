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
package org.drools.drl.ast.descr;

import java.util.List;

/**
 * A descriptor to represent a relational operator
 */
public class OperatorDescr extends BaseDescr {
    private static final long serialVersionUID = 520l;

    // prefix for custom operators which are not built-in
    public static final String CUSTOM_OPERATOR_PREFIX = "##";

    private String            operator;
    private boolean           negated;
    private List<String>      parameters;

    // the alias is used during dumps to MVEL where the operator
    // is rewritten into a function call using an alias ID. The
    // left and right strings are caches of the parameters in
    // string format for analysis during compilation
    private String            alias;
    private String            leftString;
    private String            rightString;
    private boolean           leftIsHandle;
    private boolean           rightIsHandle;

    public OperatorDescr() { }

    public OperatorDescr(String operator,
                         boolean negated,
                         List<String> parameters) {
        setOperator( operator );
        this.negated = negated;
        this.parameters = parameters;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator( String operator ) {
        this.operator = operator != null ? trimOperator(operator) : null;
    }

    private String trimOperator(String operator) {
        operator = operator.trim();
        if (operator.startsWith(CUSTOM_OPERATOR_PREFIX)) {
            operator = operator.substring(CUSTOM_OPERATOR_PREFIX.length());
        }
        return operator;
    }

    public boolean isNegated() {
        return negated;
    }

    public void setNegated( boolean negated ) {
        this.negated = negated;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public String getParametersText() {
        if ( parameters != null ) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for ( String param : parameters ) {
                if ( first ) {
                    first = false;
                } else {
                    builder.append( "," );
                }
                builder.append( param );
            }
            return builder.toString();
        }
        return null;
    }

    public void setParameters( List<String> parameters ) {
        this.parameters = parameters;
    }

    /**
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @param alias the alias to set
     */
    public void setAlias( String alias ) {
        this.alias = alias;
    }

    @Override
    public String toString() {
        return (this.negated ? "not " : "") + this.operator + (this.parameters != null ? parameters.toString() : "");
    }

    /**
     * This is an internal cache of the left string parameter that is set during the rewrite
     * into MVEL, allowing for further analysis of the parameter type. 
     * 
     * @param left
     */
    public void setLeftString( String left ) {
        this.leftString = left;
    }

    /**
     * This is an internal cache of the right string parameter that is set during the rewrite
     * into MVEL, allowing for further analysis of the parameter type. 
     * 
     * @param right
     */
    public void setRightString( String right ) {
        this.rightString = right;
    }

    /**
     * This is an internal cache of the left string parameter that is set during the rewrite
     * into MVEL, allowing for further analysis of the parameter type. 
     * 
     * @return the leftString
     */
    public String getLeftString() {
        return leftString;
    }

    /**
     * This is an internal cache of the right string parameter that is set during the rewrite
     * into MVEL, allowing for further analysis of the parameter type.
     *  
     * @return the rightString
     */
    public String getRightString() {
        return rightString;
    }

    /**
     * @return the leftIsHandle
     */
    public boolean isLeftIsHandle() {
        return leftIsHandle;
    }

    /**
     * @param leftIsHandle the leftIsHandle to set
     */
    public void setLeftIsHandle( boolean leftIsHandle ) {
        this.leftIsHandle = leftIsHandle;
    }

    /**
     * @return the rightIsHandle
     */
    public boolean isRightIsHandle() {
        return rightIsHandle;
    }

    /**
     * @param rightIsHandle the rightIsHandle to set
     */
    public void setRightIsHandle( boolean rightIsHandle ) {
        this.rightIsHandle = rightIsHandle;
    }

}
