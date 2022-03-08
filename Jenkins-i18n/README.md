# Jenkins-i18n

Perl script to generate missing translation keys and missing properties files,
to remove unused keys, and to convert utf8 properties files to iso or ascii.

1. It recursively looks for files in a folder, and analyzes them to extract
   the keys being used in the application.
2. If --add=true, it generates the appropriate file for the desired language
   and adds these keys to it, adding the english text as a reference. If the
   properties file already exists the script update it with the new keys.
3. When --remove=true and there are unused keys in our file, the script
   removes them.
4. If an editor is passed as argument, the script edits each modified file
   after adding new keys.
5. Finally, when --toiso=true or --toascii=true, the script is able to
   convert utf-8 properties files to iso or unicode hex representation is
   ascii.

Note, while the migration to Jenkins this file will report the keys which
should point to Jenkins instead of the old name.

# INSTALLATION

To install this module type the following:

   perl Makefile.PL
   make
   make test
   make install

# DEPENDENCIES

This module requires these other modules and libraries:

  blah blah blah

# REFERENCES

- [i18n](https://wiki.mageia.org/en/What_is_i18n,_what_is_l10n)

# COPYRIGHT AND LICENCE

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

