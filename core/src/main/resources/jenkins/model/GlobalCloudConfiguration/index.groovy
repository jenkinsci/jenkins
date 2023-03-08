package jenkins.model.GlobalCloudConfiguration

import hudson.slaves.Cloud
import jenkins.model.Jenkins


def f = namespace(lib.FormTagLib)
def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

st.redirect(url: rootURL + "/manage/cloud/")
