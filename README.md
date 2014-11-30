JFreeSane is a pure-Java implementation of a SANE client. See [the SANE project
page](http://www.sane-project.org/) for more information about SANE itself.

- [Introduction](#introduction)
- [Getting JFreeSane](#getting-jfreesane)
- [Limitations](#limitations)
- [Please contribute](#please-contribute)
- [Usage](#usage)
    - [Connecting to SANE](#connecting-to-sane)
    - [Obtaining a device handle](#obtaining-a-device-handle)
    - [Listing known devices](#listing-known-devices)
    - [Opening the device](#opening-the-device)
    - [Acquiring an image](#acquiring-an-image)
    - [Device options](#device-options)
        - [Setting options](#setting-options)
        - [Reading options](#reading-options)
        - [Option getters and setters](#option-getters-and-setters)
    - [Reading from an automatic document feeder](#reading-from-an-automatic-document-feeder)
    - [Authentication](#authentication)
    - [Listening to events](#listening-to-events)

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

# Getting JFreeSane

The easiest way to get this software is using [Maven](http://maven.apache.org/). Put the following in your `pom.xml`:

```xml
<project>
  ...
  <dependencies>
     ...
     <dependency>
       <groupId>com.googlecode.jfreesane</groupId>
       <artifactId>jfreesane</artifactId>
       <version>0.93</version>
     </dependency>
   </dependencies>
</project>
```

Otherwise, you can [download the jar file](https://github.com/sjamesr/jfreesane/releases/tag/jfreesane-0.93)
and put it in your project's CLASSPATH.
JFreeSane also depends on [Google Guava](http://code.google.com/p/guava-libraries/), an
excellent collection of Java libraries that you should be using in your project anyway.
You will need to download the Guava JAR file and put that in your classpath as well.

Once JFreeSane is available on your classpath, please read on for a tutorial on how to use it.

Also consider joining the
[jfreesane-discuss](http://groups.google.com/group/jfreesane-discuss) mailing list.
It is a low-volume list where people can discuss JFreeSane, release announcements are
made and issues are reported.

# Limitations

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

If you want to contribute back to JFreeSane, please consider [forking](https://help.github.com/articles/fork-a-repo)
the project. Once you have some code you'd like to contribute,
[submit a pull request](https://help.github.com/articles/using-pull-requests). We really appreciate contributions
and we'll get it checked in as fast as possible.

# Usage

Here are some ways you can use JFreeSane.

## Connecting to SANE

JFreeSane is strictly a client of the [SANE](http://www.sane-project.org/) network daemon.
You must have a SANE daemon running first before you can do anything with JFreeSane.
Discussing how to set up a SANE daemon is beyond the scope of this document, please refer to the [SANE home page](http://www.sane-project.org/) for guidance.

Once you have the daemon running on some host, for example `saneserver.mydomain.com`, you
can start a SANE session with the following:

```java
import au.com.southsky.jfreesane.SaneSession;

InetAddress address = InetAddress.getByName("saneserver.mydomain.com");
SaneSession session = SaneSession.withRemoteSane(address);
```

Now you need to obtain a device handle.

## Obtaining a device handle

If you already know the name of the SANE device, you can open it directly by name. For
example, SANE servers usually have (but do not advertise) a device whose name is "test".
This is a "pseudo" device, in that it does not represent any physical hardware. It is
useful for exercising JFreeSane.

```java
// Open the test device by name
SaneSession session = ...;
SaneDevice device = session.getDevice("test");
```

## Listing known devices

If you do not know the device name, you can list devices known to the SANE server using the following:

```java
SaneSession session = ...;
List<SaneDevice> devices = session.listDevices();
```

## Opening the device

Most likely, you now want to interact with the scanning device. Before you do anything, you need to "`open`" the device.

```java
SaneDevice device = ...;
device.open();
```

The device is now open. You can now get and set options and acquire images.

## Acquiring an image

Now you're ready to acquire an image from the scanner. Call `acquireImage`:

```java
SaneDevice device = ...;
device.open();
BufferedImage image = device.acquireImage();
```

If the default options are not sufficient, see the "Device options" section below.

## Device options

Each device has a set of parameters that control aspects of that device's operation.
There are a handful of SANE 
[built-in options](http://www.sane-project.org/html/doc014.html):

  * scan resolution
  * preview mode
  * scan area

Different scanners have different capabilities (for example, duplex scan, color, various
document sources). Each scanner type will expose a set of options in addition to the ones
described above.

In order to see what options are supported by a device:

```java
SaneDevice device = ...;
device.open();
List<SaneOption> options = device.listOptions();
```

Each option has

  * a name, used as an identifier by JFreeSane (e.g. mode)
  * a title, in English, suitable for display to the user (e.g. Scan Mode)
  * description, a brief description of the purpose of the option

### Setting options

You can set the value of an option, so long as 
`SaneOption.isActive()` and `SaneOption.isWriteable` are both true. For example, the
"source" option's value can be set by the following:

```java
SaneOption option = device.getOption("source");
option.setStringValue("Auto Document Feeder");
```

The string "Auto Document Feeder" may not be correct for your particular device.
In fact, valid option values differ from device to device. In this case, you may
need to ask SANE what values are valid for a given option.

```java
SaneOption option = device.getOption("source");
List<String> validValues = option.getStringConstraints();
```

`validValues` now contains a list of strings, each of which is a valid value for this
option.

There are some options whose valid values are in some range. For example, the "pixma"
backend has an option called "tl-x" (the x-ordinate of the top left of the scan area).
Its valid values are in the range 0 to 216.069 millimeters.
To determine this programmatically:

```java
SaneOption option = device.getOption("tl-x");
assert option.isConstrained() 
    && option.getConstraintType() == OptionValueConstraintType.RANGE_CONSTRAINT;
RangeConstraint constraint = option.getRangeConstraint();

// this option is in mm, which is a fractional value. SANE uses fixed-point arithmetic,
// so JFreeSane calls this a value of type "FIXED"
assert option.getType() == OptionValueType.FIXED;
assert option.getUnits() == SaneOption.OptionUnits.UNITS_MM;

double min = constraint.getMinimumFixed();
double max = constraint.getMaximumFixed();
```

To set the value of the "tl-x" option, you can do the following:

```java
SaneOption option = device.getOption("tl-x");
double actualValue = option.setFixedValue(97.5);
```

JFreeSane will return the value actually set by the backend. For example, the valid
values for "tl-x" are 0 to 216.069 (see the preceding section to see how this can be
determined programmatically). If you set the value to something out of range, SANE will
clamp the value to something in the valid range.

```java
SaneOption option = device.getOption("tl-x");
double actualValue = option.setFixedValue(-4);
// actualValue will be set to 0, the minimum value for this option
```

### Reading options

The methods for reading option values depend on the option's type. Options are readable
only if `isActive()` and `isReadable()` are true for the option. See the table in the
following section for the list of option accessors.

### Option getters and setters

The following table lists the `SaneOption` methods for reading and writing options of a given type:

| *`SaneOption.getType()`* | *Getter* | *Setter* |
|--------------------------|----------|----------|
| `BOOLEAN` | `getBooleanValue` | `setBooleanValue` |
| `INT` | `getIntegerValue` | `setIntegerValue` |
| `FIXED` | `getFixedValue` | `setFixedValue` |
| `STRING` | `getStringValue` | `setStringValue` |
| `BUTTON` | None | `setButtonValue` |
| `GROUP` | None | None |

Additionally, `INT`- and `FIXED`-type options may actually be an array of `INT` or
`FIXED`. You can always use `SaneOption.getValueCount` to know for sure. 
If the result is more than 1, you have an array.

  * `getIntegerArrayValue` reads an INT array, `setIntegerValue(List<Integer>)` writes one
  * `getFixedArrayValue` reads a FIXED array, `setFixedValue(List<Double>)` writes one

## Reading from an automatic document feeder

You may have a scanner with an Automatic Document Feeder (ADF). In this case, you may
want to acquire all the images until the ADF is out of paper. Use the following technique:

```java
SaneDevice device = ...;

// this value is device-dependent. See the section on "Setting Options" to find out
// how to enumerate the valid values
device.getOption("source").setStringValue("Automatic Document Feeder");

while (true) {
  try {
    BufferedImage image = device.acquireImage();
    process(image);
  } catch (SaneException e) {
    if (e.getStatus() == SaneStatus.STATUS_NO_DOCS) {
      // this is the out of paper condition that we expect
      break;
    } else {
      // some other exception that was not expected
      throw e;
    }
  }
}
```

## Authentication

Thanks to generous contributions from Paul and Matthias, JFreeSane now supports connecting to authenticated resources.

By default, JFreeSane will use SANE-style authentication as documented in
[the `scanimage(1)` man page](http://www.sane-project.org/man/scanimage.1.html). If you want to implement
an alternative method of supplying usernames and passwords, see the javadoc for `SaneSession.setPasswordProvider`.

## Listening to events

As of JFreeSane 0.93, you can now receive feedback when various scan events occur. For example, you can use
a `ScanListener` to provide scan progress information to the user. In the following example, a Swing progress bar
is updated as the scan proceeds.

```java
SaneDevice device = ...;
final JProgressBar progressBar = new JProgressBar();
progressBar.setStringPainted(true);
progressbar.setString("Starting scan...");

ScanListener progressBarUpdater = new ScanListenerAdapter() {
  @Override public void recordRead(
      SaneDevice device,
      final int totalBytesRead,
      final int imageSize) {
    final double fraction = 1.0 * totalBytesRead / imageSize;
    SwingUtilities.invokeLater(new Runnable() {
      @Override public void run() {
        progressBar.setValue((int) (fraction * 100));
        progressBar.setString(
            String.format("Read %d of %d bytes (%.2f%%)",
              totalBytesRead, imageSize, fraction));
      }
    });
  }

  @Override public void scanningFinished(SaneDevice device) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override public void run() {
        progressBar.setValue(100);
        progressBar.setString("Scanning completed!");
      }
    });
  }
};

// JFreeSane can generate recordRead events at a high rate. We don't really need more than a few
// of these per second, otherwise we spend too much time updating the UI and not enough time
// scanning. Use the RateLimitingScanListeners class to get a wrapper around our existing
// listener that delivers messages at an acceptable rate (10 per second max).
ScanListener rateLimitedListener = RateLimitedScanListeners.noMoreFrequentlyThan(
    progressBarUpdater, 100, TimeUnit.MILLISECONDS);
  
BufferedImage image = device.acquireImage(rateLimitedListener);
```

In some cases, JFreeSane cannot know the eventual size of the image. In this case, the `imageSize`
parameter of `recordRead` will be set to `-1`. Examples of this situation are:

* when using a handheld scanner
* when using a scanning driver that supports page height detection (that is, scanning stops only
when the scanner detects the end of the page)

Also, if you are using an old three-pass scanner (where one pass is made for each of three color
bands), you will want to listen to the `frameAcquisitionStarted` message. JFreeSane will try to
guess how many frames will eventually be sent and which frame it is currently acquiring.

See the javadoc for `ScanListener` for more details.
