/*
 * The MIT License
 *
 * Copyright (c) 2025, Ahmed Anwar
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

package jenkins.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Option;

/**
 * CLI command to list Jenkins nodes with various filtering options such as status, labels, and executors.
 * Supports output in plain text or JSON format, with an optional verbose mode for detailed information.
 */
@Restricted(NoExternalUse.class)
@Extension
public class ListNodesCommand extends CLICommand {
    private static final Logger LOGGER = Logger.getLogger(ListNodesCommand.class.getName());

    private static final String NL = System.lineSeparator(); // Newline character for consistent output formatting

    /** Maximum length for labels input to prevent excessive input size. */
    private static final int MAX_LABELS_LENGTH = 1000;

    private record NodeInfo(
        String displayName,
        boolean isOnline,
        boolean isTemporarilyOffline,
        String offlineCauseReason,
        Set<String> labels,
        int numExecutors,
        String osDescription,
        String architecture,
        String javaVersion,
        String connectedSince,
        String clockDifference
    ) {}

    /**
     * Node status filter options.
     */
    enum Status {
        ONLINE, OFFLINE, TEMP_OFFLINE, ALL
    }

    /**
     * Output format options for node listing.
     */
    enum OutputFormat {
        JSON, PLAIN
    }

    // Command-line options

    /**
     * Filter nodes by status: ONLINE, OFFLINE (all offline nodes, including temporarily offline), TEMP_OFFLINE (temporarily offline nodes only), or ALL (default).
     */
    @Option(name = "-status", usage = "Filter nodes by status: ONLINE, OFFLINE (all offline nodes, including temporarily offline), TEMP_OFFLINE (temporarily offline nodes only), or ALL")
    private Status status = Status.ALL;

    /**
     * Filter nodes by a label expression (e.g., 'label1 && label2 || !label3'). If not specified, all nodes are included.
     */
    @Option(name = "-labels", usage = "Filter nodes by a label expression (e.g., 'label1 && label2 || !label3')", metaVar = "label1 && label2 || !label3")
    private String labels = null;

    /**
     * Limit the number of nodes returned (positive integer, default: no limit).
     */
    @Option(name = "-limit", usage = "Limit the number of nodes returned (positive integer, default: no limit)", metaVar = "N")
    private int limit = Integer.MAX_VALUE; // Default to no limit

    /**
     * Minimum number of executors for nodes to be included in the results.
     */
    @Option(name = "-min-executors", usage = "Filter nodes by minimum number of executors", metaVar = "N")
    private int minExecutors = 0;

    /**
     * Exclude the built-in master node (i.e., Jenkins controller) from the results.
     */
    @Option(name = "-exclude-controller", usage = "Exclude the built-in master node (i.e., Jenkins controller) from the results")
    private boolean excludeController = false;

    /**
     * Output format for the node list: JSON or PLAIN text.
     */
    @Option(name = "-format", usage = "Output format: JSON, PLAIN")
    private OutputFormat format = OutputFormat.PLAIN;

    /**
     * Include detailed information for each node, such as OS, architecture, Java version, clock difference, and connected since timestamp.
     * Requires {@link Computer#EXTENDED_READ} permission.
     */
    @Option(name = "-verbose", usage = "Include detailed information for each node (e.g., OS, architecture, Java version, clock difference, connected since timestamp). Requires 'Extended Read' permission on the node(s).")
    private boolean verbose = false;

    /**
     * Only display node names, overriding other output formats and the verbose option.
     * If this is set, no other information will be displayed.
     */
    @Option(name = "-name-only", usage = "Only display node names (overrides other output formats and verbose option)")
    private boolean nameOnly = false;

    /**
     * Count only the number of matching nodes, overriding other output formats including name-only option.
     * If this is set, no node details will be displayed, only the count.
     */
    @Option(name = "-count-only", usage = "Only print the number of matching nodes (overrides other output formats and name-only option)")
    private boolean countOnly = false;

    /**
     * Label expression parsed from the command-line input.
     * This is used to filter nodes based on their labels.
     */
    private Label labelExpression;

