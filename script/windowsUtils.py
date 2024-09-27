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

import copy
import glob
import os
import platform
import shutil
import subprocess
import sys
import tempfile

import jreUtils
import miscUtils
import deployJreDist

def buildRelease(aArgs, aBuildPath, aJreNodeL):
	# We mutate args - thus make a custom copy
	args = copy.copy(aArgs)

	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreVerSpec = args.jreVerSpec
	archStr = 'x64'
	platStr = 'windows'

	# Determine the types of builds we should do
	platformType = miscUtils.getPlatformTypes(args.platform, platStr)
	if platformType.nonJre == False and platformType.withJre == False:
		return;

	# Check our system environment before proceeding
	if checkSystemEnvironment() == False:
		return

	# Form the list of distributions to build (dynamic and static JREs)
	distL = []
	if platformType.nonJre == True:
		distL = [(appName + '-' + version, None)]
	if platformType.withJre == True:
		# Select the JreNode to utilize for static releases
		tmpJreNode = jreUtils.getJreNode(aJreNodeL, archStr, platStr, jreVerSpec)
		if tmpJreNode == None:
			# Let the user know that a compatible JRE was not found - thus no static release will be made.
			print('[Warning] No compatible JRE ({}) is available for the ({}) {} platform. A static release will not be provided for the platform.'.format(jreVerSpec, archStr, platStr.capitalize()))
		else:
			distL.append((appName + '-' + version + '-jre', tmpJreNode))

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(prefix=platStr, dir=aBuildPath)

	# Unpack the proper launch4j release (for the platform we are
	# running on) into the tmp (working) folder
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	l4jPath = os.path.join(appInstallRoot, 'template', 'launch4j')
	if platform.system() == 'Darwin':
		exeCmd = ['tar', '-C', tmpPath, '-xf', l4jPath + '/launch4j-3.14-macosx-x86.tgz']
	else:
		is64Bit = sys.maxsize > 2**32
		if is64Bit == True:
			exeCmd = ['tar', '-C', tmpPath, '-xf', l4jPath + '/launch4j-3.14-linux-x64.tgz']
		else:
			exeCmd = ['tar', '-C', tmpPath, '-xf', l4jPath + '/launch4j-3.14-linux.tgz']
	retCode = subprocess.call(exeCmd)
	if retCode != 0:
		print('Failed to extract launch4j package...')
		shutil.rmtree(tmpPath)
		return

	# Create the various distributions
	for (aDistName, aJreNode) in distL:
		print('Building {} distribution: {}'.format(platStr.capitalize(), aDistName))
		# Let the user know of the JRE release we are going to build with
		if aJreNode != None:
			print('\tUtilizing JRE: ' + aJreNode.getFile())

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, aDistName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(aBuildPath, dstPath, args, aJreNode)

		# Create the zip file
		zipFile = os.path.join(aBuildPath, aDistName + ".zip")
		cmd = ["jar", "-cMf", zipFile, "-C", dstPath, '.']
		print('\tForming zip file: ' + zipFile)
		proc = miscUtils.executeAndLog(cmd, "\t\tjar: ")
		if proc.returncode != 0:
			print('\tError: Failed to form zip file. Return code: ' + str(proc.returncode))
			print('\tAborting build of release: ' + os.path.basename(zipFile) + '\n')
		else:
			print('\tFinished building release: ' + os.path.basename(zipFile) + '\n')

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(aBuildPath, aRootPath, aArgs, aJreNode):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = aArgs.name

	# Form the app contents folder
	srcPath = os.path.join(aBuildPath, "delta")
	dstPath = os.path.join(aRootPath, "app")
	shutil.copytree(srcPath, dstPath, symlinks=True)

	# Copy dlls to the app directory so they can be found at launch
	dllDir = os.path.join(aRootPath, 'app', 'code', 'win')
	for libPath in glob.iglob(os.path.join(dllDir, "*.lib")):
		libFileName = os.path.basename(libPath)
		srcPath = os.path.join(dllDir, libFileName)
		linkPath = os.path.join(dstPath, libFileName)
		shutil.copy(srcPath, linkPath)
	for dllPath in glob.iglob(os.path.join(dllDir, "*.dll")):
		dllFileName = os.path.basename(dllPath)
		srcPath = os.path.join(dllDir, dllFileName)
		linkPath = os.path.join(dstPath, dllFileName)
		shutil.copy(srcPath, linkPath)

	# Setup the launcher contents
	dstPath = os.path.join(aRootPath, "launcher/" + deployJreDist.getAppLauncherFileName())
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(os.path.dirname(dstPath))
	shutil.copy(srcPath, dstPath);

	# Build the java component of the distribution
	if aArgs.javaCode != None:
		# Generate the iconFile
		winIconFile = None
		origIconFile = aArgs.iconFile
		if origIconFile != None:
			winIconFile = os.path.join(aRootPath, os.path.basename(origIconFile) + ".ico")
			cmd = ['convert', origIconFile, winIconFile]
			proc = miscUtils.executeAndLog(cmd, "\t\t(ImageMagick) convert: ")
			if proc.returncode != 0:
				if proc.returncode == None:
					print('\t\tImageMagick convert does not appear to be properly installed.')
				else:
					print('\t\tImageMagick convert failed with eCode: ' + str(proc.returncode))
				print('\t\tThere will be no windows icon file.')
				winIconFile = None

		# Form the launch4j config file
		configFile = os.path.join(aRootPath, appName + ".xml")
		buildLaunch4JConfig(configFile, aArgs, aJreNode, winIconFile)

		# Build the Windows executable
		tmpPath = os.path.dirname(aRootPath)
		launch4jExe = os.path.join(tmpPath, "launch4j", "launch4j")
		cmd = [launch4jExe, configFile]
		print('\tBuilding windows executable via launch4j')
		proc = miscUtils.executeAndLog(cmd, "\t\t")
		if proc.returncode != 0:
			print('\tError: Failed to build executable. Return code: ' + str(proc.returncode))

	# Unpack the JRE and set up the JRE tree
	if aJreNode != None:
		jreUtils.unpackAndRenameToStandard(aJreNode, aRootPath)

		# Perform cleanup
		os.remove(configFile)
		if winIconFile != None:
			os.remove(winIconFile)

