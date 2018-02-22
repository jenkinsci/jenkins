/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package lib.form

import hudson.Functions

f=namespace(lib.FormTagLib)
h=Functions

html{ ->
    body{ ->
        select { ->
            if (it.withValue) {
                if (it.mode == 0) {
                    f.option(value: it.injection, it.injection)
                }
                if (it.mode == 1) {
                    f.option(value: it.injection) { ->
                        text(it.injection)
                    }
                }
                if (it.mode == 2) {
                    f.option(value: it.injection, h.xmlEscape(it.injection))
                }
                if (it.mode == 3) {
                    option(value: it.injection, it.injection)
                }
            } else {
                if (it.mode == 0) {
                    f.option(it.injection)
                }
                if (it.mode == 1) {
                    f.option { ->
                        text(it.injection)
                    }
                }
                if (it.mode == 2) {
                    f.option(h.xmlEscape(it.injection))
                }
            }
        }
    }
}
