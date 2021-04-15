#! /usr/bin/env python

from __future__ import print_function
import argparse
import collections
import getpass
import glob
import math
import os
import shutil
import signal
import subprocess
import sys
from collections import OrderedDict

import jreUtils
import logUtils
import miscUtils
from logUtils import errPrintln, regPrintln
from miscUtils import ErrorDM


def getAppLauncherSourceFile():
	"""Returns the source appLauncher.jar file. This file is located in ~/template/appLauncher.jar"""
	installPath = miscUtils.getInstallRoot()
	installPath = os.path.dirname(installPath)
	retFile = os.path.join(installPath, 'template/appLauncher.jar')
	return retFile


def getAppLauncherFileName():
	"""Returns the formal file name that the appLauncher.jar should be refered to as. The returned
	value will contain just the file name with out any folder paths. The returned file name will
	 be formatted: appLauncher-<version>.jar"""
	version = getAppLauncherVersion()
	retFileName = 'appLauncher-' + version + '.jar'
	return retFileName


def getAppLauncherVersion():
	"""Returns the version of the the appLauncher.jar that is used by this DistMaker release."""
	# Check for appLauncher.jar prerequisite
	jarFile = getAppLauncherSourceFile()
	if os.path.exists(jarFile) == False:
		errPrintln('\tThis installation of DistMaker appears to be broken. Please ensure there is an appLauncher.jar file in the template folder.')
		errPrintln('\tFile does not exist: ' + jarFile)
		exit(-1)

	try:
		exeCmd = ['java', '-cp', jarFile, 'appLauncher.AppLauncher', '--version']
		output = subprocess.check_output(exeCmd).decode('utf-8')
		version = output.split()[1]
		return version
	except Exception as aExp:
		errPrintln('\tThis installation of DistMaker appears to be broken. Failed to determine the AppLauncher version.')
		errPrintln(str(aExp))
		exit(-1)


def addAppLauncherRelease(aRootPath):
	""" Adds the appLauncher.jar file to a well defined location under the deploy tree.
	The appLauncher.jar file will be stored under ~/deploy/launcher/. The appLauncher.jar is
	responsible for launching a DistMaker enabled application. This jar file will typically
	only be updated to support JRE changes that are not compatible with prior JRE releases.
	"""
	# Retrive the src appLauncher.jar
	srcFile = getAppLauncherSourceFile()

	# Ensure that the AppLauncher deployed location exists
	deployPath = os.path.join(aRootPath, 'launcher')
	if os.path.isdir(deployPath) == False:
		os.makedirs(deployPath, 0o755)

	# Determine the file name of the deployed appLauncher.jar file
	dstFileName = getAppLauncherFileName()
	version = getAppLauncherVersion()

	# Bail if the deployed appLauncher.jar already exists
	dstFile = os.path.join(deployPath, dstFileName)
	if os.path.isfile(dstFile) == True:
		return

	# Create the appCatalog file
	catFile = os.path.join(deployPath, 'appCatalog.txt')
	if os.path.isfile(catFile) == False:
		f = open(catFile, 'w')
		f.write('name' + ',' + 'AppLauncher' + '\n')
		f.write('digest' + ',' + 'sha256' + '\n\n')
		f.close()
		os.chmod(catFile, 0o644)

	# Updated the appCatalog file
	f = open(catFile, 'a')
	digestStr = miscUtils.computeDigestForFile(srcFile, 'sha256')
	stat = os.stat(srcFile)
	fileLen = stat.st_size
	f.write("F,{},{},{},{}\n".format(digestStr, fileLen, dstFileName, version))
#	f.write('\n')
	f.close()

	# Copy the src appLauncher.jar file to it's deployed location
	shutil.copy2(srcFile, dstFile)
	os.chmod(dstFile, 0o644)


