/*
 * The MIT License
 *
 * Copyright (c) 2016, Baptiste Mathus
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
package hudson.node_monitors;

import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.slaves.OfflineCause;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

/**
 * Checks the used percentage of inodes on the FS (Linux/Unix only).
 */
public class InodesMonitor extends NodeMonitor {

	private static final Logger LOGGER = Logger.getLogger(InodesMonitor.class.getName());

	private static final String DEFAULT_OFFLINE_THRESHOLD = "95%";

	public final String inodesPercentThreshold;

	/**
	 * @param inodesPercentThreshold
	 *            threshod expected to be a percentage between 0% and 99% (% required). "5%" is correct. "5" is not.
	 *
	 * @throws ParseException
	 *             if unable to parse.
	 */
	@DataBoundConstructor
	public InodesMonitor(String inodesPercentThreshold) throws ParseException {
		if (inodesPercentThreshold == null) {
			inodesPercentThreshold = DEFAULT_OFFLINE_THRESHOLD;
		}
		parse(inodesPercentThreshold); // checks it parses
		this.inodesPercentThreshold = inodesPercentThreshold;
	}

	public InodesMonitor() {
		inodesPercentThreshold = DEFAULT_OFFLINE_THRESHOLD;
	}

	@VisibleForTesting
	static int parse(String threshold) throws ParseException {
		if (!threshold.matches("\\d?\\d%")) {
			throw new ParseException(threshold, 0);
		}
		return Integer.parseInt(threshold.substring(0, threshold.length() - 1));
	}

	@Override
	public Object data(Computer computer) {
		String currentValueStr = (String) super.data(computer);
		if (currentValueStr == null || currentValueStr.contains(Messages.InodesMonitor_NotApplicable())) {
			return currentValueStr;
		}
		try {
			int currentValue = parse(currentValueStr);
			String currentState = "current=" + currentValue + ",threshold=" + inodesPercentThreshold;
			String computerName = computer.getName();
			// master has no nodeName
			if ("".equals(computer.getName())) {
				computerName = hudson.model.Messages.Hudson_Computer_DisplayName();
			}

			if (currentValue >= parse(inodesPercentThreshold)) {
				OfflineCause offlineCause = OfflineCause.create(Messages._InodesMonitor_MarkedOffline(computerName, currentState));
				if (((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOffline(computer, offlineCause)) {
					String inodesmonitor_markedOffline = Messages.InodesMonitor_MarkedOffline(computerName, currentState);
					LOGGER.warning(inodesmonitor_markedOffline);
				}
			}
			else {
				if (((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOnline(computer)) {
					LOGGER.warning(Messages.InodesMonitor_MarkedOnline(computerName, currentState));
				}
			}
		}
		catch (ParseException e) {
			// Shouldn't happen since received value is the one already provided by internal GetInodesUseInPercent
			throw new IllegalStateException("WTF? Can't parse " + currentValueStr + " as integer percentage", e);
		}
		return currentValueStr;
	}

	@Override
	public final String getColumnCaption() {
		// Hide to non-admins
		return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
	}

	@Extension
	public static final InodesUseInPercentMonitorDescriptor DESCRIPTOR = new InodesUseInPercentMonitorDescriptor();

	static class InodesUseInPercentMonitorDescriptor extends AbstractAsyncNodeMonitorDescriptor<String> {

		@Override
		public String getDisplayName() {
			return Messages.InodesMonitor_UseInPercent();
		}

		@Override
		protected Callable<String, IOException> createCallable(Computer c) {
			return new GetInodesUseInPercent();
		}

		// Only augmenting visibility...
		@Override
		public boolean markOffline(Computer c, OfflineCause oc) {
			return super.markOffline(c, oc);
		}

		@Override
		public boolean markOnline(Computer c) {
			return super.markOnline(c);
		}
	};

	private static class GetInodesUseInPercent extends MasterToSlaveCallable<String, IOException> {
		private static final long serialVersionUID = 1L;
		@Override
		public String call() {
			return new InodesUsageGetter().getUsedInodesPercentage();
		}
	}
}
