/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.security.facade.event.authentication;

import org.acegisecurity.Authentication;
import org.springframework.util.Assert;


/**
 * Indicates an interactive authentication was successful.<P>The <code>ApplicationEvent</code>'s
 * <code>source</code> will be the <code>Authentication</code> object.</p>
 *
 * Copied from acegi-security
 * PATCH: nobody uses the child event
 */
public class InteractiveAuthenticationSuccessEvent extends AbstractAuthenticationEvent {
    //~ Instance fields ================================================================================================

    private Class generatedBy;

    //~ Constructors ===================================================================================================

    public InteractiveAuthenticationSuccessEvent(Authentication authentication, Class generatedBy) {
        super(authentication);
        Assert.notNull(generatedBy);
        this.generatedBy = generatedBy;
    }

    //~ Methods ========================================================================================================

    /**
     * Getter for the <code>Class</code> that generated this event. This can be useful for generating
     * additional logging information.
     *
     * @return the class
     */
    public Class getGeneratedBy() {
        return generatedBy;
    }
}
