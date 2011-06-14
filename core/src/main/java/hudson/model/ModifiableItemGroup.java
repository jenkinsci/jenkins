package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * {@link ItemGroup} that is a general purpose container, which allows users and the rest of the program
 * to create arbitrary items into it.
 *
 * <p>
 * In contrast, some other {@link ItemGroup}s compute its member {@link Item}s and the content
 * is read-only, thus it cannot allow external code/user to add arbitrary objects in it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.417
 */
public interface ModifiableItemGroup<T extends Item> extends ItemGroup<T> {
    /**
     * The request format follows that of {@code &lt;n:form xmlns:n="/lib/form">}.
     *
     */
    T doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;
}
