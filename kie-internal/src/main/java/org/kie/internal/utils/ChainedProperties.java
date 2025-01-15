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
package org.kie.internal.utils;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Priority
 * <ul>
 *  <li>System properties</li>
 *  <li>META-INF/ of provided classLoader</li>
 * </ul>
 * <br/>
 */
public class ChainedProperties
    implements
    Externalizable, Cloneable {

    private static final String[] PROPERTIES_ALLOWED_PREFIX = new String[] {
            "java.", "jdk.", "sun.", "user.", "os.", "jboss.",
            "drools.", "jbpm.", "org.uberfire.", "file.", "path.", "line."
    };

    protected static transient Logger logger = LoggerFactory.getLogger(ChainedProperties.class);

    private List<Properties> props = new ArrayList<>();
    private List<Properties> defaultProps = new ArrayList<>();

    public ChainedProperties() {
    }

    public static ChainedProperties getChainedProperties( ClassLoader classLoader ) {
        return getChainedProperties( "properties.conf", classLoader );
    }

    public static ChainedProperties getChainedProperties( String confFileName, ClassLoader classLoader ) {
        return new ChainedProperties( confFileName, classLoader );
    }

    public ChainedProperties clone() {
        ChainedProperties clone = new ChainedProperties();
        clone.props.addAll( this.props );
        clone.defaultProps.addAll( this.defaultProps );
        return clone;
    }

    private ChainedProperties(String confFileName, ClassLoader classLoader) {
        addProperties( System.getProperties() );

        loadProperties( "META-INF/kie." + confFileName, classLoader, this.props );
        loadProperties( "META-INF/kie.default." + confFileName, classLoader, this.defaultProps);
        loadProperties( "META-INF/kie.default.mvel." + confFileName, classLoader, this.defaultProps);

        // this happens only in OSGi: for some reason doing
        // ClassLoader.getResources() doesn't work but doing
        // Class.getResourse() does
        if (this.defaultProps.isEmpty()) {
            try {
                Class<?> c = Class.forName( "org.drools.core.WorkingMemory", false, classLoader);
                URL confURL = c.getResource("/META-INF/kie.default." + confFileName);
                loadProperties(confURL, this.defaultProps);
            } catch (ClassNotFoundException e) { }
        }
    }

    public void filterDroolsPropertiesForSerialization() {
        props = props.stream().map(this::filterDroolsProperties).collect(Collectors.toList());
        defaultProps = defaultProps.stream().map(this::filterDroolsProperties).collect(Collectors.toList());
    }

    private Properties filterDroolsProperties(Properties originalProperties) {
        Properties droolsProperties = new Properties();
        for (Map.Entry<Object, Object> entry : originalProperties.entrySet()) {
            if (isAllowedPropertyPrefix(entry.getKey().toString())) {
                droolsProperties.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return droolsProperties;
    }

    private boolean isAllowedPropertyPrefix(String name) {
        for (String prefix : PROPERTIES_ALLOWED_PREFIX) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        props = (List<Properties>) in.readObject();
        defaultProps = (List<Properties>) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( props );
        out.writeObject( defaultProps );
    }

    /**
     * Specifically added properties take priority, so they go to the front of the list.
     */
    public ChainedProperties addProperties(Properties properties) {
        if (properties != null && !properties.isEmpty()) {
            this.props.add(0, properties);
        }
        return this;
    }

    public String getProperty(String key,
                              String defaultValue) {
        String value = null;
        for ( Properties props : this.props ) {
            value = props.getProperty( key );
            if ( value != null ) {
                break;
            }
        }
        if ( value == null ) {
            for ( Properties props : this.defaultProps ) {
                value = props.getProperty( key );
                if ( value != null ) {
                    break;
                }
            }
        }
        return (value != null) ? value : defaultValue;
    }

    public void mapStartsWith(Map<String, String> map,
                              String startsWith,
                              boolean includeSubProperties) {
        for ( Properties props : this.props ) {
            mapStartsWith( map,
                           props,
                           startsWith,
                           includeSubProperties );
        }

        for ( Properties props : this.defaultProps ) {
            mapStartsWith( map,
                           props,
                           startsWith,
                           includeSubProperties );
        }
    }

    private void mapStartsWith(Map<String, String> map,
                               Properties properties,
                               String startsWith,
                               boolean includeSubProperties) {
        Enumeration< ? > enumeration = properties.propertyNames();
        while ( enumeration.hasMoreElements() ) {
            String key = (String) enumeration.nextElement();
            if ( key.startsWith( startsWith ) ) {
                if ( !includeSubProperties && key.substring( startsWith.length() + 1 ).indexOf( '.' ) > 0 ) {
                    // +1 to the length, as we do allow the direct property, just not ones below it
                    // This key has sub properties beyond the given startsWith, so skip
                    continue;
                }
                if ( !map.containsKey( key ) ) {
                    map.put( key,
                             properties.getProperty( key ) );
                }

            }
        }
    }

    private void loadProperties(String fileName,
                                ClassLoader classLoader,
                                List<Properties> chain) {
        try {
            chain.addAll(read(fileName,classLoader));
        } catch (IOException e){}
    }

    private List<Properties> read(String fileName, ClassLoader classLoader)
            throws IOException {
        List<Properties> properties = new ArrayList<>();
        Enumeration<URL> resources;
        if (classLoader != null) {
            resources = classLoader.getResources(fileName);
        } else {
            resources = Collections.enumeration(Collections.singletonList(new File(fileName).toURI().toURL()));
        }
        while (resources.hasMoreElements()) {
            Properties p = new Properties();
            URL nextElement = resources.nextElement();
            try (InputStream is = nextElement.openStream()) {
                p.load(is);
                properties.add(p);
            }
        }
        return properties;
    }

    private void loadProperties(URL confURL, List<Properties> chain) {
        if ( confURL == null ) {
            return;
        }
        try ( InputStream is = confURL.openStream() ) {
            Properties properties = new Properties();
            properties.load( is );
            chain.add( properties );
        } catch ( IOException e ) {
            //throw new IllegalArgumentException( "Invalid URL to properties file '" + confURL.toExternalForm() + "'" );
        }
    }
}
