/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

/**
 * Logic for Hudson startup.
 *
 * <p>
 * Hudson's start up is based on the same idea as the modern Unix init mechanism like initng/upstart/SMF.
 * It first builds a set of {@link org.jvnet.hudson.reactor.Task}s that are units of the initialization work, and have them declare
 * dependencies among themselves. For example, jobs are only loaded after all the plugins are initialized,
 * and restoring the build queue requires all the jobs to be loaded.
 *
 * <p>
 * Such micro-scopic dependencies are organized into a bigger directed acyclic graph, which is then executed
 * via <tt>Session</tt>. During execution of the reactor, additional tasks can be discovred and added to
 * the DAG. We use this additional indirection to:
 *
 * <ol>
 * <li>Perform initialization in parallel where possible.
 * <li>Provide progress report on where we are in the initialization.
 * <li>Collect status of the initialization and their failures.
 * </ol>
 */
package hudson.init;