def addRelease(aRootPath, aJreNodeL, aVerStr):
	# Normalize the JVM version for consistency
	aVerStr = jreUtils.normalizeJvmVerStr(aVerStr)

	# Check to see if the deployed location already exists
	installPath = os.path.join(aRootPath, 'jre')
	if os.path.isdir(installPath) == False:
		regPrintln('A JRE has never been deployed to the root location: ' + aRootPath)
		regPrintln('Create a new release of the JRE at the specified location?')
		input = raw_input('--> ').upper()
		if input != 'Y' and input != 'YES':
			regPrintln('Release will not be made for JRE version: ' + aVerStr)
			exit()

		# Build the deployed location
		os.makedirs(installPath, 0o755)

	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, aVerStr)
	if os.path.isdir(versionPath) == True:
		regPrintln('JREs with version, ' + aVerStr + ', has already been deployed.')
		regPrintln('\tThe JREs have already been deployed.')
		exit()

	# Update the version info
	addReleaseInfo(installPath, aJreNodeL, aVerStr)
	addAppLauncherRelease(aRootPath)
	regPrintln('JRE ({}) has been deployed to location: {}'.format(aVerStr, aRootPath))


def addReleaseInfo(aInstallPath, aJreNodeL, aVerStr):
	# Var that holds the last recorded exit (distmaker) command
	# By default assume the command has not been set
	exitVerDM = None

	# Create the jreCatalogfile
	catFile = os.path.join(aInstallPath, 'jreCatalog.txt')
	if os.path.isfile(catFile) == False:
		f = open(catFile, 'w')
		f.write('name' + ',' + 'JRE' + '\n')
		f.write('digest' + ',' + 'sha256' + '\n\n')
		# Note new JRE catalogs require DistMaker versions 0.55 or later
		f.write('exit,DistMaker,0.55' + '\n\n')
		f.close()
		os.chmod(catFile, 0o644)

		exitVerDM = [0, 55]
	# Determine the last exit,DistMaker instruction specified
	else:
		f = open(catFile, 'r')
		for line in f:
			tokens = line[:-1].split(',');
			# Record the (exit) version of interest
			if len(tokens) == 3 and tokens[0] == 'exit' and tokens[1] == 'DistMaker':
				try:
					exitVerDM = jreUtils.verStrToVerArr(tokens[2])
				except:
					exitVerDM = None
		f.close()

	# Locate the list of JREs with a matching version
	matchJreNodeL = jreUtils.getJreNodesForVerStr(aJreNodeL, aVerStr)
	if len(matchJreNodeL) == 0:
		raise ErrorDM('No JREs were located for the version: ' + aVerStr)

	# Determine if we need to record an exit instruction. An exit instruction is needed if the
	# the catalog was built with an old version of DistMaker. Old versions of DistMaker do
	# not specify exit instructions. If none is specified - ensure one is added.
	needExitInstr = False
	if exitVerDM == None:
		needExitInstr = True

	# Updated the jreCatalogfile
	f = open(catFile, 'a')
	# Write the exit info to stop legacy DistMakers from processing further
	if needExitInstr == True:
		f.write('exit,DistMaker,0.48\n\n')
	# Write out the JRE release info
	f.write("jre,{}\n".format(aVerStr))
	if needExitInstr == True:
		f.write("require,AppLauncher,0.1,0.2\n")
	for aJreNode in matchJreNodeL:
		tmpFile = aJreNode.getFile()
		stat = os.stat(tmpFile)
		digestStr = miscUtils.computeDigestForFile(tmpFile, 'sha256')
		fileLen = stat.st_size
		archStr = aJreNode.getArchitecture();
		platStr = aJreNode.getPlatform()
		if exitVerDM != None and exitVerDM > [0, 54]:
			f.write("F,{},{},{},{},{}\n".format(archStr, platStr, os.path.basename(tmpFile), digestStr, fileLen))
		elif exitVerDM != None:
			f.write("F,{},{},{},{}\n".format(digestStr, fileLen, platStr, os.path.basename(tmpFile)))
		else:
			f.write("F,{},{},{}\n".format(digestStr, fileLen, os.path.basename(tmpFile)))
	f.write('\n')
	f.close()

	destPath = os.path.join(aInstallPath, aVerStr)
	os.makedirs(destPath, 0o755)

	# Copy over the JRE files to the proper path
	for aJreNode in matchJreNodeL:
		tmpFile = aJreNode.getFile()
		shutil.copy2(tmpFile, destPath)
		destFile = os.path.join(destPath, os.path.basename(tmpFile))
		os.chmod(destFile, 0o644)


