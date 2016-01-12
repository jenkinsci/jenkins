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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

class DfRunner {
	private static final Logger LOGGER = Logger.getLogger(DfRunner.class.getName());

	private static Map<String, DfCommand> IMPLEMENTATIONS = new LinkedHashMap<String, DfCommand>();

	static {
		IMPLEMENTATIONS.put("windows", new WindowsDfCommand());
		IMPLEMENTATIONS.put("linux", new LinuxDfCommand());
		IMPLEMENTATIONS.put("mac os", new MacOsDfCommand());
		IMPLEMENTATIONS.put("freebsd", new MacOsDfCommand()); // Same as Mac
		IMPLEMENTATIONS.put("aix", new AixDfCommand());
	}

	public String getUsedInodesPercentage() {
		return findImplementation().get();
	}

	private DfCommand findImplementation() {
		String osName = System.getProperty("os.name");
		for (String key : IMPLEMENTATIONS.keySet()) {
			if (osName.toLowerCase().startsWith(key)) {
				LOGGER.info("DfRunner implementation key selected: " + key);
				return IMPLEMENTATIONS.get(key);
			}
		}
		return new DefaultDfCommand();
	}

	private static abstract class DfCommand {
		private String command;
		public final int line, column;

		DfCommand(String command, int line, int column) {
			this.command = command;
			this.line = line;
			this.column = column;
		}

		public String get() {
			try {
				LOGGER.fine("Inodes monitoring: running '" + command + "' command in " + System.getProperty("user.dir"));
				Process process = Runtime.getRuntime().exec(command);
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				for (int i = 1; i < line; ++i) {
					bufferedReader.readLine(); // Evacuating first lines (header...)
				}
				String values = bufferedReader.readLine();

				LOGGER.finest("df values output: " + values);
				String[] split = values.split(" +");
				return split[column - 1];
			}
			catch (IOException e) {
				LOGGER.severe("Error while running '" + command + "'");
				return Messages.InodesMonitor_NotApplicable_OnError();
			}
		}
	}

	private static class WindowsDfCommand extends DfCommand {
		WindowsDfCommand() {
			super(null, -1, -1);
		}

		@Override
		public String get() {
			return Messages.InodesMonitor_NotApplicable();
		}
	}

	private static class LinuxDfCommand extends DfCommand {
		LinuxDfCommand() {
			// The -P can help *not* output the values on two lines when the FS has a long name
			// But beware the other platform where -P with -i will either
			// Disable -i (Mac OS) or just fail (AIX)
			super("df -P -i .", 2, 5);
		}
	}

	private static class AixDfCommand extends DfCommand {
		AixDfCommand() {
			super("df -i .", 2, 6);
		}
	}

	private static class MacOsDfCommand extends DfCommand {
		MacOsDfCommand() {
			super("df -i .", 2, 8);
		}
	}

	/**
	 * Tries to run df anyway. Fallback. Will return N/A anyway if an error occurs. Or should we just return N/A directly?
	 */
	private static class DefaultDfCommand extends DfCommand {
		DefaultDfCommand() {
			super("df -i .", 2, 5);
		}
	}
}
