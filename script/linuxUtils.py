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


def buildRelease(aArgs, aBuildPath, aJreNodeL):
	# We mutate args - thus make a custom copy
	args = copy.copy(aArgs)

	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreVerSpec = args.jreVerSpec
	archStr = 'x64'
	platStr = 'linux'

	# Determine the types of builds we should do
	platformType = miscUtils.getPlatformTypes(args.platform, platStr)
	if platformType.nonJre == False and platformType.withJre == False:
		return;

	# Check our system environment before proceeding
	if checkSystemEnvironment() == False:
		return

	# Form the list of distributions to build (dynamic and static JREs)
	distL = []
	if platformType.nonJre == True:
		distL = [(appName + '-' + version, None)]
	if platformType.withJre == True:
		# Select the JreNode to utilize for static releases
		tmpJreNode = jreUtils.getJreNode(aJreNodeL, archStr, platStr, jreVerSpec)
		if tmpJreNode == None:
			# Let the user know that a compatible JRE was not found - thus no static release will be made.
			print('[Warning] No compatible JRE ({}) is available for the ({}) {} platform. A static release will not be provided for the platform.'.format(jreVerSpec, archStr, platStr.capitalize()))
		else:
			distL.append((appName + '-' + version + '-jre', tmpJreNode))

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(prefix=platStr, dir=aBuildPath)

	# Create the various distributions
	for (aDistName, aJreNode) in distL:
		print('Building {} distribution: {}'.format(platStr.capitalize(), aDistName))
		# Let the user know of the JRE release we are going to build with
		if aJreNode != None:
			print('\tUtilizing JRE: ' + aJreNode.getFile())

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, aDistName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(aBuildPath, dstPath, args, aJreNode)

		# Create the tar.gz archive
		tarFile = os.path.join(aBuildPath, aDistName + '.tar.gz')
		print('\tForming tar.gz file: ' + tarFile)
		childPath = aDistName
		subprocess.check_call(["tar", "-czf", tarFile, "-C", tmpPath, childPath], stderr=subprocess.STDOUT)
		print('\tFinished building release: ' + os.path.basename(tarFile) + '\n')

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(aBuildPath, aRootPath, aArgs, aJreNode):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = aArgs.name

	# Form the app contents folder
	srcPath = os.path.join(aBuildPath, "delta")
	dstPath = os.path.join(aRootPath, "app")
	shutil.copytree(srcPath, dstPath, symlinks=True)

	# Copy libs to the app directory so they can be found at launch
	soDir = os.path.join(aRootPath, 'app', 'code', 'linux')
	for libPath in glob.iglob(os.path.join(soDir, "*.so")):
		libFileName = os.path.basename(libPath)
		srcPath = os.path.join(soDir, libFileName)
		linkPath = os.path.join(dstPath, libFileName)
		shutil.copy(srcPath, linkPath)

	# Setup the launcher contents
	dstPath = os.path.join(aRootPath, "launcher/" + deployJreDist.getAppLauncherFileName())
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(os.path.dirname(dstPath))
	shutil.copy(srcPath, dstPath);

	# Build the java component of the distribution
	if aArgs.javaCode != None:
		# Form the executable bash script
		dstPath = os.path.join(aRootPath, 'run' + appName)
		buildBashScript(dstPath, aArgs, aJreNode)

	# Unpack the JRE and set up the JRE tree
	if aJreNode != None:
		jreUtils.unpackAndRenameToStandard(aJreNode, aRootPath)


def buildBashScript(aDestFile, aArgs, aJreNode):
	# Form the jvmArgStr but strip away the -Xmx* component if it is specified
	# since the JVM maxMem is dynamically configurable (via DistMaker)
	maxMem = None
	jvmArgsStr = ''
	for aStr in aArgs.jvmArgs:
		if aStr.startswith('-Xmx'):
			maxMem = aStr[4:]
		else:
			jvmArgsStr += aStr + ' '

	f = open(aDestFile, 'wb')
#	f.write('#!/bin/bash\n')
	f.write('#!/usr/bin/env bash\n')

	f.write('# Do not remove the opening or closing brackets: {}. This enables safe inline\n')
	f.write('# mutations to this script while it is running\n')
	f.write('{    # Do not remove this bracket! \n\n')

	f.write('# Define where the Java executable is located\n')
	if aJreNode == None:
		f.write('javaExe=java\n\n')
	else:
		jrePath = jreUtils.getBasePathFor(aJreNode)
		f.write('javaExe=../' + jrePath + '/bin/java\n\n')

	f.write('# Define the maximum memory to allow the application to utilize\n')
	if maxMem == None:
		f.write('#maxMem=512m # Uncomment out this line to change from defaults.\n\n')
	else:
		f.write('maxMem=' + maxMem + '\n\n')

	f.write('# Get the installation path\n')
	f.write('# We support the Linux / Macosx variants explicitly and then default back to Linux\n')
	f.write('if [ "$(uname -s)" == "Darwin" ]; then\n')
	f.write('  # Macosx platform: We assume the coreutils package has been installed...\n')
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
	os.chmod(aDestFile, 00755)


def checkSystemEnvironment():
	"""Checks to ensure that all system application / environment variables needed to build a Linux distribution are installed
	and properly configured. Returns False if the system environment is insufficient"""
	return True
