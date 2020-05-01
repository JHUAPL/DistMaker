#! /usr/bin/env python

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
	f = open(cfgFile, 'r')
	for aLine in f:
		aLine = aLine[:-1]
		if aLine.startswith('-') == True:
			exeMode = aLine;
		elif exeMode == '-name' and len(aLine) > 0:
			appName = aLine
		elif exeMode == '-version' and len(aLine) > 0:
			version = aLine
		elif exeMode == '-buildDate' and len(aLine) > 0:
			buildDate = aLine
	f.close()

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

	f = open(catFile, 'r')
	for aLine in f:
		aLine = aLine[:-1]
		tokenL = aLine.split(',')
		# Check to see if legacy JREs are allowed
		if len(tokenL) >= 2 and tokenL[0] == 'jre' and isLegacyJre == None:
			isLegacyJre = False
			if tokenL[1].strip().startswith('1.') == True:
				isLegacyJre = True
	f.close()

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
		f = open(verFile, 'r')
		for aLine in f:
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
		f.close()

	# Create the appCatalog.txt file
	catFile = os.path.join(aDeployPath, 'appCatalog.txt')
	if os.path.isfile(catFile) == False:
		if isLegacyRelease == True and len(legacyReleaseL) > 0:
			f = open(catFile, 'w')
			f.write('name' + ',' + aAppName + '\n\n')
			# Copy the legacy releases
			for (aLegacyVer, aLegacyDate) in legacyReleaseL:
				f.write('R,{},{}\n'.format(aLegacyVer, aLegacyDate))
				f.write('info,msg,This is a legacy release.\n')
				f.write('info,msg,\n')
				if aIsLegacyJre == True:
					f.write('info,msg,Downgrading to this version may require a mandatory upgrade (ver: ' + aVerStr + ') before further upgrades are allowed.\n\n')
				else:
					f.write('# A release should be made using a legacy JRE (1.8+) and this DistMaker release. The release notes will need to be manually.\n')
					f.write('info,msg,Downgrading to this version will require a mandatory 2-step upgrade in order to use releases made with non legacy JREs.\n\n')
			f.close()
			os.chmod(catFile, 0o644)
		else:
			# Form the default (empty) appCatalog.txt
			f = open(catFile, 'w')
			f.write('name' + ',' + aAppName + '\n\n')
			f.close()
			os.chmod(catFile, 0o644)

	# Updated the appCatalog.txt info file
	f = open(catFile, 'a')
	f.write('R,{},{}\n'.format(aVerStr, aBuildDate))
	f.write('info,msg,There are no release notes available.\n\n')
	f.close()

	# Update the (legacy) releaseInfo.txt file
	if isLegacyRelease == True and aIsLegacyJre == True:
		f = open(verFile, 'a')
		f.write(aVerStr + ',' + aBuildDate + '\n')
		f.close()


def delReleaseInfoLegacy(aDeployPath, aAppName, aVerStr, aBuildDate):
	verFile = os.path.join(aDeployPath, 'releaseInfo.txt')

	# Bail if the release info file does not exist
	if os.path.isfile(verFile) == False:
		return;

	# Read the file
	releaseInfo = []
	f = open(verFile, 'r')
	for line in f:
		tokens = line[:-1].split(',', 1);
		if len(tokens) == 2 and tokens[0] == aVerStr:
			# By not adding the current record to the releaseInfo list, we are effectively removing the record
			print('Removing release record from info file. Version: ' + aVerStr)
		elif len(tokens) == 2 and tokens[0] != 'name':
			releaseInfo.append((tokens[0], tokens[1]))
	f.close()

	# Write the updated file
	f = open(verFile, 'w')
	f.write('name' + ',' + aAppName + '\n')
	for verTup in releaseInfo:
		f.write(verTup[0] + ',' + verTup[1] + '\n')
	f.close()


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
	f = open(catFile, 'r')
	for aLine in f:
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
	f.close()

	# Write the updated file
	f = open(catFile, 'w')
	for aLine in passLineL:
		f.write(aLine + '\n')
	f.close()


def addRelease(aRootPath, aAppName, aVerStr, aBuildDate, aIsLegacyJre):
	# Check to see if the deployed location already exists
	deployPath = os.path.join(aRootPath, aAppName)
	if os.path.isdir(deployPath) == False:
		print('Application ' + aAppName + ' has never been deployed to the root location: ' + aRootPath)
		print('Create a new release of the application at the specified location?')
		input = raw_input('--> ').upper()
		if input != 'Y' and input != 'YES':
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

