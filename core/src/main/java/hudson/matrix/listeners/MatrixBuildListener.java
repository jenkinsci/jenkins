package hudson.matrix.listeners;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixBuild;
import hudson.model.Hudson;

public abstract class MatrixBuildListener implements ExtensionPoint {
	
	/**
	 * Determine whether to build a given configuration or not
	 * @param b
	 * @param c
	 * @return
	 */
	public boolean doBuildConfiguration(MatrixBuild b, MatrixConfiguration c){return true;}

	public static boolean buildConfiguration(MatrixBuild b, MatrixConfiguration c) {
		for (MatrixBuildListener l : all()) {
			if(!l.doBuildConfiguration(b, c)) {
				return false;
			}
			
		}
		
		return true;
	}

	/**
	 * Returns all the registered {@link MatrixBuildListener} descriptors.
	 */
	public static ExtensionList<MatrixBuildListener> all() {
		return Hudson.getInstance().getExtensionList(MatrixBuildListener.class);
	}
}
