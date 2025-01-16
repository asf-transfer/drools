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
package org.drools.decisiontable.parser.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple parser for CSV (Comma-Separated Values) format.
 * Supports quoted fields and escaped quotes.
 */
public class CsvLineParser {
    private static final char DELIMITER = ',';

    /**
     * Parses a line of CSV text into a list of fields.
     *
     * @param input The CSV line to parse
     * @return List of fields extracted from the CSV line
     */
    public List<String> parse(CharSequence input) {
        String line = input != null ? input.toString() : "";
        List<String> fields = new ArrayList<>();

        if (line.isEmpty()) {
            return Collections.singletonList("");
        }

        StringBuilder currentField = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Handle escaped quotes
                    currentField.append('"');
                    i++; // Skip the next quote
                } else {
                    insideQuotes = !insideQuotes;
                }
                continue;
            }

            if (currentChar == DELIMITER && !insideQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
                continue;
            }

            currentField.append(currentChar);
        }

        // Add the last field
        fields.add(currentField.toString());

        return fields;
    }
}