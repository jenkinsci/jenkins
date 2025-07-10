/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.markup.MarkupFormatter;
import hudson.model.BallColor;
import hudson.model.BuildBadgeAction;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.widgets.BuildHistoryWidget;
import java.util.Calendar;
import java.util.List;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A {@link Run}-like object as it might be displayed by {@link BuildHistoryWidget}.
 *
 * @since 2.477
 */
@Restricted(Beta.class)
public interface HistoricalBuild extends AddressableModelObject {

    /**
     * @return A build number
     */
    int getNumber();

    /**
     * Returns a human-readable description which is used on the main build page.
     * <p>
     * It can also be quite long, and it may use markup in a format defined by a {@link hudson.markup.MarkupFormatter}.
     * {@link #getTruncatedDescription()} may be used to retrieve a size-limited description,
     * but it implies some limitations.
     * @return the build description.
     */
    @CheckForNull
    String getDescription();

    /**
     * @return a human-readable full display name of this build.
     */
    @NonNull
    String getFullDisplayName();

    /**
     * Get the {@link Queue.Item#getId()} of the original queue item from where this {@link HistoricalBuild} instance
     * originated.
     * @return The queue item ID.
     */
    long getQueueId();

    /**
     * Returns the build result.
     *
     * <p>
     * When a build is {@link #isBuilding() in progress}, this method
     * returns an intermediate result.
     * @return The status of the build, if it has completed or some build step has set a status; may be null if the build is ongoing.
     */
    @CheckForNull
    Result getResult();

    /** @see ParametersAction#getParameters */
    @NonNull
    List<ParameterValue> getParameterValues();

    /**
     * Returns true if the build is not completed yet.
     * This includes "not started yet" state.
     */
    boolean isBuilding();

    /**
     * Gets the icon color for display.
     */
    @NonNull
    BallColor getIconColor();

    @NonNull
    default String getBuildStatusIconClassName() {
        return getIconColor().getIconClassName();
    }

    @NonNull
    default String getBuildStatusUrl() {
        return getIconColor().getImage();
    }

    /**
     * When the build is scheduled.
     */
    @NonNull
    Calendar getTimestamp();

    /**
     * Gets the string that says how long the build took to run.
     */
    @NonNull
    String getDurationString();

    /**
     * Gets the list of {@link BuildBadgeAction}s applicable to this instance.
     */
    @NonNull
    List<BuildBadgeAction> getBadgeActions();

    /**
     * Returns the length-limited description.
     * The method tries to take HTML tags within the description into account, but it is a best-effort attempt.
     * Also, the method will likely not work properly if a non-HTML {@link MarkupFormatter} is used.
     * @return The length-limited description.
     */
    @CheckForNull
    default String getTruncatedDescription() {
        String description = getDescription();
        /*
         * Target size limit for truncated {@link #description}s in the Build History Widget.
         * This is applied to the raw, unformatted description. Especially complex formatting
         * like hyperlinks can result in much less text being shown than this might imply.
         * Negative values will disable truncation, {@code 0} will enforce empty strings.
         */
        int truncatedDescriptionLimit = SystemProperties.getInteger("historyWidget.descriptionLimit", 100);
        if (truncatedDescriptionLimit < 0) { // disabled
            return description;
        }
        if (truncatedDescriptionLimit == 0) { // Someone wants to suppress descriptions, why not?
            return "";
        }

        if (description == null || description.length() < truncatedDescriptionLimit) {
            return description;
        }

        final String ending = "...";
        final int sz = description.length(), maxTruncLength = truncatedDescriptionLimit - ending.length();

        boolean inTag = false;
        int displayChars = 0;
        int lastTruncatablePoint = -1;

        for (int i = 0; i < sz; i++) {
            char ch = description.charAt(i);
            if (ch == '<') {
                inTag = true;
            } else if (ch == '>') {
                inTag = false;
                if (displayChars <= maxTruncLength) {
                    lastTruncatablePoint = i + 1;
                }
            }
            if (!inTag) {
                displayChars++;
                if (displayChars <= maxTruncLength && ch == ' ') {
                    lastTruncatablePoint = i;
                }
            }
        }

        String truncDesc = description;

        // Could not find a preferred truncatable index, force a trunc at maxTruncLength
        if (lastTruncatablePoint == -1)
            lastTruncatablePoint = maxTruncLength;

        if (displayChars >= truncatedDescriptionLimit) {
            truncDesc = truncDesc.substring(0, lastTruncatablePoint) + ending;
        }

        return truncDesc;

    }

}
