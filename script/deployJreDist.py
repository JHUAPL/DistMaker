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

import miscUtils
import jreUtils
from miscUtils import ErrorDM


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
		os.makedirs(installPath)

	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, version)
	if os.path.isdir(versionPath) == True:
		print('JREs with version, ' + version + ', has already been deployed.')
		print('  The JREs have already been deployed.')
		exit()

	# Update the version info
	addReleaseInfo(installPath, version)
	print('JRE ({}) has been deployed to location: {}'.format(version, args.deployRoot))


def addReleaseInfo(installPath, verStr):
	verFile = os.path.join(installPath, 'jreCatalog.txt')

	# Create the release info file
	if os.path.isfile(verFile) == False:
		f = open(verFile, 'w')
		f.write('name' + ',' + 'JRE' + '\n')
		f.write('digest' + ',' + 'sha256' + '\n\n')
		f.close()

	# Locate the list of JRE files with a matching verStr
	jreFiles = jreUtils.getJreTarGzFilesForVerStr(verStr)
	if len(jreFiles) == 0:
		raise ErrorDM('No JREs were located for the version: ' + verStr)

	# Updated the release info file
	f = open(verFile, 'a')
	f.write("jre,{}\n".format(verStr))
	for aFile in jreFiles:
		stat = os.stat(aFile)
		digestStr = miscUtils.computeDigestForFile(aFile, 'sha256')
#		platformStr = jreUtils.getPlatformForJre(aFile)
#		stat = os.stat(aFile)
# 		fileLen = os.path.s
#		f.write("jre,{},{},{},{},{}\n".format(verStr, platformStr, os.path.basename(aFile), fileLen, digestStr))
 		f.write("F,{},{},{}\n".format(digestStr, stat.st_size, os.path.basename(aFile)))
	f.write('\n')
	f.close()

	destPath = os.path.join(installPath, verStr)
	os.makedirs(destPath)

	# Copy over the JRE files to the proper path
	for aFile in jreFiles:
		shutil.copy(aFile, destPath)
		destFile = os.path.join(destPath, aFile)
		os.chmod(destFile, 0644)


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
	verFile = os.path.join(installPath, 'jreCatalog.txt')

	# Bail if the release info file does not exist
	if os.path.isfile(verFile) == False:
		print('Failed to locate deployment release info file: ' + verFile)
		print('Aborting removal action for version: ' + version)
		exit()

	# Read the file
	inputLineL = []
	isDeleteMode = False
	f = open(verFile, 'r')
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
	f = open(verFile, 'w')
	for aLine in inputLineL:
		f.write(aLine)
	f.close()


if __name__ == "__main__":
	argv = sys.argv;
	argc = len(argv);

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
	for aArg in argv:
		if aArg == '-h' or aArg == '-help':
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

