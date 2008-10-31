package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;

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

    protected final void sendError(Exception e) throws ServletException, IOException {
        sendError(e,Stapler.getCurrentRequest(),Stapler.getCurrentResponse());
    }

    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message",message);
        rsp.forward(this,"error",req);
    }

    protected final void sendError(String message) throws ServletException, IOException {
        sendError(message,Stapler.getCurrentRequest(),Stapler.getCurrentResponse());
    }

    /**
     * Convenience method to verify that the current request is a POST request.
     */
    protected final void requirePOST() throws ServletException {
        String method = Stapler.getCurrentRequest().getMethod();
        if(!method.equalsIgnoreCase("POST"))
            throw new ServletException("Must be POST, Can't be "+method);
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
