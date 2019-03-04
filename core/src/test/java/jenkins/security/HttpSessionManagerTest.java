/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.security;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextImpl;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.acegisecurity.context.HttpSessionContextIntegrationFilter.ACEGI_SECURITY_CONTEXT_KEY;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class HttpSessionManagerTest {

    private HttpSessionManager manager = new HttpSessionManager();

    private HttpSession createSession(String sessionId) {
        HttpSession session = mock(HttpSession.class);
        given(session.getId()).willReturn(sessionId);
        manager.sessionCreated(new HttpSessionEvent(session));
        return session;
    }

    @Test
    public void invalidateAllSessions() {
        List<HttpSession> sessions = IntStream.range(0, 100)
                .mapToObj(i -> createSession("sessionId-" + i))
                .collect(Collectors.toList());

        manager.doInvalidateAllSessions();

        sessions.forEach(session -> {
            then(session).should(times(2)).getId();
            then(session).should().invalidate();
        });
    }

    @Test
    public void invalidateAllSessionsExcept() {
        List<HttpSession> sessions = IntStream.range(0, 100)
                .mapToObj(i -> createSession("sessionId-" + i))
                .collect(Collectors.toList());

        manager.doInvalidateAllSessionsExcept("sessionId-99");

        HttpSession last = sessions.remove(99);
        then(last).should().getId();
        then(last).shouldHaveNoMoreInteractions();

        sessions.forEach(session -> {
            then(session).should(times(2)).getId();
            then(session).should().invalidate();
        });
    }

    @Test
    public void invalidateSession() {
        HttpSession session = createSession("session");

        manager.doInvalidateSession("session");

        then(session).should(times(2)).getId();
        then(session).should().invalidate();
    }

}