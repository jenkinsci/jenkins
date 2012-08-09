/*
 * The MIT License
 * 
 * Copyright (c) 2012 Red Hat, Inc.
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
package hudson.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.ExtensionList;
import hudson.model.Hudson;
import hudson.model.User;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest( {MailAddressResolver.class, Mailer.class, Mailer.DescriptorImpl.class})
public class MailAddressResolverTest {
    
    private User user;
    private Jenkins jenkins;
    
    
    @Before
    public void setUp () {
        
        jenkins = Mockito.mock(Hudson.class);
        user = Mockito.mock(User.class);
        when(user.getFullName()).thenReturn("Full name");
        when(user.getId()).thenReturn("user_id");
    }
    
    private static class MockExtensionList extends ExtensionList<MailAddressResolver> {
        
        private List extensions;
        
        public MockExtensionList(
                final Jenkins jenkins,
                final MailAddressResolver... resolvers
        ) {
            
            super(jenkins, MailAddressResolver.class);
            
            extensions = Arrays.asList(resolvers);
        }
        
        @Override
        public Iterator<MailAddressResolver> iterator() {
            
            return extensions.iterator();
        }
    }
    
    private ExtensionList<MailAddressResolver> getResolverListMock(
            final Jenkins jenkins,
            final MailAddressResolver... resolvers
    ) {
        
        return new MockExtensionList(jenkins, resolvers);
    }
    
    private Mailer.DescriptorImpl getResolveDescriptor(final boolean answer) {
        
        final Mailer.DescriptorImpl descriptor = PowerMockito.mock(Mailer.DescriptorImpl.class);
        
        when(descriptor.getTryToResolve()).thenReturn(answer);
        
        return descriptor;
    }

    @Test
    public void useAllResolversWhenNothingFound() throws Exception {
        
        final MailAddressResolver[] resolvers = {
                Mockito.mock(MailAddressResolver.class),
                Mockito.mock(MailAddressResolver.class)
        };
                        
        configure(resolvers, true);
                               
        final String address = MailAddressResolver.resolve(user);
        
        verify(resolvers[0]).findMailAddressFor(user);
        verify(resolvers[1]).findMailAddressFor(user);
        
        assertNull(address);
    }
    
    @Test
    public void stopResolutionWhenAddressFound() throws Exception {
        
        final MailAddressResolver[] resolvers = {
                Mockito.mock(MailAddressResolver.class),
                Mockito.mock(MailAddressResolver.class)
        };
        
        when(resolvers[0].findMailAddressFor(user)).thenReturn("mail@addr.com");
                        
        configure(resolvers, true);
                               
        final String address = MailAddressResolver.resolve(user);
        
        verify(resolvers[0]).findMailAddressFor(user);
        verify(resolvers[1], never()).findMailAddressFor(user);
        
        assertEquals(address, "mail@addr.com");
    }
    
    @Test
    public void doNetResolveWhenForbidden() throws Exception {
        
        final MailAddressResolver[] resolvers = {
                Mockito.mock(MailAddressResolver.class)
        };
        
        configure(resolvers, false);
                               
        final String address = MailAddressResolver.resolve(user);
        
        verify(resolvers[0], never()).findMailAddressFor(user);
        
        assertNull(address);
    }
    
    private void configure(
            final MailAddressResolver[] resolvers,
            final boolean resolve
    ) throws Exception {
        
        PowerMockito.spy(Mailer.class);
        PowerMockito.doReturn(getResolveDescriptor(resolve))
                .when(Mailer.class, "descriptor")
        ;
        
        PowerMockito.spy(MailAddressResolver.class);
        
        PowerMockito.doReturn(getResolverListMock(jenkins, resolvers))
            .when(MailAddressResolver.class, "all")
        ;
    }
}
