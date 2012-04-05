package jenkins.plugins.ui_samples.CopyButton;

import lib.JenkinsTagLib
import lib.LayoutTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)
l=namespace(LayoutTagLib.class)

namespace("/lib/samples").sample(title:_("Copy Button")) {
    raw(_("blurb"))

    div(style:"margin:2em") {
        text("Copy this text! ")
        l.copyButton(message:"text copied",text:"here comes ABC",container:"DIV")
    }

    raw(_("aboutContainerElement"))
}
