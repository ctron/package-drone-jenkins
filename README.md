# package-drone-jenkins

[![Build Status](https://travis-ci.org/ctron/package-drone-jenkins.svg?branch=master)](https://travis-ci.org/ctron/package-drone-jenkins) [![Coverage Status](https://coveralls.io/repos/github/ctron/package-drone-jenkins/badge.svg?branch=master)](https://coveralls.io/github/ctron/package-drone-jenkins?branch=master)

A Package Drone Plugin for Jenkins

[Eclipse Package Drone](https://eclipse.org/package-drone) is a
software artifact repository compatible with Maven 2, OSGi, RPM/YUM and DEB/APT. 

# Installation

Install it using the Jenkins Plugin Manager.

Also see https://wiki.jenkins-ci.org/display/JENKINS/Package+Drone+Plugin

# Requirements

Requires a Package Drone server version 0.11.0 or higher. 

# Release build

```
mvn release:prepare release:perform
```
 