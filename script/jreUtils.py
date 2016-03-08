#! /usr/bin/env python

import os
import re

import miscUtils

# Globals
# The global variable defaultJreRelease is a hint for which (full) JRE release to
# default to. Note if this variable is not specified then the latest JRE release
# located in the appropriate platform directory will be utilized.
#
# There should be a release for the following:
# Apple:   <installpath>/jre/apple/defaultJreRelease
# Linux:   <installpath>/jre/linux/defaultJreRelease
# Windows: <installpath>/jre/windows/defaultJreRelease
defaultJreRelease = 'jre1.8.0_60'


def getDefaultJreRelease(aPlatform):
	"""Utility method to locate the default JRE to utilize"""
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Locate the JRE platform search path		
	jreSearchPath = os.path.join(appInstallRoot, 'jre/' + aPlatform)
	if os.path.isdir(jreSearchPath) == False:
		print ('JRE search path is: ' + jreSearchPath + '  This path does not exist. No embedded JRE builds will be made for platform: ' + aPlatform)
		return None
	
	# Return the default if it is hard wired (and is available)
	if 'defaultJreRelease' in globals():
		evalPath = os.path.join(jreSearchPath, defaultJreRelease)
		if os.path.isdir(evalPath) == True:
			return defaultJreRelease
		else:
			print('[WARNING] The specified default JRE for the ' + aPlatform.capitalize() + ' platform is: ' + defaultJreRelease + '. However this JRE is not installed! <---')

	# Assume no JRE found		
	pickJre = None
	pickVer = None

	# Search the platform path for available JREs
	for aPath in os.listdir(jreSearchPath):
		evalPath = os.path.join(jreSearchPath, aPath)
		if os.path.isdir(evalPath) == True and aPath.startswith('jre') == True:
			tmpVer = re.split('[._]', aPath[3:])
			# print('\tEvaluating path: ' + aPath + ' Ver: ' + str(tmpVer))
				
			if pickJre == None:
				pickJre = aPath
				pickVer = tmpVer
				continue
			
			# Evaluate the version and select the most recent
			for i, j in zip(tmpVer, pickVer):
				if i < j:
					break
				if i > j:
					pickJre = aPath
					pickVer = tmpVer
					break
							 
	# Return the result (default JRE)
	return pickJre


def getJreMajorVersion(aJreRelease):
	"""Returns the minimum version of the JRE to utilize based on the passed in JRE release. 
	The passed in value should be of the form jreX.X.X_XX ---> example: jre1.8.0_60 
	If aJreRelease is invalid then returns the default value of: 1.8.0"""
	if aJreRelease == None or aJreRelease.startswith('jre') == False:
		return "1.8.0"
	return aJreRelease[3:].split("_")[0]


def isJreAvailable(aPlatform, aJreRelease):
	"""Tests to see if the specified JRE is available for aPlatform"""
	if aPlatform == None or aJreRelease == None:
		return False
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	srcPath = os.path.join(appInstallRoot, 'jre', aPlatform, aJreRelease)
	return os.path.isdir(srcPath)

