<!-- Comment:
!!! ⚠️ IMPORTANT ⚠️ !!!
Do not remove any of the sections below, even if they are not applicable to your change. The sections are used by the
changelog generator and other tools to extract information about the change. If a section is not applicable,
leave as is. Carefully read the instructions in each section and provide the necessary information.

Pull requests that do not follow the template might be closed without further review.
-->

<!-- Comment:
A great PR typically begins with the line below.
Replace <issue-number> with the issue number.
-->

Fixes #<issue-number>

<!-- Comment:
If the issue is not fully described in the issue tracker, add more information here (justification, pull request links, etc.).

 * We do not require an issue for minor improvements.
 * Major new features should have an issue created.
-->

### Testing done

<!-- Comment:
Provide a clear description of how this change was tested.
At minimum this should include proof that a computer has executed the changed lines.
Ideally this should include an automated test or an explanation as to why this change has no tests.
Note that automated test coverage is less than complete, so a successful PR build does not necessarily imply that a computer has executed the changed lines.
If automated test coverage does not exist for the lines you are changing, you must describe the scenario(s) in which you manually tested the change.
For frontend changes, include screenshots of the relevant page(s) before and after the change.
For refactoring and code cleanup changes, exercise the code before and after the change and verify the behavior remains the same.
-->

### Screenshots (UI changes only)

<!--
If your change involves a UI change, please include screenshots showing the before and after states.
-->

#### Before

#### After

### Proposed changelog entries

- human-readable text

<!-- Comment:
The changelog entry should be in the imperative mood; e.g., write "do this"/"return that" rather than "does this"/"returns that".
For examples, see: https://www.jenkins.io/changelog/

Do not include the issue in the changelog entry.
Include the issue in the description of the pull request so that the changelog generator can find it and include it in the generated changelog.

You may add multiple changelog entries if applicable by adding a new entry to the list, e.g.
- First changelog entry
- Second changelog entry
-->

### Proposed changelog category

/label <update-this-with-category>

<!--
The changelog entry needs to have a category which is selected based on the label.
If there's no changelog then the label should be `skip-changelog`.

The available categories are:
* bug - Minor bug. Will be listed after features
* developer - Changes which impact plugin developers
* dependencies - Pull requests that update a dependency
* internal - Internal only change, not user facing
* into-lts - Changes that are backported to the LTS baseline
* localization - Updates localization files
* major-bug - Major bug. Will be highlighted on the top of the changelog
* major-rfe - Major enhancement. Will be highlighted on the top
* rfe - Minor enhancement
* regression-fix - Fixes a regression in one of the previous Jenkins releases
* removed - Removes a feature or a public API
* skip-changelog - Should not be shown in the changelog

Non-changelog categories:
* web-ui - Changes in the web UI

Non-changelog categories require a changelog category but should be used if applicable,
comma separate to provide multiple categories in the label command.
-->

### Proposed upgrade guidelines

N/A

<!-- Comment:
Leave the proposed upgrade guidelines in the pull request with the "N/A" value if no upgrade guidelines are needed.
The changelog generator relies on the presence of the upgrade guidelines section as part of its data extraction process.
-->

### Submitter checklist

- [ ] The issue, if it exists, is well-described.
- [ ] The changelog entries and upgrade guidelines are appropriate for the audience affected by the change (users or developers, depending on the change) and are in the imperative mood (see [examples](https://github.com/jenkins-infra/jenkins.io/blob/master/content/_data/changelogs/weekly.yml)). Fill in the **Proposed upgrade guidelines** section only if there are breaking changes or changes that may require extra steps from users during upgrade.
- [ ] There is automated testing or an explanation as to why this change has no tests.
- [ ] New public classes, fields, and methods are annotated with `@Restricted` or have `@since TODO` Javadocs, as appropriate.
- [ ] New deprecations are annotated with `@Deprecated(since = "TODO")` or `@Deprecated(forRemoval = true, since = "TODO")`, if applicable.
- [ ] UI changes do not introduce regressions when enforcing the current default rules of [Content Security Policy Plugin](https://plugins.jenkins.io/csp/). In particular, new or substantially changed JavaScript is not defined inline and does not call `eval` to ease future introduction of Content Security Policy (CSP) directives (see [documentation](https://www.jenkins.io/doc/developer/security/csp/)).
- [ ] For dependency updates, there are links to external changelogs and, if possible, full differentials.
- [ ] For new APIs and extension points, there is a link to at least one consumer.

### Desired reviewers

@mention

<!-- Comment:
If you need an accelerated review process by the community (e.g., for critical bugs), mention @jenkinsci/core-pr-reviewers.
-->

Before the changes are marked as `ready-for-merge`:

### Maintainer checklist

- [ ] There are at least two (2) approvals for the pull request and no outstanding requests for change.
- [ ] Conversations in the pull request are over, or it is explicit that a reviewer is not blocking the change.
- [ ] Changelog entries in the pull request title and/or **Proposed changelog entries** are accurate, human-readable, and in the imperative mood.
- [ ] Proper changelog labels are set so that the changelog can be generated automatically.
- [ ] If the change needs additional upgrade steps from users, the `upgrade-guide-needed` label is set and there is a **Proposed upgrade guidelines** section in the pull request title (see [example](https://github.com/jenkinsci/jenkins/pull/4387)).
- [ ] If it would make sense to backport the change to LTS, be a _Bug_ or _Improvement_, and either the issue or pull request must be labeled as `lts-candidate` to be considered.
