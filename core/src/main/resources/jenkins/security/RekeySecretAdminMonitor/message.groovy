/*
The MIT License

Copyright (c) 2013, CloudBees, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package jenkins.security.RekeySecretAdminMonitor;

def f = namespace(lib.FormTagLib)

if (!my.isDone()) {
    div(class:"error") {
        raw _("pleaseRekeyAsap",app.rootDir,my.url)
    }
}

if (my.isFixingActive()) {
    div(class:"info") {
        raw _("rekeyInProgress",my.url)
    }
} else if (my.logFile.exists()) {
    if (my.isDone()) {
        div(class:"info") {
            raw _("rekeySuccessful",my.url)
        }
    } else {
        div(class:"warning") {
            raw _("rekeyHadProblems",my.url)
        }
    }
}

form(method:"POST",action:"${my.url}/scan",style:"text-align:center; margin-top:0.5em;",name:"rekey") {
    f.submit(name:"background",value:_("Re-key in background now"))
    if (my.isScanOnBoot()) {
        input(type:"button",class:"yui-button",disabled:"true",
                value:_("Re-keying currently scheduled during the next startup"))
    } else {
        f.submit(name:"schedule",  value:_("Schedule a re-key during the next startup"))
    }
    f.submit(name:"dismiss",   value:_("Dismiss this message"))
}