def buildLaunch4JConfig(aDstFile, aArgs, aJreNode, aIconFile):
	with open(aDstFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:

		writeln(tmpFO, 0, "<launch4jConfig>")
		if aArgs.debug == True:
			writeln(tmpFO, 1, "<headerType>console</headerType>");
		else:
			writeln(tmpFO, 1, "<headerType>gui</headerType>");
		writeln(tmpFO, 1, "<outfile>" + aArgs.name + ".exe</outfile>");
		writeln(tmpFO, 1, "<dontWrapJar>true</dontWrapJar>");
		writeln(tmpFO, 1, "<errTitle>" + aArgs.name + "</errTitle>");
		writeln(tmpFO, 1, "<downloadUrl>http://java.com/download</downloadUrl>");
	# 	writeln(tmpFO, 1, "<supportUrl>url</supportUrl>");

	#	writeln(tmpFO, 1, "<cmdLine>app.cfg</cmdLine>");
		writeln(tmpFO, 1, "<chdir>app/</chdir>");
		writeln(tmpFO, 1, "<priority>normal</priority>");
		writeln(tmpFO, 1, "<customProcName>true</customProcName>");
		writeln(tmpFO, 1, "<stayAlive>false</stayAlive>");
		if aIconFile != None:
			writeln(tmpFO, 1, "<icon>" + aIconFile + "</icon>");

		writeln(tmpFO, 1, "<classPath>");
		writeln(tmpFO, 2, "<mainClass>appLauncher.AppLauncher</mainClass>");
		writeln(tmpFO, 2, "<cp>../launcher/" + deployJreDist.getAppLauncherFileName() + "</cp>");
		writeln(tmpFO, 1, "</classPath>");

		if aArgs.forceSingleInstance != False:
			writeln(tmpFO, 0, "");
			writeln(tmpFO, 1, "<singleInstance>");
			writeln(tmpFO, 2, "<mutexName>" + aArgs.name + ".mutex</mutexName>");
			writeln(tmpFO, 2, "<windowTitle>" + aArgs.name + "</windowTitle>");
			writeln(tmpFO, 1, "</singleInstance>");

		writeln(tmpFO, 0, "");
		writeln(tmpFO, 1, "<jre>");
		if aJreNode != None:
			jrePath = jreUtils.getBasePathFor(aJreNode)
			writeln(tmpFO, 2, "<path>" + jrePath + "</path>");
		else:
			jreVer = getJreMajorVersion(aArgs.jreVerSpec)
			writeln(tmpFO, 2, "<minVersion>" + jreVer + "</minVersion>");  # Valid values: '1.7.0' or '1.8.0' ...
		writeln(tmpFO, 2, "<jdkPreference>preferJre</jdkPreference>");  # Valid values: jreOnlyjdkOnly|preferJre|preferJdk
		for aJvmArg in aArgs.jvmArgs:
			writeln(tmpFO, 2, "<opt>" + aJvmArg + "</opt>");
		writeln(tmpFO, 2, "<opt>-Djava.system.class.loader=appLauncher.RootClassLoader</opt>");
		writeln(tmpFO, 1, "</jre>");

		writeln(tmpFO, 0, "");
		writeln(tmpFO, 1, "<messages>");
		writeln(tmpFO, 2, "<startupErr>" + aArgs.name + " error...</startupErr>");
		writeln(tmpFO, 2, "<bundledJreErr>Failed to locate the bundled JRE</bundledJreErr>");
		writeln(tmpFO, 2, "<jreVersionErr>Located JRE is not the proper version.</jreVersionErr>");
		writeln(tmpFO, 2, "<launcherErr>Failed to launch " + aArgs.name + "</launcherErr>");
		writeln(tmpFO, 1, "</messages>");

		writeln(tmpFO, 0, "")
		writeln(tmpFO, 0, "</launch4jConfig>")
		tmpFO.write('\n')


def checkSystemEnvironment():
	"""Checks to ensure that all system application / environment variables needed to build a Windows distribution are installed
	and properly configured. Returns False if the system environment is insufficient"""
	return True


def getJreMajorVersion(aJreVerSpec):
	"""Returns the minimum version of the JRE to utilize based on the passed in aJreVerSpec."""
	minJreVerStr = aJreVerSpec[0]

	try:
		verArr = jreUtils.verStrToVerArr(minJreVerStr)
	except:
		return '1.8.0'
	if len(verArr) <= 1: return '1.8.0'
	if len(verArr) == 2: verArr.append(0)
	return jreUtils.verArrToVerStr(verArr)


def writeln(f, tabL, aStr, tabStr='\t'):
	tStr = ''
	for i in range(tabL):
		tStr += tabStr
	f.write(tStr + aStr + '\n')