    @Override
    public String getShortDescription() {
        return Messages.ListNodesCommand_ShortDescription();
    }

    /**
     * Executes the list-nodes command, filtering and formatting node information.
     *
     * @return 0 on success, 1 on invalid input or other errors.
     * @throws Exception If an unexpected error occurs during execution.
     */
    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();

        jenkins.checkPermission(Jenkins.READ);

        // Validate inputs before processing
        if (!validateInputs()) {
            return 1;
        }

        Computer[] computers = jenkins.getComputers();
        if (computers == null) {
            LOGGER.severe("Failed to retrieve nodes: Jenkins.getComputers() returned null, possible initialization failure");
            stderr.println("Error: No nodes found. Please check if Jenkins is properly initialized.");

            return 1;
        }

        List<NodeInfo> nodesList = Arrays.stream(computers)
        .filter(this::excludeControllerIfNeeded) // Exclude controller if needed
        .filter(this::matchesStatus)
        .filter(this::matchesLabels)
        .filter(this::matchesExecutorCount)
        .limit(Math.max(0, limit)) // Apply limit, ensuring non-negative
        .map(this::mapComputerToNodeInfo)
        .toList();

        if (countOnly) {
            stdout.println(nodesList.size());
            return 0;
        }

        if (nodesList.isEmpty()) {
            stdout.println("No nodes found matching the specified criteria.");
            return 0;
        }

        if (nameOnly) {
            printNodeNamesOnly(nodesList);
            return 0;
        }

        switch (format) {
            case JSON:
                printNodesAsJson(nodesList);
                break;
            case PLAIN:
            default:
                printNodesAsPlainText(nodesList);
                break;
        }