def delRelease(aRootPath, aVerStr):
	# Normalize the JVM version for consistency
	aVerStr = jreUtils.normalizeJvmVerStr(aVerStr)

	# Check to see if the deployed location already exists
	installPath = os.path.join(aRootPath, 'jre')
	if os.path.isdir(installPath) == False:
		regPrintln('A JRE has never been deployed to the root location: ' + aRootPath)
		regPrintln('There are no JRE releases to remove. ')
		exit()

	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, aVerStr)
	if os.path.isdir(versionPath) == False:
		regPrintln('JREs with version, ' + aVerStr + ', has not been deployed.')
		regPrintln('There is nothing to remove.')
		exit()

	# Update the version info
	delReleaseInfo(installPath, aVerStr)

	# Remove the release from the deployed location
	shutil.rmtree(versionPath)
	regPrintln('JRE ({}) has been removed from location: {}'.format(aVerStr, aRootPath))


def delReleaseInfo(aInstallPath, aVerStr):
	# Bail if the jreCatalogfile does not exist
	catFile = os.path.join(aInstallPath, 'jreCatalog.txt')
	if os.path.isfile(catFile) == False:
		errPrintln('Failed to locate deployment catalog file: ' + catFile)
		errPrintln('Aborting removal action for version: ' + aVerStr)
		exit()

	# Read the file
	inputLineL = []
	isDeleteMode = False
	f = open(catFile, 'r')
	for line in f:
		tokens = line[:-1].split(',', 1);
		# Switch to deleteMode when we find a matching JRE release
		if len(tokens) == 2 and tokens[0] == 'jre' and tokens[1] == aVerStr:
			isDeleteMode = True
			continue
		# Switch out of deleteMode when we find a different JRE release
		elif len(tokens) == 2 and tokens[0] == 'jre' and tokens[1] != aVerStr:
			isDeleteMode = False
		# Skip over the input line if we are in deleteMode
		elif isDeleteMode == True:
			continue

		# Save off the input line
		inputLineL.append(line)
	f.close()

	# Write the updated file
	f = open(catFile, 'w')
	for aLine in inputLineL:
		f.write(aLine)
	f.close()


def showReleaseInfo(aRootPath, aJreNodeL):
	"""This action will display information on the deployed / undeployed JREs to stdout."""
	# Header section
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	regPrintln('Install Path: ' + os.path.abspath(appInstallRoot))
	regPrintln(' Deploy Root: ' + aRootPath + '\n')

	# Validate the (deploy) root location
	if os.path.exists(aRootPath) == False:
		errPrintln('The specified deployRoot does not exits.')
		exit()
	if os.path.isdir(aRootPath) == False:
		errPrintln('The specified deployRoot does not appear to be a valid folder.')
		exit()

	# Check to see if the jre folder exists in the deployRoot
	installPath = os.path.join(aRootPath, 'jre')
	if os.path.isdir(installPath) == False:
		errPrintln('The specified deployRoot does not have any deployed JREs...')
		exit();

	# Form 2 dictionaries:
	# [1] File name (excluding path) to corresponding JreNode
	# [2] JRE version to corresponding JreNodes
	nameD = OrderedDict()
	verD = OrderedDict()
	for aJreNode in aJreNodeL:
		tmpFileName = os.path.basename(aJreNode.getFile())
		nameD[tmpFileName] = aJreNode

		tmpVerArr = aJreNode.getVersion()
		tmpVerStr = jreUtils.verArrToVerStr(tmpVerArr)
		verD.setdefault(tmpVerStr, [])
		verD[tmpVerStr].append(aJreNode)

	# Get the list of available (installable) JREs
	availablePathL = []
	for aVer in sorted(verD.keys()):
		# Skip to next if this version has already been installed
		versionPath = os.path.join(installPath, aVer)
		if os.path.isdir(versionPath) == True:
			continue
		availablePathL.append(aVer)

	# Show the list of available (installable) JREs
	regPrintln('Available JREs:')
	if len(availablePathL) == 0:
		regPrintln('\t\tThere are no installable JREs.\n')
	for aVer in availablePathL:
		regPrintln('\tVersion: {}'.format(aVer))
		for aJreNode in verD[aVer]:
			archStr = aJreNode.getArchitecture();
			platStr = aJreNode.getPlatform()
			pathStr = aJreNode.getFile()
			regPrintln('\t\t{}  {:<7}  {}'.format(archStr, platStr, pathStr))
		regPrintln('')

	# Get the list of all installed (version) folders
	installedPathL = []
	searchPath = installPath + '/*'
	for aFile in glob.glob(searchPath):
		if os.path.isdir(aFile) == False:
			continue
		try:
			verStr = os.path.basename(aFile)
		except:
			continue
		installedPathL.append(verStr)

	# Show the list of installed JREs
	regPrintln('Installed JREs:')
	if len(installedPathL) == 0:
		regPrintln('\t\tThere are no installed JREs.')
	for aVer in sorted(installedPathL):
		regPrintln('\tVersion: {}'.format(aVer))

		# Show all of the JREs in the specified (version) folder
		for aFile in sorted(glob.glob(os.path.join(installPath, aVer) + '/*')):
			tmpFileName = os.path.basename(aFile)

			# Bail if the file name does not correspond to a JRE
			tmpJreNode = nameD.get(tmpFileName, None)
			if tmpJreNode == None:
				errPrintln('\t\tJRE file is not in the JRE catalog. File: ' + aFile)
				continue

			archStr = tmpJreNode.getArchitecture();
			platStr = tmpJreNode.getPlatform()
			pathStr = tmpJreNode.getFile()
			regPrintln('\t\t{}  {:<7}  {}'.format(archStr, platStr, pathStr))
		regPrintln('')



