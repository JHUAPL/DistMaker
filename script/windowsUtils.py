#! /usr/bin/env python

import copy
import os
import shutil
import tempfile
import glob

import jreUtils
import miscUtils


def buildRelease(args, buildPath):
	# We mutate args - thus make a custom copy
	args = copy.copy(args)

	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreRelease = args.jreVersion
	platformStr = 'windows'

	# Check our system environment before proceeding
	if checkSystemEnvironment() == False:
		return

	# Attempt to locate a default JRE if none is specified in the args.
	if jreRelease == None:
		jreRelease = jreUtils.getDefaultJreRelease(platformStr)
	# Let the user know if the 'user' specified JRE is not available and locate an alternative
	elif jreUtils.getJreTarGzFile(platformStr, jreRelease) == None:
		print('[Warning] User specified JRE ({0}) is not available for {1} platform. Searching for alternative...'.format(jreRelease, platformStr.capitalize()))
		jreRelease = jreUtils.getDefaultJreRelease(platformStr)
	args.jreRelease = jreRelease

	# Form the list of distributions to build (dynamic and static JREs)
	distList = [(appName + '-' + version, None)]
	jreTarGzFile = jreUtils.getJreTarGzFile(platformStr, jreRelease)
	if jreTarGzFile != None:
		distList.append((appName + '-' + version + '-jre', jreTarGzFile))

	# Create a tmp (working) folder
	tmpPath = tempfile.mkdtemp(prefix=platformStr, dir=buildPath)

	# Create the various distributions
	for (aDistName, aJreTarGzFile) in distList:
		print('Building {0} distribution: {1}'.format(platformStr.capitalize(), aDistName))
		# Let the user know of the JRE tar.gz  we are going to build with
		if aJreTarGzFile != None:
			print('\tUtilizing jreRelease: ' + aJreTarGzFile)

		# Create the (top level) distribution folder
		dstPath = os.path.join(tmpPath, aDistName)
		os.mkdir(dstPath)

		# Build the contents of the distribution folder
		buildDistTree(buildPath, dstPath, args, aJreTarGzFile)

		# Create the zip file
		zipFile = os.path.join(buildPath, aDistName + ".zip")
		cmd = ["jar", "-cMf", zipFile, "-C", dstPath, '.']
		print('\tForming zip file: ' + zipFile)
		proc = miscUtils.executeAndLog(cmd, "\t\tjar: ")
		if proc.returncode != 0:
			print('\tError: Failed to form zip file. Return code: ' + str(proc.returncode))
			print('\tAborting build of release: ' + os.path.basename(zipFile))
		else:
			print('\tFinished building release: ' + os.path.basename(zipFile))

	# Perform cleanup
	shutil.rmtree(tmpPath)


def buildDistTree(buildPath, rootPath, args, jreTarGzFile):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appName = args.name
	jreRelease = args.jreRelease

	# Form the app contents folder
	srcPath = os.path.join(buildPath, "delta")
	dstPath = os.path.join(rootPath, "app")
	shutil.copytree(srcPath, dstPath, symlinks=True)

	# Copy dlls to the app directory so they can be found at launch
	dllDir = os.path.join(rootPath, 'app', 'code', 'win')
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
	exePath = os.path.join(rootPath, "launcher")
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(exePath)
	shutil.copy(srcPath, exePath);

	# Build the java component of the distribution
	if args.javaCode != None:
		# Generate the iconFile
		winIconFile = None
		origIconFile = args.iconFile
		if origIconFile != None:
			winIconFile = os.path.join(rootPath, os.path.basename(origIconFile) + ".ico")
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
		configFile = os.path.join(rootPath, appName + ".xml")
		buildLaunch4JConfig(configFile, args, jreTarGzFile, winIconFile)

		# Build the Windows executable
		launch4jExe = os.path.join(appInstallRoot, "launch4j", "launch4j")
		cmd = [launch4jExe, configFile]
		print('\tBuilding windows executable via launch4j')
		proc = miscUtils.executeAndLog(cmd, "\t\t")
		if proc.returncode != 0:
			print('\tError: Failed to build executable. Return code: ' + str(proc.returncode))

	# Unpack the JRE and set up the JRE tree
	if jreTarGzFile != None:
		jreUtils.unpackAndRenameToStandard(jreTarGzFile, rootPath)

		# Perform cleanup
		os.remove(configFile)
		if winIconFile != None:
			os.remove(winIconFile)

