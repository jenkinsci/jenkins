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
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;

/**
 * Configuration for the new token generation when a user is created
 *
 * @since TODO
 */
@Extension 
@Symbol("apiToken")
public class ApiTokenPropertyConfiguration extends GlobalConfiguration {
    /**
     * When a user is created, this property determine if we create a legacy token for the user or not
     * For security reason, we do not recommend to enable this but we let that open to ease upgrade.
     */
    private boolean tokenGenerationOnCreationEnabled = false;

    /**
     * When a user has a legacy token, this property determine if the user can request a new legacy token or not
     * For security reason, we do not recommend to enable this but we let that open to ease upgrade.
     */
    private boolean creationOfLegacyTokenEnabled = false;
    
    /**
     * Each time an API Token is used, its usage counter is incremented and the last usage date is updated. 
     * You can disable this feature using this property.
     */
    private boolean usageStatisticsEnabled = true;
    
    public static ApiTokenPropertyConfiguration get() {
        return GlobalConfiguration.all().get(ApiTokenPropertyConfiguration.class);
    }

    public ApiTokenPropertyConfiguration() {
        load();
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