if __name__ == "__main__":
	# Require python version 2.7 or later
	targVer = (2, 7)
	miscUtils.requirePythonVersion(targVer)

	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, miscUtils.handleSignal)

	# Retrieve the location of the scriptPath
	scriptPath = os.path.realpath(__file__)
	scriptPath = os.path.dirname(scriptPath)

	# Set up the argument parser
	tmpDescr = 'Utility that allow JREs to be deployed or removed from the specified deployRoot. The deployRoot is the '
	tmpDescr += 'top level deployment location. This location is typically made available via a public web server. The ';
	tmpDescr +=  'deployRoot should NOT refer to the child jre folder but rather the top level folder!'
	parser = argparse.ArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@', description=tmpDescr)
	parser.add_argument('--help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('--jreCatalog', help='A JRE catalog file. This file provides the listing of available JREs for DistMaker to utilize.')
	parser.add_argument('--deploy', metavar='version', help='Deploy the specified JRE distribution to the deployRoot.', action='store', default=None)
	parser.add_argument('--remove', metavar='version', help='Remove the specified JRE distribution from the deployRoot.', action='store', default=None)
	parser.add_argument('--status', help='Display stats of all deployed/undeployed JREs relative to the deployRoot.', action='store_true', default=False)
	parser.add_argument('deployRoot', help='Top level folder to the deployment root.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv or '--help' in argv:
		parser.print_help()
		exit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	# Load the JRE catalog
	jreCatalog = args.jreCatalog

	errMsg = None
	if jreCatalog == None:
		errMsg = 'A JRE catalog must be specified! Please specify --jreCatalog'
	elif os.path.exists(jreCatalog) == False:
		errMsg = 'The specified JRE catalog does not exist! File: ' + jreCatalog
	elif os.path.isfile(jreCatalog) == False:
		errMsg = 'The specified JRE catalog is not a valid file! File: ' + jreCatalog
	if errMsg != None:
		errPrintln(errMsg + '\n')
		exit()

	jreNodeL = jreUtils.loadJreCatalog(jreCatalog)
	if len(jreNodeL) == 0:
		errPrintln('Failed to load any JREs from the JRE catalog.')
		errPrintln('\tA valid populated JRE catalog must be specified!')
		errPrintln('\tJRE Catalog specified: {}\n'.format(jreCatalog))
		exit()

	# Execute the approriate action
	rootPath = args.deployRoot

	if args.status == True:
		showReleaseInfo(rootPath, jreNodeL)
		exit()
	elif args.deploy != None:
		# Deploy the specified JRE
		version = args.deploy
		try:
			addRelease(rootPath, jreNodeL, version)
		except ErrorDM as aExp:
			errPrintln('Failed to deploy JREs with version: ' + version)
			errPrintln('\t' + aExp.message)
	elif args.remove != None:
		# Remove the specified JRE
		version = args.remove
		try:
			delRelease(rootPath, version)
		except ErrorDM as aExp:
			errPrintln('Failed to deploy JREs with version: ' + version)
			errPrintln('\t' + aExp.message)
	else:
		regPrintln('Please specify one of the valid actions: [--deploy, --remove, --status]')

