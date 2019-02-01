/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package test.security.realm;

import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * Accept any password
 * Allow creation and removal of users
 */
public class InMemorySecurityRealm extends AbstractPasswordBasedSecurityRealm {
    private Map<String, UserDetails> userStorage = new HashMap<>();

    public synchronized void createAccount(String username) {
        if (userStorage.containsKey(username)) {
            return;
        }
        UserDetails details = new InMemoryUserDetails(username);
        userStorage.put(username, details);
    }

    public synchronized void deleteAccount(String username) {
        if (!userStorage.containsKey(username)) {
            return;
        }
        userStorage.remove(username);
    }

    @Override
    protected UserDetails authenticate(String username, String password) throws AuthenticationException {
        if (userStorage.containsKey(username)) {
            return userStorage.get(username);
        }
        throw new UsernameNotFoundException("Unknown user: " + username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        return userStorage.get(username);
    }

    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        return null;
    }

    private static class InMemoryUserDetails implements UserDetails {
        private final String username;

        private InMemoryUserDetails(String username) {
            this.username = username;
        }

        @Override
        public GrantedAuthority[] getAuthorities() {
            return new GrantedAuthority[0];
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
