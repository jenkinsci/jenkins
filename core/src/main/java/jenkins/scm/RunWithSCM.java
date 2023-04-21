/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, inc.
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

package jenkins.scm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.util.AdaptedIterator;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.export.Exported;

/**
 * Allows a {@link Run} to provide {@link SCM}-related methods, such as providing changesets and culprits.
 *
 * @since 2.60
 */
public interface RunWithSCM<JobT extends Job<JobT, RunT>,
        RunT extends Run<JobT, RunT> & RunWithSCM<JobT, RunT>> {

    /**
     * Gets all {@link ChangeLogSet}s currently associated with this item.
     *
     * @return A possibly empty list of {@link ChangeLogSet}s.
     */
    @NonNull
    List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets();

    /**
     * Gets the ids for all {@link User}s included in {@link #getChangeSets()} for this item.
     *
     * @return A set of user IDs, or null if this was the first time the method was called or the build is still running
     * for a {@link RunWithSCM} instance with no culprits.
     */
    @CheckForNull
    Set<String> getCulpritIds();

    /**
     * Determines whether culprits should be recalcuated or the existing {@link #getCulpritIds()} should be used instead.
     *
     * @return True if culprits should be recalcuated, false otherwise.
     */
    boolean shouldCalculateCulprits();

    /**
     * List of users who committed a change since the last non-broken build till now.
     *
     * <p>
     * This list at least always include people who made changes in this build, but
     * if the previous build was a failure it also includes the culprit list from there.
     *
     * <p>
     * Missing {@link User}s will be created on-demand.
     *
     * @return
     *      can be empty but never null.
     */
    @Exported
    @NonNull default Set<User> getCulprits() {
        if (shouldCalculateCulprits()) {
            return calculateCulprits();
        }

        return new AbstractSet<>() {
            private Set<String> culpritIds = Set.copyOf(getCulpritIds());

            @Override
            public Iterator<User> iterator() {
                return new AdaptedIterator<>(culpritIds.iterator()) {
                    @Override
                    protected User adapt(String id) {
                        // TODO: Probably it should not auto-create users
                        return User.getById(id, true);
                    }
                };
            }

            @Override
            public int size() {
                return culpritIds.size();
            }
        };
    }

    /**
     * Method used for actually calculating the culprits from scratch. Called by {@link #getCulprits()} and
     * overrides of {@link #getCulprits()}. Does not persist culprits information.
     *
     * @return a non-null {@link Set} of {@link User}s associated with this item.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    default Set<User> calculateCulprits() {
        Set<User> r = new HashSet<>();
        RunT p = ((RunT) this).getPreviousCompletedBuild();
        if (p != null) {
            Result pr = p.getResult();
            if (pr != null && pr.isWorseThan(Result.SUCCESS)) {
                // we are still building, so this is just the current latest information,
                // but we seems to be failing so far, so inherit culprits from the previous build.
                r.addAll(p.getCulprits());
            }
        }
        for (ChangeLogSet<? extends ChangeLogSet.Entry> c : getChangeSets()) {
            for (ChangeLogSet.Entry e : c) {
                r.add(e.getAuthor());
            }
        }

        return r;
    }

    /**
     * Returns true if this user has made a commit to this build.
     */
    @SuppressWarnings("unchecked")
    default boolean hasParticipant(User user) {
        for (ChangeLogSet<? extends ChangeLogSet.Entry> c : getChangeSets()) {
            for (ChangeLogSet.Entry e : c) {
                try {
                    if (e.getAuthor() == user) {
                        return true;
                    }
                } catch (RuntimeException re) {
                    Logger LOGGER = Logger.getLogger(RunWithSCM.class.getName());
                    LOGGER.log(Level.INFO, "Failed to determine author of changelog " + e.getCommitId() + "for " + ((RunT) this).getParent().getDisplayName() + ", " + ((RunT) this).getDisplayName(), re);
                }
            }
        }
        return false;
    }
}
