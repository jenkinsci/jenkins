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
package hudson.tasks.Ant;
f=namespace(lib.FormTagLib)

if (descriptor.installations.length != 0) {
    f.entry(title:_("Ant Version")) {
        select(class:"setting-input",name:"ant.antName") {
            option(value:"(Default)", _("Default"))
            descriptor.installations.each {
                f.option(selected:it.name==instance?.ant?.name, value:it.name, it.name)
            }
        }
    }
}

f.entry(title:_("Targets"),help:"/help/ant/ant-targets.html") {
    f.expandableTextbox(name:"ant.targets",value:instance?.targets)
}

f.advanced {
    f.entry(title:_("Build File"),help:"/help/ant/ant-buildfile.html") {
        f.expandableTextbox(name:"ant.buildFile",value:instance?.buildFile)
    }
    f.entry(title:_("Properties"),help:"/help/ant/ant-properties.html") {
        f.expandableTextbox(name:"ant.properties",value:instance?.properties)
    }
    f.entry(title:_("Java Options"),help:"/help/ant/ant-opts.html") {
        f.expandableTextbox(name:"ant.antOpts",value:instance?.antOpts)
    }
}
