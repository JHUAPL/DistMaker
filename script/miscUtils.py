#! /usr/bin/env python
import sys
import os

def handleSignal(signal, frame):
		"""Signal handler, typically used to capture ctrl-c."""
		print('User aborted processing!')
		sys.exit(0)


def getPathSize(aRoot):
	"""Computes the total disk space used by the specified path.
	Note if aRoot does not exist or is None then this will return 0"""
	# Check for existance
	if aRoot == None or os.path.exists(aRoot) == False:
		return 0
	
	# Return the file size of aRoot if just a file
	aRoot = os.path.abspath(aRoot)
	if os.path.isfile(aRoot) == True:
		return os.path.getsize(aRoot)
	
	# Add in all of the file sizes in the directory
	numBytes = 0
	for path, dirs, files in os.walk(aRoot):
		for f in files:
			fPath = os.path.join(path, f)
			if os.path.isfile(fPath) == True:
				numBytes += os.path.getsize(fPath)
	return numBytes


def checkRoot():
	"""
	Determines if the scrip ist running with root priveleges."""
	# This logic will may break on SELinux systems
	if os.geteuid() != 0:
		msg = '   You need to have root privileges to run this script.\n'
		msg += '   Please run this script with sudo!\n'
		msg += '   Exiting...\n'
		exit(msg)


def getInstallRoot():
	"""Returns the root path where the running script is insalled."""
	argv = sys.argv;
	installRoot = os.path.dirname(argv[0])
#	print('appInstallRoot: ' + appInstallRoot)
	return installRoot


def isJreAvailable(systemName, jreRelease):
	appInstallRoot = getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	srcPath = os.path.join(appInstallRoot, 'jre', systemName, jreRelease)
	return os.path.isdir(srcPath)
