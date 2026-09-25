/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Contains information about the token and the secret value.
 * It should not be stored as is, but just displayed once to the user and then forget about it.
 * @since This was added in 2.260 but marked NoExternalUse, opened as Beta in 2.265
 */
@Restricted(Beta.class)
public class TokenUuidAndPlainValue {
    /**
     * The token identifier to allow manipulation of the token
     */
    public final String tokenUuid;

    /**
     * Confidential information, must not be stored.<p>
     * It's meant to be send only one to the user and then only store the hash of this value.
     */
    public final String plainValue;


    public final String expirationDate;

    public final boolean aboutToExpire;

    public TokenUuidAndPlainValue(String tokenUuid, String plainValue, LocalDate expirationDate, boolean aboutToExpire) {
        this.tokenUuid = tokenUuid;
        this.plainValue = plainValue;
        this.aboutToExpire = aboutToExpire;
        if (expirationDate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, L d u");
            this.expirationDate = formatter.format(expirationDate);
        } else {
            this.expirationDate = "Never";
        }
    }
}
