#! /usr/bin/env python

import argparse
import copy
import getpass
import math
import os
import shutil
import signal
import sys
import tempfile
import time
import glob

import jreUtils
import miscUtils


def buildRelease(args, buildPath):
	# We mutate args - thus make a custom copy  
	args = copy.copy(args)
	
	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreRelease = args.jreRelease
	
	# Attempt to locate a default JRE if none is specified in the args.
	if jreRelease == None:
		jreRelease = jreUtils.getDefaultJreRelease('windows')
	# Let the user know if the 'user' specified JRE is not available and locate an alternative
	elif jreUtils.isJreAvailable('windows', jreRelease) == False:
		print('[Warning] User specified JRE (' + jreRelease + ') is not available for Windows platform. Searching for alternative...')
		jreRelease = jreUtils.getDefaultJreRelease('windows')
	args.jreRelease = jreRelease

	# Bail if launch4j is not installed	
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	launch4jExe = os.path.join(appInstallRoot, "launch4j", "launch4j")
	if os.path.exists(launch4jExe) == False:
		print('Launch4j is not installed. Windows releases will not be built.')
		return

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(dir=buildPath)

	# Form the list of distributions to build
	distList = [(appName + '-' + version, False)]
	if jreUtils.isJreAvailable('windows', jreRelease) == True:
		distList.append((appName + '-' + version + '-jre', True))

	# Create the various Windows distributions	
	for (distName, isStaticRelease) in distList:
		print('Building Windows distribution: ' + distName)
		# Let the user know of the JRE release we are going to build with
		if isStaticRelease == True:
			print('\tUtilizing jreRelease: ' + jreRelease)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, distName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(buildPath, dstPath, args, isStaticRelease)

		# Create the zip file
		zipFile = os.path.join(buildPath, distName + ".zip")
		cmd = ["jar", "-cMf", zipFile, "-C", dstPath, '.']
		print('\tForming zip file: ' + zipFile)
		proc = miscUtils.executeAndLog(cmd, "\t\tjar: ")
		if proc.returncode != 0:
			print('\tError: Failed to form zip file. Return code: ' + str(proc.returncode))
			print('\tAborting build of release: ' + os.path.basename(zipFile))
		else:
			print('\tFinished building release: ' + os.path.basename(zipFile))

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(buildPath, rootPath, args, isStaticRelease):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = args.name
	jreRelease = args.jreRelease

	# Form the app contents folder
	srcPath = os.path.join(buildPath, "delta")
	dstPath = os.path.join(rootPath, "app")
	shutil.copytree(srcPath, dstPath, symlinks=True)

	#Copy dlls to the app directory so they can be found at launch
	dllDir = os.path.join(rootPath,'app', 'code','win')
	for libPath in glob.iglob(os.path.join(dllDir,"*.lib")):
		libFileName = os.path.basename(libPath)
		srcPath = os.path.join(dllDir,libFileName)
		linkPath = os.path.join(dstPath,libFileName)
		shutil.copy(srcPath,linkPath)
	for dllPath in glob.iglob(os.path.join(dllDir,"*.dll")):
		dllFileName = os.path.basename(dllPath)
		srcPath = os.path.join(dllDir,dllFileName)
		linkPath = os.path.join(dstPath,dllFileName)
		shutil.copy(srcPath,linkPath)

	# Setup the launcher contents
	exePath = os.path.join(rootPath, "launcher")
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(exePath)
	shutil.copy(srcPath, exePath);

	# Build the java component of the distribution
	if args.javaCode != None:
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
			cmd = ['convert', origIconFile, winIconFile]
			proc = miscUtils.executeAndLog(cmd, "\t\t(ImageMagick) convert: ")
			if proc.returncode != 0:
				if proc.returncode == None:
					print('\t\tImageMagick convert does not appear to be properly installed.')
				else:
					print('\t\tImageMagick convert failed with eCode: ' + str(proc.returncode))
				print('\t\tThere will be no windows icon file.')
				winIconFile = None

		# Form the launch4j config file
		configFile = os.path.join(rootPath, appName + ".xml")
		buildLaunch4JConfig(configFile, args, isStaticRelease, winIconFile)

		# Build the windows executable 
		launch4jExe = os.path.join(appInstallRoot, "launch4j", "launch4j")
		cmd = [launch4jExe, configFile]
		print('\tBuilding windows executable via launch4j')
		proc = miscUtils.executeAndLog(cmd, "\t\t")
		if proc.returncode != 0:
			print('\tError: Failed to build executable. Return code: ' + str(proc.returncode))

		# Perform cleanup
		os.remove(configFile)
		if winIconFile != None:
			os.remove(winIconFile)

def buildLaunch4JConfig(destFile, args, isStaticRelease, iconFile):
	f = open(destFile, 'wb')

	writeln(f, 0, "<launch4jConfig>")
	if args.debug == True:
		writeln(f, 1, "<headerType>console</headerType>");
	else:
		writeln(f, 1, "<headerType>gui</headerType>");
	writeln(f, 1, "<outfile>" + args.name + ".exe</outfile>");
	writeln(f, 1, "<dontWrapJar>true</dontWrapJar>");
	writeln(f, 1, "<errTitle>" + args.name + "</errTitle>");
	writeln(f, 1, "<downloadUrl>http://java.com/download</downloadUrl>");
#	writeln(f, 1, "<supportUrl>url</supportUrl>");

	writeln(f, 1, "<cmdLine>app.cfg</cmdLine>");
	writeln(f, 1, "<chdir>app/</chdir>");
	writeln(f, 1, "<priority>normal</priority>");
	writeln(f, 1, "<customProcName>true</customProcName>");
	writeln(f, 1, "<stayAlive>false</stayAlive>");
	if iconFile != None:
		writeln(f, 1, "<icon>" + iconFile + "</icon>");

	writeln(f, 1, "<classPath>");
	writeln(f, 2, "<mainClass>appLauncher.AppLauncher</mainClass>");
	writeln(f, 2, "<cp>../launcher/appLauncher.jar</cp>");
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
		writeln(f, 2, "<path>" + args.jreRelease + "</path>");
	else:
		jreVer = jreUtils.getJreMajorVersion(args.jreRelease)
#		writeln(f, 2, "<minVersion>" + "1.7.0" + "</minVersion>");
		writeln(f, 2, "<minVersion>" + jreVer + "</minVersion>");
#	writeln(f, 2, "<jdkPreference>jreOnly|preferJre|preferJdk|jdkOnly</jdkPreference>");
	writeln(f, 2, "<jdkPreference>preferJre</jdkPreference>");
	for aJvmArg in args.jvmArgs:
		writeln(f, 2, "<opt>" + aJvmArg + "</opt>");
	writeln(f, 2, "<opt>-Djava.system.class.loader=appLauncher.RootClassLoader</opt>");
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

