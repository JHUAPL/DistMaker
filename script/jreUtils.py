#! /usr/bin/env python

import glob
import os
import re
import subprocess
import tempfile

import miscUtils
from miscUtils import ErrorDM


# Globals
# The global variable defaultVersionStr is a hint for which (tar.gz) JRE release to
# default to. Note if this variable is not specified then the latest JRE release
# located in the appropriate <installPath>/jre directory will be utilized.
#
# There should be corresponding tar.gz files with the following pattern:
#      <installpath>/jre/jre-<VERSION>-<PLATFORM>-x64.tar.gz
# where PLATFORM is one of the following: apple, linux, windows
# where VERSION  is something like: 8u73
# For example the Linux JRE 1.8.0_73 tar.gz release would be:
#      <installpath>/jre/jre-8u73-linux-x64.tar.gz
defaultVersionStr = '1.8'


def getBasePathForJreTarGzFile(aJreTarGzFile):
	"""Returns the JRE (base) path that should be used to access the JRE found in the specified JRE tar.gz.
	This is needed since different JRE tar.gz files have been found to have different top level paths. Using
	this method ensures consistency between JRE tar.gz releases after the tar.gz file is unpacked."""
	verArr = getJreTarGzVerArr(aJreTarGzFile);
	if verArr == None:
		raise  ErrorDM('File name (' + aJreTarGzFile + ') does not conform to proper JRE tar.gz name specification.')
	basePath = 'jre' + verArrToVerStr(verArr)
	return basePath;


def getDefaultJreVerStr():
	"""Returns the default JRE version. This will return None if a default JRE version has not been specified
	and one can not be determined."""
	if 'defaultVersionStr' in globals():
		return defaultVersionStr;
	return None


def getJreTarGzFilesForVerStr(aVerStr):
	"""Returns the JRE tar.gz files matching the specified specific JRE version specification. This will return an empty
	list if there is no JRE tar.gz files that is sufficient for the request. 

	The version must be fully qualified (must specify platform, major, minor, and update components). If it is not then
	an ErrorDM will be raised. An example of a valid fully qualified version string is: '1.8.0_73'"""
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Retrieve the target version - and ensure it is fully qualified
	targVer = verStrToVerArr(aVerStr)
	if len(targVer) != 4:
		raise ErrorDM('The specified version is not a fully qualified version. Version: ' + aVerStr)

	# Search all the appropriate tar.gz JREs for exact matches in our JRE folder
	retList = []
	matchName = "jre-*-*-x64.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', matchName)
#	for aFile in ['jre-8u739-windows-x64.tar.gz', 'jre-8u60-windows-x64.tar.gz', 'jre-7u27-windows-x64.tar.gz']:
	for aFile in glob.glob(searchPath):
		# Ensure that the aFile's JVM version is an exact match of targVer
		tmpVer = getJreTarGzVerArr(aFile)
		if targVer != tmpVer:
			continue

		retList.append(aFile);

	return sorted(retList)


def getJreTarGzFile(aPlatform, aJvmVerSpec):
	"""Returns the JRE tar.gz file for the appropriate platform and JRE release. If there are several possible 
	matches then the tar.gz with the latest version will be rteurned. 

	This will return None if there is no file that is sufficient for the request. Note if you do not care about 
	any specific update for a major version of JAVA then just specify the major version. Example '1.8' instead of
	1.8.0_73'"""
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Normalize aPlotform to the proper string
	platStr = aPlatform.lower()
	if platStr == 'apple':
		platStr = 'macosx'

	# Retrieve the min and max JVM versions from aJvmVerSpec
	minJvmVer = None
	if aJvmVerSpec != None and len(aJvmVerSpec) >= 1:
		minJvmVer = verStrToVerArr(aJvmVerSpec[0])
	maxJvmVer = None
	if aJvmVerSpec != None and len(aJvmVerSpec) == 2:
		maxJvmVer = verStrToVerArr(aJvmVerSpec[1])

	# Search all the appropriate tar.gz JREs for the best match from our JRE folder
	matchList = []
	searchName = "jre-*-" + platStr + "-x64.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', searchName)
#	for file in ['jre-8u739-windows-x64.tar.gz', 'jre-8u60-windows-x64.tar.gz', 'jre-7u27-windows-x64.tar.gz']:
	for file in glob.glob(searchPath):
		# Ensure that the file's JVM version is in range of minJvmVer and maxJvmVer
		tmpVer = getJreTarGzVerArr(file)
		if minJvmVer != None and isVerAfterAB(minJvmVer, tmpVer) == True:
			continue
		if maxJvmVer != None and isVerAfterAB(tmpVer, maxJvmVer) == True:
			continue
		matchList.append(file);

	# Determine the best match of all the matches
	bestMatch = None
	for aFile in matchList:
		if bestMatch == None:
			bestMatch = aFile
			continue

		bestVer = getJreTarGzVerArr(bestMatch)
		testVer = getJreTarGzVerArr(aFile)
		for b, t in zip(bestVer, testVer):
			if b == t:
				continue
			try:
				if t > b:
					bestMatch = aFile
					break;
			except:
				continue

	return bestMatch


