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

package hudson.cli.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import hudson.cli.CLICommand;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.jvnet.hudson.annotation_indexer.Indexed;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Annotates methods on model objects to expose them as CLI commands.
 *
 * <p>
 * You need to have {@code Messages.properties} in the same package with the
 * {@code CLI.<i>command-name</i>.shortDescription} key to describe the command.
 * This is used for the same purpose as {@link CLICommand#getShortDescription()}.
 *
 * <p>
 * If you put a {@link CLIMethod} on an instance method (as opposed to a static method),
 * you need a corresponding {@linkplain CLIResolver CLI resolver method}.
 *
 * <p>
 * A CLI method can have its parameters annotated with {@link Option} and {@link Argument},
 * to receive parameter/argument injections.
 *
 * <p>
 * A CLI method needs to be public.
 *
 * @author Kohsuke Kawaguchi
 * @see CLICommand
 * @since 1.321
 */
@Indexed
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface CLIMethod {
    /**
     * CLI command name. Used as {@link CLICommand#getName()}
     */
    String name();

    /**
     *
     */
    boolean usesChannel() default false;
}
