/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.model.ModifiableItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link hudson.model.ModifiableItemGroup} to manage {@link hudson.model.TopLevelItem},
 * including copying, creating from descriptor and from XML.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 1.480
 */
public interface ModifiableTopLevelItemGroup extends ModifiableItemGroup<TopLevelItem> {

    /**
     * Copys a job.
     *
     * @param src
     *      A {@link TopLevelItem} to be copied.
     * @param name
     *      Name of the newly created project.
     * @return
     *      Newly created {@link TopLevelItem}.
     */
    <T extends TopLevelItem> T copy(T src, String name) throws IOException;

    /**
     * /**
     * Creates a new job from its configuration XML. The type of the job created will be determined by
     * what's in this XML.
     * @param name
     *      Name of the newly created project.
     * @param xml
     *      Item configuration as xml
     * @return
     *      Newly created {@link TopLevelItem}.
     */
    TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException;

    /**
     * Creates a new job.
     * @param type Descriptor for job type
     * @param name Name for job
     * @param notify Whether to fire onCreated method for all ItemListeners
     * @throws IllegalArgumentException
     *      if a project of the give name already exists.
     */
    TopLevelItem createProject(TopLevelItemDescriptor type, String name, boolean notify) throws IOException;
}
