/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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
package jenkins.security.security218;

import jenkins.security.security218.ysoserial.payloads.*;


/**
 * Allows to select {@link ObjectPayload}s.
 * @author Oleg Nenashev
 */
public enum Payload {
    CommonsBeanutils1(CommonsBeanutils1.class),
    CommonsCollections1(CommonsCollections1.class),
    CommonsCollections2(CommonsCollections2.class),
    CommonsCollections3(CommonsCollections3.class),
    CommonsCollections4(CommonsCollections4.class),
    CommonsCollections5(CommonsCollections5.class),
    CommonsCollections6(CommonsCollections6.class),
    FileUpload1(FileUpload1.class),
    Groovy1(Groovy1.class),
    Jdk7u21(Jdk7u21.class),
    JRMPClient(JRMPClient.class),
    JRMPListener(JRMPListener.class),
    JSON1(JSON1.class),
    Spring1(Spring1.class),
    Spring2(Spring2.class),
    Ldap(Ldap.class),
    ;

    private final Class<? extends ObjectPayload> payloadClass;
    
    private Payload(Class<? extends ObjectPayload> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<? extends ObjectPayload> getPayloadClass() {
        return payloadClass;
    }
}
