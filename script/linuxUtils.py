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
# The global var jre is a pointer to a full jre release for a Linux system
# There should be a release located at <installpath>/jre/linux/jreRelease
jreRelease = 'jre1.7.0_15'



def buildRelease(args, buildPath):
	# Retrieve vars of interest
	appName = args.name
	version = args.version

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(dir=buildPath)
	
	# Form the list of distributions to build
	distList = [(appName + '-' + version, False)]
	if miscUtils.isJreAvailable('linux', jreRelease) == True:
		distList.append((appName + '-' + version + '-jre', True))

	# Create the various Linux distributions	
	for (distName, isStaticRelease) in distList:
		print('Building Linux distribution: ' + distName)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, distName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(dstPath, args, isStaticRelease)

		# Create the tar.gz archive
		tarFile = os.path.join(buildPath, distName + '.tar.gz')
		childPath = distName
		#	print '[' + getCurrTimeStr() + '] Building tar archive: ' + tarFile
		subprocess.check_call(["tar", "-czf", tarFile, "-C", tmpPath, childPath], stderr=subprocess.STDOUT)
#		subprocess.check_call(["gzip", tarFile], stderr=subprocess.STDOUT)

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
			srcPath = os.path.join(appInstallRoot, 'jre', 'linux', jreRelease)
			dstPath = os.path.join(rootPath, os.path.basename(srcPath))
			shutil.copytree(srcPath, dstPath, symlinks=True)

		# Form the executable bash script
		dstPath = os.path.join(rootPath, 'run' + appName)
		buildBashScript(dstPath, args, isStaticRelease)



def buildBashScript(destFile, args, isStaticRelease):
	classPathStr = ''
	for aStr in args.classPath:
		classPathStr += 'java/' + aStr + ':'
	if len(classPathStr) > 0:
		classPathStr = classPathStr[0:-1]

	jvmArgsStr = ''
	for aStr in args.jvmArgs:
		if len(aStr) > 2 and aStr[0:1] == '\\':
			aStr = aStr[1:]
		jvmArgsStr += aStr + ' '

	appArgsStr = ''
	for aStr in args.appArgs:
		appArgsStr += ' ' + aStr

	f = open(destFile, 'wb')
	f.write('# Get the instalation path\n')
	f.write('installPath=$(readlink -f "$BASH_SOURCE")\n')
	f.write('installPath=$(dirname "$installPath")\n\n')

	f.write('# Change the working directory to the installation path\n')
	f.write('cd "$installPath"\n\n')

	exeCmd = 'java ' + jvmArgsStr + ' -cp ' + classPathStr + ' ' + args.mainClass + appArgsStr
	if isStaticRelease == True:
		exeCmd = jreRelease + '/bin/java ' + jvmArgsStr + ' -cp ' + classPathStr + ' ' + args.mainClass + appArgsStr

	f.write('# Run the application\n')
	f.write(exeCmd + '\n')
	f.write('\n')

	f.close()

	# Make the script executable
	os.chmod(destFile, 00755)

