/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.security.stapler;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CancelRequestHandlingException;
import org.kohsuke.stapler.DispatchValidator;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;

/**
 * Validates views dispatched by Stapler. This validation consists of two phases:
 * <ul>
 *     <li>Before views are loaded, the model class is checked for {@link StaplerViews}/{@link StaplerFragments} along
 *     with whitelist entries specified by the default views whitelist and the optionally defined whitelist specified
 *     by the system property {@code jenkins.security.stapler.StaplerDispatchValidator.whitelist}. Then,
 *     the model class's superclass and interfaces are recursively inspected adding views and fragments that do not
 *     conflict with the views and fragments already declared. This effectively allows model classes to override
 *     parent classes.</li>
 *     <li>Before views write any response output, this validator is checked to see if the view has declared itself
 *     dispatchable using the {@code l:view} Jelly tag. As this validation comes later, annotations will take
 *     precedence over the use or lack of a layout tag.</li>
 * </ul>
 * <p>Validation can be disabled by setting the system property
 * {@code jenkins.security.stapler.StaplerDispatchValidator.disabled=true} or setting {@link #DISABLED} to
 * {@code true} in the script console.</p>
 *
 * @since 2.176.2 / 2.186
 */
@Restricted(NoExternalUse.class)
public class StaplerDispatchValidator implements DispatchValidator {

    private static final Logger LOGGER = Logger.getLogger(StaplerDispatchValidator.class.getName());
    private static final String ATTRIBUTE_NAME = StaplerDispatchValidator.class.getName() + ".status";
    private static final String ESCAPE_HATCH = StaplerDispatchValidator.class.getName() + ".disabled";
    /**
     * Escape hatch to disable dispatch validation.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* script-console editable */ boolean DISABLED = SystemProperties.getBoolean(ESCAPE_HATCH);

    @NonNull
    private static YesNoMaybe setStatus(@NonNull StaplerRequest2 req, @NonNull YesNoMaybe status) {
        switch (status) {
            case YES:
            case NO:
                LOGGER.fine(() -> "Request dispatch set status to " + status.toBool() + " for URL " + req.getPathInfo());
                req.setAttribute(ATTRIBUTE_NAME, status.toBool());
                return status;
            case MAYBE:
                return status;
            default:
                throw new IllegalStateException("Unexpected value: " + status);
        }
    }

    @NonNull
    private static YesNoMaybe computeStatusIfNull(@NonNull StaplerRequest2 req, @NonNull Supplier<YesNoMaybe> statusIfNull) {
        Object requestStatus = req.getAttribute(ATTRIBUTE_NAME);
        if (requestStatus instanceof Boolean) {
            return (Boolean) requestStatus ? YesNoMaybe.YES : YesNoMaybe.NO;
        } else {
            return setStatus(req, statusIfNull.get());
        }
    }

    private final ValidatorCache cache;

    public StaplerDispatchValidator() {
        cache = new ValidatorCache();
        cache.load();
    }

    @Override
    public @CheckForNull Boolean isDispatchAllowed(@NonNull StaplerRequest2 req, @NonNull StaplerResponse2 rsp) {
        if (DISABLED) {
            return true;
        }
        YesNoMaybe status = computeStatusIfNull(req, () -> {
            if (rsp.getContentType() != null) {
                return YesNoMaybe.YES;
            }
            if (rsp.getStatus() >= 300) {
                return YesNoMaybe.YES;
            }
            return YesNoMaybe.MAYBE;
        });
        LOGGER.finer(() -> req.getRequestURI() + " -> " + status.toBool());
        return status.toBool();
    }

    @Override
    public @CheckForNull Boolean isDispatchAllowed(@NonNull StaplerRequest2 req, @NonNull StaplerResponse2 rsp, @NonNull String viewName, @CheckForNull Object node) {
        if (DISABLED) {
            return true;
        }
        YesNoMaybe status = computeStatusIfNull(req, () -> {
            if (viewName.equals("index")) {
                return YesNoMaybe.YES;
            }
            if (node == null) {
                return YesNoMaybe.MAYBE;
            }
            return cache.find(node.getClass()).isViewValid(viewName);
        });
        LOGGER.finer(() -> "<" + req.getRequestURI() + ", " + viewName + ", " + node + "> -> " + status.toBool());
        return status.toBool();
    }

    @Override
    public void allowDispatch(@NonNull StaplerRequest2 req, @NonNull StaplerResponse2 rsp) {
        if (DISABLED) {
            return;
        }
        setStatus(req, YesNoMaybe.YES);
    }

    @Override
    public void requireDispatchAllowed(@NonNull StaplerRequest2 req, @NonNull StaplerResponse2 rsp) throws CancelRequestHandlingException {
        if (DISABLED) {
            return;
        }
        Boolean status = isDispatchAllowed(req, rsp);
        if (status == null || !status) {
            LOGGER.fine(() -> "Cancelling dispatch for " + req.getRequestURI());
            throw new CancelRequestHandlingException();
        }
    }

    @VisibleForTesting
    static StaplerDispatchValidator getInstance(@NonNull ServletContext context) {
        return (StaplerDispatchValidator) WebApp.get(context).getDispatchValidator();
    }

    @VisibleForTesting
    void loadWhitelist(@NonNull InputStream in) throws IOException {
        cache.loadWhitelist(IOUtils.readLines(in, StandardCharsets.UTF_8));
    }

