/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts,
 * Yahoo!, Inc.
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
package hudson.model.view.operations;

import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Items;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.model.operations.Operation;
import jenkins.model.Jenkins;
import jenkins.model.item_category.Categories;
import jenkins.model.item_category.Category;
import jenkins.model.item_category.ItemCategory;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by haswell on 8/7/17.
 */
public class DoItemCategories implements Operation<Categories, View> {


    private final View view;




    public DoItemCategories(View view) {
        this.view = view;
    }


    @Override
    public View getHost() {
        return view;
    }

    @Override
    public Categories invoke(
            StaplerRequest request,
            StaplerResponse response,
            Object... args
    ) {
        final String iconStyle = argument(0, args);

        setHeaders(response);



        Categories categories = new Categories();
        int order = 0;
        JellyContext ctx;

        if (StringUtils.isNotBlank(iconStyle)) {
            ctx = new JellyContext();
            ctx.setVariable("resURL", request.getContextPath() + Jenkins.RESOURCE_PATH);
        } else {
            ctx = null;
        }
        for (TopLevelItemDescriptor descriptor : listDescriptors()) {
            ItemCategory ic = ItemCategory.getCategory(descriptor);
            Map<String, Serializable> metadata = new HashMap<>();

            // Information about Item.
            order = setMetadata(order, descriptor, metadata);
            String iconClassName = descriptor.getIconClassName();
            setIcon(iconStyle, ctx, metadata, iconClassName);

            Category category = categories.getItem(ic.getId());
            if (category != null) {
                category.getItems().add(metadata);
            } else {
                List<Map<String, Serializable>> temp = new ArrayList<>();
                temp.add(metadata);
                category = new Category(ic.getId(), ic.getDisplayName(), ic.getDescription(), ic.getOrder(), ic.getMinToShow(), temp);
                categories.getItems().add(category);
            }
        }
        return categories;
    }


    List<TopLevelItemDescriptor> listDescriptors() {
        return DescriptorVisibilityFilter.apply(
                view.getOwner().getItemGroup(),
                Items.all(Jenkins.getAuthentication(),
                        view.getOwner().getItemGroup()));
    }

    private void setIcon(String iconStyle, JellyContext ctx, Map<String, Serializable> metadata, String iconClassName) {
        if (StringUtils.isNotBlank(iconClassName)) {
            metadata.put("iconClassName", iconClassName);
            if (ctx != null) {
                Icon icon = IconSet.icons
                        .getIconByClassSpec(StringUtils.join(new String[]{iconClassName, iconStyle}, " "));
                if (icon != null) {
                    metadata.put("iconQualifiedUrl", icon.getQualifiedUrl(ctx));
                }
            }
        }
    }

    private int setMetadata(int order, TopLevelItemDescriptor descriptor, Map<String, Serializable> metadata) {
        metadata.put("class", descriptor.getId());
        metadata.put("order", ++order);
        metadata.put("displayName", descriptor.getDisplayName());
        metadata.put("description", descriptor.getDescription());
        metadata.put("iconFilePathPattern", descriptor.getIconFilePathPattern());
        return order;
    }

    private void setHeaders(StaplerResponse response) {
        Headers.NO_CACHE.decorateResponse(response);
    }
}
