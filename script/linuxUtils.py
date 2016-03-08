#! /usr/bin/env python

import argparse
import copy
import getpass
import math
import os
import shutil
import signal
import subprocess
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
		jreRelease = jreUtils.getDefaultJreRelease('linux')
	# Let the user know if the 'user' specified JRE is not available and locate an alternative
	elif jreUtils.isJreAvailable('linux', jreRelease) == False:
		print('[Warning] User specified JRE (' + jreRelease + ') is not available for Linux platform. Searching for alternative...')
		jreRelease = jreUtils.getDefaultJreRelease('linux')
	args.jreRelease = jreRelease

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(dir=buildPath)

	# Form the list of distributions to build
	distList = [(appName + '-' + version, False)]
	if jreUtils.isJreAvailable('linux', jreRelease) == True:
		distList.append((appName + '-' + version + '-jre', True))

	# Create the various Linux distributions
	for (distName, isStaticRelease) in distList:
		print('Building Linux distribution: ' + distName)
		# Let the user know of the JRE release we are going to build with
		if isStaticRelease == True:
			print('\tUtilizing jreRelease: ' + jreRelease)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, distName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(buildPath, dstPath, args, isStaticRelease)

		# Create the tar.gz archive
		tarFile = os.path.join(buildPath, distName + '.tar.gz')
		print('\tForming tar.gz file: ' + tarFile)
		childPath = distName
#		print '[' + getCurrTimeStr() + '] Building tar archive: ' + tarFile
		subprocess.check_call(["tar", "-czf", tarFile, "-C", tmpPath, childPath], stderr=subprocess.STDOUT)
#		subprocess.check_call(["gzip", tarFile], stderr=subprocess.STDOUT)
		print('\tFinished building release: ' + os.path.basename(tarFile))

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

	# Copy libs to the app directory so they can be found at launch
	soDir = os.path.join(rootPath, 'app', 'code', 'linux')
	for libPath in glob.iglob(os.path.join(soDir, "*.so")):
		libFileName = os.path.basename(libPath)
		srcPath = os.path.join(soDir, libFileName)
		linkPath = os.path.join(dstPath, libFileName)
		shutil.copy(srcPath, linkPath)



	# Setup the launcher contents
	exePath = os.path.join(rootPath, "launcher")
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(exePath)
	shutil.copy(srcPath, exePath);

	# Build the java component of the distribution
	if args.javaCode != None:
		# Copy over the jre
		if isStaticRelease == True:
			srcPath = os.path.join(appInstallRoot, 'jre', 'linux', jreRelease)
			dstPath = os.path.join(rootPath, os.path.basename(srcPath))
			shutil.copytree(srcPath, dstPath, symlinks=True)

		# Form the executable bash script
		dstPath = os.path.join(rootPath, 'run' + appName)
		buildBashScript(dstPath, args, isStaticRelease)


def buildBashScript(destFile, args, isStaticRelease):
	jvmArgsStr = ''
	for aStr in args.jvmArgs:
		if len(aStr) > 2 and aStr[0:1] == '\\':
			aStr = aStr[1:]
		jvmArgsStr += aStr + ' '

	f = open(destFile, 'wb')
	f.write('#!/bin/bash\n')
	f.write('# Do not remove the opening or closing brackets: {}. This enables safe inline\n')
	f.write('# mutations to this script while it is running\n')
	f.write('{    # Do not remove this bracket! \n\n')
	
	f.write('# Define the maximum memory to allow the application to utilize\n')
	f.write('#maxMem=512m # Uncomment out this line to change from defaults.\n\n')

	f.write('# Get the instalation path\n')
	f.write('installPath=$(readlink -f "$BASH_SOURCE")\n')
	f.write('installPath=$(dirname "$installPath")\n\n')

	f.write('# Change the working directory to the app folder in the installation path\n')
	f.write('cd "$installPath"/app\n\n')

	f.write('# Setup the xmxStr to define the maximum JVM memory.\n')
	f.write('if [ -z ${maxMem+x} ]; then\n')
	f.write('  xmxStr=""\n')
	f.write('else\n')
	f.write('  xmxStr=\'-Xmx\'$maxMem\n')
	f.write('fi\n\n')

	exeCmd = 'java ' + jvmArgsStr + '$xmxStr '
	if isStaticRelease == True:
		exeCmd = '../' + args.jreRelease + '/bin/java ' + jvmArgsStr + '$xmxStr '
	exeCmd = exeCmd + '-Djava.system.class.loader=appLauncher.RootClassLoader -cp ../launcher/appLauncher.jar appLauncher.AppLauncher app.cfg '
	f.write('# Run the application\n')
	f.write(exeCmd + '\n\n')
	
	f.write('exit # Do not remove this exit! (just before the bracket)\n')
	f.write('}    # Do not remove this bracket! \n\n')

	f.close()

	# Make the script executable
	os.chmod(destFile, 00755)

