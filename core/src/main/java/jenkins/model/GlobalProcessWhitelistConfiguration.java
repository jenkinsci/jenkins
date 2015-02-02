/*
 * The MIT License
 *
 * Copyright (c) 2015, Daniel Weber
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
package jenkins.model;

import hudson.Extension;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Configures a whitelist for processes that should not be killed 
 * 
 * @author Daniel Weber
 */
@Extension(ordinal=401)
public class GlobalProcessWhitelistConfiguration extends GlobalConfiguration {

	public String getProcessWhitelist(){
		return Jenkins.getInstance().getProcessCleanupWhitelist();
	}
	
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
    	String whitelist = (String) json.get("processCleanupWhitelist");
    	try {
            Jenkins.getInstance().setProcessCleanupWhitelist(whitelist);
        } catch (IOException e) {
            throw new FormException(e, "processCleanupWhitelist");
        }
    	return true;
    }
}
