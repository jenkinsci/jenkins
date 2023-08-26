/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.FormValidation;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;

public interface Renamable {

    /**
     * Controls whether the default rename action is available for this object.
     *
     * @return whether the name can be modified by a user
     * @since TODO
     */
    boolean isNameEditable();

    /**
     * Renames the object
     *
     * @since TODO
     */
    HttpResponse doConfirmRename(@QueryParameter String newName) throws Exception;

    /**
     * Controls whether the default rename action is available.
     *
     * @return whether object name can be modified by a user
     * @since TODO
     */
    @NonNull
    FormValidation doCheckNewName(@QueryParameter String newName);
}
