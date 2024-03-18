/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package org.acegisecurity.context;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class SecurityContextTest {

    @Test
    public void serializabilityFromSpring() throws Exception {
        org.springframework.security.core.context.SecurityContext spring1 = new org.springframework.security.core.context.SecurityContextImpl();
        spring1.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", null));
        SecurityContext acegi1 = SecurityContext.fromSpring(spring1);
        SecurityContext acegi2 = serDeser(SecurityContext.class, acegi1);
        org.springframework.security.core.context.SecurityContext spring2 = acegi2.toSpring();
        assertThat(spring2.getAuthentication().getPrincipal(), is("user"));
    }

    @Test
    public void serializabilityToSpring() throws Exception {
        SecurityContext acegi1 = new SecurityContextImpl();
        acegi1.setAuthentication(new UsernamePasswordAuthenticationToken("user", null));
        org.springframework.security.core.context.SecurityContext spring1 = acegi1.toSpring();
        org.springframework.security.core.context.SecurityContext spring2 = serDeser(org.springframework.security.core.context.SecurityContext.class, spring1);
        SecurityContext acegi2 = SecurityContext.fromSpring(spring2);
        assertThat(acegi2.getAuthentication().getPrincipal(), is("user"));
    }

    private static <T> T serDeser(Class<T> type, T object) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()); ObjectInputStream ois = new ObjectInputStream(bais)) {
                return type.cast(ois.readObject());
            }
        }
    }

}
