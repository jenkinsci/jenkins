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

import hudson.Extension;
import hudson.model.PersistentDescriptor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;

/**
 * Configuration for the new token generation when a user is created
 *
 * @since 2.129
 */
@Extension 
@Symbol("apiToken")
public class ApiTokenPropertyConfiguration extends GlobalConfiguration implements PersistentDescriptor {
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
    
    public static ApiTokenPropertyConfiguration get() {
        return GlobalConfiguration.all().get(ApiTokenPropertyConfiguration.class);
    }

    public boolean hasExistingConfigFile(){
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
    
    @Override 
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }
}
