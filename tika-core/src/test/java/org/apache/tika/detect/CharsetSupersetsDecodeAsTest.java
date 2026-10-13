/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Corrected, dependency-free tests based on ChatUniTest's decodeAs draft. */
public class CharsetSupersetsDecodeAsTest {

    @Test
    public void decodesWithSupersetWhenOverrideExists() {
        Charset detected = Charset.forName("EUC-KR");

        assertEquals(Charset.forName("x-windows-949"),
                CharsetSupersets.decodeAs(detected));
    }

    @Test
    public void preservesDetectedCharsetWhenNoOverrideExists() {
        Charset detected = StandardCharsets.UTF_8;

        assertSame(detected, CharsetSupersets.decodeAs(detected));
    }

    @Test
    public void preservesNullWhenDetectionReturnedNull() {
        assertNull(CharsetSupersets.decodeAs(null));
    }
}
