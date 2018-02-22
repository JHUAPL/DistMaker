#! /usr/bin/env python

import argparse
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys

def getDistInfo(distPath):
	appName = None
	version = None
	buildDate = None
	isLegacyJre = None

	# Process the app.cfg file
	cfgFile = os.path.join(distPath, 'delta', 'app.cfg')
	if os.path.isfile(cfgFile) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
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
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('The configuration file, ' + cfgFile + ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()

	# Process the catalog.txt file
	catFile = os.path.join(distPath, 'delta', 'catalog.txt')
	if os.path.isfile(catFile) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
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
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('The catalog file, ' + catFile + ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()

	return (appName, version, buildDate, isLegacyJre)


def handleSignal(signal, frame):
		"""Signal handler, typically used to capture ctrl-c."""
		print('User aborted processing!')
		sys.exit(0)


def addReleaseInfo(deployPath, appName, version, buildDate, isLegacyJre):
	# Determine if this Application was deployed with a a legacy DistMaker release
	legacyReleaseL = []
	isLegacyRelease = False
	verFile = os.path.join(deployPath, 'releaseInfo.txt')
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
	catFile = os.path.join(deployPath, 'appCatalog.txt')
	if os.path.isfile(catFile) == False:
		if isLegacyRelease == True and len(legacyReleaseL) > 0:
			f = open(catFile, 'w')
			f.write('name' + ',' + appName + '\n\n')
			# Copy the legacy releases
			for (aLegacyVer, aLegacyDate) in legacyReleaseL:
				f.write('R,{},{}\n'.format(aLegacyVer, aLegacyDate))
				f.write('info,msg,This is a legacy release.\n')
				f.write('info,msg,\n')
				if isLegacyJre == True:
					f.write('info,msg,Downgrading to this version may require a mandatory upgrade (ver: ' + version + ') before further upgrades are allowed.\n\n')
				else:
					f.write('# A release should be made using a legacy JRE (1.8+) and this DistMaker release. The release notes will need to be manually.\n')
					f.write('info,msg,Downgrading to this version will require a mandatory 2-step upgrade in order to use releases made with non legacy JREs.\n\n')
			f.close()
			os.chmod(catFile, 0o644)
		else:
			# Form the default (empty) appCatalog.txt
			f = open(catFile, 'w')
			f.write('name' + ',' + appName + '\n\n')
			f.close()
			os.chmod(catFile, 0o644)

	# Updated the appCatalog.txt info file
	f = open(catFile, 'a')
	f.write('R,{},{}\n'.format(version, buildDate))
	f.write('info,msg,There are no release notes available.\n\n')
	f.close()

	# Update the (legacy) releaseInfo.txt file
	if isLegacyRelease == True and isLegacyJre == True:
		f = open(verFile, 'a')
		f.write(version + ',' + buildDate + '\n')
		f.close()


def delReleaseInfoLegacy(deployPath, appName, version, buildDate):
	verFile = os.path.join(deployPath, 'releaseInfo.txt')

	# Bail if the release info file does not exist
	if os.path.isfile(verFile) == False:
		return;

	# Read the file
	releaseInfo = []
	f = open(verFile, 'r')
	for line in f:
		tokens = line[:-1].split(',', 1);
		if len(tokens) == 2 and tokens[0] == version:
			# By not adding the current record to the releaseInfo list, we are effectively removing the record
			print('Removing release record from info file. Version: ' + version)
		elif len(tokens) == 2 and tokens[0] != 'name':
			releaseInfo.append((tokens[0], tokens[1]))
	f.close()

	# Write the updated file
	f = open(verFile, 'w')
	f.write('name' + ',' + appName + '\n')
	for verTup in releaseInfo:
		f.write(verTup[0] + ',' + verTup[1] + '\n')
	f.close()


def delReleaseInfo(deployPath, appName, version, buildDate):
	# Remove any legacy releases
	delReleaseInfoLegacy(deployPath, appName, version, buildDate)

	catFile = os.path.join(deployPath, 'appCatalog.txt')

	# Bail if the appCatalog.txt file does not exist
	if os.path.isfile(catFile) == False:
		print('Failed to locate deployment appCatalog file: ' + catFile)
		print('Aborting removal action for version: ' + version)
		exit()

	# Read the file (and skip over all lines found after the release we are searching for)
	isDeleteMode = False
	passLineL = []
	f = open(catFile, 'r')
	for aLine in f:
		aLine = aLine[:-1]
		tokenL = aLine.split(',', 1);
		# Determine when to enter / exit isDeleteMode
		if len(tokenL) == 3 and tokenL[0] == 'R' and tokenL[1] == version:
			# By not adding the current record to the releaseInfo list, we are effectively removing the record
			isDeleteMode = True
			print('Removing release record from info file. Version: ' + version)
		# We exit deleteMode when see a different release or exit instruction
		elif len(tokenL) == 3 and tokenL[0] == 'R' and tokenL[1] != version:
			isDeleteMode = False
		elif len(tokenL) >= 1 and tokenL[0] == 'exit':
			isDeleteMode = False

		# Skip to next if we are in deleteMode
		if isDeleteMode == True:
			continue

		# Save off all lines when we are not in delete mode
		passLineL += aLine
	f.close()

	# Write the updated file
	f = open(verFile, 'w')
	for aLine in passLineL:
		f.write(aLine + '\n')
	f.close()


def addRelease(appName, version, buildDate, isLegacyJre):
	# Check to see if the deployed location already exists
	deployPath = os.path.join(rootPath, appName)
	if os.path.isdir(deployPath) == False:
		print('Application ' + appName + ' has never been deployed to the root location: ' + args.deployRoot)
		print('Create a new release of the application at the specified location?')
		input = raw_input('--> ').upper()
		if input != 'Y' and input != 'YES':
			print('Release will not be made for app ' + appName)
			exit()

		# Build the deployed location
		os.makedirs(deployPath, 0o755)

	# Check to see if the deploy version already exists
	versionPath = os.path.join(deployPath, version)
	if os.path.isdir(versionPath) == True:
		print('Application ' + appName + ' with version, ' + version + ', has already been deployed.')
		print('Release will not be made for app ' + appName)
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
	addReleaseInfo(deployPath, appName, version, buildDate, isLegacyJre)
	print('Application {} ({}) has been deployed to location: {}'.format(appName, version, args.deployRoot))


def delRelease(appName, version, buildDate):
	# Check to see if the deployed location already exists
	deployPath = os.path.join(rootPath, appName)
	if os.path.isdir(deployPath) == False:
		print('Application ' + appName + ' has never been deployed to the root location: ' + args.deployRoot)
		print('There are no releases to remove. ')
		exit()

	# Check to see if the deploy version already exists
	versionPath = os.path.join(deployPath, version)
	if os.path.isdir(versionPath) == False:
		print('Application ' + appName + ' with version, ' + version + ', has not been deployed.')
		print('Release will not be removed for app ' + appName)
		exit()

	# Remove the release from the deployed location
	shutil.rmtree(versionPath)

	# Update the version info
	delReleaseInfo(deployPath, appName, version, buildDate)


if __name__ == "__main__":
	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, handleSignal)

	# Retrieve the location of the scriptPath
	scriptPath = os.path.realpath(__file__)
	scriptPath = os.path.dirname(scriptPath)

	# Set up the argument parser
	parser = argparse.ArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('-help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('-remove', help='Remove the specified distribution.', action='store_true', default=False)
	parser.add_argument('deployRoot', help='Root location to deploy the specified distribution.')
	parser.add_argument('distLoc', nargs='?', default=scriptPath, help='The location of the distribution to deploy.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv:
		parser.print_help()
		exit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	distPath = args.distLoc
	rootPath = args.deployRoot

	# Retrieve the distPath and ensure that it exists
	if os.path.isdir(distPath) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not exist!')
		print('Release will not be deployed...')
		exit()

	# Determine the appName, version, and buildDate of the distribution
	(appName, version, buildDate, isLegacyJre) = getDistInfo(distPath)

	# Uninstall the app, if remove argument is specified
	if args.remove == True:
		delRelease(appName, version, buildDate)
	else:
		addRelease(appName, version, buildDate, isLegacyJre)

