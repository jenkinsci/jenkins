/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Represents fields that are protected for concurrency by the copy-on-write semantics.
 *
 * <p>
 * Fields marked by this annotation always holds on to an immutable collection.
 * A change to the collection must be done by first creating a new collection
 * object, making changes, then replacing the reference atomically.
 *
 * <p>
 * This allows code to access the field without synchronization, and
 * greatly reduces the risk of dead-lock bugs.
 *
 * <p>
 * The field marked with this annotation usually needs to be marked as
 * <tt>volatile</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(SOURCE)
@Documented
@Target(FIELD)
public @interface CopyOnWrite {
}
