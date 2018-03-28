#! /usr/bin/env python

from __future__ import print_function
import argparse
import getpass
import glob
import math
import os
import shutil
import signal
import subprocess
import sys

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
		errPrintln('This installation of DistMaker appears to be broken. Please ensure there is an appLauncher.jar file in the template folder.', indent=1)
		errPrintln('File does not exist: ' + jarFile, indent=1)
		exit(-1)

	try:
		exeCmd = ['java', '-cp', jarFile, 'appLauncher.AppLauncher', '--version']
		output = subprocess.check_output(exeCmd).decode('utf-8')
		version = output.split()[1]
		return version
	except Exception as aExp:
		errPrintln('This installation of DistMaker appears to be broken. Failed to determine the AppLauncher version.', indent=1)
		errPrintln(str(aExp))
		exit(-1)


def addAppLauncherRelease():
	""" Adds the appLauncher.jar file to a well defined location under the deploy tree.
	The appLauncher.jar file will be stored under ~/deploy/launcher/. The appLauncher.jar is
	responsible for launching a DistMaker enabled application. This jar file will typically
	only be updated to support JRE changes that are not compatible with prior JRE releases.
	"""
	# Retrive the src appLauncher.jar
	srcFile = getAppLauncherSourceFile()

	# Ensure that the AppLauncher deployed location exists
	deployPath = os.path.join(rootPath, 'launcher')
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


def addRelease(version):
	# Normalize the JVM version for consistency
	version = jreUtils.normalizeJvmVerStr(version)

	# Check to see if the deployed location already exists
	installPath = os.path.join(rootPath, 'jre')
	if os.path.isdir(installPath) == False:
		print('A JRE has never been deployed to the root location: ' + args.deployRoot)
		print('Create a new release of the JRE at the specified location?')
		input = raw_input('--> ').upper()
		if input != 'Y' and input != 'YES':
			print('Release will not be made for JRE version: ' + version)
			exit()

		# Build the deployed location
		os.makedirs(installPath, 0o755)

	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, version)
	if os.path.isdir(versionPath) == True:
		print('JREs with version, ' + version + ', has already been deployed.')
		print('  The JREs have already been deployed.')
		exit()

	# Update the version info
	addReleaseInfo(installPath, version)
	addAppLauncherRelease()
	print('JRE ({}) has been deployed to location: {}'.format(version, args.deployRoot))


