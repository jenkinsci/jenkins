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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 *  This class provides an in-memory implementation of Jenkin's SecurityRealm for testing. 
 *  It allows the creation and removal of users, and accepts any password for authentication.
 *  It maintains an in-memory storage of user accounts in a HashMap-userStorage and allows for creation and deletion of users 
 *  by modifying this hashmap.
 *  The user also gets authenticated with any password as the only thing validated is presence of username entry in the hashmap
 *  It implements the loadUserByUsername2 method which simply returns the user from the in memory storage based on username passed
 *  A dummy implementation for UserDetails is provided in class InMemoryUserDetails which contains no roles and only username is returned
 *    
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
    protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
        if (userStorage.containsKey(username)) {
            return userStorage.get(username);
        }
        throw new UsernameNotFoundException("Unknown user: " + username);
    }

    @Override
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        return userStorage.get(username);
    }

    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
        return null;
    }

    private static class InMemoryUserDetails implements UserDetails {
        private final String username;

        private InMemoryUserDetails(String username) {
            this.username = username;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.emptySet();
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
