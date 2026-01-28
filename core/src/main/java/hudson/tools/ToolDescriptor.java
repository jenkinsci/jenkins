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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.tools.ToolConfigurationCategory;
import net.sf.json.JSONObject;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link Descriptor} for {@link ToolInstallation}.
 *
 * @author huybrechts
 * @since 1.286
 */
public abstract class ToolDescriptor<T extends ToolInstallation> extends Descriptor<ToolInstallation> {

    private T[] installations;

    protected ToolDescriptor() { }

    /**
     * @since 2.102
     */
    protected ToolDescriptor(Class<T> clazz) {
        super(clazz);
    }

    /**
     * Configured instances of {@link ToolInstallation}s.
     *
     * @return read-only list of installations;
     *      can be empty but never null.
     */
    @SuppressWarnings("unchecked")
    public T[] getInstallations() {
        if (installations != null)
            return installations.clone();

        Type bt = Types.getBaseClass(getClass(), ToolDescriptor.class);
        if (bt instanceof ParameterizedType pt) {
            // this 't' is the closest approximation of T of Descriptor<T>.
            Class t = Types.erasure(pt.getActualTypeArguments()[0]);
            return (T[]) Array.newInstance(t, 0);
        } else {
            // can't infer the type. Fallback
            return emptyArray_unsafeCast();
        }
    }

    //TODO: Get rid of it?
    //It's unsafe according to http://stackoverflow.com/questions/2927391/whats-the-reason-i-cant-create-generic-array-types-in-java
    @SuppressWarnings("unchecked")
    @SuppressFBWarnings(value = "BC_IMPOSSIBLE_DOWNCAST",
            justification = "Such casting is generally unsafe, but we use it as a last resort.")
    private T[] emptyArray_unsafeCast() {
        return (T[]) new Object[0];
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
        return PropertyDescriptor.for_(ToolProperty.all(), clazz);
    }


    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(ToolConfigurationCategory.class);
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
    public DescribableList<ToolProperty<?>, ToolPropertyDescriptor> getDefaultProperties() throws IOException {
        DescribableList<ToolProperty<?>, ToolPropertyDescriptor> r
                = new DescribableList<>(NOOP);

        List<? extends ToolInstaller> installers = getDefaultInstallers();
        if (!installers.isEmpty())
            r.add(new InstallSourceProperty(installers));

        return r;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        if (Util.isOverridden(ToolDescriptor.class, getClass(), "configure", StaplerRequest.class, JSONObject.class)) {
            return configure(StaplerRequest.fromStaplerRequest2(req), json);
        } else {
            return configureImpl(req, json);
        }
    }

    /**
     * @deprecated use {@link #configure(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        return configureImpl(StaplerRequest.toStaplerRequest2(req), json);
    }

    @SuppressWarnings("unchecked") // cast to T[]
    private boolean configureImpl(StaplerRequest2 req, JSONObject json) {
        setInstallations(req.bindJSONToList(clazz, json.get("tool")).toArray((T[]) Array.newInstance(clazz, 0)));
        return true;
    }

    /**
     * Checks if the home directory is valid.
     * @since 1.563
     */
    public FormValidation doCheckHome(@QueryParameter File value) {
        // this can be used to check the existence of a file on the server, so needs to be protected
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (value.getPath().isEmpty()) {
            return FormValidation.ok();
        }

        if (!value.isDirectory()) {
            return FormValidation.warning(Messages.ToolDescriptor_NotADirectory(value));
        }

        return checkHomeDirectory(value);
    }

    /**
     * May be overridden to provide tool-specific validation of a tool home directory.
     * @param home a possible value for {@link ToolInstallation#getHome}, known to already exist on the controller TODO(terminology) or is it built-in?
     * @return by default, {@link FormValidation#ok()}
     * @since 1.563
     */
    protected FormValidation checkHomeDirectory(File home) {
        return FormValidation.ok();
    }

    /**
     * Checks if the tool name is valid.
     * @since 1.563
     */
    public FormValidation doCheckName(@QueryParameter String value) {
        return FormValidation.validateRequired(value);
    }

}