def buildLaunch4JConfig(destFile, args, jreTarGzFile, iconFile):
	f = open(destFile, 'wb')

	writeln(f, 0, "<launch4jConfig>")
	if args.debug == True:
		writeln(f, 1, "<headerType>console</headerType>");
	else:
		writeln(f, 1, "<headerType>gui</headerType>");
	writeln(f, 1, "<outfile>" + args.name + ".exe</outfile>");
	writeln(f, 1, "<dontWrapJar>true</dontWrapJar>");
	writeln(f, 1, "<errTitle>" + args.name + "</errTitle>");
	writeln(f, 1, "<downloadUrl>http://java.com/download</downloadUrl>");
# 	writeln(f, 1, "<supportUrl>url</supportUrl>");

	writeln(f, 1, "<cmdLine>app.cfg</cmdLine>");
	writeln(f, 1, "<chdir>app/</chdir>");
	writeln(f, 1, "<priority>normal</priority>");
	writeln(f, 1, "<customProcName>true</customProcName>");
	writeln(f, 1, "<stayAlive>false</stayAlive>");
	if iconFile != None:
		writeln(f, 1, "<icon>" + iconFile + "</icon>");

	writeln(f, 1, "<classPath>");
	writeln(f, 2, "<mainClass>appLauncher.AppLauncher</mainClass>");
	writeln(f, 2, "<cp>../launcher/appLauncher.jar</cp>");
	writeln(f, 1, "</classPath>");

	if args.forceSingleInstance != False:
		writeln(f, 0, "");
		writeln(f, 1, "<singleInstance>");
		writeln(f, 2, "<mutexName>" + args.name + ".mutex</mutexName>");
		writeln(f, 2, "<windowTitle>" + args.name + "</windowTitle>");
		writeln(f, 1, "</singleInstance>");

	writeln(f, 0, "");
	writeln(f, 1, "<jre>");
	if jreTarGzFile != None:
		jrePath = jreUtils.getBasePathForJreTarGzFile(jreTarGzFile)
		writeln(f, 2, "<path>" + jrePath + "</path>");
	else:
		jreVer = getJreMajorVersion(args.jreRelease)
		writeln(f, 2, "<minVersion>" + jreVer + "</minVersion>");  # Valid values: '1.7.0' or '1.8.0' ...
	writeln(f, 2, "<jdkPreference>preferJre</jdkPreference>");  # Valid values: jreOnlyjdkOnly|preferJre|preferJdk
	for aJvmArg in args.jvmArgs:
		writeln(f, 2, "<opt>" + aJvmArg + "</opt>");
	writeln(f, 2, "<opt>-Djava.system.class.loader=appLauncher.RootClassLoader</opt>");
	writeln(f, 1, "</jre>");

	writeln(f, 0, "");
	writeln(f, 1, "<messages>");
	writeln(f, 2, "<startupErr>" + args.name + " error...</startupErr>");
	writeln(f, 2, "<bundledJreErr>Failed to locate the bundled JRE</bundledJreErr>");
	writeln(f, 2, "<jreVersionErr>Located JRE is not the proper version.</jreVersionErr>");
	writeln(f, 2, "<launcherErr>Failed to launch " + args.name + "</launcherErr>");
	writeln(f, 1, "</messages>");

	writeln(f, 0, "")
	writeln(f, 0, "</launch4jConfig>")
	f.write('\n')
	f.close()


def checkSystemEnvironment():
	"""Checks to ensure that all system application / environment variables needed to build a Windows distribution are installed
	and properly configured. Returns False if the system environment is insufficient"""
	# Bail if launch4j is not installed
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	launch4jExe = os.path.join(appInstallRoot, "launch4j", "launch4j")
	if os.path.exists(launch4jExe) == False:
		print('Launch4j is not installed. Windows releases will not be built.')
		return False

	return True


def getJreMajorVersion(aJreRelease):
	"""Returns the minimum version of the JRE to utilize based on the passed in JRE release. 
	The passed in value should be of the form X.X.X ---> example: 1.8.73 would result in 1.8.0 
	If aJreRelease is invalid then returns the default value of: 1.8.0"""
	if aJreRelease == None: return '1.8.0'
	verList = aJreRelease.split('.')
	if len(verList) == 2: verList.append('0')
	if len(verList) == 3 and verList[2] == '': verList[2] = '0'
	if len(verList) != 3: return '1.8.0'
	try:
		[int(y) for y in verList]
	except:
		return '1.8.0'
	verList[2] = '0'
	return ".".join(verList)


def writeln(f, tabL, aStr, tabStr='\t'):
	tStr = ''
	for i in range(tabL):
		tStr += tabStr
	f.write(tStr + aStr + '\n')

