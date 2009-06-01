/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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

package hudson.tools;

import hudson.model.Descriptor;
import hudson.util.DescribableList;

import java.util.Collections;
import java.util.List;
import java.io.IOException;
import java.lang.reflect.Array;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link Descriptor} for {@link ToolInstallation}.
 *
 * @author huybrechts
 * @since 1.286
 */
public abstract class ToolDescriptor<T extends ToolInstallation> extends Descriptor<ToolInstallation> {

    private T[] installations;

    /**
     * Configured instances of {@link ToolInstallation}s.
     *
     * @return read-only list of installations;
     *      can be empty but never null.
     */
    public T[] getInstallations() {
        return installations.clone();
    }

    /**
     * Overwrites {@link ToolInstallation}s.
     *
     * @param installations list of installations;
     *      can be empty but never null.
     */
    public void setInstallations(T... installations) {
        this.installations = installations.clone();
    }

    /**
     * Lists up {@link ToolPropertyDescriptor}s that are applicable to this {@link ToolInstallation}.
     */
    public List<ToolPropertyDescriptor> getPropertyDescriptors() {
        return PropertyDescriptor.for_(ToolProperty.all(),clazz);
    }

    /**
     * Optional list of installers to be configured by default for new tools of this type.
     * If there are popular versions of the tool available using generic installation techniques,
     * they can be returned here for the user's convenience.
     * @since 1.305
     */
    public List<? extends ToolInstaller> getDefaultInstallers() {
        return Collections.emptyList();
    }

    /**
     * Default value for {@link ToolInstallation#getProperties()} used in the form binding.
     * @since 1.305
     */
    public DescribableList<ToolProperty<?>,ToolPropertyDescriptor> getDefaultProperties() throws IOException {
        DescribableList<ToolProperty<?>,ToolPropertyDescriptor> r
                = new DescribableList<ToolProperty<?>, ToolPropertyDescriptor>(NOOP);

        List<? extends ToolInstaller> installers = getDefaultInstallers();
        if(!installers.isEmpty())
            r.add(new InstallSourceProperty(installers));

        return r;
    }

    @Override
    @SuppressWarnings("unchecked") // cast to T[]
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        setInstallations(req.bindJSONToList(clazz, json.get("tool")).toArray((T[]) Array.newInstance(clazz, 0)));
        return true;
    }

}
