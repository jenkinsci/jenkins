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
package jenkins.security;

import hudson.Util;
import hudson.model.User;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Restricted(NoExternalUse.class)
public class BasicApiTokenHelper {
    public static @CheckForNull User isConnectingUsingApiToken(String username, String tokenValue){
        User user = User.getById(username, false);
        if(user == null){
            if(isLegacyTokenGeneratedOnUserCreation()){
                String generatedTokenOnCreation = Util.getDigestOf(ApiTokenProperty.API_KEY_SEED.mac(username));
                boolean areTokenEqual = MessageDigest.isEqual(
                        generatedTokenOnCreation.getBytes(StandardCharsets.US_ASCII),
                        tokenValue.getBytes(StandardCharsets.US_ASCII)
                );
                if(areTokenEqual){
                    // directly return the user freshly created
                    // and no need to check its token as the generated token 
                    // will be the same as the one we checked just above
                    return User.getById(username, true);
                }
            }
        }else{
            ApiTokenProperty t = user.getProperty(ApiTokenProperty.class);
            if (t!=null && t.matchesPassword(tokenValue)) {
                return user;
            }
        }
        
        return null;
    }
    
    //TODO could be simplified when the patch is merged in 2.129+
    @SuppressWarnings("unchecked")
    static boolean isLegacyTokenGeneratedOnUserCreation(){
        Class<GlobalConfiguration> clazz;
        try {
            clazz = (Class<GlobalConfiguration>) BasicApiTokenHelper.class.getClassLoader().loadClass("jenkins.security.apitoken.ApiTokenPropertyConfiguration");
        } catch (ClassNotFoundException e) {
            // case the new api token system is not in place
            return true;
        }
        
        Object apiTokenConfiguration = GlobalConfiguration.all().get(clazz);
        boolean tokenGenerationOnCreationEnabled;
        try {
            Method isTokenGenerationOnCreationEnabled = clazz.getDeclaredMethod("isTokenGenerationOnCreationEnabled");
            tokenGenerationOnCreationEnabled = (boolean) isTokenGenerationOnCreationEnabled.invoke(apiTokenConfiguration);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return false;
        }
        
        return tokenGenerationOnCreationEnabled;
    }
}
