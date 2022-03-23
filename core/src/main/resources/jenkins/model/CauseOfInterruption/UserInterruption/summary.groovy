package jenkins.model.CauseOfInterruption.UserInterruption

// by default we just print the short description.
def user = my.userOrNull
if (user != null) {
  raw(gettext("blurb", user.fullName, rootURL+'/'+user.url))
} else {
  raw(gettext("userNotFound", my.userId))
}
