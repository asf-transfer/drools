package org.kie.dmn.model.v1x;

import java.util.List;

public interface Context extends Expression {

    List<ContextEntry> getContextEntry();

}
