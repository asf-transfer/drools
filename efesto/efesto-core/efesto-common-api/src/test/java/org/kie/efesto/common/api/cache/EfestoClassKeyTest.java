/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.efesto.common.api.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class EfestoClassKeyTest {

    @Test
    void testEquals() {
        EfestoClassKey first = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        EfestoClassKey second = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        assertThat(first.equals(second)).isTrue();
        second = new EfestoClassKey(ArrayList.class, Collections.singletonList(String.class));
        assertThat(first.equals(second)).isFalse();
        first = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        second = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        assertThat(first.equals(second)).isTrue();
        second = new EfestoClassKey(HashMap.class, Arrays.asList(String.class, Integer.class));
        assertThat(first.equals(second)).isFalse();
        second = new EfestoClassKey(Map.class, Arrays.asList(Integer.class, String.class));
        assertThat(first.equals(second)).isFalse();
    }

    @Test
    void testSet() {
        Set<EfestoClassKey> set = new HashSet<>();
        EfestoClassKey first = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        set.add(first);
        EfestoClassKey second = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        assertThat(set.contains(second)).isTrue();
        set.add(second);
        assertThat(set).size().isEqualTo(1);
        second = new EfestoClassKey(ArrayList.class, Collections.singletonList(String.class));
        assertThat(set.contains(second)).isFalse();
        set.add(second);
        assertThat(set).size().isEqualTo(2);

        set = new HashSet<>();
        first = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        set.add(first);
        second = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        assertThat(set.contains(second)).isTrue();
        set.add(second);
        assertThat(set).size().isEqualTo(1);
        second = new EfestoClassKey(Map.class, Arrays.asList(Integer.class, String.class));
        assertThat(set.contains(second)).isFalse();
        set.add(second);
        assertThat(set).size().isEqualTo(2);
    }

    @Test
    void testMap() {
        Map<EfestoClassKey, String> map = new HashMap<>();
        EfestoClassKey first = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        map.put(first, "");
        EfestoClassKey second = new EfestoClassKey(List.class, Collections.singletonList(String.class));
        assertThat(map.containsKey(second)).isTrue();
        map.put(second, "");
        assertThat(map).size().isEqualTo(1);
        second = new EfestoClassKey(ArrayList.class, Collections.singletonList(String.class));
        assertThat(map.containsKey(second)).isFalse();
        map.put(second, "");
        assertThat(map).size().isEqualTo(2);

        map = new HashMap<>();
        first = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        map.put(first, "");
        second = new EfestoClassKey(Map.class, Arrays.asList(String.class, Integer.class));
        assertThat(map.containsKey(second)).isTrue();
        map.put(second, "");
        assertThat(map).size().isEqualTo(1);
        second = new EfestoClassKey(Map.class, Arrays.asList(Integer.class, String.class));
        assertThat(map.containsKey(second)).isFalse();
        map.put(second, "");
        assertThat(map).size().isEqualTo(2);
    }
}