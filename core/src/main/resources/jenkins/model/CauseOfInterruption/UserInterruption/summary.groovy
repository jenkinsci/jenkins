package jenkins.model.CauseOfInterruption.UserInterruption

// by default we just print the short description.
def user = my.userOrNull
if (user != null) {
  raw(_("blurb", user.fullName, rootURL+'/'+user.url))
} else {
  raw(_("userNotFound", my.userId))
}
