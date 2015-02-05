package jenkins.dom4j;

import org.jaxen.Function;
import org.jaxen.FunctionContext;
import org.jaxen.UnresolvableException;
import org.jaxen.XPathFunctionContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link org.jaxen.FunctionContext} that removes some {@link org.dom4j.XPath}
 * function names that are deemed bad as user input.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see org.jaxen.FunctionContext
 * @see org.dom4j.XPath
 * @see hudson.model.Api
 */
public class FilteredFunctionContext implements FunctionContext {

    /**
     * Default set of "bad" function names.
     */
    private static final Set<String> DEFAULT_ILLEGAL_FUNCTIONS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("document")
    ));
    private final FunctionContext base;
    private final Set<String> illegalFunctions;

    public FilteredFunctionContext(Set<String> illegalFunctions) {
        this.illegalFunctions = illegalFunctions;
        base = XPathFunctionContext.getInstance();
    }

    public FilteredFunctionContext() {
        this(DEFAULT_ILLEGAL_FUNCTIONS);
    }

    @Override
    public Function getFunction(String namespaceURI, String prefix, String localName) throws UnresolvableException {
        if (localName != null && illegalFunctions.contains(localName.toLowerCase())) {
            throw new UnresolvableException("Illegal function: " + localName);
        }
        return base.getFunction(namespaceURI, prefix, localName);
    }
}