def getJreTarGzVerArr(aFile):
	"""Returns the version corresponding to the passed in JRE tar.gz file.
	The returned value will be a list consisting of the (platform, major, minor, update) integer components of the version.
	 
	 The file naming convention is expected to be:
	jre-<B>u<D>-*.tar.gz
	where:
		B ---> The major version
		D ---> The update version
	Note that the platform version <A> will be assumed to be: 1 
	Note that the minor version <C> will be assumed to be: 0
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
	tokenArr = verStr.split('u')
	retVerArr = [1, int(tokenArr[0]), 0]
	if len(retVerArr) == 1:
		retVerArr.append(0)
	else:
		retVerArr.append(int(tokenArr[1]))
	return retVerArr


def normalizeJvmVerStr(aVerStr):
	"""Returns a normalized JVM version corresponding to the string. Normalization consists of ensuring all of the components
	of the version are separated by '.' except if applicable the third '.' is changed into '_'. This is useful so that referencing
	JVM versions is consistent. Some examples of normalization are:
	1.8      ---> 1.8
	1.8.0.73 ---> 1.8.0_73
	1.7.0_55 ---> 1.7.0_55
	"""
	verArr = verStrToVerArr(aVerStr)
	retVerStr = verArrToVerStr(verArr)
	return retVerStr


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


def validateJreVersionSpec(aVerSpec):
	"""Method that ensures that the specified aVerSpec is a valid definition. A valid JVM version spec can be composed of the following
	   - None: The jreVersion was not specified and should default to DistMaker's default values.
	   - A list of 1: The value defines the minimum allowable JRE version
	   - A list of 2: The 2 value defines the 2 JRE versions. The first is the minimum value. The second is the maximum value
	Furthermore each JRE version must be a valid specification. Examples of valid specifications are:
	1.7, 1.8.0, 1.8.0_73"""
	# Bail if no version was specified
	if aVerSpec == None:
		return

	# Ensure the number of arguments are correct. Must be 1 or 2
	if (len(aVerSpec) in [1, 2]) == False:
		raise ErrorDM('The parameter jreVersion can either be  1 or 2 values. The first value is the minVersion and (if specified) the later is the maxVersion. Values provided: ' + str(len(aVerSpec)))

	# Ensure the minVer is valid
	minVer = verStrToVerArr(aVerSpec[0])
	if minVer == None:
		raise ErrorDM('The specified string: "' + aVerSpec[0] + ' is not a valid JRE version specification.')
	if len(aVerSpec) == 1:
		return

	# Ensure the maxVer is valid and is after minVer
	maxVer = verStrToVerArr(aVerSpec[1])
	if maxVer == None:
		raise ErrorDM('The specified string: "' + aVerSpec[1] + ' is not a valid JRE version specification.')

	# Ensure the minVer is not later than the maxVer. Note it is ok if it is equal
	if isVerAfterAB(minVer, maxVer) == True:
		raise ErrorDM('In the parameter jreVersion, the minVer ({0}) is later than the maxVer ({1}).'.format(aVerSpec[0], aVerSpec[1]))


def verStrToVerArr(aVerStr):
	"""Utility method to convert a jvm version string to the equivalent integral version (list) component. If the specified version is not a valid jvm version
	then an ErrorDM will be raised. Each component in the array will be integral. Note typical versions follow this pattern: <langVer>.<majVer>.<minVer>_<upVer>
	Thus the following will get transformed to:
	'1.7.0     ---> [1, 7, 0]
	'1.8'      ---> [1, 8]
	'1.8.0_73' ---> [1, 8, 0, 73]"""
	verStrArr = re.compile("[._]").split(aVerStr)
	# Transform from string list to integer list
	try:
		retVerArr = [int(val) for val in verStrArr]
	except:
		raise ErrorDM('Invalid JVM version: ' + aVerStr)
	return retVerArr


def verArrToVerStr(aVerArr):
	"""Utility method to convert an integral version (list) to the equivalent jvm version string. If the specified version is not a valid jvm version
	then an ErrorDM will be raised. Each component in the list must be integral. Note typical versions follow this pattern: <langVer>.<majVer>.<minVer>_<upVer>
	Thus the following will get transformed to:
	[1, 7, 0]     ---> '1.7.0' 
	[1, 8]        ---> '1.8' 
	[1, 8, 0, 73] ---> '1.8.0_73'"""
	# Transform from integer list to string
	if len(aVerArr) <= 3:
		retVerStr = ".".join(str(x) for x in aVerArr)
		return retVerStr
	else:
		retVerStr = ".".join(str(x) for x in aVerArr[0:3])
		retVerStr += '_' + ".".join(str(x) for x in aVerArr[3:])
		return retVerStr


def isVerAfterAB(aJvmVerA, aJvmVerB):
	"""Returns True if the specified JvmVerA is later than aJvmVerB. Both versions should be composed of a list of integers. Typically this list would be formed
	from the method verStrToVerArr. Note that if one version has more detail than the other and all others are previous components are equivalent then the more
	detailed version will be considered later (since it can be assumed that the missing details refer to the very first issue)"""
	for a, b in zip(aJvmVerA, aJvmVerB):
		if a == b:
			continue
		if a > b:
			return True
		else:
			return False

	# Since all other components are equal then verA is better than verB only if it is more detailed
	if len(aJvmVerA) > len(aJvmVerB):
		return True
	return False
