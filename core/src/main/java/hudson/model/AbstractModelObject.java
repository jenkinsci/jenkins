package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

import hudson.search.SearchableModelObject;
import hudson.search.Search;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchIndex;

/**
 * {@link ModelObject} with some convenience methods.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractModelObject implements SearchableModelObject {
    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        sendError(e.getMessage(),req,rsp);
    }

    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message",message);
        rsp.forward(this,"error",req);
    }

    /**
     * Default implementation that returns empty index.
     */
    protected SearchIndexBuilder makeSearchIndex() {
        return new SearchIndexBuilder().addAllAnnotations(this);
    }

    public final SearchIndex getSearchIndex() {
        return makeSearchIndex().make();
    }

    public Search getSearch() {
        return new Search();
    }

    /**
     * Default implementation that returns the display name.
     */
    public String getSearchName() {
        return getDisplayName();
    }
}
