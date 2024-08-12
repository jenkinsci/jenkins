package hudson.search;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import hudson.model.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import jenkins.model.Jenkins;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import java.util.*;

public class CollectionSearchIndexTest {

    private CollectionSearchIndex<SearchableModelObject> index;


    @Before
    public void setUp() {
        // Create a mock implementation of CollectionSearchIndex
        index = new CollectionSearchIndex<SearchableModelObject>() {
            @Override
            protected SearchItem get(String key) {
                return null;
            }

            @Override
            protected Collection<SearchableModelObject> all() {
                // mock collection for testing
                return List.of(
                        createMockUser("Alice"),
                        createMockUser("Bob"),
                        createMockUser("Charlie")
                );
            }
            @Override
            public boolean isUserItem(SearchableModelObject obj) {
                if (obj instanceof User) {
                    return true;
                }
                return false;
            }
        };
    }

    private User createMockUser(String name) {
        User user = mock(User.class);
        when(user.getDisplayName()).thenReturn(name);
        return user;
    }

    @Test
    public void testSuggestWithAnonymousReadOnlyPermission() {
        try (MockedStatic<UserSearchProperty> userSearchPropertyMock = mockStatic(UserSearchProperty.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class)) {

            // Mock UserSearchProperty to make search case-insensitive
            userSearchPropertyMock.when(UserSearchProperty::isCaseInsensitive).thenReturn(true);

            // Mock Jenkins and ACL for anonymous user with read-only permission
            Jenkins jenkins = mock(Jenkins.class);
            ACL acl = mock(ACL.class);
            Authentication authentication = mock(Authentication.class);

            // Simulate anonymous user by mocking Authentication to return "anonymous" principal
            when(authentication.getPrincipal()).thenReturn("anonymous");
            when(authentication.isAuthenticated()).thenReturn(false);

            when(jenkins.getACL()).thenReturn(acl);
            when(Jenkins.getAuthentication()).thenReturn(authentication);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.READ))).thenReturn(true);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.ADMINISTER))).thenReturn(false);

            jenkinsMock.when(Jenkins::get).thenReturn(jenkins);

            List<SearchItem> result = new ArrayList<>();
            index.suggest("a", result);

            // Anonymous user with read-only permission should not see any users in search suggestions
            assertEquals(0, result.size());
        }
    }

    @Test
    public void testSuggestWithReadOnlyPermission() {
        try (MockedStatic<UserSearchProperty> userSearchPropertyMock = mockStatic(UserSearchProperty.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class)) {

            // Mock UserSearchProperty
            userSearchPropertyMock.when(UserSearchProperty::isCaseInsensitive).thenReturn(true);

            // Mock Jenkins and ACL for read-only user
            Jenkins jenkins = mock(Jenkins.class);
            ACL acl = mock(ACL.class);
            Authentication authentication = mock(Authentication.class);

            when(authentication.getPrincipal()).thenReturn("readOnlyUser");
            when(authentication.isAuthenticated()).thenReturn(true);

            when(jenkins.getACL()).thenReturn(acl);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.READ))).thenReturn(true);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.ADMINISTER))).thenReturn(false);

            jenkinsMock.when(Jenkins::get).thenReturn(jenkins);
            jenkinsMock.when(Jenkins::getAuthentication).thenReturn(authentication);

            List<SearchItem> result = new ArrayList<>();
            index.suggest("a", result);

            // log in read-only user can also get to search for other users
            assertEquals(2, result.size());
        }
    }

    @Test
    public void testSuggestWithAdminPermission() {
        try (MockedStatic<UserSearchProperty> userSearchPropertyMock = mockStatic(UserSearchProperty.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class)) {

            userSearchPropertyMock.when(UserSearchProperty::isCaseInsensitive).thenReturn(true);

            Jenkins jenkins = mock(Jenkins.class);
            ACL acl = mock(ACL.class);
            Authentication authentication = mock(Authentication.class);

            when(authentication.getPrincipal()).thenReturn("adminUser");
            when(authentication.isAuthenticated()).thenReturn(true);

            when(jenkins.getACL()).thenReturn(acl);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.READ))).thenReturn(true);
            when(acl.hasPermission(eq(authentication), eq(Jenkins.ADMINISTER))).thenReturn(true);

            jenkinsMock.when(Jenkins::get).thenReturn(jenkins);

            List<SearchItem> result = new ArrayList<>();
            index.suggest("a", result);

            // admin is able to search other users
            assertEquals(2, result.size());
        }
    }
}