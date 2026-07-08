/*
 * The MIT License
 *
 * Copyright (c) 2025, Jenkins project contributors
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

package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Extension point for password complexity validation in {@link HudsonPrivateSecurityRealm}.
 *
 * <p>
 * Implementations define rules that passwords must satisfy during user registration
 * and password changes. Each implementation can have an optional {@code config.jelly}
 * to configure its validation rules.
 *
 * @since TODO
 */
public abstract class PasswordComplexityRule implements Describable<PasswordComplexityRule>, ExtensionPoint {

    /**
     * Returns all the registered {@link PasswordComplexityRule} descriptors.
     */
    public static DescriptorExtensionList<PasswordComplexityRule, Descriptor<PasswordComplexityRule>> all() {
        return Jenkins.get().getDescriptorList(PasswordComplexityRule.class);
    }

    /**
     * Validates the given password against this rule.
     *
     * @param password the password to validate
     * @throws PasswordComplexityException if the password does not meet the requirements
     */
    public abstract void validate(@NonNull String password) throws PasswordComplexityException;

    @Override
    public PasswordComplexityRuleDescriptor getDescriptor() {
        return (PasswordComplexityRuleDescriptor) Describable.super.getDescriptor();
    }
}
