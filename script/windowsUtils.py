#! /usr/bin/env python

import argparse
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time

import miscUtils

# Globals
# The global var jre is a pointer to a full jre release for a Windows system
# There should be a release located at <installpath>/jre/windows/jreRelease
jreRelease = 'jre1.7.0_15'



def buildRelease(args, buildPath):
	# Retrieve vars of interest
	appName = args.name
	version = args.version

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(dir=buildPath)

	# Form the list of distributions to build
	distList = [(appName + '-' + version, False)]
	if miscUtils.isJreAvailable('windows', jreRelease) == True:
		distList.append((appName + '-' + version + '-jre', True))

	# Create the various Windows distributions	
	for (distName, isStaticRelease) in distList:
		print('Building Windows distribution: ' + distName)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, distName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(dstPath, args, isStaticRelease)

		# Create the zip file
		zipFile = os.path.join(buildPath, distName + ".zip")
		subprocess.check_call(["jar", "-cMf", zipFile, "-C", dstPath, '.'], stderr=subprocess.STDOUT)

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(rootPath, args, isStaticRelease):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = args.name
	dataCodeList = args.dataCode
	javaCodePath = args.javaCode

	# Copy the dataCode to the proper location
	for aPath in dataCodeList:
		srcPath = aPath
		dstPath = os.path.join(rootPath, os.path.basename(aPath))
		shutil.copytree(srcPath, dstPath, symlinks=True)

	# Build the java component of the distribution
	if javaCodePath != None:
		# Copy the javaCode to the proper location
		srcPath = javaCodePath
		dstPath = os.path.join(rootPath, 'java')
		shutil.copytree(srcPath, dstPath, symlinks=True)

		# Copy over the jre
		if isStaticRelease == True:
			srcPath = os.path.join(appInstallRoot, 'jre', 'windows', jreRelease)
			dstPath = os.path.join(rootPath, os.path.basename(srcPath))
			shutil.copytree(srcPath, dstPath, symlinks=True)

		# Generate the iconFile
		winIconFile = None
		origIconFile = args.iconFile
		if origIconFile != None:
			winIconFile = os.path.join(rootPath, os.path.basename(origIconFile) + ".ico")
			eCode = subprocess.call(["convert", origIconFile, winIconFile], stderr=subprocess.STDOUT)
			if eCode != 0:
				print('ImageMagick convert failed with eCode: ' + str(eCode))
				print('There will be no windows icon file. System call:')
				print('   convert ' + origIconFile + ' ' + winIconFile)
				winIconFile = None

		# Form the launch4j config file
		configFile = os.path.join(rootPath, appName + ".xml")
		buildLaunch4JConfig(configFile, args, isStaticRelease, winIconFile)

		# Build the windows executable 
		launch4jExe = os.path.join(appInstallRoot, "launch4j", "launch4j")
		subprocess.check_call([launch4jExe, configFile], stderr=subprocess.STDOUT)
		print(launch4jExe + ' ' + configFile)

		# Perform cleanup
		os.remove(configFile)
		if winIconFile != None:
			os.remove(winIconFile)

def buildLaunch4JConfig(destFile, args, isStaticRelease, iconFile):
	classPathStr = ''
	for aStr in args.classPath:
		classPathStr += 'java/' + aStr + ':'
	if len(classPathStr) > 0:
		classPathStr = classPathStr[0:-1]

	appArgsStr = ''
	for aStr in args.appArgs:
		appArgsStr += ' ' + aStr

	f = open(destFile, 'wb')

	writeln(f, 0, "<launch4jConfig>")
	writeln(f, 1, "<headerType>gui</headerType>");
#	writeln(f, 1, "<headerType>console</headerType>");
	writeln(f, 1, "<outfile>" + args.name + ".exe</outfile>");
	writeln(f, 1, "<dontWrapJar>true</dontWrapJar>");
	writeln(f, 1, "<errTitle>" + args.name + "</errTitle>");
	writeln(f, 1, "<downloadUrl>http://java.com/download</downloadUrl>");
#	writeln(f, 1, "<supportUrl>url</supportUrl>");

	if len(appArgsStr) > 0:
			writeln(f, 1, "<cmdLine>" + appArgsStr + "</cmdLine>");
	writeln(f, 1, "<chdir>.</chdir>");
	writeln(f, 1, "<priority>normal</priority>");
	writeln(f, 1, "<customProcName>true</customProcName>");
	writeln(f, 1, "<stayAlive>false</stayAlive>");
	if iconFile != None:
		writeln(f, 1, "<icon>" + iconFile + "</icon>");

	writeln(f, 1, "<classPath>");
	writeln(f, 2, "<mainClass>" + args.mainClass + "</mainClass>");
	for aClassPath in args.classPath:
		writeln(f, 2, "<cp>" + 'java/' + aClassPath + "</cp>");
	writeln(f, 1, "</classPath>");

	if args.forceSingleInstance != False:
		writeln(f, 0, "");
		writeln(f, 1, "<singleInstance>");
		writeln(f, 2, "<mutexName>" + args.name + ".mutex</mutexName>");
		writeln(f, 2, "<windowTitle>" + args.name + "</windowTitle>");
		writeln(f, 1, "</singleInstance>");

	writeln(f, 0, "");
	writeln(f, 1, "<jre>");
	if isStaticRelease == True:
		writeln(f, 2, "<path>" + jreRelease + "</path>");
	else:
		writeln(f, 2, "<minVersion>" + "1.7.0" + "</minVersion>");
#	writeln(f, 2, "<jdkPreference>jreOnly|preferJre|preferJdk|jdkOnly</jdkPreference>");
	writeln(f, 2, "<jdkPreference>preferJre</jdkPreference>");
	for aJvmArg in args.jvmArgs:
		writeln(f, 2, "<opt>" + aJvmArg + "</opt>");
	writeln(f, 1, "</jre>");

	writeln(f, 0, "");
	writeln(f, 1, "<messages>");
	writeln(f, 2, "<startupErr>" + args.name + " error...</startupErr>");
	writeln(f, 2, "<bundledJreErr>Failed to locate the bundled JRE</bundledJreErr>");
	writeln(f, 2, "<jreVersionErr>Located JRE is not the proper version.</jreVersionErr>");
	writeln(f, 2, "<launcherErr>Failed to launch " + args.name + "</launcherErr>");
	writeln(f, 1, "</messages>");

	writeln(f, 0, "")
	writeln(f, 0, "</launch4jConfig>")
	f.write('\n')
	f.close()


def writeln(f, tabL, aStr, tabStr='\t'):
	tStr = ''
	for i in range(tabL):
		tStr += tabStr
	f.write(tStr + aStr + '\n')
