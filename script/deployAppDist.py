#! /usr/bin/env python3

# Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys

def getDistInfo(aDistPath):
	appName = None
	version = None
	buildDate = None
	isLegacyJre = None

	# Process the app.cfg file
	cfgFile = os.path.join(aDistPath, 'delta', 'app.cfg')
	if os.path.isfile(cfgFile) == False:
		print('Distribution corresponding to the folder: ' + aDistPath + ' does not appear to be valid!')
		print('File does not exist: ' + cfgFile)
		print('Release will not be deployed...')
		exit()

	exeMode = None
	with open(cfgFile, mode='rt', encoding='utf-8') as tmpFO:
		for aLine in tmpFO:
			aLine = aLine[:-1]
			if aLine.startswith('-') == True:
				exeMode = aLine;
			elif exeMode == '-name' and len(aLine) > 0:
				appName = aLine
			elif exeMode == '-version' and len(aLine) > 0:
				version = aLine
			elif exeMode == '-buildDate' and len(aLine) > 0:
				buildDate = aLine

	if appName == None or version == None or buildDate == None:
		print('Distribution corresponding to the folder: ' + aDistPath + ' does not appear to be valid!')
		print('The configuration file, ' + cfgFile + ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()

	# Process the catalog.txt file
	catFile = os.path.join(aDistPath, 'delta', 'catalog.txt')
	if os.path.isfile(catFile) == False:
		print('Distribution corresponding to the folder: ' + aDistPath + ' does not appear to be valid!')
		print('File does not exist: ' + catFile)
		print('Release will not be deployed...')
		exit()

	with open(catFile, mode='rt', encoding='utf-8') as tmpFO:
		for aLine in tmpFO:
			aLine = aLine[:-1]
			tokenL = aLine.split(',')
			# Check to see if legacy JREs are allowed
			if len(tokenL) >= 2 and tokenL[0] == 'jre' and isLegacyJre == None:
				isLegacyJre = False
				if tokenL[1].strip().startswith('1.') == True:
					isLegacyJre = True

	if isLegacyJre == None:
		print('Distribution corresponding to the folder: ' + aDistPath + ' does not appear to be valid!')
		print('The catalog file, ' + catFile + ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()

	return (appName, version, buildDate, isLegacyJre)


def handleSignal(signal, frame):
		"""Signal handler, typically used to capture ctrl-c."""
		print('User aborted processing!')
		sys.exit(0)


def addReleaseInfo(aDeployPath, aAppName, aVerStr, aBuildDate, aIsLegacyJre):
	# Determine if this Application was deployed with a a legacy DistMaker release
	legacyReleaseL = []
	isLegacyRelease = False
	verFile = os.path.join(aDeployPath, 'releaseInfo.txt')
	if os.path.isfile(verFile) == True:
		# Read the legacy releases
		with open(verFile, mode='rt', encoding='utf-8') as tmpFO:
			for aLine in tmpFO:
				aLine = aLine[:-1]
				# Ignore empty lines and comments
				if len(aLine) == 0:
					continue
				if aLine.startswith('#') == True:
					continue

				tokenL = aLine.split(',')
				if len(tokenL) >= 2 and tokenL[0] == 'name':
					isLegacyRelease = True
					continue

				# Ignore legacy exit instructions
				if len(tokenL) >= 1 and tokenL[0] == 'exit':
					continue

				# Record all legacy releases
				if len(tokenL) == 2:
					legacyReleaseL += [(tokenL[0], tokenL[1])]
					continue

	# Create the appCatalog.txt file
	catFile = os.path.join(aDeployPath, 'appCatalog.txt')
	if os.path.isfile(catFile) == False:
		if isLegacyRelease == True and len(legacyReleaseL) > 0:
			with open(catFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:
				tmpFO.write('name' + ',' + aAppName + '\n\n')
				# Copy the legacy releases
				for (aLegacyVer, aLegacyDate) in legacyReleaseL:
					tmpFO.write('R,{},{}\n'.format(aLegacyVer, aLegacyDate))
					tmpFO.write('info,msg,This is a legacy release.\n')
					tmpFO.write('info,msg,\n')
					if aIsLegacyJre == True:
						tmpFO.write('info,msg,Downgrading to this version may require a mandatory upgrade (ver: ' + aVerStr + ') before further upgrades are allowed.\n\n')
					else:
						tmpFO.write('# A release should be made using a legacy JRE (1.8+) and this DistMaker release. The release notes will need to be manually.\n')
						tmpFO.write('info,msg,Downgrading to this version will require a mandatory 2-step upgrade in order to use releases made with non legacy JREs.\n\n')
		else:
			# Form the default (empty) appCatalog.txt
			with open(catFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:
				tmpFO.write('name' + ',' + aAppName + '\n\n')

		os.chmod(catFile, 0o644)

	# Updated the appCatalog.txt info file
	with open(catFile, mode='at', encoding='utf-8', newline='\n') as tmpFO:
		tmpFO.write('R,{},{}\n'.format(aVerStr, aBuildDate))
		tmpFO.write('info,msg,There are no release notes available.\n\n')

	# Update the (legacy) releaseInfo.txt file
	if isLegacyRelease == True and aIsLegacyJre == True:
		with open(verFile, mode='at', encoding='utf-8', newline='\n') as tmpFO:
			tmpFO.write(aVerStr + ',' + aBuildDate + '\n')


def delReleaseInfoLegacy(aDeployPath, aAppName, aVerStr, aBuildDate):
	verFile = os.path.join(aDeployPath, 'releaseInfo.txt')

	# Bail if the release info file does not exist
	if os.path.isfile(verFile) == False:
		return;

	# Read the file
	releaseInfo = []
	with open(verFile, mode='rt', encoding='utf-8') as tmpFO:
		for line in tmpFO:
			tokens = line[:-1].split(',', 1);
			if len(tokens) == 2 and tokens[0] == aVerStr:
				# By not adding the current record to the releaseInfo list, we are effectively removing the record
				print('Removing release record from info file. Version: ' + aVerStr)
			elif len(tokens) == 2 and tokens[0] != 'name':
				releaseInfo.append((tokens[0], tokens[1]))

	# Write the updated file
	with open(verFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:
		tmpFO.write('name' + ',' + aAppName + '\n')
		for verTup in releaseInfo:
			tmpFO.write(verTup[0] + ',' + verTup[1] + '\n')


def delReleaseInfo(aDeployPath, aAppName, aVerStr, aBuildDate):
	# Remove any legacy releases
	delReleaseInfoLegacy(aDeployPath, aAppName, aVerStr, aBuildDate)

	catFile = os.path.join(aDeployPath, 'appCatalog.txt')

	# Bail if the appCatalog.txt file does not exist
	if os.path.isfile(catFile) == False:
		print('Failed to locate deployment appCatalog file: ' + catFile)
		print('Aborting removal action for version: ' + aVerStr)
		exit()

	# Read the file (and skip over all lines found after the release we are searching for)
	isDeleteMode = False
	passLineL = []
	with open(catFile, mode='rt', encoding='utf-8') as tmpFO:
		for aLine in tmpFO:
			aLine = aLine[:-1]
			tokenL = aLine.split(',');
			# Determine when to enter / exit isDeleteMode
			if len(tokenL) == 3 and tokenL[0] == 'R' and tokenL[1] == aVerStr:
				# By not adding the current record to the releaseInfo list, we are effectively removing the record
				isDeleteMode = True
			# We exit deleteMode when see a different release or exit instruction
			elif len(tokenL) == 3 and tokenL[0] == 'R' and tokenL[1] != aVerStr:
				isDeleteMode = False
			elif len(tokenL) >= 1 and tokenL[0] == 'exit':
				isDeleteMode = False

			# Skip to next if we are in deleteMode
			if isDeleteMode == True:
				continue

			# Save off all lines when we are not in delete mode
			passLineL += [aLine]

	# Write the updated file
	with open(catFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:
		for aLine in passLineL:
			tmpFO.write(aLine + '\n')


def addRelease(aRootPath, aAppName, aVerStr, aBuildDate, aIsLegacyJre):
	# Check to see if the deployed location already exists
	deployPath = os.path.join(aRootPath, aAppName)
	if os.path.isdir(deployPath) == False:
		print('Application ' + aAppName + ' has never been deployed to the root location: ' + aRootPath)
		print('Create a new release of the application at the specified location?')
		tmpAns = input('--> ').upper()
		if tmpAns != 'Y' and tmpAns != 'YES':
			print('Release will not be made for app ' + aAppName)
			exit()

		# Build the deployed location
		os.makedirs(deployPath, 0o755)

	# Check to see if the deploy version already exists
	versionPath = os.path.join(deployPath, aVerStr)
	if os.path.isdir(versionPath) == True:
		print('Application ' + aAppName + ' with version, ' + aVerStr + ', has already been deployed.')
		print('Release will not be made for app ' + aAppName)
		exit()

	# Copy over the contents of the release folder to the deploy location
	shutil.copytree(distPath, versionPath, symlinks=True)

	# Ensure all folders and files have the proper permissions
	for root, dirs, files in os.walk(versionPath):
		for d in dirs:
			os.chmod(os.path.join(root, d), 0o755)
		for f in files:
			os.chmod(os.path.join(root, f), 0o644)
	os.chmod(versionPath, 0o755)

	# Update the version info
	addReleaseInfo(deployPath, aAppName, aVerStr, aBuildDate, aIsLegacyJre)
	print('Application {} ({}) has been deployed to location: {}'.format(aAppName, aVerStr, aRootPath))


def delRelease(aRootPath, aAppName, aVerStr, aBuildDate):
	# Check to see if the deployed location already exists
	deployPath = os.path.join(aRootPath, aAppName)
	if os.path.isdir(deployPath) == False:
		print('Application ' + aAppName + ' has never been deployed to the root location: ' + aRootPath)
		print('There are no releases to remove. ')
		exit()

	# Check to see if the deploy version already exists
	versionPath = os.path.join(deployPath, aVerStr)
	if os.path.isdir(versionPath) == False:
		print('Application ' + aAppName + ' with version, ' + aVerStr + ', has not been deployed.')
		print('Release will not be removed for app ' + aAppName)
		exit()

	# Remove the release from the deployed location
	shutil.rmtree(versionPath)

	# Update the version info
	delReleaseInfo(deployPath, aAppName, aVerStr, aBuildDate)
	print('Application {} ({}) has been removed from location: {}'.format(aAppName, aVerStr, aRootPath))


if __name__ == "__main__":
	# Require python version 2.7 or later
	targVer = (2, 7)
	if sys.version_info < targVer:
		print('The installed version of python is too old. Please upgrade.')
		print('   Current version: ' + '.'.join(str(i) for i in sys.version_info))
		print('   Require version: ' + '.'.join(str(i) for i in targVer))
		sys.exit(-1)

	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, handleSignal)

	# Retrieve the location of the scriptPath
	scriptPath = os.path.realpath(__file__)
	scriptPath = os.path.dirname(scriptPath)

	# Set up the argument parser
	parser = argparse.ArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('--help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('--remove', help='Remove the specified distribution.', action='store_true', default=False)
	parser.add_argument('deployRoot', help='Root location to deploy the specified distribution.')
	parser.add_argument('distLoc', nargs='?', default=scriptPath, help='The location of the distribution to deploy.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv or '--help' in argv:
		parser.print_help()
		exit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	# Retrieve the distPath and ensure that it exists
	distPath = args.distLoc
	if os.path.isdir(distPath) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not exist!')
		print('Release will not be deployed...')
		exit()

	# Determine the appName, version, and buildDate of the distribution
	(appName, version, buildDate, isLegacyJre) = getDistInfo(distPath)

	# Execute the appropriate action
	rootPath = args.deployRoot

	# Uninstall the app, if remove argument is specified
	if args.remove == True:
		delRelease(rootPath, appName, version, buildDate)
	else:
		addRelease(rootPath, appName, version, buildDate, isLegacyJre)

