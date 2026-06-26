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
import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Built-in {@link PasswordComplexityRule} that checks minimum length
 * and character type requirements.
 *
 * @since TODO
 */
public class BasicPasswordComplexityRule extends PasswordComplexityRule {

    private final int minimumLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireDigit;
    private final boolean requireSpecialCharacter;

    @DataBoundConstructor
    public BasicPasswordComplexityRule(
            int minimumLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireDigit,
            boolean requireSpecialCharacter) {
        this.minimumLength = Math.max(0, minimumLength);
        this.requireUppercase = requireUppercase;
        this.requireLowercase = requireLowercase;
        this.requireDigit = requireDigit;
        this.requireSpecialCharacter = requireSpecialCharacter;
    }

    public int getMinimumLength() {
        return minimumLength;
    }

    public boolean isRequireUppercase() {
        return requireUppercase;
    }

    public boolean isRequireLowercase() {
        return requireLowercase;
    }

    public boolean isRequireDigit() {
        return requireDigit;
    }

    public boolean isRequireSpecialCharacter() {
        return requireSpecialCharacter;
    }

    @Override
    public void validate(@NonNull String password) throws PasswordComplexityException {
        List<String> errors = new ArrayList<>();
        if (minimumLength > 0 && password.length() < minimumLength) {
            errors.add(Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordTooShort(minimumLength));
        }
        if (requireUppercase && !password.matches(".*[A-Z].*")) {
            errors.add(Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequiresUppercase());
        }
        if (requireLowercase && !password.matches(".*[a-z].*")) {
            errors.add(Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequiresLowercase());
        }
        if (requireDigit && !password.matches(".*[0-9].*")) {
            errors.add(Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequiresDigit());
        }
        if (requireSpecialCharacter && !password.matches(".*[^a-zA-Z0-9].*")) {
            errors.add(Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequiresSpecialCharacter());
        }
        if (!errors.isEmpty()) {
            throw new PasswordComplexityException(String.join(" ", errors));
        }
    }

    @Extension @Symbol("basicPasswordComplexity")
    public static final class DescriptorImpl extends PasswordComplexityRuleDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BasicPasswordComplexityRule_DisplayName();
        }
    }
}
