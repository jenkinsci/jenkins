package hudson.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.User;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class ChangeLogSetEntryTest {

    @Test
    public void getCommentDefaultsToMsg() {
        ChangeLogSet.Entry entry = new ChangeLogSet.Entry() {

            @Override
            public String getMsg() {
                return "short message";
            }

            @Override
            public User getAuthor() {
                 return User.getOrCreateByIdOrFullName("test-user");
            }

            @Override
            public Collection<String> getAffectedPaths() {
                return Collections.emptyList();
            }
        };

        assertEquals("short message", entry.getComment());
    }

    @Test
    public void getCommentCanBeOverridden() {
        ChangeLogSet.Entry entry = new ChangeLogSet.Entry() {

            @Override
            public String getMsg() {
                return "short";
            }

            @Override
            public String getComment() {
                return "full commit message";
            }

            @Override
            public User getAuthor() {
                return User.getOrCreateByIdOrFullName("testest-user");
            }

            @Override
            public Collection<String> getAffectedPaths() {
                return Collections.emptyList();
            }
        };

        assertEquals("full commit message", entry.getComment());
    }
}
