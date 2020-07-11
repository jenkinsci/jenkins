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

/**
 * <code>PortMapper</code> implementations provide callers with information
 * about which HTTP ports are associated with which HTTPS ports on the system,
 * and vice versa.
 *
 * Copied from acegi-security
 */
public interface PortMapper {
    /**
     * Locates the HTTP port associated with the specified HTTPS port.<P>Returns <code>null</code> if unknown.</p>
     *
     * @param httpsPort
     *
     * @return the HTTP port or <code>null</code> if unknown
     */
    Integer lookupHttpPort(Integer httpsPort);

    /**
     * Locates the HTTPS port associated with the specified HTTP port.<P>Returns <code>null</code> if unknown.</p>
     *
     * @param httpPort
     *
     * @return the HTTPS port or <code>null</code> if unknown
     */
    Integer lookupHttpsPort(Integer httpPort);
}
