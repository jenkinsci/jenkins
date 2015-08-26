# Overview
Jenkins used to maintain a forked version of HtmlUnit and use it via `JenkinsRule` (and `HudsonTestCase`). Moving
away from the forked version has allowed Jenkins GUI to start using more modern JavaScript libraries than were
possible through the forked version.

Moving away from the forked version also means that some tests in plugins may no longer compile due to the
fact that they were using some methods that were added to the fork, but not available in the mainstream HtmlUnit.
To help with that, the test harness now provides static utilities for all of this functionality.

# WebClientUtil
The `WebClientUtil` class provides static utilities for interacting asynchronously with HtmlUnit's `WebClient`,
supporting `waitForJSExec` calls to allow your test code wait for background (asynchronous) JavaScript code to complete
before the test continues. Calling these methods are not required in many cases as they are called for you from
other static utilities (e.g. `HtmlFormUtil.submit`), but sometimes it is required to call them directly.

Because HtmlUnit executes JavaScript asynchronously, it's usually not possible to block and catch exceptions. For
that reason, `WebClientUtil` provides the `addExceptionListener` utility as a way of registering an exception listener.
This typically needs to be used in conjunction with the `waitForJSExec` method e.g.

```
WebClient webClient = jenkinsRule.createWebClient();
WebClientUtil.ExceptionListener exceptionListener = WebClientUtil.addExceptionListener(webClient);

// Interact with Jenkins UI as normal in a test...

// Make sure all background JavaScript has completed so as expected exceptions have been thrown.
WebClientUtil.waitForJSExec(webClient);

// Now we can check for exceptions etc...
exceptionListener.assertHasException();
ScriptException e = exceptionListener.getScriptException();
Assert.assertTrue(e.getMessage().contains("simulated error"));
```

# HtmlElementUtil, HtmlFormUtil and DomNodeUtil 
These classes provide static utility methods to replace functionality that was added to the forked HtmlUnit e.g.
for triggering `<form>` submit, triggering a click event on an element etc.

Use your IDE to access these utility classes and the static methods available.