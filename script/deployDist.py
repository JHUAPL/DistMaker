#! /usr/bin/env python

import argparse
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys
import time

def getDistInfo(distPath):
	appName = None
	version = None
	
	cfgFile = os.path.join(distPath, 'delta', 'app.cfg')
	if os.path.isfile(cfgFile) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('Release will not be deployed...')
		exit()
		
	isNameMode = isVerMode = False
	f = open(cfgFile, 'r')
	for line in f:
		line = line[:-1]
		if line == '-name':
			isNameMode = True
			isVerMode = False
		elif line == '-version':
			isNameMode = False
			isVerMode = True
		elif line.startswith('-') == True:
			isNameMode = False
			isVerMode = False
		elif isNameMode == True and len(line) > 0:
			appName = line
		elif isVerMode == True and len(line) > 0:
			version = line
	f.close()
	
	if appName == None or version == None:
		print('Distribution corresponding to the folder: ' + distPath + ' does not appear to be valid!')
		print('The configuration file, ' + cfgFile+ ', is not valid.')
		print('Release will not be made for app ' + appName)
		exit()
		
	return (appName, version)


def handleSignal(signal, frame):
		"""Signal handler, typically used to capture ctrl-c."""
		print('User aborted processing!')
		sys.exit(0)


def updateAppConfig(versionPath, deployDate):
	cfgFile = os.path.join(versionPath, 'delta', 'app.cfg')
	
	# Updated the config file
	f = open(cfgFile, 'a')
	f.write('-deployDate\n')
	f.write(str(deployDate) + '\n')
	f.close()


def updateReleaseInfo(installPath, appName, version, deployDate):
	verFile = os.path.join(installPath, 'releaseInfo.txt')
	
	releaseInfo = []
	
	# Read the file
	if os.path.isfile(verFile) == True:
		f = open(verFile, 'r')
		for line in f:
			tokens = line[:-1].split(',', 1);
			if len(tokens) == 2 and tokens[0] != 'name':
				releaseInfo.append((tokens[0], tokens[1]))
		f.close()

	# Add the new version		
	releaseInfo.append((version, deployDate))
	
	# Write the updated file
	f = open(verFile, 'w')
	f.write('name' + ',' + appName + '\n')
	for verTup in releaseInfo:
		f.write(verTup[0] + ',' + verTup[1] + '\n')
	f.close()

	
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
	parser.add_argument('rootLoc', help='Root location to deploy the specified distribution.')
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
	rootPath = args.rootLoc

	# Retrieve the distPath and ensure that it exists 
	if os.path.isdir(distPath) == False:
		print('Distribution corresponding to the folder: ' + distPath + ' does not exist!')
		print('Release will not be deployed...')
		exit()
		
	# Determine the appName and version of the distribution
	(appName, version) = getDistInfo(distPath)

	# Check to see if the deployed location already exists
	installPath = os.path.join(rootPath, appName)
	if os.path.isdir(installPath) == False:
		print('Application ' + appName + ' has never been deployed to the root location: ' + args.rootLoc)
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
	
	# Compute the official deployDate			
	exeDate = time.localtime()
	deployDate = time.strftime('%Y%b%d %H:%M:%S', exeDate)
			
	# Update the app.cfg file
	updateAppConfig(versionPath, deployDate)
		
	# Update the version info
	updateReleaseInfo(installPath, appName, version, deployDate)