def addReleaseInfo(installPath, verStr):
	# Var that holds the last recorded exit (distmaker) command
	# By default assume the command has not been set
	exitVerDM = None

	# Create the jreCatalogfile
	catFile = os.path.join(installPath, 'jreCatalog.txt')
	if os.path.isfile(catFile) == False:
		f = open(catFile, 'w')
		f.write('name' + ',' + 'JRE' + '\n')
		f.write('digest' + ',' + 'sha256' + '\n\n')
		f.close()
		os.chmod(catFile, 0o644)
	# Determine the last exit,DistMaker instruction specified
	else:
		f = open(catFile, 'r')
		for line in f:
			tokens = line[:-1].split(',');
			# Record the (exit) version of interest
			if len(tokens) == 3 and tokens[0] == 'exit' and tokens[1] == 'DistMaker':
				exitVerDM = tokens[2]
		f.close()

	# Locate the list of JRE files with a matching verStr
	jreFiles = jreUtils.getJreTarGzFilesForVerStr(verStr)
	if len(jreFiles) == 0:
		raise ErrorDM('No JREs were located for the version: ' + verStr)

	# Determine if the user is deploying a legacy JREs. Legacy JRE versions match the pattern 1.*
	isLegacyJre = verStr.startswith('1.') == True

	# Let the user know that legacy JREs can NOT be deployed once an exit,DistMaker instruction
	# has been found. Basically once non-legacy JREs have been deployed then legacy JREs are no
	# longer allowed.
	if isLegacyJre == True and exitVerDM != None:
		logUtils.errPrintln('Legacy JREs can not be deployed once any non legacy JRE has been deployed.')
		logUtils.errPrintln('The specified JRE ({}) is considered legacy.'.format(verStr), indent=1)
		exit()

	# Determine if we need to record an exit instruction. An exit instruction is needed if the
	# deployed JRE is non-legacy. Legacy DistMaker apps will not be able to handle non-legacy JREs.
	needExitInstr = False
	if isLegacyJre == False:
		try:
			tokenArr = exitVerDM.split('.')
			majorVerAL = int(tokenArr[0])
			minorVerAL = int(tokenArr[1])
			if majorVerAL == 0 and minorVerAL < 1:
				needExitInstr = True
		except:
			needExitInstr = True
			pass

	# Updated the jreCatalogfile
	f = open(catFile, 'a')
	# Write the exit info to stop legacy DistMakers from processing further
	if needExitInstr == True:
		f.write('exit,DistMaker,0.48\n\n')
	# Write out the JRE release info
	f.write("jre,{}\n".format(verStr))
	if needExitInstr == True:
		f.write("require,AppLauncher,0.1,0.2\n")
	for aFile in jreFiles:
		stat = os.stat(aFile)
		digestStr = miscUtils.computeDigestForFile(aFile, 'sha256')
		fileLen = stat.st_size
		platformStr = jreUtils.getPlatformForJreTarGzFile(aFile)
		if isLegacyJre == False:
			f.write("F,{},{},{},{}\n".format(digestStr, fileLen, platformStr, os.path.basename(aFile)))
		else:
			f.write("F,{},{},{}\n".format(digestStr, fileLen, os.path.basename(aFile)))
	f.write('\n')
	f.close()

	destPath = os.path.join(installPath, verStr)
	os.makedirs(destPath, 0o755)

	# Copy over the JRE files to the proper path
	for aFile in jreFiles:
		shutil.copy2(aFile, destPath)
		destFile = os.path.join(destPath, aFile)
		os.chmod(destFile, 0o644)


def delRelease(verStr):
	# Normalize the JVM version for consistency
	verStr = jreUtils.normalizeJvmVerStr(verStr)

	# Check to see if the deployed location already exists
	installPath = os.path.join(rootPath, 'jre')
	if os.path.isdir(installPath) == False:
		print('A JRE has never been deployed to the root location: ' + args.deployRoot)
		print('There are no JRE releases to remove. ')
		exit()

	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, verStr)
	if os.path.isdir(versionPath) == False:
		print('JREs with version, ' + verStr + ', has not been deployed.')
		print('  There is nothing to remove.')
		exit()

	# Update the version info
	delReleaseInfo(installPath, verStr)

	# Remove the release from the deployed location
	shutil.rmtree(versionPath)


def delReleaseInfo(installPath, version):
	# Bail if the jreCatalogfile does not exist
	catFile = os.path.join(installPath, 'jreCatalog.txt')
	if os.path.isfile(catFile) == False:
		print('Failed to locate deployment catalog file: ' + catFile)
		print('Aborting removal action for version: ' + version)
		exit()

	# Read the file
	inputLineL = []
	isDeleteMode = False
	f = open(catFile, 'r')
	for line in f:
		tokens = line[:-1].split(',', 1);
		# Switch to deleteMode when we find a matching JRE release
		if len(tokens) == 2 and tokens[0] == 'jre' and tokens[1] == version:
			isDeleteMode = True
			continue
		# Switch out of deleteMode when we find a different JRE release
		elif len(tokens) == 2 and tokens[0] == 'jre' and tokens[1] != version:
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


