/*
 * Copyright 2005 JBoss Inc
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

package org.drools.modelcompiler;

import java.util.ArrayList;
import java.util.List;

import org.drools.modelcompiler.domain.Person;
import org.junit.Test;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class MvelDialectMapTest extends BaseModelTest {

    public MvelDialectMapTest(RUN_TYPE testRunType ) {
        super( testRunType );
    }


    @Test
    public void testMVELMapSyntax() {
        final String drl = "" +
                "import java.util.*;\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "global java.util.List results;\n" +
                "\n" +
                "dialect \"mvel\"\n" +
                "\n" +
                "rule \"rule1\"\n" +
                "  when\n" +
                "    m: Person($i : itemsString[\"key1\"])" +
                "\n" +
                "  then\n" +
                "   results.add($i);" +
                "end";

        KieSession ksession = getKieSession(drl);

        List<String> results = new ArrayList<>();
        ksession.setGlobal("results", results);

        Person p = new Person("Luca");
        p.getItemsString().put("key1", "item1");
        p.getItemsString().put("key2", "item2");

        ksession.insert(p);

        assertEquals(1, ksession.fireAllRules());

        assertThat(results).containsExactly("item1");
    }

  }
