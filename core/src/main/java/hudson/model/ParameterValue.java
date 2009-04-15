/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.util.VariableResolver;

import java.io.Serializable;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A value for a parameter in a build.
 *
 * Created by {@link ParameterDefinition#createValue(StaplerRequest, JSONObject)} for
 * a particular build (although this 'owner' build object is passed in for every method
 * call as a parameter so that the parameter won't have to persist it.)
 *
 * <h2>Persistence</h2>
 * <p>
 * Instances of {@link ParameterValue}s are persisted into build's <tt>build.xml</tt>
 * through XStream (via {@link ParametersAction}), so instances need to be persistable.
 *
 * <h2>Assocaited Views</h2>
 * <h4>value.jelly</h4>
 * The <tt>value.jelly</tt> view contributes a UI fragment to display the parameter
 * values used for a build.
 *
 * <h2>Notes</h2>
 * <ol>
 * <li>{@link ParameterValue} is used to record values of the past build, but
 *     {@link ParameterDefinition} used back then might be gone already, or represent
 *     a different parameter now. So don't try to use the name to infer
 *     {@link ParameterDefinition} is.
 * </ol>
 * @see ParameterDefinition
 */
@ExportedBean
public abstract class ParameterValue implements Serializable {
	protected final String name;

    private String description;

    protected ParameterValue(String name, String description) {
        this.name = name;
        this.description = description;
    }

    protected ParameterValue(String name) {
        this(name, null);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Name of the parameter.
     *
     * This uniquely distinguishes {@link ParameterValue} among other parameters
     * for the same build. This must be the same as {@link ParameterDefinition#getName()}.
     */
    @Exported(visibility=3)
    public final String getName() {
        return name;
    }

    /**
     * Adds environmental variables for the builds to the given map.
     *
     * <p>
     * This provides a means for a parameter to pass the parameter
     * values to the build to be performed.
     *
     * <p>
     * When this method is invoked, the map already contains the
     * current "planned export" list. The implementation is
     * expected to add more values to this map (or do nothing)
     *
     * <p>
     * Environment variables should be by convention all upper case.
     * (This is so that a Windows/Unix heterogenous environment
     * won't get inconsistent result depending on which platform to
     * execute.)
     *
     * @param env
     *      never null.
     * @param build
     *      The build for which this parameter is being used. Never null.
     */
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        // no-op by default
    }

    /**
     * Called at the beginning of a build to let a {@link ParameterValue}
     * contributes a {@link BuildWrapper} to the build.
     *
     * <p>
     * This provides a means for a parameter to perform more extensive
     * set up / tear down during a build.
     *
     * @param build
     *      The build for which this parameter is being used. Never null.
     * @return
     *      null if the parameter has no {@link BuildWrapper} to contribute to.
     */
    public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
        return null;
    }

    /**
     * Returns a {@link VariableResolver} so that other components like {@link Builder}s
     * can perform variable substitution to reflect parameter values into the build process.
     *
     * <p.
     * This is yet another means in which a {@link ParameterValue} can influence
     * a build.
     *
     * @param build
     *      The build for which this parameter is being used. Never null.
     * @return
     *      if the parameter value is not interested in participating to the
     *      variable replacement process, return {@link VariableResolver#NONE}.
     */
    public VariableResolver<String> createVariableResolver(AbstractBuild<?,?> build) {
        return VariableResolver.NONE;
    }

    /**
     * Accessing {@link ParameterDefinition} is not a good idea.
     *
     * @deprecated
     *    parameter definition may change any time. So if you find yourself
     *    in need of accessing the information from {@link ParameterDefinition},
     *    instead copy them in {@link ParameterDefinition#createValue(StaplerRequest, JSONObject)}
     *    into {@link ParameterValue}.
     */
    public ParameterDefinition getDefinition() {
        throw new UnsupportedOperationException();
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ParameterValue other = (ParameterValue) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