def showReleaseInfo():
	"""This action will display information on the deployed / undeployed JREs to stdout."""
	# Header section
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	logUtils.regPrintln('Install Path: ' + os.path.abspath(appInstallRoot))
	logUtils.regPrintln(' Deploy Root: ' + rootPath + '\n')

	# Validate that the deployRoot location
	if os.path.exists(rootPath) == False:
		logUtils.errPrintln('The specified deployRoot does not exits.')
		exit()
	if os.path.isdir(rootPath) == False:
		logUtils.errPrintln('The specified deployRoot does not appear to be a valid folder.')
		exit()

	# Check to see if the jre folder exists in the deployRoot
	installPath = os.path.join(rootPath, 'jre')
	if os.path.isdir(installPath) == False:
		logUtils.errPrintln('The specified deployRoot does not have any deployed JREs...')
		exit();

	# Form a dictionary of all the JRE version to corresponding (JRE) tar.gz files
	# This dictionary will include all the JREs that are known by this release of DistMaker
	searchName = "jre-*.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', searchName)
	fullD = dict()
	for aFile in glob.glob(searchPath):
		# Skip to next if aFile is not a valid JRE tar.gz file
		aFile = os.path.basename(aFile)
		tmpVerArr = jreUtils.getJreTarGzVerArr(aFile)
		if tmpVerArr == None:
			continue

		tmpVerStr =  jreUtils.verArrToVerStr(tmpVerArr)
		fullD.setdefault(tmpVerStr, [])
		fullD[tmpVerStr].append(aFile)

	# Get the list of available (installable) JREs
	availablePathL = []
	for aVer in sorted(fullD.keys()):
		# Skip to next if this version has already been installed
		versionPath = os.path.join(installPath, aVer)
		if os.path.isdir(versionPath) == True:
			continue
		availablePathL.append(aVer)

	# Show the list of available (installable) JREs
	print('Available JREs:')
	if len(availablePathL) == 0:
		logUtils.regPrintln('There are no installable JREs.', indent=2)
	for aVer in availablePathL:
		logUtils.regPrintln('Version: {}'.format(aVer), indent=1)
		for aFile in sorted(fullD[aVer]):
			logUtils.regPrintln('{}'.format(aFile), indent=2)
	logUtils.regPrintln('')

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
	print('Installed JREs:')
	if len(installedPathL) == 0:
		logUtils.regPrintln('There are no installed JREs.', indent=2)
	for aVer in sorted(installedPathL):
		logUtils.regPrintln('Version: {}'.format(aVer), indent=1)
		for aFile in sorted(glob.glob(os.path.join(installPath, aVer) + '/*')):
			tmpVerArr = jreUtils.getJreTarGzVerArr(aFile)
			if tmpVerArr == None:
				continue;
			aFile = os.path.basename(aFile)
			logUtils.regPrintln('{}'.format(aFile), indent=2)
	logUtils.regPrintln('')



if __name__ == "__main__":
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
	parser.add_argument('-help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('-deploy', metavar='version', help='Deploy the specified JRE distribution to the deployRoot.', action='store', default=None)
	parser.add_argument('-remove', metavar='version', help='Remove the specified JRE distribution from the deployRoot.', action='store', default=None)
	parser.add_argument('-status', help='Display stats of all deployed/undeployed JREs relative to the deployRoot.', action='store_true', default=False)
	parser.add_argument('deployRoot', help='Top level folder to the deployment root.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv:
		parser.print_help()
		exit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	# Process the args
	rootPath = args.deployRoot

	if args.status == True:
		showReleaseInfo()
		exit()
	elif args.deploy != None:
		# Deploy the specified JRE
		version = args.deploy
		try:
			addRelease(version)
		except ErrorDM as aExp:
			print('Failed to deploy JREs with version: ' + version)
			print('  ' + aExp.message, file=sys.stderr)
	elif args.remove != None:
		# Remove the specified JRE
		version = args.remove
		try:
			delRelease(version)
		except ErrorDM as aExp:
			print('Failed to deploy JREs with version: ' + version)
			print('  ' + aExp.message, file=sys.stderr)
	else:
		print('Please specify one of the valid actions: [-deploy, -remove, -status]')

