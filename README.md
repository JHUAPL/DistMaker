# DistMaker


## Description
DistMaker provides a capability to enable developers to package a Java client application for any target platform (Linux, MacOS, and Windows). A DistMaker enabled package allows for easy distribution via its in-app update capability.

Currently DistMaker is distributed in source form only, so it will need to be compiled. See the section: **Building DistMaker**.


## More details
DistMaker is a software toolkit which allows a software developer to develop applications that have a built-in update mechanism.

The update mechanism can be manually triggered by an end user or through an auto update mechanism.

DistMaker provides a capability to allow a software developer to deploy (or remove) updates to a deployment site.


## Usage
Please read the ./doc/QuickStartGuide.pdf for instructions on:

- Source code changes to make the application DistMaker enabled
- Setting up the deploy site for the DistMaker application
- Deploying the DistMaker application


## Dependencies
A DistMaker enabled (Java) application has the following dependencies:

- Java 17+
- Apache Commons Compress 1.15+
- Guava 18.0+
- Glum 2.0.0+
- MigLayout 3.7.2+

An additional dependency for DistMaker enabled applications on the Windows platform is:

- Lanch4J 3.14

To package and distribute a DistMaker application the following are the software (server side) dependencies:

- Java 17+
- Python 3.6+


Note the following:

- In theory DistMaker should work with later versions of the above listed software.
- To allow for distribution of updates, you will need to have permissions to a web server.


## Building DistMaker
To build a DistMaker release from the console, run the following command:

&nbsp;&nbsp;&nbsp;&nbsp;./tools/buildRelease

The following sofware are dependencies for compiling DistMaker:

- JDK 17+
- Python 3.6+
- Apache Ant 1.10.8+

Note, you will have to edit the script, ./tools/buildRelease, so that the variables antPath and jdkPath are relative to your system.

In addition, if DistMaker is to build compressed DMG files you will need to get a copy of the libdmg-hfsplus software. That software is located at:

&nbsp;&nbsp;&nbsp;&nbsp;https://github.com/fanquake/libdmg-hfsplus

You would then need to update the file ./script/appleUtils.py, and change the compressCmd to reflect the location to the actual dmg executable. This executable does the actual compression.

## Legal Notice
DistMaker utilizes a number of copyrighted products.

A listing of all copyrights can be found in the folder:

&nbsp;&nbsp;&nbsp; ./doc/legal/

Each copyrighted product has a textual file with a naming convention of:

&nbsp;&nbsp;&nbsp; License.&lt;aProductName&gt;.txt

Note that &lt;aProductName&gt; corresponds to the formal product name.

&nbsp;
