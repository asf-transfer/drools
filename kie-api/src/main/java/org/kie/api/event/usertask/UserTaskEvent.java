/*
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

package org.kie.api.event.usertask;

import java.util.Date;

import org.kie.api.event.KieRuntimeEvent;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;

/**
 * A runtime event related to the execution of process instances.
 */
public interface UserTaskEvent
    extends
    KieRuntimeEvent {

    /**
     * The ProcessInstance this event relates to.
     *
     * @return the process instance
     */
    ProcessInstance getProcessInstance();

    NodeInstance getNodeInstance();
    
    WorkItem getWorkItem();
    
    String getUserTaskId();
    
    String getUserTaskDefinitionId();
    
    /**
     * Returns exact date when the event was created
     * @return time when event was created
     */
    Date getEventDate();

    /**
     * @return associated identity that performed the event
     */
    String getEventUser();

}
