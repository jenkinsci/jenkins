/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.matrix;

import hudson.Extension;
import hudson.Util;
import jenkins.model.Jenkins;

import java.util.LinkedList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link Axis} that selects label expressions.
 *
 * @since 1.403
 */
public class LabelExpAxis extends Axis {
	
    @DataBoundConstructor
    public LabelExpAxis(String name, String values) {
        super(name, getExprValues(values));
    }
	
    public LabelExpAxis(String name, List<String> values) {
        super(name, values);
    }
    
    @Override
    public boolean isSystem() {
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public String getValuesString(){
    	StringBuffer sb = new StringBuffer();
    	for(String item: this.getValues()){
    		sb.append(item);
    		sb.append("\n");
    	}
    	return sb.toString();
    }
    
    @Extension
    public static class DescriptorImpl extends AxisDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.LabelExpAxis_DisplayName();
        }

        /**
         * If there's no distributed build set up, it's pointless to provide this axis.
         */
        @Override
        public boolean isInstantiable() {
            Jenkins h = Jenkins.getInstance();
            return !h.getNodes().isEmpty() || !h.clouds.isEmpty();
        }
    }
    
    public static List<String> getExprValues(String valuesString){
		List<String> expressions = new LinkedList<String>();
		String[] exprs = valuesString.split("\n");
		for(String expr: exprs){
    		expressions.add(Util.fixEmptyAndTrim(expr));
    	}
		return expressions;
	}

}

