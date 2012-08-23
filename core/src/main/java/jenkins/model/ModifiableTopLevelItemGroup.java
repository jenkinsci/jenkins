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
 */
public interface ModifiableTopLevelItemGroup extends ModifiableItemGroup<TopLevelItem> {

    <T extends TopLevelItem> T copy(T src, String name) throws IOException;

    TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException;

    TopLevelItem createProject(TopLevelItemDescriptor type, String name) throws IOException;

    TopLevelItem createProject(TopLevelItemDescriptor type, String name, boolean notify) throws IOException;
}
