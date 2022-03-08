# Jenkins-i18n

Perl script to generate missing translation keys and missing properties files,
to remove unused keys, and to convert utf8 properties files to iso or ascii.

Note, while the migration to Jenkins this file will report the keys which
should point to Jenkins instead of the old name.

## Installation

Although this module *should* be ready to be published to CPAN, at this moment
is just used by the `translation-tool.pl` CLI, so installing from CPAN is not an
option.

That said, `Jenkins::i18n` module has external dependencies as well.

There are several ways to install Perl modules, all very well documented by
the community. Those external dependencies are all Perl modules available at
[CPAN](https://metacpan.org/).

Here is a list of ways to do it:

1. Install through packages from your OS: best way.
2. Install with [cpan](https://metacpan.org/dist/CPAN/view/scripts/cpan#SYNOPSIS) CLI and [local::lib](https://metacpan.org/pod/local::lib).
3. Install [perlbrew](https://perlbrew.pl/), install your personal `perl` then use `cpan` CLI.
4. Install modules as root using `cpan` CLI: worst and not recommended method.

### Dependencies

See the `Makefile.PL` file for `TEST_REQUIRES` and `PREREQ_PM` entries.

## Testing

Once the dependencies are installed, you can run the tests available for this
module:

```
prove -lvm
```

Here is a sample:

```
$ prove -lvm
t/Jenkins-i18n.t ....
1..1
ok 1 - use Jenkins::i18n;
ok
t/removed_unused.t ..
1..15
ok 1 - dies without file parameter
ok 2 - get the expected error message
# Using /home/semantix/Projects/jenkins/Jenkins-i18n/tmp/t_removed_unused_t/default_1/sample.properties for tests
ok 3 - dies without keys parameter
ok 4 - get the expected error message
ok 5 - dies with invalid keys parameter
ok 6 - get the expected error message
# Without a license
ok 7 - got the expected number of keys removed
ok 8 - resulting properties file has the expected number of lines
ok 9 - dies with invalid license parameter
ok 10 - get the expected error message
# Restoring file
# With a license
ok 11 - got the expected number of keys removed
ok 12 - resulting properties file has the expected number of lines
# Restoring file
# With a backup
ok 13 - got the expected number of keys removed
ok 14 - resulting properties file has the expected number of lines
ok 15 - File has a backup as expected at /home/semantix/Projects/jenkins/Jenkins-i18n/tmp/t_removed_unused_t/default_1/sample.properties.bak
ok
All tests successful.
Files=2, Tests=16,  0 wallclock secs ( 0.02 usr  0.00 sys +  0.14 cusr  0.01 csys =  0.17 CPU)
Result: PASS
```

## References

- [Jenkins Internationalization](https://www.jenkins.io/doc/developer/internationalization/)
- [i18n](https://wiki.mageia.org/en/What_is_i18n,_what_is_l10n)

## Copyright and licence

The MIT License

Copyright (c) 2004-, Kohsuke Kawaguchi, Sun Microsystems, Inc., and a number
of other of contributors

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
