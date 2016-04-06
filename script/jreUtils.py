#! /usr/bin/env python

import glob
import os
import re
import subprocess
import tempfile

import miscUtils

# Globals
# The global variable defaultJreVersion is a hint for which (tar.gz) JRE release to
# default to. Note if this variable is not specified then the latest JRE release
# located in the appropriate <installPath>/jre directory will be utilized.
#
# There should be corresponding tar.gz files with the following pattern:
#      <installpath>/jre/jre-<VERSION>-<PLATFORM>-x64.tar.gz
# where PLATFORM is one of the following: apple, linux, windows
# where VERSION  is something like: 8u73
# For example the Linux JRE 1.8.73 tar.gz release would be:
#      <installpath>/jre/jre-8u73-linux-x64.tar.gz
defaultJreVersion = '1.8'


def getDefaultJreRelease(aPlatform):
	"""Utility method to locate the default JRE to utilize. The returned value will be a string such as 1.8.73"""
	# Return the default if it is hard wired (and is available)
	if 'defaultJreVersion' in globals():
		jreTarGzFile = getJreTarGzFile(aPlatform, defaultJreVersion)
		if jreTarGzFile == None:
			print('[WARNING] The specified default JRE for the ' + aPlatform.capitalize() + ' platform is: ' + defaultJreVersion + '. However this JRE is not installed! <---')
		else:
			return '1.' + '.'.join(getJreTarGzVersion(jreTarGzFile))

	jreTarGzFile = getJreTarGzFile(aPlatform, None)
	if jreTarGzFile == None:
		return None
	return '1.' + '.'.join(getJreTarGzVersion(jreTarGzFile))


def getDefaultJreVersion():
	"""Returns the default JRE version. This will return None if a default JRE version has not been specified
	and one can not be determined."""
	if 'defaultJreVersion' in globals():
		return defaultJreVersion;
	return None


def doesJreVersionMatch(aRequestVer, aEvaluateVer):
	"""Returns true if aEvaluateVer is a match for the requested version. Note that aEvaluateVer is considered
	a match if each (available) component corresponds to the component in a aRequestVer. aRequestVer must
	not be any more specific than aEvaluateVer"""
	if len(aRequestVer) > len(aEvaluateVer):
		return False

	# check each component for equality
	for r, e in zip(aRequestVer, aEvaluateVer):
		if r != e:
			return False

	return True

def getBasePathForJreTarGzFile(aJreTarGzFile):
	"""Returns the JRE (base) path that should be used to access the JRE found in the specified JRE tar.gz.
	This is needed since different JRE tar.gz files have been found to have different top level paths. Using
	this method ensures consistency between JRE tar.gz releases after the tar.gz file is unpacked."""
	verDescr = getJreTarGzVersion(aJreTarGzFile);
	if verDescr == None:
		raise  Exception("File name (' + aJreTarGzFile + ') does not conform to proper JRE tar.gz name specification.")
	basePath = 'jre1.' + '.'.join(verDescr)
	return basePath;


def getJreTarGzFile(aPlatform, aJreRelease):
	"""Returns the JRE tar.gz file for the appropriate platform and JRE release. This will return None if
	there is no file that is sufficient for the request. Note if you do not care about any specific update
	for a major version of JAVA then just specify the major version. Example '1.8' instead of '1.8.73'"""
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Normalize aPlotform to the proper string
	platStr = aPlatform.lower()
	if platStr == 'apple':
		platStr = 'macosx'

	# Transform the version from a string into a list of (version) components. Due to SUNs funny naming
	# conventions we automatically strip the first part of the string if it matches '1.'
	# Some examples:
	# '1.8'    ---> [8]                        [1.8.0] ---> [8, 0]
	# '1.8.73' ---> [8, 73]
	reqVer = []
	if aJreRelease != None:
		if aJreRelease.startswith("1.") == True:
			aJreRelease = aJreRelease[2:]
		reqVer = aJreRelease.split(".")
		reqVer = [y for y in reqVer if y != '']
#	print str(reqVer)

	# Search all the appropriate tar.gz JREs for the best match from our JRE folder
	matchList = []
	searchName = "jre-*-" + platStr + "-x64.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', searchName)
#	for file in ['jre-8u739-windows-x64.tar.gz', 'jre-8u60-windows-x64.tar.gz', 'jre-7u27-windows-x64.tar.gz']:
	for file in glob.glob(searchPath):
		# Determine if the tar.gz version is a match for the reqVer
		tmpVer = getJreTarGzVersion(file)
		if doesJreVersionMatch(reqVer, tmpVer) == True:
			matchList.append(file);

	# Determine the best match of all the matches
	bestMatch = None
	for aFile in matchList:
		if bestMatch == None:
			bestMatch = aFile
			continue

		bestVer = getJreTarGzVersion(bestMatch)
		testVer = getJreTarGzVersion(aFile)
		for b, t in zip(bestVer, testVer):
			if b == t:
				continue
			try:
				if int(t) > int(b):
					bestMatch = aFile
					break;
			except:
				continue

	return bestMatch


def getJreTarGzVersion(aFile):
	"""Returns the version corresponding to the passed in JRE tar.gz file.
	The returned value will be a list consisting of the (major, minor) components of the version.
	 
	 The file naming convention is expected to be:
	jre-<A>u<B>-*.tar.gz
	where:
		A ---> The major version
		B ---> The minor version
	"""
	# Retrieve the version component of the fileName
	fileName = os.path.basename(aFile)
	idx = fileName[4:].find('-')
	if idx == -1:
		return None
	verStr = fileName[4: 4 + idx]
	if len(verStr) == 0:
		return None
#	print('verStr: ' + verStr)

	# Determine the version based on the pattern '<A>u<B>' where:
	# if there is no <B> component then just assume 0 for minor version
	retVer = verStr.split('u')
	if len(retVer) == 1:
		retVer.append(0)
	return retVer


def unpackAndRenameToStandard(aJreTarGzFile, aDestPath):
	"""Method that will unpack the specified JRE tar.gz into the folder aDestPath. The unpacked JRE folder will also 
	be renamed to a standard convention: jre<A>.<B> where
		A: represents the major (classical) version
		B: represents the minor version
	Hence the JRE tar.gz file: jre-8u73-linux-x64.tar.gz should be unpacked to jre1.8.73"""
	# Ensure the aDestPath exists
	if os.path.isdir(aDestPath) == False:
		os.makedirs(aDestPath)

	# Unpack to a temporary folder at aDestPath
	tmpPath = tempfile.mkdtemp(dir=aDestPath)
	subprocess.check_call(["tar", "-xf", aJreTarGzFile, "-C", tmpPath], stderr=subprocess.STDOUT)

	# Rename the single extracted folder to a standard convention and move to aDestPath
	fileList = glob.glob(os.path.join(tmpPath, '*'))
	if len(fileList) != 1 or os.path.isdir(fileList[0]) == False:
		print('Fatal error while unpacking JRE tar.gz file. Did not resolve to single folder! Terminating build process!')
		print('\tJRE tar.gz: ' + aJreTarGzFile)
		print('\tUnpacked files: ' + str(fileList))
		exit(-1)
	jreBasePath = getBasePathForJreTarGzFile(aJreTarGzFile)
	targPath = os.path.join(aDestPath, jreBasePath)
	os.rename(fileList[0], targPath)

	# Remove the the temporary path
	os.rmdir(tmpPath)

