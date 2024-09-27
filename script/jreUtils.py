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

import glob
import os
import re
import subprocess
import tempfile

import logUtils
import miscUtils
from logUtils import errPrintln, regPrintln
from miscUtils import ErrorDM


# JreNode class definition
class JreNode:
	def __init__(self, aArchitecture, aPlatform, aVersion, aFilePath):
		self.architecture = aArchitecture
		self.platform = aPlatform
		self.version = aVersion
		self.filePath = aFilePath

	def getArchitecture(self):
		"""Returns the JRE's architecture."""
		return self.architecture

	def getPlatform(self):
		"""Returns the JRE's platform."""
		return self.platform

	def getVersion(self):
		"""Returns the JRE's version."""
		return self.version

	def getFile(self):
		"""Returns the (tar.gz or zip) file which contains the packaged JRE."""
		return self.filePath



def getBasePathFor(aJreNode):
	"""Returns the JRE (base) path that should be used to access the JRE found in the specified JreNode.
	This is needed since different JRE tar.gz files have been found to have different top level paths. Using
	this method ensures consistency between JRE tar.gz releases after the tar.gz file is unpacked.

	Please note that legacy JREs will expand to a different path than non-legacy JREs.
	"""
	verArr = aJreNode.getVersion()
	verStr = verArrToVerStr(verArr)
	if verStr.startswith("1.") == True:
		basePath = 'jre' + verStr
	else:
		basePath = 'jre-' + verStr
	return basePath;


def getJreNodesForVerStr(aJreNodeL, aVerStr):
	"""Returns the JREs matching the specified specific JRE version specification. This will return an empty
	list if there is no JRE that is sufficient for the request.

	aJreNodeL --- The list of valid JREs
	aVerStr   --- The version of interest. The version must be fully qualified.

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
	# Retrieve the target version - and ensure it is fully qualified
	targVer = verStrToVerArr(aVerStr)
	if len(targVer) < 3:
		raise ErrorDM('The specified version is not a fully qualified version. Version: ' + aVerStr)
	if targVer[0] == 1 and len(targVer) != 4:
		raise ErrorDM('Legacy JRE releases require exactly 4 elements for the release. Legacy releases refer to any release before Java 9. Version: ' + aVerStr)

	# Search the appropriate JREs for exact matches (of targVer)
	retL = []
	for aJreNode in aJreNodeL:
		if aJreNode.getVersion() == targVer:
			retL.append(aJreNode)

	return retL;


def getJreNode(aJreNodeL, aArchStr, aPlatStr, aJvmVerSpec):
	"""Returns the JRE for the appropriate platform and JRE release. If there are several possible	matches then
	the JRE with the latest version will be returned.
	aJreNodeL   --- The list of available JREs.
	aArchStr    --- The architecture of the JRE of interest. Architecture will typically be one of: 'x64'
	aPlatStr    --- The platform of the JRE of interest. Platform will typically be one of: 'linux', 'macosx', 'windows'
	aJvmVerSpec --- A list of 1 or 2 items that define the range of JRE versions you are interested in. If the
	                list has just one item then that version will be used as the minimum version.

	Method will return None if there is no JRE that is sufficient for the request. Note if you do not care about
	any specific update for a major version of JAVA then just specify the major version. Example '1.8' instead of
	1.8.0_73'"""
	# Transform a single string to a list of size 1
	if isinstance(aJvmVerSpec, str):
		aJvmVerSpec = [aJvmVerSpec]

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

	# Evaluate all available JREs for potential matches
	matchL = []
	for aJreNode in aJreNodeL:
		# Ensure the architecture is a match
		if aArchStr != aJreNode.getArchitecture():
			continue
		# Ensure the platform is a match
		if aPlatStr != aJreNode.getPlatform():
			continue

		# Ensure that the JRE's version is in range of minJvmVer and maxJvmVer
		evalVer = aJreNode.getVersion()
		if minJvmVer != None and isVerAfterAB(minJvmVer, evalVer) == True:
			continue
		if maxJvmVer != None and isVerAfterAB(evalVer, maxJvmVer) == True:
			continue

		matchL.append(aJreNode);

	# Bail if no matches were found
	if len(matchL) == 0:
		return None

	# Determine the best match of all the matches
	bestJreNode = matchL[0]

	for aJreNode in matchL:
		bestVer = bestJreNode.getVersion()
		testVer = aJreNode.getVersion()
		for b, t in zip(bestVer, testVer):
			if b == t:
				continue
			try:
				if t > b:
					bestJreNode = aJreNode
					break;
			except:
				continue

	return bestJreNode


