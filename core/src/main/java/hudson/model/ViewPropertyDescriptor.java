/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc., Stephen Connolly
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

/**
 * {@link Descriptor} for {@link ViewProperty}
 *
 * @author Stephen Connolly
 * @since 1.406
 */
public abstract class ViewPropertyDescriptor extends Descriptor<ViewProperty> {
    protected ViewPropertyDescriptor(Class<? extends ViewProperty> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link Describable} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     */
    protected ViewPropertyDescriptor() {
    }

    /**
     * Creates a default instance of {@link ViewProperty} to be associated
     * with {@link View} object that wasn't created from a persisted XML data.
     *
     * <p>
     * See {@link View} class javadoc for more details about the life cycle
     * of {@link View} and when this method is invoked.
     *
     * @return null
     *      if the implementation choose not to add any property object for such view.
     */
    public ViewProperty newInstance(View view) {
        return null;
    }

    /**
     * Whether or not the described property is enabled in the current context.
     * Defaults to true.  Over-ride in sub-classes as required.
     *
     * <p>
     * Returning false from this method essentially has the same effect of
     * making core behaves as if this {@link ViewPropertyDescriptor} is
     * not a part of {@link ViewProperty#all()}.
     *
     * <p>
     * This mechanism is useful if the availability of the property is
     * contingent of some other settings.
     *
     * @param view
     *      View for which this property is considered. Never null.
     */
    public boolean isEnabledFor(View view) {
        return true;
    }
}
