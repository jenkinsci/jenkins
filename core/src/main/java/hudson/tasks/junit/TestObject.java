/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.tasks.junit;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractModelObject;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Build;
import hudson.model.Item;
import hudson.model.Messages;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import java.awt.Color;
import java.awt.Paint;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.servlet.ServletException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Base class for all test result objects.
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class TestObject extends AbstractModelObject implements Serializable {
	public AbstractBuild<?, ?> getOwner() {
		return getParent().getOwner();
	}

	private volatile transient String id;

	public abstract TestObject getParent();

	public String getId() {
		if (id == null) {
			id = getParent().getId() + "/" + getSafeName();
		}
		return id;
	}
	
	/**
	 * Returns url relative to TestResult
	 */
	public String getUrl() {
		return getId();
	}

	public TestResult getTestResult() {
		return getParent().getTestResult();
	}

	public TestResultAction getTestResultAction() {
		return getOwner().getAction(TestResultAction.class);
	}

	public List<TestAction> getTestActions() {
		return getTestResultAction().getActions(this);
	}

	public <T> T getTestAction(Class<T> klazz) {
		for (TestAction action : getTestActions()) {
			if (klazz.isAssignableFrom(action.getClass())) {
				return klazz.cast(action);
			}
		}
		return null;
	}

	/**
	 * Gets the counter part of this {@link TestObject} in the previous run.
	 * 
	 * @return null if no such counter part exists.
	 */
	public abstract TestObject getPreviousResult();
	
	public abstract TestObject getResultInBuild(AbstractBuild<?,?> build); 

	/**
	 * Time took to run this test. In seconds.
	 */
	public abstract float getDuration();

	/**
	 * Returns the string representation of the {@link #getDuration()}, in a
	 * human readable format.
	 */
	public String getDurationString() {
		return Util.getTimeSpanString((long) (getDuration() * 1000));
	}
	
	public String getDescription() {
		return getTestResultAction().getDescription(this);
	}
	
	public void setDescription(String description) {
		getTestResultAction().setDescription(this, description);
	}

	/**
	 * Exposes this object through the remote API.
	 */
	public Api getApi() {
		return new Api(this);
	}

	/**
	 * Gets the name of this object.
	 */
	public/* abstract */String getName() {
		return "";
	}

	/**
	 * Gets the version of {@link #getName()} that's URL-safe.
	 */
	public String getSafeName() {
		return safe(getName());
	}
	
	public String getSearchUrl() {
		return getSafeName();
	}

	/**
	 * #2988: uniquifies a {@link #getSafeName} amongst children of the parent.
	 */
	protected final synchronized String uniquifyName(
			Collection<? extends TestObject> siblings, String base) {
		String uniquified = base;
		int sequence = 1;
		for (TestObject sibling : siblings) {
			if (sibling != this
					&& uniquified.equals(UNIQUIFIED_NAMES.get(sibling))) {
				uniquified = base + '_' + ++sequence;
			}
		}
		UNIQUIFIED_NAMES.put(this, uniquified);
		return uniquified;
	}

	private static final Map<TestObject, String> UNIQUIFIED_NAMES = new WeakHashMap<TestObject, String>();

	/**
	 * Replaces URL-unsafe characters.
	 */
	protected static String safe(String s) {
		// 3 replace calls is still 2-3x faster than a regex replaceAll
		return s.replace('/', '_').replace('\\', '_').replace(':', '_');
	}
	
    /**
     * Gets the total number of passed tests.
     */
    public abstract int getPassCount();

    /**
     * Gets the total number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of skipped tests.
     */
    public abstract int getSkipCount();

    /**
     * Gets the total number of tests.
     */
    public final int getTotalCount() {
        return getPassCount()+getFailCount()+getSkipCount();
    }
	
	public History getHistory() {
		return new History(this);
	}
	
	public Object getDynamic(String token, StaplerRequest req,
			StaplerResponse rsp) {
		for (Action a : getTestActions()) {
			if (a == null)
				continue; // be defensive
			String urlName = a.getUrlName();
			if (urlName == null)
				continue;
			if (urlName.equals(token))
				return a;
		}
		return null;
	}

	public synchronized HttpResponse doSubmitDescription(
			@QueryParameter String description) throws IOException,
			ServletException {
		getOwner().checkPermission(Item.BUILD);

		setDescription(description);
		getOwner().save();
		
		return new HttpRedirect(".");
	}

	private static final long serialVersionUID = 1L;
}