        return 0;
    }

    /**
     * Validates command-line input options to ensure correctness before execution.
     * @return true if all inputs are valid; false otherwise.
     */
    private boolean validateInputs() {
        // Validate minExecutors
        if (minExecutors < 0) {
            stderr.println("Invalid input: The minimum number of executors (-min-executors) must be a non-negative value.");

            return false;
        }

        // Validate limit
        if (limit <= 0) {
            stderr.println("Invalid input: -limit must be a positive integer.");

            return false;
        }

        // Validate labels
        if (labels != null && !labels.trim().isEmpty()) {
            // Check length to prevent excessive input
            if (labels.length() > MAX_LABELS_LENGTH) {
                stderr.println("Invalid input: -labels input exceeds maximum length of " + MAX_LABELS_LENGTH + " characters.");

                return false;
            }

            if (!parseLabelExpression()) {
                return false; // label parsing fails, return false
            }
        }

        return true;
    }

    /**
     * Parses the label expression from the command-line input.
     * If the expression is invalid, it prints an error message and returns false.
     *
     * @return true if the label expression is valid; false otherwise.
     */
    private boolean parseLabelExpression() {
        try {
            labelExpression = Label.parseExpression(labels.trim());

            if (labelExpression == null) {
                stderr.println("Invalid input: The label expression '" + labels + "' is not valid.");

                return false;
            }
        } catch (IllegalArgumentException e) {
            stderr.println("Invalid input: The label expression '" + labels + "' is not valid. " + e.getMessage());

            return false;
        }

        return true;
    }

    /**
     * Excludes the Jenkins controller node if the excludeController option is set.
     * The controller is excluded only if it is not null and is an instance of Jenkins.
     *
     * @param computer The computer to check.
     * @return true if the computer should be included, false if it should be excluded.
     */
    private boolean excludeControllerIfNeeded(Computer computer) {
        if (!excludeController) {
            return true;
        }

        Node node = computer.getNode();
        if (node == null) {
            return true;
        }

        return !(node instanceof Jenkins);
    }

    /**
     * Checks if the computer matches the specified status filter.
     * If the status is ALL, it returns true for all computers.
     *
     * @param computer The computer to check.
     * @return true if the computer matches the status filter, false otherwise.
     */
    private boolean matchesStatus(Computer computer) {
        switch (status) {
            case TEMP_OFFLINE:
                return computer.isTemporarilyOffline();
            case ONLINE:
                return computer.isOnline();
            case OFFLINE:
                return !computer.isOnline();
            case ALL:
            default:
                return true;
        }
    }

    /**
     * Checks if the computer matches the specified label expression.
     * If no labels are specified, it returns true (no label filter applied).
     *
     * @param computer The computer to check.
     * @return true if the computer matches the label expression, false otherwise.
     */
    private boolean matchesLabels(Computer computer) {
        if (labels == null || labels.trim().isEmpty()) {
            return true;
        }

        Node node = computer.getNode();

        return node != null && labelExpression.matches(node);
    }

    /**
     * Checks if the computer has at least the specified minimum number of executors.
     *
     * @param computer The computer to check.
     * @return true if the computer meets the minimum executor count, false otherwise.
     */
    private boolean matchesExecutorCount(Computer computer) {
        return computer.getNumExecutors() >= minExecutors;
    }

    /**
     * Maps a {@link Computer} object to a {@code NodeInfo} object, extracting relevant information.
     * Handles exceptions and provides default values for missing data or insufficient permissions.
     *
     * @param computer The Computer object to map.
     * @return A {@code NodeInfo} object containing the mapped information.
     */
    private NodeInfo mapComputerToNodeInfo(Computer computer) {
            String displayName = "";
            boolean isOnline = false;
            boolean isTemporarilyOffline = false;
            String offlineCauseReason = "N/A";
            int numExecutors = 0;
            Set<String> labels = Set.of();

            String osDescription = "N/A";
            String architecture = "N/A";
            String javaVersion = "N/A";
            String connectedSince = "N/A";
            String clockDifferenceStr = "N/A";

        try {
            displayName = computer.getDisplayName();
            isOnline = computer.isOnline();
            isTemporarilyOffline = computer.isTemporarilyOffline();

            if (!isOnline) {
                offlineCauseReason = computer.getOfflineCauseReason();
            }

            numExecutors = computer.getNumExecutors();

            labels = computer.getAssignedLabels().stream()
            .map(label -> label.getName())
            .collect(Collectors.toUnmodifiableSet());


            if (computer.hasPermission(Computer.EXTENDED_READ)) {
                if (verbose && isOnline) {
                    connectedSince = formatTimestamp(computer.getConnectTime());
                    clockDifferenceStr = getClockDifference(computer);

                    // Fetch system properties
                    try {
                        Map<Object, Object> sysProps = computer.getSystemProperties();

                        osDescription = getSystemProp(sysProps, "os.name");
                        architecture = getSystemProp(sysProps, "os.arch");
                        javaVersion = getSystemProp(sysProps, "java.version");

                    } catch (IOException | InterruptedException e) {
                        LOGGER.warning("Failed to fetch system properties for node '" + displayName + "': " + e.getMessage());
                        stderr.println("Failed to fetch system properties for node '" + displayName + "'.");
                    }
                }
            } else {
                LOGGER.info("Verbose details for node '" + displayName + "' skipped due to insufficient permissions.");

                osDescription = "Permission Denied";
                architecture = "Permission Denied";
                javaVersion = "Permission Denied";
                connectedSince = "Permission Denied";
                clockDifferenceStr = "Permission Denied";
            }
        } catch (Exception e) {
            LOGGER.warning("Error mapping computer '" + displayName + "': " + e.getMessage());
            stderr.println("Error: Failed to fetch node '" + displayName + "'.");
        }

        return new NodeInfo(displayName, isOnline, isTemporarilyOffline, offlineCauseReason, labels, numExecutors, osDescription, architecture, javaVersion, connectedSince, clockDifferenceStr);
    }

    /**
     * Retrieves a system property value from the provided map.
     * If the property is not found or verbose mode is disabled, returns "N/A".
     *
     * @param sysProps The map containing system properties.
     * @param key The key of the system property to retrieve.
     * @return The value of the system property or "N/A" if not found or verbose mode is off.
     */
    private String getSystemProp(Map<Object, Object> sysProps, String key) {
        if (!verbose || sysProps == null || sysProps.isEmpty()) {
            return "N/A";
        }

        Object value = sysProps.get(key);

        return value != null ? value.toString() : "N/A";
    }

    /**
     * Formats a timestamp in milliseconds to a human-readable UTC date-time string.
     * If the timestamp is less than or equal to zero, returns "N/A".
     *
     * @param millis The timestamp in milliseconds.
     * @return A formatted date-time string or "N/A" if the timestamp is invalid.
     */
    private String formatTimestamp(long millis) {
        if (millis <= 0) {
            return "N/A";
        }

        Instant instant = Instant.ofEpochMilli(millis);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
            .withZone(ZoneId.of("UTC"));

        return formatter.format(instant);
    }

    /**
     * Retrieves the clock difference for a given computer node.
     * If the node is offline or verbose mode is not enabled, returns "N/A".
     *
     * @param computer The computer to check.
     * @return The clock difference as a string, or "N/A" if not applicable.
     */
    private String getClockDifference(Computer computer) {
        if (!verbose || !computer.isOnline()) {
            return "N/A";
        }

        try {
            Node node = computer.getNode();

            if (node == null) {
                return "N/A";
            }

            return node.getClockDifference().toString();
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Prints only the names of the nodes, one per line.
     * This method is used when the -name-only option is specified.
     *
     * @param nodes The list of {@code NodeInfo} objects to print.
     */
    private void printNodeNamesOnly(List<NodeInfo> nodes) {
        for (NodeInfo node : nodes) {
            stdout.println(node.displayName());
        }
    }

    /**
     * Prints the list of nodes in JSON format.
     *
     * @param nodes The list of {@code NodeInfo} objects to print.
     */
    private void printNodesAsJson(List<NodeInfo> nodes) {
        // Convert nodes to JSON format and print
        JSONArray array = new JSONArray();

        for (NodeInfo node : nodes) {
            JSONObject json = new JSONObject();

            json.put("displayName", node.displayName());
            json.put("isOnline", node.isOnline());
            json.put("isTemporarilyOffline", node.isTemporarilyOffline());
            json.put("offlineCauseReason", node.offlineCauseReason());
            json.put("numExecutors", node.numExecutors());
            json.put("labels", node.labels());

            if (verbose) {
                json.put("osDescription", node.osDescription());
                json.put("architecture", node.architecture());
                json.put("javaVersion", node.javaVersion());
                json.put("connectedSince", node.connectedSince());
                json.put("clockDifference", node.clockDifference());
            }
            array.add(json);
        }

        String output = array.toString(2); // Pretty-print JSON with indent

        stdout.println(output);
    }

    /**
     * Prints the list of nodes in plain text format.
     * Each node's details are printed with appropriate formatting.
     *
     * @param nodes The list of {@code NodeInfo} objects to print.
     */
    private void printNodesAsPlainText(List<NodeInfo> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            NodeInfo node = nodes.get(i);
            StringBuilder sb = new StringBuilder();

            sb.append("Node: ").append(node.displayName()).append(NL);
            sb.append("  Online: ").append(node.isOnline() ? "Yes" : "No").append(NL);
            sb.append("  isTemporarilyOffline: ").append(node.isTemporarilyOffline() ? "Yes" : "No").append(NL);
            sb.append("  Offline Reason: ").append(node.offlineCauseReason()).append(NL);
            sb.append("  Executors: ").append(node.numExecutors()).append(NL);
            sb.append("  Labels:").append(NL);

            for (String label : node.labels()) {
                sb.append("    - ").append(label).append(NL);
            }

            if (verbose) {
                sb.append("  OS: ").append(node.osDescription()).append(NL);
                sb.append("  Architecture: ").append(node.architecture()).append(NL);
                sb.append("  Java Version: ").append(node.javaVersion()).append(NL);
                sb.append("  Connected Since: ").append(node.connectedSince()).append(NL);
                sb.append("  Clock Difference: ").append(node.clockDifference()).append(NL);

                if (node.osDescription().equals("Permission Denied")) {
                    sb.append(" Note: Verbose details unavailable due to insufficient permissions.").append(NL);
                }
            }

            if (i < nodes.size() - 1) {
                sb.append("---").append(NL);
            }

            stdout.print(sb); // single print per node
        }
    }
}
