//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.08.06 at 04:40:00 PM CEST 
//


package org.kie.dmn.model.v1_2;

import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.model.v1x.Context;
import org.kie.dmn.model.v1x.ContextEntry;

public class TContext extends TExpression implements Context {

    protected List<ContextEntry> contextEntry;

    @Override
    public List<ContextEntry> getContextEntry() {
        if (contextEntry == null) {
            contextEntry = new ArrayList<ContextEntry>();
        }
        return this.contextEntry;
    }

}
