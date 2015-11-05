/*
 * The MIT License
 * 
 * Copyright (c) 2012, CloudBees, Inc.
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
package hudson.scheduler;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Generates a pseudo-random sequence of integers in the specified range.
 *
 * <p>
 * {@link CronTab} supports tokens like '@daily', which means "do it once a day".
 * Exactly which time of the day this gets scheduled is randomized --- randomized
 * in the sense that it's spread out when many jobs choose @daily, but it's at
 * the same time stable so that every job sticks to a specific time of the day
 * even after the configuration is updated.
 *
 * <p>
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.448
 */
public abstract class Hash {
    /*package*/ Hash() {}

    /**
     * Produces an integer in [0,n)
     */
    public abstract int next(int n);
    
    public static Hash from(String seed) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(seed.getBytes("UTF-8"));
            byte[] digest = md5.digest();

            for (int i=8; i<digest.length; i++)
                digest[i%8] ^= digest[i];

            long l = 0;
            for (int i=0; i<8; i++)
                l = (l<<8)+(digest[i]&0xFF);

            final Random rnd = new Random(l);
            return new Hash() {
                @Override
                public int next(int n) {
                    return rnd.nextInt(n);
                }
            };
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);    // MD5 is a part of JRE
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);    // UTF-8 is mandatory
        }
    }

    /**
     * Creates a hash that always return 0.
     */
    public static Hash zero() {
        return ZERO;
    }

    private static final Hash ZERO = new Hash() {
        @Override
        public int next(int n) {
            return 0;
        }
    };
}
