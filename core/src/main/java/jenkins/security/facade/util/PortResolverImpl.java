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

package jenkins.security.facade.util;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import javax.servlet.ServletRequest;


/**
 * Concrete implementation of {@link PortResolver} that obtains the port from
 * <code>ServletRequest.getServerPort()</code>.<P>This class is capable of handling the IE bug which results in an
 * incorrect URL being presented in the header subsequent to a redirect to a different scheme and port where the port
 * is not a well-known number (ie 80 or 443). Handling involves detecting an incorrect response from
 * <code>ServletRequest.getServerPort()</code> for the scheme (eg a HTTP request on 8443) and then determining the
 * real server port (eg HTTP request is really on 8080). The map of valid ports is obtained from the configured {@link
 * PortMapper}.</p>
 *
 * Copied from acegi-security
 */
public class PortResolverImpl implements InitializingBean, PortResolver {
    //~ Instance fields ================================================================================================

    private PortMapper portMapper = new PortMapperImpl();

    //~ Methods ========================================================================================================

    public void afterPropertiesSet() throws Exception {
        Assert.notNull(portMapper, "portMapper required");
    }

    public PortMapper getPortMapper() {
        return portMapper;
    }

    public int getServerPort(ServletRequest request) {
        int result = request.getServerPort();

        if ("http".equals(request.getScheme().toLowerCase())) {
            Integer http = portMapper.lookupHttpPort(result);

            if (http != null) {
                // IE 6 bug
                result = http.intValue();
            }
        }

        if ("https".equals(request.getScheme().toLowerCase())) {
            Integer https = portMapper.lookupHttpsPort(result);

            if (https != null) {
                // IE 6 bug
                result = https;
            }
        }

        return result;
    }

    public void setPortMapper(PortMapper portMapper) {
        this.portMapper = portMapper;
    }
}
