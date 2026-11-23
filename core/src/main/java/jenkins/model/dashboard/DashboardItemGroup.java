/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import hudson.model.ItemGroup;
import hudson.model.ViewGroup;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Implemented by an {@link ItemGroup}, such as a folder, whose page shows its own {@link Dashboard} to users who
 * enable the experimental dashboard, like the root URL does. The dashboard lists the items of the group, and only
 * the builds of jobs inside it.
 *
 * <p>The group's page must be served by its {@linkplain ViewGroup#getPrimaryView() primary view}, typically through
 * {@link org.kohsuke.stapler.StaplerFallback#getStaplerFallback()}. The view's page shows the dashboard when the
 * request is for the group's own URL, so the group's views stay reachable at their own URLs. To make the tabs
 * reachable under the group's URL, the group consults {@link Dashboard#findTab(ItemGroup, String)} after its own
 * actions:
 * <pre>{@code
 * public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
 *     Object result = super.getDynamic(token, req, rsp);
 *     return result != null ? result : Dashboard.findTab(this, token);
 * }
 * }</pre>
 *
 * @since TODO
 */
@Restricted(Beta.class)
public interface DashboardItemGroup extends ViewGroup {}
