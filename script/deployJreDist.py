#! /usr/bin/env python

from __future__ import print_function
import argparse
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys

import jreUtils
import logUtils
import miscUtils
from logUtils import errPrintln
from miscUtils import ErrorDM


def getAppLauncherSourceFile():
	"""Returns the source appLauncher.jar file. This file is located in ~/template/appLauncher.jar"""
	installPath = miscUtils.getInstallRoot()
	installPath = os.path.dirname(installPath)
	retFile = os.path.join(installPath, 'template/appLauncher.jar')
	return retFile


def getAppLauncherVersion():
	# Check for appLauncher.jar prerequisite
	jarFile = getAppLauncherSourceFile()
	if os.path.exists(jarFile) == False:
		errPrintln('This installation of DistMaker appears to be broken. Please ensure there is an appLauncher.jarfile in the template folder.', indent=1)
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
	version = getAppLauncherVersion()
	dstFileName = 'appLauncher-' + version + '.jar'

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


if __name__ == "__main__":
	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, miscUtils.handleSignal)

	# Retrieve the location of the scriptPath
	scriptPath = os.path.realpath(__file__)
	scriptPath = os.path.dirname(scriptPath)

	# Set up the argument parser
	parser = argparse.ArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('-help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('-remove', help='Remove the specified JRE distribution.', action='store_true', default=False)
	parser.add_argument('-version', help='The fully qualified JRE version to deploy.', required=True)
	parser.add_argument('deployRoot', help='Root location to deploy the specified JRE distribution.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv:
		parser.print_help()
		exit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	rootPath = args.deployRoot

	# Uninstall the JRE, if remove argument is specified
	version = args.version
	if args.remove == True:
		try:
			delRelease(version)
		except ErrorDM as aExp:
			print('Failed to remove JREs with version: ' + version)
			print('  ' + aExp.message, file=sys.stderr)
	else:
		try:
			addRelease(version)
		except ErrorDM as aExp:
			print('Failed to deploy JREs with version: ' + version)
			print('  ' + aExp.message, file=sys.stderr)

