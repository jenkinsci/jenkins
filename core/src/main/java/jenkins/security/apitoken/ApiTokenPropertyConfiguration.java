/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.apitoken;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.model.PersistentDescriptor;
import hudson.model.User;
import hudson.util.FormValidation;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Configuration of the API tokens: whether a legacy token is generated when a user is created, whether a user can
 * request a new legacy token, whether the usage of the tokens is recorded, and the maximum lifetime of the tokens that
 * are created through the HTTP endpoints, see {@link ApiTokenLifetimePolicy}.
 *
 * @since 2.129
 */
@Extension
@Symbol("apiToken")
public class ApiTokenPropertyConfiguration extends GlobalConfiguration implements PersistentDescriptor {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenPropertyConfiguration.class.getName());

    /**
     * When a user is created, this property determines whether or not we create a legacy token for the user.
     * For security reasons, we do not recommend you enable this but we left that open to ease upgrades.
     */
    private boolean tokenGenerationOnCreationEnabled = false;

    /**
     * When a user has a legacy token, this property determines whether or not the user can request a new legacy token.
     * For security reasons, we do not recommend you enable this but we left that open to ease upgrades.
     */
    private boolean creationOfLegacyTokenEnabled = false;

    /**
     * Each time an API Token is used, its usage counter is incremented and the last used date is updated.
     * You can disable this feature using this property.
     */
    private boolean usageStatisticsEnabled = true;

    /**
     * Maximum lifetime in days of the API tokens created through the HTTP endpoints, {@code 0} for no limit.
     * See {@link ApiTokenLifetimePolicy}.
     */
    private int maxTokenLifetimeDays;

    /**
     * IDs of the users that are exempt from {@link #maxTokenLifetimeDays}, one per line.
     * Only ever read as the raw string: {@link hudson.model.Descriptor#load()} fills the fields without calling the setters.
     */
    private String exemptUserIds;

    /**
     * ISO date ({@code yyyy-MM-dd}) from which {@link #maxTokenLifetimeDays} is enforced, {@code null} to enforce it
     * right away. Kept as the raw string, see {@link ApiTokenLifetimePolicy}.
     */
    private String enforceFrom;

    public static ApiTokenPropertyConfiguration get() {
        return GlobalConfiguration.all().get(ApiTokenPropertyConfiguration.class);
    }

    public boolean hasExistingConfigFile() {
        return getConfigFile().exists();
    }

    public boolean isTokenGenerationOnCreationEnabled() {
        return tokenGenerationOnCreationEnabled;
    }

    public void setTokenGenerationOnCreationEnabled(boolean tokenGenerationOnCreationEnabled) {
        this.tokenGenerationOnCreationEnabled = tokenGenerationOnCreationEnabled;
        save();
    }

    public boolean isCreationOfLegacyTokenEnabled() {
        return creationOfLegacyTokenEnabled;
    }

    public void setCreationOfLegacyTokenEnabled(boolean creationOfLegacyTokenEnabled) {
        this.creationOfLegacyTokenEnabled = creationOfLegacyTokenEnabled;
        save();
    }

    public boolean isUsageStatisticsEnabled() {
        return usageStatisticsEnabled;
    }

    public void setUsageStatisticsEnabled(boolean usageStatisticsEnabled) {
        this.usageStatisticsEnabled = usageStatisticsEnabled;
        save();
    }

    /**
     * Maximum number of days an API token created through the HTTP endpoints may live.
     *
     * @return the maximum lifetime in days; {@code 0} (the default) means that there is no limit
     * @since TODO
     */
    public int getMaxTokenLifetimeDays() {
        return maxTokenLifetimeDays;
    }

    /**
     * Sets the maximum lifetime of API tokens, see {@link #getMaxTokenLifetimeDays()}.
     * This setter never throws: a negative value, which cannot come from the form (see
     * {@link #configure(StaplerRequest2, JSONObject)}) but may come from a configuration as code file, is logged and
     * stored as {@code 0}.
     *
     * @since TODO
     */
    public void setMaxTokenLifetimeDays(int maxTokenLifetimeDays) {
        if (maxTokenLifetimeDays < 0) {
            LOGGER.log(Level.WARNING, "The maximum API token lifetime cannot be negative ({0}), the limit is disabled", maxTokenLifetimeDays);
        }
        this.maxTokenLifetimeDays = Math.max(0, maxTokenLifetimeDays);
        save();
    }

    /**
     * IDs of the users that are exempt from the maximum lifetime of API tokens.
     *
     * @return one user ID per line, or {@code null} or empty when there is no exemption
     * @since TODO
     */
    @CheckForNull
    public String getExemptUserIds() {
        return exemptUserIds;
    }

    /**
     * Sets the exempt users, see {@link #getExemptUserIds()}. The value is stored as is.
     *
     * @since TODO
     */
    public void setExemptUserIds(@CheckForNull String exemptUserIds) {
        this.exemptUserIds = exemptUserIds;
        save();
    }

    /**
     * Date from which the maximum lifetime of API tokens is enforced.
     *
     * @return an ISO date ({@code yyyy-MM-dd}) as stored, or {@code null} when the limit is enforced right away
     * @since TODO
     */
    @CheckForNull
    public String getEnforceFrom() {
        return enforceFrom;
    }

    /**
     * Sets the enforcement date, see {@link #getEnforceFrom()}. A blank value is stored as {@code null}, any other
     * value is trimmed and stored as is: it is not parsed here, so that this setter never throws.
     *
     * @since TODO
     */
    public void setEnforceFrom(@CheckForNull String enforceFrom) {
        this.enforceFrom = Util.fixEmptyAndTrim(enforceFrom);
        save();
    }

    /**
     * Validates the {@code maxTokenLifetimeDays} and {@code enforceFrom} values that are submitted before binding
     * anything, so that an invalid value rejects the whole submission instead of being half applied.
     * A key that is absent from the submission leaves its field unchanged.
     * <p>
     * Note that {@link hudson.security.GlobalSecurityConfiguration} does not roll back: the descriptors that were
     * configured before this one are already saved when this method throws.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        if (json.has("maxTokenLifetimeDays")) {
            String value = asString(json.get("maxTokenLifetimeDays"));
            if (validateMaxTokenLifetimeDays(value, null).kind == FormValidation.Kind.ERROR) {
                throw new FormException(Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_Rejected(), "maxTokenLifetimeDays");
            }
            json.put("maxTokenLifetimeDays", parseDays(value));
        }
        if (json.has("enforceFrom")) {
            String value = Util.fixNull(asString(json.get("enforceFrom"))).trim();
            if (validateEnforceFrom(value, LocalDate.now()).kind == FormValidation.Kind.ERROR) {
                throw new FormException(Messages.ApiTokenPropertyConfiguration_EnforceFrom_Rejected(), "enforceFrom");
            }
            json.put("enforceFrom", value);
        }
        // Binding calls each setter, which saves: one write for the whole form.
        try (BulkChange bc = new BulkChange(this)) {
            req.bindJSON(this, json);
            bc.commit();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
        return true;
    }

    /**
     * Form validation of {@link #getMaxTokenLifetimeDays()}.
     *
     * @since TODO
     */
    @POST
    public FormValidation doCheckMaxTokenLifetimeDays(@QueryParameter String value, @QueryParameter String enforceFrom) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok();
        }
        return validateMaxTokenLifetimeDays(value, enforceFrom);
    }

    /**
     * Form validation of {@link #getEnforceFrom()}.
     *
     * @since TODO
     */
    @POST
    public FormValidation doCheckEnforceFrom(@QueryParameter String value) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok();
        }
        return validateEnforceFrom(value, LocalDate.now());
    }

    /**
     * Form validation of {@link #getExemptUserIds()}: warns about the IDs that no user has yet.
     * Users are only looked up, never created: a typo does not leave a user behind, and the full name of a user
     * is not an ID.
     *
     * @since TODO
     */
    @POST
    public FormValidation doCheckExemptUserIds(@QueryParameter String value) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok();
        }
        List<String> unknown = new ArrayList<>();
        for (String id : ApiTokenLifetimePolicy.parseUserIds(value)) {
            if (User.getById(id, false) == null) {
                unknown.add(id);
            }
        }
        if (unknown.isEmpty()) {
            return FormValidation.ok();
        }
        return FormValidation.warning(Messages.ApiTokenPropertyConfiguration_ExemptUserIds_Unknown(String.join(", ", unknown)));
    }

    // only for Jelly
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public LocalDate pendingEnforceFrom() {
        return new ApiTokenLifetimePolicy().getPendingEnforceFrom(LocalDate.now());
    }

    // only for Jelly
    @Restricted(NoExternalUse.class)
    public boolean legacyTokensBypassLimit() {
        return new ApiTokenLifetimePolicy().isActive() && (tokenGenerationOnCreationEnabled || creationOfLegacyTokenEnabled);
    }

    /**
     * @return the value as submitted by the form, or {@code null} when it is absent or JSON {@code null}
     */
    @CheckForNull
    private static String asString(@CheckForNull Object value) {
        return value == null || JSONUtils.isNull(value) ? null : value.toString();
    }

    /**
     * @return the number of days, {@code 0} for a blank value, or a negative number when the value is not a whole
     *         number of days that is at least 0
     */
    private static int parseDays(@CheckForNull String value) {
        String trimmed = Util.fixEmptyAndTrim(value);
        if (trimmed == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(trimmed), -1);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @param enforceFrom the value of the enforcement date, only used to warn when there is no maximum
     */
    private static FormValidation validateMaxTokenLifetimeDays(@CheckForNull String value, @CheckForNull String enforceFrom) {
        int days = parseDays(value);
        if (days < 0) {
            return FormValidation.error(Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_Invalid());
        }
        if (days == 0) {
            if (Util.fixEmptyAndTrim(enforceFrom) != null) {
                return FormValidation.warning(Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_EnforceFromIgnored());
            }
            return FormValidation.ok();
        }
        if (days < 7) {
            // HashedToken#isAboutToExpire: every token is flagged as soon as it is created
            return FormValidation.warning(Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_TooShort());
        }
        return FormValidation.ok();
    }

    private static FormValidation validateEnforceFrom(@CheckForNull String value, LocalDate today) {
        String trimmed = Util.fixEmptyAndTrim(value);
        if (trimmed == null) {
            return FormValidation.ok();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            return FormValidation.error(Messages.ApiTokenPropertyConfiguration_EnforceFrom_Invalid());
        }
        if (date.isBefore(today)) {
            return FormValidation.ok(Messages.ApiTokenPropertyConfiguration_EnforceFrom_AlreadyInForce());
        }
        return FormValidation.ok();
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }
}
