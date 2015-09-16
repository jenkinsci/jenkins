__TODO__: Move to jenkins-ci web/wiki if changes accepted.

# Fuzzy Search
This is obviously the easiest form of build item search. Just enter a string in the "find" box and build
items/runs will be matched across a number of predefined fields (number, name, status description etc).
 
For more control, see the next section.

# Search Terms

* [__name:__] : Is contained in the name of the build.
* [__desc:__] : Is contained in the description of the build.d.
* [__result:__] : Is the "result" of the build (SUCCESS, UNSTABLE, FAILURE, NOT_BUILT, ABORTED).
* [__date-from:__] and/or [__date-to:__] : Build date is before, after or between. Date format is *yyyy-MM-yy*. Date is start of day i.e. 00:00. Also supports some shorthands e.g. "today", "yesterday", "1 day", "2 days", "2 weeks", "2 months".

Different search terms are __AND__ together to result in a matching Build, while multiples of the same search term are __OR__'d together e.g.

* `result: FAILURE desc: staging desc: production`

Is interpreted as: "(result: FAILURE) AND (desc: staging OR desc: production)"

# Examples

Builds that contain the words "staging" or "production" in the description:

* `desc: staging desc: production`

Failed builds in last week (7 days):

* `result: FAILURE date-from: 1 week`

Failed builds since a specific date:

* `result: FAILURE date-from: 2015-03-31`

Unstable builds in March:

* `result: UNSTABLE date-from: 2015-03-01 date-to: 2015-04-01`
