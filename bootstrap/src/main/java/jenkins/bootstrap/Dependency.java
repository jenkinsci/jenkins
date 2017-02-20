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
package jenkins.bootstrap;

import hudson.util.VersionNumber;

/**
 * One dependency entry in {@link DependenciesTxt}.
 *
 * @author Kohsuke Kawaguchi
 */
final class Dependency {
    /**
     * groupId, artifactId, and version.
     */
    final String g,a,v;
    final VersionNumber vv;
    final String ga;

    /**
     * classifier, if any.
     */
    final String c;

    Dependency(String g, String a, String v, String c) {
        this.g = g;
        this.a = a;
        this.v = v;
        this.c = "".equals(c) ? null : c;
        this.vv = new VersionNumber(v);
        this.ga = g+':'+a;
    }

    String getFileName() {
        return a+"-"+v+(c==null?"":"-"+c)+".jar";
    }

}
