/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.Extension;
import hudson.diagnosis.OldDataMonitor;
import hudson.util.XStream2;
import jenkins.util.SystemProperties;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.security.ACL;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Remembers the API token for this user, that can be used like a password to login.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see ApiTokenFilter
 * @since 1.426
 */
public class ApiTokenProperty extends UserProperty {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenProperty.class.getName());
    
    private transient volatile Secret apiToken;
    private ApiTokenStore tokenStore;
    
    /**
     * If enabled, the users with {@link Jenkins#ADMINISTER) permissions can generate new tokens for
     * other users. Normally only a user can generate tokens for himself.
     * Disabled by default due to the security reasons.
     * If enabled, it restores the original Jenkins behavior (SECURITY-200).
     * 
     * @since 1.638
     */
    private static final boolean ADMIN_CAN_GENERATE_NEW_TOKENS =
            SystemProperties.getBoolean(ApiTokenProperty.class.getName() + ".showTokenToAdmins");
    
    @DataBoundConstructor
    public ApiTokenProperty() {
        this.init();
    }
    
    public ApiTokenProperty readResolve() {
        this.init();
        return this;
    }
    
    private void init() {
        if (this.tokenStore == null) {
            this.tokenStore = new ApiTokenStore();
        }
    }
    
    /**
     * We don't let the external code set the API token,
     * but for the initial value of the token we need to compute the seed by ourselves.
     */
    /*package*/ ApiTokenProperty(String seed) {
        apiToken = Secret.fromString(seed);
        this.init();
    }
    
    /**
     * Gets the API token.
     * The method performs security checks since 1.638. Only the current user and SYSTEM may see it.
     * Users with {@link Jenkins#ADMINISTER} may be allowed to do it using {@link #ADMIN_CAN_GENERATE_NEW_TOKENS}.
     *
     * @return API Token. Never null, but may be {@link Messages#ApiTokenProperty_ChangeToken_TokenIsHidden()}
     *         if the user has no appropriate permissions.
     * @since 1.426, and since 1.638 the method performs security checks
     */
    @Nonnull
    @Deprecated
    public String getApiToken() {
        LOGGER.log(Level.WARNING, "Deprecated usage of getApiToken");
        return "deprecated";
    }
    
    public boolean matchesPassword(String token) {
        if(StringUtils.isBlank(token)){
            return false;
        }

        boolean matchFound = tokenStore.doesContainToken(token);
        if (matchFound) {
            try {
                user.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error saving the user after token match", e);
            }
        }
        
        return matchFound;
    }
    
    /**
     * @deprecated Each token can be revoked now and new tokens can be requested without altering existing ones.
     */
    @Deprecated
    public void changeApiToken() throws IOException {
        // just to keep the same level of security
        user.checkPermission(Jenkins.ADMINISTER);
        
        LOGGER.log(Level.WARNING, "Deprecated usage of changeApiToken");
    }
    
    // only for Jelly
    @Restricted(NoExternalUse.class)
    public List<ApiTokenStore.HashedToken> getTokenList() {
        return tokenStore.getTokenListSortedByName();
    }
    
    @Override
    public UserProperty reconfigure(StaplerRequest req, @CheckForNull JSONObject form) throws FormException {
        if(form == null){
            return this;
        }

        Object tokenStoreData = form.get("tokenStore");
        Map<String, JSONObject> tokenStoreTypedData = convertToTokenMap(tokenStoreData);
        this.tokenStore.reconfigure(tokenStoreTypedData);
        return this;
    }
    
    private Map<String, JSONObject> convertToTokenMap(Object tokenStoreData) {
        if (tokenStoreData == null) {
            // in case there are no token
            return Collections.emptyMap();
        } else if (tokenStoreData instanceof JSONObject) {
            // in case there is only one token
            JSONObject singleTokenData = (JSONObject) tokenStoreData;
            Map<String, JSONObject> result = new HashMap<>();
            addJSONTokenIntoMap(result, singleTokenData);
            return result;
        } else if (tokenStoreData instanceof JSONArray) {
            // in case there are multiple tokens
            JSONArray tokenArray = ((JSONArray) tokenStoreData);
            Map<String, JSONObject> result = new HashMap<>();
            for (int i = 0; i < tokenArray.size(); i++) {
                JSONObject tokenData = tokenArray.getJSONObject(i);
                addJSONTokenIntoMap(result, tokenData);
            }
            return result;
        }
        
        throw HttpResponses.error(400, "Unexpected class received for the token store information");
    }
    
    private void addJSONTokenIntoMap(Map<String, JSONObject> tokenMap, JSONObject tokenData) {
        String uuid = tokenData.getString("tokenUuid");
        tokenMap.put(uuid, tokenData);
    }
    
    // for Jelly view
    @Restricted(NoExternalUse.class)
    public boolean hasCurrentUserPermissionToGenerateTokenForThisUser() {
        return hasPermissionToGenerateTokenForUser(user);
    }

    private static boolean hasPermissionToGenerateTokenForUser(User currentOwner) {
        if (ADMIN_CAN_GENERATE_NEW_TOKENS && Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return true;
        }
        
        User currentUser = User.current();
        if (currentUser == null) { 
            // Anonymous
            return false;
        }
        
        if (Jenkins.getAuthentication() == ACL.SYSTEM) {
            // SYSTEM user is always eligible to see tokens
            return true;
        }

        return User.idStrategy().equals(currentOwner.getId(), currentUser.getId());
    }

    public static class ConverterImpl extends XStream2.PassthruConverter<ApiTokenProperty> {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }
        
        @Override
        protected void callback(ApiTokenProperty apiTokenProperty, UnmarshallingContext context) {
            // support legacy configuration
            if (apiTokenProperty.apiToken != null) {
                apiTokenProperty.tokenStore.generateTokenFromLegacy(apiTokenProperty.apiToken);
                OldDataMonitor.report(context, "@since TODO");
            }
            
            apiTokenProperty.tokenStore.optimize();
        }
    }

    @Extension
    @Symbol("apiToken")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public String getDisplayName() {
            return Messages.ApiTokenProperty_DisplayName();
        }

        /**
         * New approach:
         * API Token are generated only when a user request a new one. The value is randomly generated
         * without any link to the user and only displayed to him the first time. 
         * We only store the hash for future comparisons.
         * 
         * Legacy approach:
         * When we are creating a default {@link ApiTokenProperty} for User,
         * we need to make sure it yields the same value for the same user,
         * because there's no guarantee that the property is saved.
         *
         * But we also need to make sure that an attacker won't be able to guess
         * the initial API token value. So we take the seed by hashing the secret + user ID.
         */
        public ApiTokenProperty newInstance(User user) {
            if (ApiTokenPropertyConfiguration.get().isTokenGenerationOnCreationDisabled()) {
                // recommended way
                return null;
            }

            return forceNewInstance(user);
        }
        
        private ApiTokenProperty forceNewInstance(User user) {
            return new ApiTokenProperty(API_KEY_SEED.mac(user.getId()));
        }

        /**
         * @deprecated use {@link #doGenerateNewToken(User, StaplerResponse, String)} instead
         */
        @Deprecated
        @RequirePOST
        public HttpResponse doChangeToken(@AncestorInPath User u, StaplerResponse rsp) throws IOException {
            LOGGER.log(Level.WARNING, "Deprecated action /changeToken used, consider using /generateNewToken instead");

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                p = forceNewInstance(u);
                u.addProperty(p);
            }
            
            String newValue = p.tokenStore.generateNewTokenAndReturnHiddenValue("Created using deprecated method");
            
            rsp.setHeader("script","document.getElementById('apiToken').value='"+newValue+"'");
            return HttpResponses.html(p.hasCurrentUserPermissionToGenerateTokenForThisUser()
                    ? Messages.ApiTokenProperty_ChangeToken_Success()
                    : Messages.ApiTokenProperty_ChangeToken_SuccessHidden());
    
        }

        @RequirePOST
        public HttpResponse doGenerateNewToken(@AncestorInPath User u, StaplerResponse rsp, @QueryParameter String newTokenName) throws IOException {
            if(!hasPermissionToGenerateTokenForUser(u)){
                return HttpResponses.forbidden();
            }
            
            if (StringUtils.isBlank(newTokenName)) {
                return HttpResponses.errorJSON("The name cannot be empty");
            }
            
            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                p = forceNewInstance(u);
                u.addProperty(p);
            }
            
            String valueToDisplayOnce = p.tokenStore.generateNewTokenAndReturnHiddenValue(newTokenName);
            u.save();
            
            return HttpResponses.okJSON(new HashMap<String, String>() {{ 
                put("tokenValue", valueToDisplayOnce); 
            }});
        }
        
        @RequirePOST
        public HttpResponse doRename(@AncestorInPath User u,
                                     @QueryParameter String tokenId, @QueryParameter String newName) throws IOException {
            // only current user + administrator can rename token
            u.checkPermission(Jenkins.ADMINISTER);
    
            if (StringUtils.isBlank(newName)) {
                return HttpResponses.errorJSON("The name cannot be empty");
            }
            if(StringUtils.isBlank(tokenId)){
                // using the web UI this should not occur
                return HttpResponses.errorWithoutStack(400, "The tokenId cannot be empty");
            }
            
            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorWithoutStack(400, "The user does not have any ApiToken yet, try generating one before.");
            }
            
            p.tokenStore.renameToken(tokenId, newName);
            u.save();
            
            return HttpResponses.ok();
        }
        
        @RequirePOST
        public HttpResponse doRevoke(@AncestorInPath User u,
                                     @QueryParameter String tokenId) throws IOException {
            // only current user + administrator can revoke token
            u.checkPermission(Jenkins.ADMINISTER);
            
            if(StringUtils.isBlank(tokenId)){
                // using the web UI this should not occur
                return HttpResponses.errorWithoutStack(400, "The tokenId cannot be empty");
            }
            
            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorWithoutStack(400, "The user does not have any ApiToken yet, try generating one before.");
            }
            
            p.tokenStore.revokeToken(tokenId);
            u.save();
            
            return HttpResponses.ok();
        }
    }
    
    /**
     * We don't want an API key that's too long, so cut the length to 16 (which produces 32-letter MAC code in hexdump)
     * @deprecated only used for the migration of data from previous save
     */
    @Deprecated
    private static final HMACConfidentialKey API_KEY_SEED = new HMACConfidentialKey(ApiTokenProperty.class, "seed", 16);
}
