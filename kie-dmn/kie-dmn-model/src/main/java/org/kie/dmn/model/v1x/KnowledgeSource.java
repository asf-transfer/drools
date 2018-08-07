package org.kie.dmn.model.v1x;

import java.util.List;

public interface KnowledgeSource extends DRGElement {

    List<AuthorityRequirement> getAuthorityRequirement();

    String getType();

    void setType(String value);

    DMNElementReference getOwner();

    void setOwner(DMNElementReference value);

    String getLocationURI();

    void setLocationURI(String value);

}
