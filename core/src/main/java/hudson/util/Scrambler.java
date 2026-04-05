/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scrambles, but does not encrypt, text.
 * <strong>Do not use this</strong> for storing passwords and the like in configuration.
 * Use {@link Secret} instead.
 *
 * @deprecated There's no reason to use this. To store secrets encrypted, use {@link Secret}. To encode/decode Base64, use {@link java.util.Base64}.
 *
 * @author Kohsuke Kawaguchi
 */
@Deprecated(since = "2.505")
public class Scrambler {
    private static final Logger LOGGER = Logger.getLogger(Scrambler.class.getName());

    public static String scramble(String secret) {
        if (secret == null)    return null;
        return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String descramble(String scrambled) {
        if (scrambled == null)    return null;
        try {
            return new String(Base64.getDecoder().decode(scrambled.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Corrupted data", e);
            return "";  // corrupted data.
        }
    }
}
