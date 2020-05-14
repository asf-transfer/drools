/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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

package org.drools.core.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.drools.core.common.BaseNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerfLogUtils {

    private static final Logger logger = LoggerFactory.getLogger(PerfLogUtils.class);

    public static final String PERF_LOGGER_ENABLED = "drools.performance.logger.enabled";
    private static boolean enabled = Boolean.parseBoolean(System.getProperty(PERF_LOGGER_ENABLED, "false"));

    public static final String PERF_LOGGER_THRESHOLD = "drools.performance.logger.threshold";
    private static int threshold = Integer.parseInt(System.getProperty(PERF_LOGGER_THRESHOLD, "500")); // microseconds

    private static final ThreadLocal<Boolean> started = new ThreadLocal<>();
    private static final ThreadLocal<AtomicInteger> evalCount = new ThreadLocal<>();
    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();
    private static final ThreadLocal<BaseNode> node = new ThreadLocal<>();

    public static int getThreshold() {
        return threshold;
    }

    public static void setThreshold(int threshold) {
        PerfLogUtils.threshold = threshold;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        PerfLogUtils.enabled = enabled;
    }

    public static void startMetrics(BaseNode baseNode) {
        if (enabled) {
            started.set(true);
            node.set(baseNode);
            evalCount.set(new AtomicInteger(0));
            startTime.set(System.nanoTime());
        }
    }

    public static void endMetrics() {
        if (enabled) {
            started.set(false);
        }
    }

    public static void incrementEvalCount() {
        if (enabled && started.get().booleanValue()) {
            evalCount.get().getAndIncrement();
        }
    }

    public static void logAndEndMetrics() {
        if (enabled && started.get().booleanValue()) {
            long elapsedTime = (System.nanoTime() - startTime.get());
            int count = evalCount.get().intValue();
            if (count > 0 && (elapsedTime / 1000) > threshold) {
                logger.trace("{}, evalCount:{}, elapsed:{}", node.get(), count, elapsedTime / 1000); // microseconds
            }
            started.set(false);
        }
    }

    private PerfLogUtils() {
        // It is not allowed to create instances of util classes.
    }
}