def loadJreCatalog(aFile):
	"""
	Utility method that loads the specified (DistMaker) JRE catalog file and returns a list of
	JREs. The list of JREs will be grouped by JVM version and each individual JRE will have the
	following information:
	- architecture
	- platform
	- file path
	"""
	retL = []

	validArchL = ['x64']
	validPlatL = ['linux', 'macosx', 'windows']
	workJreVer = None;

	pathS = set()

	# Read the file
	regPrintln('Loading JRE catalog: {}'.format(aFile))
	with open(aFile, mode='rt', encoding='utf-8') as tmpFO:
		for (lineNum, aLine) in enumerate(tmpFO, 1):
			# Skip empty lines / comments
			line = aLine.strip()
			if len(line) == 0 or line[0] == '#':
				continue

			# Parse the JRE version
			tokenL = line.split(',')
			if tokenL[0] == 'jre' and len(tokenL) == 2:
				workJreVer = verStrToVerArr(tokenL[1])
				continue

			errMsgL = []

			# Parse the JRE Node
			if tokenL[0] == 'F' and len(tokenL) == 4:
				archStr = tokenL[1]
				platStr = tokenL[2]
				pathStr = tokenL[3]

				# Ensure the JRE version section has been declared
				if workJreVer == None:
					errMsg = 'JRE version section must be declared first. Skipping input: {}'.format(aLine[:-1])
					errPrintln('\tERROR: [L: {}] {}'.format(lineNum, errMsg))
					continue

				# Ensure the path is a file
				if os.path.isfile(pathStr) == False:
					errMsgL += ['JRE file does not exist! Path: {}'.format(pathStr)];
				# Ensure the architecture is recognized
				if (archStr in validArchL) == False:
					errMsgL += ['Architecture is not recognized. Valid: {}   ->   Input: {}'.format(validArchL, archStr)];
				# Ensure the platform is recognized
				if (platStr in validPlatL) == False:
					errMsgL += ['Platform is not recognized. Valid: {}   ->   Input: {}'.format(validPlatL, platStr)];
				# Ensure the reference JRE file has not been seen before
				if pathStr in pathS:
					errMsgL += ['JRE file has already been specified earlier! Path: {}'.format(pathStr)];

				# If no errors then form the JreNode
				if len(errMsgL) == 0:
					retL += [JreNode(archStr, platStr, workJreVer, pathStr)]
					pathS.add(pathStr)
					continue

			# Unrecognized line
			else:
				errMsgL += ['Input line is not recognized. Input: {}'.format(aLine[:-1])]

			# Log the errors
			for aErrMsg in errMsgL:
				errPrintln('\tERROR: [L: {}] {}'.format(lineNum, aErrMsg))

	regPrintln('\tLoaded JRE declarations: {}\n'.format(len(retL)))

	return retL;


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


def unpackAndRenameToStandard(aJreNode, aDestPath):
	"""Method that will unpack the specified JRE (tar.gz or zip) into the folder aDestPath. The unpacked JRE folder will also
	be renamed to a standard convention: jre<A>.<B> where
		A: represents the major (classical) version
		B: represents the minor version
	Hence the JRE tar.gz file: jre-8u73-linux-x64.tar.gz should be unpacked to jre1.8.73"""
	# Ensure the aDestPath exists
	if os.path.isdir(aDestPath) == False:
		os.makedirs(aDestPath)

	# Unpack to a temporary folder at aDestPath
	tmpPath = tempfile.mkdtemp(dir=aDestPath)
	jreFile = aJreNode.getFile()
	if len(jreFile) > 4 and jreFile.upper()[-3:] == 'ZIP':
		subprocess.check_call(["unzip", "-d", tmpPath, "-q", jreFile], stderr=subprocess.STDOUT)
	else:
		subprocess.check_call(["tar", "-C", tmpPath, "-xf", jreFile], stderr=subprocess.STDOUT)

	# Rename the single extracted folder to a standard convention and move to aDestPath
	fileL = glob.glob(os.path.join(tmpPath, '*'))
	if len(fileL) != 1 or os.path.isdir(fileL[0]) == False:
		errPrintln('Fatal error while unpacking JRE package file. Did not resolve to single folder! Terminating build process!')
		errPrintln('\tJRE package file: ' + aJreNode.getFile())
		errPrintln('\tUnpacked files: ' + str(fileL))
		exit(-1)
	jreBasePath = getBasePathFor(aJreNode)
	targPath = os.path.join(aDestPath, jreBasePath)
	os.rename(fileL[0], targPath)

	# Warn if hidden files are located (and remove them)
	# It appears that some JREs (Macosx specific? may include spurious hidden files.)
	for aName in os.listdir(tmpPath):
		spurFile = os.path.join(tmpPath, aName)
		errPrintln('\tFound spurious (hidden) file: ' + spurFile)
		errPrintln('\t\tAuto removing file: ' + spurFile)
		os.remove(spurFile)

	# Remove the temporary path
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
		raise ErrorDM('The parameter jreVersion can either be 1 or 2 values. The first value is the minVersion and (if specified) the later is the maxVersion. Values provided: ' + str(len(aVerSpec)))

	# Ensure the minVer is valid
	minVer = verStrToVerArr(aVerSpec[0])
	if minVer == None:
		raise ErrorDM('The specified string: "' + aVerSpec[0] + ' is not a valid JRE version specification.')

	# Ensure the minVer is not before Java 1.7
	if isVerAfterAB([1, 7], minVer) == True:
		raise ErrorDM('In the parameter jreVersion, the minVer ({}) must be later than 1.7.'.format(aVerSpec[0]))

	# Bail if only the minVer was specified
	if len(aVerSpec) == 1:
		return

	# Ensure the maxVer is valid and is after minVer
	maxVer = verStrToVerArr(aVerSpec[1])
	if maxVer == None:
		raise ErrorDM('The specified string: "' + aVerSpec[1] + ' is not a valid JRE version specification.')

	# Ensure the minVer is not later than the maxVer. Note it is ok if it is equal
	if isVerAfterAB(minVer, maxVer) == True:
		raise ErrorDM('In the parameter jreVersion, the minVer ({}) is later than the maxVer ({}).'.format(aVerSpec[0], aVerSpec[1]))


def verStrToVerArr(aVerStr):
	"""Utility method to convert a jvm version string to the equivalent integral version (list) component. Each component in the array will be integral.
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
	"""Utility method to convert an integral version (list) to the equivalent jvm version string. Each component in the list must be integral.
	Thus the following will get transformed to:
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
