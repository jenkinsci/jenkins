package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Node;

import java.util.ArrayList;
import java.util.List;

public class NodeProperties {
	public static final List<NodePropertyDescriptor> PROPERTIES = Descriptor
			.toList((NodePropertyDescriptor) EnvironmentVariablesNodeProperty.DESCRIPTOR);

	/**
	 * List up all {@link NodePropertyDescriptor}s that are applicable for the
	 * given project.
	 */
	public static List<NodePropertyDescriptor> getFor(Node node) {
		List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
		for (NodePropertyDescriptor npd : PROPERTIES) {
			if (npd.isApplicable(node.getClass())) {
				result.add(npd);
			}
		}
		return result;
	}
	
}
