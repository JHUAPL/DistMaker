#! /usr/bin/env python

import copy
import os
import shutil
import subprocess
import tempfile
import glob

import jreUtils
import miscUtils
import deployJreDist


def buildRelease(args, buildPath):
	# We mutate args - thus make a custom copy
	args = copy.copy(args)

	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreVerSpec = args.jreVerSpec
	platformStr = 'linux'

	# Determine the types of builds we should do
	platformType = miscUtils.getPlatformTypes(args.platform, platformStr)
	if platformType.nonJre == False and platformType.withJre == False:
		return;

	# Check our system environment before proceeding
	if checkSystemEnvironment() == False:
		return

	# Form the list of distributions to build (dynamic and static JREs)
	distList = []
	if platformType.nonJre == True:
		distList = [(appName + '-' + version, None)]
	if platformType.withJre == True:
		# Select the jreTarGzFile to utilize for static releases
		jreTarGzFile = jreUtils.getJreTarGzFile(platformStr, jreVerSpec)
		if jreTarGzFile == None:
			# Let the user know that a compatible JRE was not found - thus no static release will be made.
			print('[Warning] No compatible JRE ({0}) is available for the {1} platform. A static release will not be provided for the platform.'.format(jreVerSpec, platformStr.capitalize()))
		else:
			distList.append((appName + '-' + version + '-jre', jreTarGzFile))

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(prefix=platformStr, dir=buildPath)

	# Create the various distributions
	for (aDistName, aJreTarGzFile) in distList:
		print('Building {0} distribution: {1}'.format(platformStr.capitalize(), aDistName))
		# Let the user know of the JRE release we are going to build with
		if aJreTarGzFile != None:
			print('\tUtilizing JRE: ' + aJreTarGzFile)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, aDistName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(buildPath, dstPath, args, aJreTarGzFile)

		# Create the tar.gz archive
		tarFile = os.path.join(buildPath, aDistName + '.tar.gz')
		print('\tForming tar.gz file: ' + tarFile)
		childPath = aDistName
		subprocess.check_call(["tar", "-czf", tarFile, "-C", tmpPath, childPath], stderr=subprocess.STDOUT)
		print('\tFinished building release: ' + os.path.basename(tarFile) + '\n')

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(buildPath, rootPath, args, jreTarGzFile):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = args.name

	# Form the app contents folder
	srcPath = os.path.join(buildPath, "delta")
	dstPath = os.path.join(rootPath, "app")
	shutil.copytree(srcPath, dstPath, symlinks=True)

	# Copy libs to the app directory so they can be found at launch
	soDir = os.path.join(rootPath, 'app', 'code', 'linux')
	for libPath in glob.iglob(os.path.join(soDir, "*.so")):
		libFileName = os.path.basename(libPath)
		srcPath = os.path.join(soDir, libFileName)
		linkPath = os.path.join(dstPath, libFileName)
		shutil.copy(srcPath, linkPath)

	# Setup the launcher contents
	dstPath = os.path.join(rootPath, "launcher/" + deployJreDist.getAppLauncherFileName())
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(os.path.dirname(dstPath))
	shutil.copy(srcPath, dstPath);

	# Build the java component of the distribution
	if args.javaCode != None:
		# Form the executable bash script
		dstPath = os.path.join(rootPath, 'run' + appName)
		buildBashScript(dstPath, args, jreTarGzFile)

	# Unpack the JRE and set up the JRE tree
	if jreTarGzFile != None:
		jreUtils.unpackAndRenameToStandard(jreTarGzFile, rootPath)


def buildBashScript(destFile, args, jreTarGzFile):
	# Form the jvmArgStr but strip away the -Xmx* component if it is specified
	# since the JVM maxMem is dynamically configurable (via DistMaker)
	maxMem = None
	jvmArgsStr = ''
	for aStr in args.jvmArgs:
		if aStr.startswith('-Xmx'):
			maxMem = aStr[4:]
		else:
			jvmArgsStr += aStr + ' '

	f = open(destFile, 'wb')
#	f.write('#!/bin/bash\n')
	f.write('#!/usr/bin/env bash\n')

	f.write('# Do not remove the opening or closing brackets: {}. This enables safe inline\n')
	f.write('# mutations to this script while it is running\n')
	f.write('{    # Do not remove this bracket! \n\n')

	f.write('# Define where the Java executable is located\n')
	if jreTarGzFile == None:
		f.write('javaExe=java\n\n')
	else:
		jrePath = jreUtils.getBasePathForJreTarGzFile(jreTarGzFile)
		f.write('javaExe=../' + jrePath + '/bin/java\n\n')

	f.write('# Define the maximum memory to allow the application to utilize\n')
	if maxMem == None:
		f.write('#maxMem=512m # Uncomment out this line to change from defaults.\n\n')
	else:
		f.write('maxMem=' + maxMem + '\n\n')

	f.write('# Get the installation path\n')
	f.write('# We support the Apple / Linux variants explicitly and then default back to Linux\n')
	f.write('if [ "$(uname -s)" == "Darwin" ]; then\n')
	f.write('  # Apple platform: We assume the coreutils package has been installed...\n')
	f.write('  installPath=$(greadlink -f "$BASH_SOURCE")\n')
	f.write('elif [ "$(uname -s)" == "Linux" ]; then\n')
	f.write('  # Linux platform\n')
	f.write('  installPath=$(readlink -f "$BASH_SOURCE")\n')
	f.write('else\n')
	f.write('  # Other platform: ---> Defaults back to Linux platform\n')
	f.write('  installPath=$(readlink -f "$BASH_SOURCE")\n')
	f.write('fi\n')
	f.write('installPath=$(dirname "$installPath")\n\n')

	f.write('# Change the working directory to the app folder in the installation path\n')
	f.write('cd "$installPath"/app\n\n')

	f.write('# Setup the xmxStr to define the maximum JVM memory.\n')
	f.write('if [ -z ${maxMem+x} ]; then\n')
	f.write('  xmxStr=""\n')
	f.write('else\n')
	f.write('  xmxStr=\'-Xmx\'$maxMem\n')
	f.write('fi\n\n')

	exeCmd = '$javaExe ' + jvmArgsStr + '$xmxStr -Djava.system.class.loader=appLauncher.RootClassLoader '
	exeCmd = exeCmd + '-cp ../launcher/' + deployJreDist.getAppLauncherFileName() + ' appLauncher.AppLauncher $*'
	f.write('# Run the application\n')
	f.write(exeCmd + '\n\n')

	f.write('exit # Do not remove this exit! (just before the bracket)\n')
	f.write('}    # Do not remove this bracket! \n\n')

	f.close()

	# Make the script executable
	os.chmod(destFile, 00755)


def checkSystemEnvironment():
	"""Checks to ensure that all system application / environment variables needed to build a Linux distribution are installed
	and properly configured. Returns False if the system environment is insufficient"""
	return True
