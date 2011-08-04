package jenkins.plugins.ui_samples.ProgressBar;

import lib.JenkinsTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)

namespace("/lib/samples").sample(title:_("Progress Bar")) {
    // in this sample, we add extra margin around them
    style(".progress-bar {margin:1em;}")
    p("This page shows you how to use the progress bar widget")

    p("The 'pos' parameter controls the degree of progress, 0-100")
    t.progressBar(pos:30)
    t.progressBar(pos:60)
    t.progressBar(pos:90)
    p("-1 will draw the progress bar in the 'indeterminate' state");
    t.progressBar(pos:-1)

    p("The 'red' parameter turns the progress bar to red. Used to indicate that it's overdue")
    t.progressBar(pos:99, red:true)
    t.progressBar(pos:-1, red:true)

    p("Tooltip can be added with the 'tooltip' parameter. It can contain arbitrary HTML. Hover your mouse below to see.")
    t.progressBar(pos:70, tooltip:"Allows <b>arbitrary</b> html")

    p("Hyperlink can be added with the 'href' pointer. Click the progress bar below")
    t.progressBar(pos:70, href:"http://jenkins-ci.org/")
}
