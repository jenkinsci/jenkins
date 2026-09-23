/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentToControllerCallableTest {

    @Test void validateType() {
        AgentToControllerCallable.validateType(int.class);
        AgentToControllerCallable.validateType(Simple.class);
        AgentToControllerCallable.validateType(Good.class);
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(NotSer.class)).getMessage(), is(NotSer.class + " is not serializable"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(BadArrayType.class)).getMessage(), is(NotSer.class + " is not serializable"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(UsesList.class)).getMessage(), is("java.util.List<java.lang.String> is not a known immutable monomorphic type"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(NonRecord.class)).getMessage(), is(NonRecord.class + " is not a supported class type"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(BadTOType.class)).getMessage(), is(NonRecord.class + " is not a supported class type"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> AgentToControllerCallable.validateType(BadEOType.class)).getMessage(), is(NonRecord.class + " is not a supported class type"));
    }

    record Simple(String a, boolean b) implements Serializable {}

    record Good(String a, boolean b, AgentToControllerCallable.TrustedObject<Simple> c, AgentToControllerCallable.EncryptedObject<Simple> d, Simple[] e) implements Serializable {}

    record NotSer() {}

    record BadArrayType(NotSer[] a) implements Serializable {}

    record UsesList(List<String> a) implements Serializable {}

    static final class NonRecord implements Serializable {}

    record BadTOType(AgentToControllerCallable.TrustedObject<NonRecord> a) implements Serializable {}

    record BadEOType(AgentToControllerCallable.EncryptedObject<NonRecord> a) implements Serializable {}

}
