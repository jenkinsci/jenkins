/*
 * The MIT License
 *
 * Copyright (c) 2023, Cloudbees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
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

import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;

/**
 * Utilities to help code change behaviour when it is desired to run in a FIPS-140 enabled environment.
 * The environment (host, JVM and servlet container), must be suitably configured which is outside the scope of the Jenkins project.
 * @see <a href="https://csrc.nist.gov/pubs/fips/140-2/upd2/final">FIPS-140-2</a>
 * @see <a href="https://github.com/jenkinsci/jep/tree/master/jep/237#readme">JEP-237</a>
 * @since 2.426
 */
public class FIPS140 {

    private static final Logger LOGGER = Logger.getLogger(FIPS140.class.getName());

    private static final boolean FIPS_COMPLIANCE_MODE = SystemProperties.getBoolean(FIPS140.class.getName() + ".COMPLIANCE");

    static {
        if (useCompliantAlgorithms()) {
            LOGGER.log(Level.CONFIG, "System has been asked to run in FIPS-140 compliant mode");
        }
    }

    /**
     * Provide a hint that the system should strive to be compliant with <a href="https://csrc.nist.gov/pubs/fips/140-2/upd2/final">FIPS-140-2</a>.
     * This can be used by code that needs to make choices at runtime whether to disable some optional behaviour that is not compliant with FIPS-140,
     * or to switch to a compliant (yet less secure) alternative.
     * If this returns {@code true} it does not mean that the instance is compliant, it merely acts as a hint.
     * @return {@code true} iff the system should prefer compliance with FIPS-140-2 over compatibility with existing data or alternative non approved algorithms.
     */
    public static boolean useCompliantAlgorithms() {
        return FIPS_COMPLIANCE_MODE;
    }

}
