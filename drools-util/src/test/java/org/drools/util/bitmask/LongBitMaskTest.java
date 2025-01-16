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

package org.drools.util.bitmask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

public class LongBitMaskTest {

  @Test
  public void testSet() {
      assertThat(new LongBitMask().set(0).toString()).isEqualTo("1");
      assertThat(new LongBitMask().set(1).toString()).isEqualTo("2");
      assertThat(new LongBitMask().set(65).toString()).isEqualTo("0, 2");
  }

  @Test
  public void testSetAll() {
      assertThat(new LongBitMask().setAll(new LongBitMask()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().setAll(AllSetBitMask.get()).toString()).isEqualTo("-1");
      assertThat(new LongBitMask().setAll(AllSetButLastBitMask.get()).toString()).isEqualTo("9223372036854775807");
      assertThat(new LongBitMask(1).setAll(AllSetButLastBitMask.get()).toString()).isEqualTo("-1");
      assertThat(new LongBitMask().setAll(new OpenBitSet()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().setAll(EmptyButLastBitMask.get()).toString()).isEqualTo("1");
      assertThat(new LongBitMask().setAll(EmptyBitMask.get()).toString()).isEqualTo("0");
  }

  @Test
  public void testReset() {
      assertThat(new LongBitMask().reset(0).toString()).isEqualTo("0");
      assertThat(new LongBitMask().reset(1).toString()).isEqualTo("0");
      assertThat(new LongBitMask().reset(65).toString()).isEqualTo("0");
  }

  @Test
  public void testResetAll() {
      assertThat(new LongBitMask().resetAll(new LongBitMask()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().resetAll(AllSetBitMask.get()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().resetAll(AllSetButLastBitMask.get()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().resetAll(EmptyButLastBitMask.get()).toString()).isEqualTo("0");
      assertThat(new LongBitMask().resetAll(EmptyBitMask.get()).toString()).isEqualTo("0");
      
      assertThatRuntimeException().isThrownBy(() -> new LongBitMask().resetAll(new OpenBitSet()).toString());
  }

  @Test
  public void testIsSet() {
      assertThat(new LongBitMask().set(1).isSet(0)).isFalse();
      assertThat(new LongBitMask().set(1).isSet(1)).isTrue();
  }

  @Test
  public void testIsAllSet() {
      assertThat(new LongBitMask().isAllSet()).isFalse();
      assertThat(new LongBitMask(-1L).isAllSet()).isTrue();
  }

  @Test
  public void testIsEmpty() {
      assertThat(new LongBitMask(1L).isEmpty()).isFalse();
      assertThat(new LongBitMask(0L).isEmpty()).isTrue();
  }

  @Test
  public void testIntersects() {
      assertThat(new LongBitMask(0L).intersects(EmptyBitMask.get())).isFalse();
      assertThat(new LongBitMask(0L).intersects(new LongBitMask(0L))).isFalse();
      assertThat(new LongBitMask(2L).intersects(new LongBitMask(2L))).isTrue();
  }

  @Test
  public void testClone() {
      assertThat(new LongBitMask(1L).clone().asLong()).isEqualTo(1L);
  }

  @Test
  public void testGetInstancingStatement() {
      assertThat(new LongBitMask(0L).getInstancingStatement()).isEqualTo("org.drools.util.bitmask.EmptyBitMask.get()");
      assertThat(new LongBitMask(1L).getInstancingStatement()).isEqualTo("org.drools.util.bitmask.EmptyButLastBitMask.get()");
      assertThat(new LongBitMask(Long.MAX_VALUE).getInstancingStatement()).isEqualTo("org.drools.util.bitmask.AllSetButLastBitMask.get()");
      assertThat(new LongBitMask(-1L).getInstancingStatement()).isEqualTo("org.drools.util.bitmask.AllSetBitMask.get()");
      assertThat(new LongBitMask(2L).getInstancingStatement()).isEqualTo("new org.drools.util.bitmask.LongBitMask(2L)");
  }
}
