/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package jenkins.security;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.StaplerRequest;

import com.google.inject.Injector;

import hudson.Extension;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Configuration for {@link AuthorizationStrategyOverride}.
 *
 * @since TODO
 */
@Extension @Symbol("authorizationStrategyOverride")
public class AuthorizationStrategyOverrideConfiguration extends GlobalConfiguration {
    private final DescribableList<AuthorizationStrategyOverride<?>, AuthorizationStrategyOverrideDescriptor> overrides
        = new DescribableList<AuthorizationStrategyOverride<?>, AuthorizationStrategyOverrideDescriptor>(this);

    public AuthorizationStrategyOverrideConfiguration() {
        load();
    }

    private Object readResolve() {
        overrides.setOwner(this);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    /**
     * @return the list of configured {@link AuthorizationStrategyOverride}
     */
    public DescribableList<AuthorizationStrategyOverride<?>, AuthorizationStrategyOverrideDescriptor> getOverrides() {
        return overrides;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            overrides.rebuildHetero(
                    req,
                    json,
                    AuthorizationStrategyOverrideDescriptor.all(),
                    "overrides"
            );
            return true;
        } catch (IOException e) {
            throw new FormException(e, "overrides");
        }
    }

    /**
     * @return the singleton instance. never {@code null}
     * @throws IllegalStateException {@link Jenkins} has not been started, or was already shut down
     */
    @Nonnull
    public static AuthorizationStrategyOverrideConfiguration get() {
        return Jenkins.getInstance().getInjector().getInstance(AuthorizationStrategyOverrideConfiguration.class);
    }

    /**
     * @return the singleton instance. may be null if {@link Jenkins} has not been started, or was already shut down.
     */
    @CheckForNull
    public static AuthorizationStrategyOverrideConfiguration getOrNull() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return null;
        }
        Injector i = j.getInjector();
        if (i == null) {
            return null;
        }
        return i.getInstance(AuthorizationStrategyOverrideConfiguration.class);
    }

    /**
     * Returns the list of enabled {@link AuthorizationStrategyOverrideBase}.
     *
     * includes all available {@link AuthorizationStrategyDenyOverride}
     * and all enabled {@link AuthorizationStrategyOverride}.
     *
     * @return the list of {@link AuthorizationStrategyOverrideBase}
     */
    public static List<AuthorizationStrategyOverrideBase<?>> enables() {
        List<AuthorizationStrategyOverrideBase<?>> lst = new ArrayList<>();
        lst.addAll(AuthorizationStrategyDenyOverride.all());

        AuthorizationStrategyOverrideConfiguration conf = AuthorizationStrategyOverrideConfiguration.getOrNull();
        if (conf != null) {
            lst.addAll(conf.getOverrides());
        }
        return lst;
    }

    /**
     * Returns the list of enabled {@link AuthorizationStrategyOverrideBase} targeting a specific type.
     *
     * @param klazz a type to test ACL.
     * @return the list of {@link AuthorizationStrategyOverrideBase}
     */
    public static <T> List<AuthorizationStrategyOverrideBase<? super T>> enables(Class<T> klazz) {
        List<AuthorizationStrategyOverrideBase<? super T>> lst = new ArrayList<>();
        for (AuthorizationStrategyOverrideBase<?> d: enables()) {
            Type p = Types.getBaseClass(d.getClass(), AuthorizationStrategyOverrideBase.class);
            if (!(p instanceof ParameterizedType)) {
                continue;
            }
            ParameterizedType pt = (ParameterizedType)p;
            Class<?> targetType = Types.erasure(Types.getTypeArgument(pt, 0));
            if (!targetType.isAssignableFrom(klazz)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            AuthorizationStrategyOverrideBase<? super T> decorator = (AuthorizationStrategyOverrideBase<? super T>)d;
            lst.add(decorator);
        }
        return lst;
    }
}
