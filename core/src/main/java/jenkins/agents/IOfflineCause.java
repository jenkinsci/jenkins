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
package jenkins.agents;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.util.Date;
import java.util.Objects;
import jenkins.model.IComputer;

/**
 * Represents a cause that puts a {@linkplain IComputer#isOffline() computer offline}.
 * @since 2.483
 */
public interface IOfflineCause {
    /**
     * @return The icon to use for the computer that has this offline cause. It will be displayed in the build executor status widget, as well as in nodes list screen.
     */
    @NonNull
    default String getComputerIcon() {
        return "symbol-computer-offline";
    }

    /**
     * @return The alt text for the icon returned by {@link #getComputerIcon()}.
     */
    @NonNull
    default String getComputerIconAltText() {
        return "[offline]";
    }

    /**
     * @return The icon to render this offline cause. It will be displayed in the build executor status widget.
     */
    @NonNull
    default String getIcon() {
        return "symbol-error";
    }

    /**
     * @return The reason why this offline cause exists.
     * <p>
     * For implementers: this can use HTML formatting, so make sure to only include trusted content.
     */
    @NonNull
    default String getReason() {
        // fetch the localized string for "Disconnected By"
        String gsub_base = hudson.slaves.Messages.SlaveComputer_DisconnectedBy("", "");
        // regex to remove commented reason base string
        String gsub1 = "^" + gsub_base + "[\\w\\W]* \\: ";
        // regex to remove non-commented reason base string
        String gsub2 = "^" + gsub_base + "[\\w\\W]*";
        return Objects.requireNonNull(Util.escape(toString().replaceAll(gsub1, "").replaceAll(gsub2, "")));
    }

    /**
     * @return A short message (one word) that summarizes the offline cause.
     *
     * <p>
     * For implementers: this can use HTML formatting, so make sure to only include trusted content.
     */
    @NonNull
    default String getMessage() {
        return Messages.IOfflineCause_offline();
    }

    /**
     * @return the CSS class name that should be used to render the status.
     */
    @SuppressWarnings("unused") // jelly
    default String getStatusClass() {
        return "warning";
    }

    /**
     * Timestamp in which the event happened.
     */
    long getTimestamp();

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     */
    @NonNull
    default Date getTime() {
        return new Date(getTimestamp());
    }
}
