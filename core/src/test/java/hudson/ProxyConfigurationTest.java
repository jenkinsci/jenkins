/*
 * The MIT License
 *
 * Copyright (c) 2012, Hayato ITO
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

package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.Proxy;
import org.junit.jupiter.api.Test;

public class ProxyConfigurationTest {

    @Test
    public void noProxyHost() {
        String noProxyHost = "*.example.com|192.168.*";
        assertEquals(Proxy.Type.HTTP, ProxyConfiguration.createProxy("test.example.co.jp", "proxy.example.com", 8080, noProxyHost).type());
        assertEquals(Proxy.Type.DIRECT, ProxyConfiguration.createProxy("test.example.com", "proxy.example.com", 8080, noProxyHost).type());
        assertEquals(Proxy.Type.HTTP, ProxyConfiguration.createProxy("test.example.com.test.example.co.jp", "proxy.example.com", 8080, noProxyHost).type());
        assertEquals(Proxy.Type.DIRECT, ProxyConfiguration.createProxy("test.test.example.com", "proxy.example.com", 8080, noProxyHost).type());
        assertEquals(Proxy.Type.DIRECT, ProxyConfiguration.createProxy("192.168.10.10", "proxy.example.com", 8080, noProxyHost).type());
        assertEquals(Proxy.Type.HTTP, ProxyConfiguration.createProxy("192.169.10.10", "proxy.example.com", 8080, noProxyHost).type());
    }
}
