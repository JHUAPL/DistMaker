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
	
	cfgFile = os.path.join(distPath, 'delta', 'app.cfg')
	if os.path.isfile(cfgFile) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('Release will not be deployed...')
		exit()
		
	exeMode = None
	f = open(cfgFile, 'r')
	for line in f:
		line = line[:-1]
		if line.startswith('-') == True:
			exeMode = line;
		elif exeMode == '-name' and len(line) > 0:
			appName = line
		elif exeMode  == '-version' and len(line) > 0:
			version = line
		elif exeMode  == '-buildDate' and len(line) > 0:
			buildDate = line
	f.close()
	
	if appName == None or version == None or buildDate == None:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('The configuration file, ' + cfgFile+ ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()
		
	return (appName, version, buildDate)


def handleSignal(signal, frame):
		"""Signal handler, typically used to capture ctrl-c."""
		print('User aborted processing!')
		sys.exit(0)


def addReleaseInfo(installPath, appName, version, buildDate):
	verFile = os.path.join(installPath, 'releaseInfo.txt')
	
	# Create the release info file
	if os.path.isfile(verFile) == False:
		f = open(verFile, 'w')
		f.write('name' + ',' + appName + '\n')
		f.close()

	# Updated the release info file
	f = open(verFile, 'a')
	f.write(version + ',' + buildDate + '\n')
	f.close()
	

def delReleaseInfo(installPath, appName, version, buildDate):
	verFile = os.path.join(installPath, 'releaseInfo.txt')
	
	# Bail if the release info file does not exist
	if os.path.isfile(verFile) == False:
		print('Failed to locate deployment release info file: ' + verFile)
		print('Aborting removal action for version: ' + version)
		exit()

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


def addRelease(appName, version, buildDate):
	# Check to see if the deployed location already exists
	installPath = os.path.join(rootPath, appName)
	if os.path.isdir(installPath) == False:
		print('Application ' + appName + ' has never been deployed to the root location: ' + args.deployRoot)
		print('Create a new release of the application at the specified location?')
		input = raw_input('--> ').upper()
		if input != 'Y' and input != 'YES':
			print('Release will not be made for app ' + appName)
			exit()
			
		# Build the deployed location
		os.makedirs(installPath)
			
	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, version)
	if os.path.isdir(versionPath) == True:
		print('Application ' + appName + ' with version, ' + version + ', has already been deployed.')
		print('Release will not be made for app ' + appName)
		exit()

	# Copy over the contents of the release folder to the deploy location
	shutil.copytree(distPath, versionPath, symlinks=True) 
	
	# Update the version info
	addReleaseInfo(installPath, appName, version, buildDate)


def delRelease(appName, version, buildDate):
	# Check to see if the deployed location already exists
	installPath = os.path.join(rootPath, appName)
	if os.path.isdir(installPath) == False:
		print('Application ' + appName + ' has never been deployed to the root location: ' + args.deployRoot)
		print('There are no releases to remove. ')
		exit()
		
	# Check to see if the deploy version already exists
	versionPath = os.path.join(installPath, version)
	if os.path.isdir(versionPath) == False:
		print('Application ' + appName + ' with version, ' + version + ', has not been deployed.')
		print('Release will not be removed for app ' + appName)
		exit()
		
	# Remove the release from the deployed location
	shutil.rmtree(versionPath)
	
	# Update the version info
	delReleaseInfo(installPath, appName, version, buildDate)


if __name__ == "__main__":
	argv = sys.argv;
	argc = len(argv);

	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, handleSignal)
	
	# Retrive the location of the scriptPath
	scriptPath = os.path.realpath(__file__)
	scriptPath = os.path.dirname(scriptPath)
	
	# Set up the argument parser	
	parser = argparse.ArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('-help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('-remove', help='Remove the specified distribution.', action='store_true', default=False)
	parser.add_argument('deployRoot', help='Root location to deploy the specified distribution.')
	parser.add_argument('distLoc', nargs='?', default=scriptPath, help='The location of the distribution to deploy.')

	# Intercept any request for a  help message and bail
	for aArg in argv:
		if aArg == '-h' or aArg == '-help':
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
	(appName, version, buildDate) = getDistInfo(distPath)
	
	# Uninstall the app, if remove argument is specified
	if args.remove == True:
		delRelease(appName, version, buildDate)
	else:
		addRelease(appName, version, buildDate)

