/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package jenkins.model.item_category;

import hudson.model.TopLevelItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Represents an {@link ItemCategory} and its {@link TopLevelItem}s.
 *
 * This class is not thread-safe.
 */
@ExportedBean
@Restricted(NoExternalUse.class)
public class Category implements Serializable {

    private String id;

    private String name;

    private String description;

    private int order;

    private int minToShow;

    private List<Map<String, Serializable>> items;

    public Category(String id, String name, String description, int order, int minToShow, List<Map<String, Serializable>> items) {
        this.id= id;
        this.name = name;
        this.description = description;
        this.order = order;
        this.minToShow = minToShow;
        this.items = items;
    }

    @Exported
    public String getId() {
        return id;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported
    public String getDescription() {
        return description;
    }

    @Exported
    public int getOrder() {
        return order;
    }

    @Exported
    public int getMinToShow() {
        return minToShow;
    }

    @Exported
    public List<Map<String, Serializable>> getItems() {
        return items;
    }

}
