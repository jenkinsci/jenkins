/*
 * The MIT License
 *
 * Copyright (c) 2014
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

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.JDK;
import org.junit.jupiter.api.Test;

class JDKNameTest {

    @Test
    void nullIsDefaultName() {
        assertThat(JDK.isDefaultName(null), is(true));
    }

    @Test
    void recognizeOldDefaultName() {
        // DEFAULT_NAME took this value prior to 1.598.
        assertThat(JDK.isDefaultName("(Default)"), is(true));
    }

    @Test
    void recognizeDefaultName() {
        assertThat(JDK.isDefaultName(JDK.DEFAULT_NAME), is(true));
    }

    @Test
    void othernameNotDefault() {
        assertThat(JDK.isDefaultName("I'm a customized name"), is(false));
    }

}
