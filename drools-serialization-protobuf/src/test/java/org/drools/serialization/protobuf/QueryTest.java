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
package org.drools.serialization.protobuf;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.drools.commands.runtime.FlatQueryResults;
import org.drools.core.QueryResultsImpl;
import org.drools.core.QueryResultsRowImpl;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.runtime.rule.impl.FlatQueryResultRow;
import org.drools.mvel.CommonTestMethodBase;
import org.drools.mvel.compiler.Cheese;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryTest extends CommonTestMethodBase {


    private static QueryResults getQueryResults(KieSession session, String queryName, Object... arguments ) throws Exception {
        QueryResultsImpl results = (QueryResultsImpl) session.getQueryResults( queryName, arguments );

        FlatQueryResults flatResults = new FlatQueryResults(results);

        assertThat(flatResults.size()).as("Query results size").isEqualTo(results.size());
        assertThat(flatResults.getIdentifiers().length).as("Query results identifiers").isEqualTo(results.getIdentifiers().length);
        Set<String> resultIds = new TreeSet<String>(Arrays.asList(results.getIdentifiers()));
        Set<String> flatIds = new TreeSet<String>(Arrays.asList(flatResults.getIdentifiers()));
        assertThat(flatIds.toArray() ).as("Flat query results identifiers").isEqualTo(resultIds.toArray());

        String [] identifiers = results.getIdentifiers();
        Iterator<QueryResultsRow> copyFlatIter = flatResults.iterator();
        for( int i = 0; i < results.size(); ++i ) {
            QueryResultsRow row = results.get(i);
            assertThat(copyFlatIter.hasNext()).as("Round-tripped flat query results contain less rows than original query results").isTrue();
            QueryResultsRow copyRow = copyFlatIter.next();
            for( String id : identifiers ) {
                Object obj = row.get(id);
                if( obj != null ) {
                    Object copyObj = copyRow.get(id);
                    assertThat(obj != null && obj.equals(copyObj)).as("Flat query result [" + i + "] does not contain result: '" + id + "': " + obj + "/" + copyObj).isTrue();
                }
                FactHandle fh = row.getFactHandle(id);
                FactHandle copyFh = copyRow.getFactHandle(id);
                if( fh != null ) {
                    assertThat(copyFh).as("Flat query result [" + i + "] does not contain facthandle: '" + ((InternalFactHandle) fh).getId() + "'").isNotNull();
                    String fhStr = fh.toExternalForm();
                    fhStr = fhStr.substring(0, fhStr.lastIndexOf(":"));
                    String copyFhStr = copyFh.toExternalForm();
                    copyFhStr = copyFhStr.substring(0, copyFhStr.lastIndexOf(":"));
                    assertThat(copyFhStr).as("Unequal fact handles for fact handle '" + ((InternalFactHandle) fh).getId() + "':").isEqualTo(fhStr);
                }
            }
        }

        // check identifiers
        Set<String> copyFlatIds = new TreeSet<String>(Arrays.asList(flatResults.getIdentifiers()));
        assertThat(copyFlatIds.toArray() ).as("Flat query results identifiers").isEqualTo(flatIds.toArray());
        return flatResults;
    }

    @Test
    public void testQuery() throws Exception {
        KieBase kbase = loadKnowledgeBase("simple_query_test.drl");
        KieSession session = createKieSession( kbase );

        final Cheese stilton = new Cheese( "stinky",
                5 );
        FactHandle factHandle = session.insert( stilton );
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);

        String queryName = "simple query";
        org.kie.api.runtime.rule.QueryResults results = getQueryResults(session, queryName);
        assertThat(results.size()).isEqualTo(1);

        QueryResultsRow row = results.iterator().next();
        if( row instanceof FlatQueryResultRow ) {
            FlatQueryResultRow flatRow = (FlatQueryResultRow) row;
            assertThat(flatRow.getIdentifiers().size()).isEqualTo(0);
        } else if( row instanceof QueryResultsRowImpl ) {
            QueryResultsRowImpl rowImpl = (QueryResultsRowImpl) row;
            assertThat(rowImpl.getDeclarations().size()).isEqualTo(0);
        }
    }

    @Test
    public void testQueryRemoval() throws Exception {
        KieBase kbase = loadKnowledgeBase("simple_query_test.drl");
        KieSession session = createKieSession( kbase );

        final Cheese stilton = new Cheese( "stinky",
                                           5 );
        session.insert( stilton );
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);
        QueryResults results = session.getQueryResults( "simple query" );
        assertThat(results.size()).isEqualTo(1);

        Rule rule = kbase.getKiePackage( "org.drools.compiler.test" ).getRules().iterator().next();

        assertThat(rule.getName()).isEqualTo("simple query");

        kbase.removeQuery( "org.drools.compiler.test",
                           "simple query" );

        assertThat(kbase.getKiePackage("org.drools.compiler.test").getRules().isEmpty()).isTrue();

        try {
            results = session.getQueryResults( "simple query" );
        } catch ( Exception e ) {
            assertThat(e.getMessage().endsWith("does not exist")).isTrue();
        }
    }
}
