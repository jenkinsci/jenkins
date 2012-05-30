package hudson.tasks;

import static org.mockito.Mockito.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.User;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.common.collect.Sets;

/**
 * Test case for the {@link MailSender}
 * 
 * See also {@link MailerTest} for more tests for the mailer.
 * 
 * @author Christoph Kutzinski
 */
@SuppressWarnings("rawtypes")
public class MailSenderTest {
    
    private AbstractBuild build;
    private AbstractBuild previousBuild;
    
    private AbstractBuild upstreamBuild;
    private AbstractBuild previousBuildUpstreamBuild;
    private AbstractBuild upstreamBuildBetweenPreviousAndCurrent;
    
    private AbstractProject upstreamProject;
    private BuildListener listener;

    @SuppressWarnings("unchecked")
    @Before
    public void before() throws IOException {
        this.upstreamProject = mock(AbstractProject.class);
        
        this.previousBuildUpstreamBuild = mock(AbstractBuild.class);
        this.upstreamBuildBetweenPreviousAndCurrent = mock(AbstractBuild.class);
        this.upstreamBuild = mock(AbstractBuild.class);
        
        createPreviousNextRelationShip(this.previousBuildUpstreamBuild, this.upstreamBuildBetweenPreviousAndCurrent,
                this.upstreamBuild);
                
        
        
        User user1 = mock(User.class);
        when(user1.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.should.not.be.included@example.com"));
        Set<User> badGuys1 = Sets.newHashSet(user1);
        when(this.previousBuildUpstreamBuild.getCulprits()).thenReturn(badGuys1);
        
        User user2 = mock(User.class);
        when(user2.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.must.be.included@example.com"));
        Set<User> badGuys2 = Sets.newHashSet(user2);
        when(this.upstreamBuildBetweenPreviousAndCurrent.getCulprits()).thenReturn(badGuys2);
        
        User user3 = mock(User.class);
        when(user3.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.must.be.included.too@example.com"));
        Set<User> badGuys3 = Sets.newHashSet(user3);
        when(this.upstreamBuild.getCulprits()).thenReturn(badGuys3);
        
        
        this.previousBuild = mock(AbstractBuild.class);
        when(this.previousBuild.getResult()).thenReturn(Result.SUCCESS);
        when(this.previousBuild.getUpstreamRelationshipBuild(this.upstreamProject)).thenReturn(this.previousBuildUpstreamBuild);
        
        this.build = mock(AbstractBuild.class);
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(build.getUpstreamRelationshipBuild(upstreamProject)).thenReturn(this.upstreamBuild);

        createPreviousNextRelationShip(this.previousBuild, this.build);
        
        this.listener = mock(BuildListener.class);
        when(this.listener.getLogger()).thenReturn(System.out);
    }
    
    /**
     * Creates a previous/next relationship between the builds in the given order.
     */
    private static void createPreviousNextRelationShip(AbstractBuild... builds) {
        int max = builds.length - 1;
        
        for (int i = 0; i < builds.length; i++) {
            if (i < max) {
                when(builds[i].getNextBuild()).thenReturn(builds[i+1]);
            }
        }
        
        for (int i = builds.length - 1; i >= 0; i--) {
            if (i >= 1) {
                when(builds[i].getPreviousBuild()).thenReturn(builds[i-1]);
            }
        }
    }

    /**
     * Tests that all culprits from the previous builds upstream build (exclusive)
     * until the current builds upstream build (inclusive) are contained in the recipients
     * list.
     */
    @Test
    public void testIncludeUpstreamCulprits() throws Exception {
        Collection<AbstractProject> upstreamProjects = Collections.singleton(this.upstreamProject);
        
        MailSender sender = new MailSender("", false, false, "UTF-8", upstreamProjects);
        Set<InternetAddress> recipients = Sets.newHashSet();
        sender.includeCulpritsOf(upstreamProject, build, listener, recipients);
        
        assertEquals(2, recipients.size());
        assertFalse(recipients.contains(new InternetAddress("this.one.should.not.be.included@example.com")));
        assertTrue(recipients.contains(new InternetAddress("this.one.must.be.included@example.com")));
        assertTrue(recipients.contains(new InternetAddress("this.one.must.be.included.too@example.com")));
    }
}
