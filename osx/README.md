# Jenkins OS X Installer Project

To create the Jenkins Installer for OS X you need an OS X machine with Developer Tools installed. 
You can download the Developer Tools from [Apple's Developer Connection website](http://developer.apple.com)

## Build the Installer

To build the installer package you will need a copy of the jenkins.war file. You can download it from the
[Jenkins home page](http://mirrors.jenkins-ci.org/war/latest/). Then open a terminal, navigate to this
directory, and run the build script

    ./build.sh /path/to/jenkins.war

## Some Links

* [Jenkins](http://jenkins-ci.org)
* [Apple Developer Center](http://www.developer.apple.com)

## License

    Original Copyright (C) 2011 by Ingo Richter
    Portions Copyright (C) 2011 by Will Ross
    
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