    private static class ValidatorCache {
        private final Map<String, Validator> validators = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        // provided as alternative to ConcurrentHashMap.computeIfAbsent which doesn't allow for recursion in the supplier
        // see https://stackoverflow.com/q/28840047
        private @NonNull Validator computeIfAbsent(@NonNull String className, @NonNull Function<String, Validator> constructor) {
            lock.readLock().lock();
            try {
                if (validators.containsKey(className)) {
                    // cached value
                    return validators.get(className);
                }
            } finally {
                lock.readLock().unlock();
            }
            lock.writeLock().lock();
            try {
                if (validators.containsKey(className)) {
                    // cached between readLock.unlock and writeLock.lock
                    return validators.get(className);
                }
                Validator value = constructor.apply(className);
                validators.put(className, value);
                return value;
            } finally {
                lock.writeLock().unlock();
            }
        }

        private @NonNull Validator find(@NonNull Class<?> clazz) {
            return computeIfAbsent(clazz.getName(), name -> create(clazz));
        }

        private @NonNull Validator find(@NonNull String className) {
            return computeIfAbsent(className, this::create);
        }

        private @NonNull Collection<Validator> findParents(@NonNull Class<?> clazz) {
            List<Validator> parents = new ArrayList<>();
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) {
                parents.add(find(superclass));
            }
            for (Class<?> iface : clazz.getInterfaces()) {
                parents.add(find(iface));
            }
            return parents;
        }

        private @NonNull Validator create(@NonNull Class<?> clazz) {
            Set<String> allowed = new HashSet<>();
            StaplerViews views = clazz.getDeclaredAnnotation(StaplerViews.class);
            if (views != null) {
                allowed.addAll(Arrays.asList(views.value()));
            }
            Set<String> denied = new HashSet<>();
            StaplerFragments fragments = clazz.getDeclaredAnnotation(StaplerFragments.class);
            if (fragments != null) {
                denied.addAll(Arrays.asList(fragments.value()));
            }
            return new Validator(() -> findParents(clazz), allowed, denied);
        }

        private @NonNull Validator create(@NonNull String className) {
            ClassLoader loader = Jenkins.get().pluginManager.uberClassLoader;
            return new Validator(() -> {
                try {
                    return findParents(loader.loadClass(className));
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, e, () -> "Could not load class " + className + " to validate views");
                    return Collections.emptySet();
                }
            });
        }

        private void load() {
            try {
                try (InputStream is = Validator.class.getResourceAsStream("default-views-whitelist.txt")) {
                    loadWhitelist(IOUtils.readLines(is, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load default views whitelist", e);
            }
            String whitelist = SystemProperties.getString(StaplerDispatchValidator.class.getName() + ".whitelist");
            Path configFile = whitelist != null ? Paths.get(whitelist) : Jenkins.get().getRootDir().toPath().resolve("stapler-views-whitelist.txt");
            if (Files.exists(configFile)) {
                try {
                    loadWhitelist(Files.readAllLines(configFile, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> "Could not load user defined whitelist from " + configFile);
                }
            }
        }

        private void loadWhitelist(@NonNull List<String> whitelistLines) {
            for (String line : whitelistLines) {
                if (line.matches("#.*|\\s*")) {
                    // commented line
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    // invalid input format
                    LOGGER.warning(() -> "Cannot update validator with malformed line: " + line);
                    continue;
                }
                Validator validator = find(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    String view = parts[i];
                    if (view.startsWith("!")) {
                        validator.denyView(view.substring(1));
                    } else {
                        validator.allowView(view);
                    }
                }
            }
        }

        private class Validator {
            // lazy load parents to avoid trying to load potentially unavailable classes
            private final Supplier<Collection<Validator>> parentsSupplier;
            private volatile Collection<Validator> parents;
            private final Set<String> allowed = ConcurrentHashMap.newKeySet();
            private final Set<String> denied = ConcurrentHashMap.newKeySet();

            private Validator(@NonNull Supplier<Collection<Validator>> parentsSupplier) {
                this.parentsSupplier = parentsSupplier;
            }

            private Validator(@NonNull Supplier<Collection<Validator>> parentsSupplier, @NonNull Collection<String> allowed, @NonNull Collection<String> denied) {
                this(parentsSupplier);
                this.allowed.addAll(allowed);
                this.denied.addAll(denied);
            }

            private @NonNull Collection<Validator> getParents() {
                if (parents == null) {
                    synchronized (this) {
                        if (parents == null) {
                            parents = parentsSupplier.get();
                        }
                    }
                }
                return parents;
            }

            @NonNull
            private YesNoMaybe isViewValid(@NonNull String viewName) {
                if (allowed.contains(viewName)) {
                    return YesNoMaybe.YES;
                }
                if (denied.contains(viewName)) {
                    return YesNoMaybe.NO;
                }
                for (Validator parent : getParents()) {
                    YesNoMaybe result = parent.isViewValid(viewName);
                    if (!result.equals(YesNoMaybe.MAYBE)) {
                        return result;
                    }
                }
                return YesNoMaybe.MAYBE;
            }

            private void allowView(@NonNull String viewName) {
                allowed.add(viewName);
            }

            private void denyView(@NonNull String viewName) {
                denied.add(viewName);
            }
        }
    }
}
