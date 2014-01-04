JFreeSane is a pure-Java implementation of a SANE client. See

http://www.sane-project.org/

# Introduction

The purpose of this library is to provide a client to the Scanner Access Now Easy (SANE) daemon.
This allows Java programmers to obtain images from SANE image sources over a network.

For example, you can do the following:

```java
SaneSession session = SaneSession.withRemoteSane(
    InetAddress.getByName("my-sane-server.intranet"));
List<SaneDevice> devices = session.listDevices();
SaneDevice device = ...;  // determine which device you want to use
device.open();

BufferedImage image = device.acquireImage();  // scan an image
```

See the CommonOperations wiki page for a fairly comprehensive introduction to using JFreeSane.

# Usage

The easiest way to get this software is using [Maven](http://maven.apache.org/). Put the following in your `pom.xml`:

```xml
<project>
  ...
  <dependencies>
     ...
     <dependency>
       <groupId>com.googlecode.jfreesane</groupId>
       <artifactId>jfreesane</artifactId>
       <version>0.9</version>
     </dependency>
   </dependencies>
</project>
```

Otherwise, you can [download the jar file](https://github.com/sjamesr/jfreesane/releases/tag/jfreesane-0.9)
and put it in your project's CLASSPATH.
JFreeSane also depends on [Google Guava](http://code.google.com/p/guava-libraries/), an
excellent collection of Java libraries that you should be using in your project anyway.
You will need to download the Guava JAR file and put that in your classpath as well.

Once JFreeSane is available on your classpath, see the CommonOperations wiki page for a tutorial on how to use it.

Also consider joining the
[jfreesane-discuss](http://groups.google.com/group/jfreesane-discuss) mailing list.
It is a low-volume list where people can discuss JFreeSane, release announcements are
made and issues are reported.

# Limitations

* ~~JFreeSane currently cannot be used to obtain images from a handheld scanner.~~
Fixed in 0.9!

* JFreeSane currently does not support using SANE authenticated resources
(i.e. a username and password).

* JFreeSane must be used with a running SANE daemon. It will not run SANE for you.
It cannot talk to your scanners without a SANE daemon.

# Please contribute

If you've been looking for a Java SANE client and you're familiar with SANE, please
consider contributing documentation or patches. The easiest way to get started with
Eclipse is to do this:

```
$ git clone https://github.com/sjamesr/jfreesane.git
$ cd jfreesane
~/jfreesane$ mvn eclipse:configure-workspace \
    -Declipse.workspace=/path/to/eclipse/workspace  # eclipse workspace, not working directory
jfreesane$ mvn eclipse:eclipse
```
* run eclipse, import existing project, specify this jfreesane directory 
as the project root
* start hacking
