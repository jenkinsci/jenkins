/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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
package jenkins.model.lazy;

/**
 * ceil/floor/lower/higher implementations
 * that takes the return value of a binary search as an input.
 *
 * <p>
 * Consider a sorted array of int X={x<sub>i</sub>} and a binary search of p on it.
 * this class provides likes of {@code ceil(X,p)} which is the smallest x<sub>i</sub>
 * that still satisfies x<sub>i</sub> >= p.
 *
 * Similarly, {@link #HIGHER} is the smallest x<sub>i</sub>
 * that still satisfies x<sub>i</sub> > p.
 *
 * @author Kohsuke Kawaguchi
 */
enum Boundary {
    LOWER(-1,-1),
    HIGHER(1,0),
    FLOOR(0,-1),
    CEIL(0,0);

    private final int offsetOfExactMatch, offsetOfInsertionPoint;

    private Boundary(int offsetOfExactMatch, int offsetOfInsertionPoint) {
        this.offsetOfExactMatch = offsetOfExactMatch;
        this.offsetOfInsertionPoint = offsetOfInsertionPoint;
    }

    /**
     * Computes the boundary value.
     */
    public int apply(int binarySearchOutput) {
        int r = binarySearchOutput;
        if (r>=0)    return r+offsetOfExactMatch;   // if we had some x_i==p

        int ip = -(r+1);
        return ip+offsetOfInsertionPoint;
    }
}
