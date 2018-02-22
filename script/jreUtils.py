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
# There should be corresponding tar.gz files with patterns that match one of the below:
#     <installpath>/jre/jre-<VERSION>-<PLATFORM>-x64.tar.gz
#     <installpath>/jre/jre-<VERSION>_<PLATFORM>-x64_bin.tar.gz
# where PLATFORM is one of the following: apple, linux, windows
# where VERSION  is something like: 8u73 or 9.0.4
#
# Following are valid examples of named JRE tar.gz files:
#     The Linux JRE 1.8.0_73 tar.gz release would be:
#         <installpath>/jre/jre-8u73-linux-x64.tar.gz
#     The Apple JRE 9.0.4 tar.gz release would be:
#         <installpath>/jre/jre-9.0.4_osx-x64_bin.tar.gz
defaultVersionStr = '1.8'


def getBasePathForJreTarGzFile(aJreTarGzFile):
	"""Returns the JRE (base) path that should be used to access the JRE found in the specified JRE tar.gz.
	This is needed since different JRE tar.gz files have been found to have different top level paths. Using
	this method ensures consistency between JRE tar.gz releases after the tar.gz file is unpacked.

	Please note that legacy JREs will expand to a different path than non-legacy JREs.
	"""
	verArr = getJreTarGzVerArr(aJreTarGzFile);
	if verArr == None:
		raise  ErrorDM('File name (' + aJreTarGzFile + ') does not conform to proper JRE tar.gz name specification.')
	verStr = verArrToVerStr(verArr)
	if verStr.startswith("1.") == True:
		basePath = 'jre' + verArrToVerStr(verArr)
	else:
		basePath = 'jre-' + verArrToVerStr(verArr)
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

	aVerStr --- The version of interest. The version must be fully qualified.

	Fully qualified is defined as having the following:
	   Pre Java 9 ---> Exactly 4 fields:
	      <A>.<B>.<C>.<D>
	         where:
	         A ---> Platform (always defined as 1)
	         B ---> Major (stored in minor index)
	         C ---> Minor version (always defined as 0)
		      D ---> Patch
		Java 9 or later ---> 3 fields or more
		   <A>.<B>.<C>...
		      where:
		      A ---> The major version
		      B ---> The minor version
		      C ---> The security release
		      ... Any extra fields are vendor specific.

	An ErrorDM will be raised if the version is not fully qualified Below are examples of fully qualified fields:
	   '1.8.0_73'
	   '9.0.4'
	"""
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Retrieve the target version - and ensure it is fully qualified
	targVer = verStrToVerArr(aVerStr)
	if len(targVer) < 3:
		raise ErrorDM('The specified version is not a fully qualified version. Version: ' + aVerStr)
	if targVer[0] == 1 and len(targVer) != 4:
		raise ErrorDM('Legacy JRE releases require exactly 4 elements for the release. Legacy releases refer to any release before Java 9. Version: ' + aVerStr)

	# Search all the appropriate tar.gz JREs for exact matches in our JRE folder
	retList = []
	searchName = "jre-*.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', searchName)
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
	matches then the tar.gz with the latest version will be returned.

	aPlatform   --- The platform of the JRE tar.gz file of interest. Platform will typically be one of: 'apple',
	               'linux', 'windows'
	aJvmVerSpec --- A list of 1 or 2 items that define the range of JRE versions you are interested in. If the
	                list has just one item then that version will be used as the minimum version.

	Method will return None if there is no file that is sufficient for the request. Note if you do not care about
	any specific update for a major version of JAVA then just specify the major version. Example '1.8' instead of
	1.8.0_73'"""
	# Transform a single string to a list of size 1
	if isinstance(aJvmVerSpec, basestring):
		aJvmVerSpec = [aJvmVerSpec]

	# Retrieve the application installation location
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)

	# Retrieve the min and max JVM versions from aJvmVerSpec
	minJvmVer = None
	if aJvmVerSpec != None and len(aJvmVerSpec) >= 1:
		minJvmVer = verStrToVerArr(aJvmVerSpec[0])
	maxJvmVer = None
	if aJvmVerSpec != None and len(aJvmVerSpec) == 2:
		maxJvmVer = verStrToVerArr(aJvmVerSpec[1])
	if aJvmVerSpec != None and len(aJvmVerSpec) > 2:
		errorMsg = 'At most only 2 elements are allowed. Number of elements specified: {}'.format(aJvmVerSpec)
		raise ValueError(errorMsg)

	# Search all the appropriate tar.gz JREs for the best match from our JRE folder
	aPlatform = aPlatform.lower()
	matchList = []
	searchName = "jre-*.tar.gz";
	searchPath = os.path.join(os.path.abspath(appInstallRoot), 'jre', searchName)
	for aFile in glob.glob(searchPath):
		# Retrieve the platform and skip to next if it is not a match
		platStr = getPlatformForJreTarGzFile(aFile)
		if platStr != aPlatform:
			continue

		# Ensure that the file's JVM version is in range of minJvmVer and maxJvmVer
		tmpVer = getJreTarGzVerArr(aFile)
		if minJvmVer != None and isVerAfterAB(minJvmVer, tmpVer) == True:
			continue
		if maxJvmVer != None and isVerAfterAB(tmpVer, maxJvmVer) == True:
			continue
		matchList.append(aFile);

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
	The returned value will be a list consisting of intergers defining the version associated with the
	tar.gz file.
	See the following references:
	   https://docs.oracle.com/javase/9/migrate/#GUID-3A71ECEF-5FC5-46FE-9BA9-88CBFCE828CB
	   http://openjdk.java.net/jeps/223

	The file naming convention is expected to follow the standard:
	   jre-<Version>-<Platform>.tar.gz
	   where <Version> can be:
	      Pre Java 9:
	         <B>u<D>
	         where:
	            B ---> The major version (stored in minor index)
		         D ---> The update version (stored as the security)
		         - Note the major version will be assumed to be: 1
		   Java 9 or later:
		      <A>.<B>.<C>.<D>
		         A ---> The major version
		         B ---> The minor version
		         C ---> The security release
	"""
	# Retrieve the base file name of the path
	fileName = os.path.basename(aFile)

	# Tokenize the filename by spliting along chars: '_', '-'
	compL = re.split('[_-]', fileName)
	if len(compL) < 3:
		return None
#		raise Error('Failed to tokenize the file name: ' + fileName)

	# The version string should be the second item
	verStr = compL[1]

	# Retrieve the version component of the fileName
	# Based on the old naming convention - prior to Java 9
	if verStr.find('u') != -1:
		# Determine the version based on the pattern '<A>u<B>' where:
		# if there is no <B> component then just assume 0 for minor version
		tokenL = verStr.split('u')
		retVerL = [1, int(tokenL[0]), 0]
		if len(tokenL) == 1:
			retVerL.append(0)
		else:
			retVerL.append(int(tokenL[1]))
		return retVerL

	# Retrieve the version component of the fileName
	# Based on the new naming convention - Java 9 and later
	else:
		retVerL = [int(aVal) for aVal in verStr.split('.')]
		return retVerL


def getPlatformForJreTarGzFile(aFile):
	"""Returns a string representing the platform of the specified JRE file. The platform is computed by evaluating the name of the
	JRE tar.gz file. The returned values will be in lowercase. Currently the known returned values are one of the following: (apple,
	linux, windows). These returned values should correspond to x86-64 JRE releases. On failure None will be returned.
	"""
	# Tokenize the filename by spliting along chars: '_', '-'
	fileName = os.path.basename(aFile)
	compL = re.split('[_-]', fileName)
	if len(compL) != 4 and len(compL) != 5:
		return None

	# The platform component is stored in the 3rd token. Any string matching osx or macosx will be transformed to apple
	platStr = compL[2].lower()
	if platStr == 'osx' or platStr == 'macosx':
		platStr = 'apple'

	return platStr;


def normalizeJvmVerStr(aVerStr):
	"""Returns a normalized JVM version corresponding to the string. Normalization consists of ensuring all of the components
	of the version are separated by '.' except if applicable the third '.' is changed into '_'. This is useful so that referencing
	JVM versions is consistent. Some examples of normalization are:
	1.8      ---> 1.8
	1.8.0.73 ---> 1.8.0_73
	1.7.0_55 ---> 1.7.0_55
	9.0.4    ---> 9.0.4
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

	# Ensure the minVer is not before Java 1.7
	if isVerAfterAB([1, 7], minVer) == True:
		raise ErrorDM('In the parameter jreVersion, the minVer ({0}) must be later than 1.7.'.format(aVerSpec[0]))

	# Bail if only the minVer was specified
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
	verStrL = re.compile("[._]").split(aVerStr)
	# Transform from string list to integer list
	try:
		retVerL = [int(val) for val in verStrL]
	except:
		raise ErrorDM('Invalid JVM version: ' + aVerStr)
	return retVerL


def verArrToVerStr(aVerL):
	"""Utility method to convert an integral version (list) to the equivalent jvm version string. If the specified version is not a valid jvm version
	then an ErrorDM will be raised. Each component in the list must be integral.
	Note that as of Java 9 the version scheme has changed. Below defines the version scheme
	    Prior to Java 9: <language>.<major>.<minor>_<update> to <majVer>.<minVer>.<s>
	   Java 9 and later: <major>.<minor>.<security>.<patch>
	[1, 8]        ---> '1.8'
	[1, 8, 0, 73] ---> '1.8.0_73'
	[9, 0, 4]     ---> '9.0.4'"""
	# Transform from integer list to string
	if len(aVerL) <= 3:
		retVerStr = ".".join(str(x) for x in aVerL)
		return retVerStr
	else:
		retVerStr = ".".join(str(x) for x in aVerL[0:3])
		retVerStr += '_' + ".".join(str(x) for x in aVerL[3:])
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
