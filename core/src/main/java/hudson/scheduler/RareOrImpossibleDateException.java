/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package hudson.scheduler;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Calendar;

/**
 * This exception is thrown when trying to determine the previous or next occurrence of a given date determines
 * that it's not happened, or going to happen, within some time period (e.g. within the next year).
 *
 * <p>This can typically have a few different reasons:</p>
 *
 * <ul>
 *   <li>The date is impossible. For example, June 31 does never happen, so {@code 0 0 31 6 *} will never happen</li>
 *   <li>The date happens only rarely
 *     <ul>
 *       <li>February 29 being the obvious one</li>
 *       <li>Cron tab patterns specifying all of month, day of month, and day of week can also occur so rarely to trigger this exception</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see CronTab#floor(Calendar)
 * @see CronTab#ceil(Calendar)
 * @since 2.49
 */
@Restricted(NoExternalUse.class)
public class RareOrImpossibleDateException extends RuntimeException {
}
