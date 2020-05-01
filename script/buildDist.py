#! /usr/bin/env python

from __future__ import print_function
import argparse
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import tempfile

import distutils.spawn
import jreUtils
import miscUtils
import appleUtils
import linuxUtils
import windowsUtils
from miscUtils import ErrorDM
from miscUtils import FancyArgumentParser


def buildCatalogFile(aArgs, aDeltaPath):
	# Build the delta catalog
	records = []

	# Record the digest used
	digestType = aArgs.digest
	record = ('digest', digestType)
	records.append(record)

	# Record the required JRE version
	jreVerSpec = aArgs.jreVerSpec
	record = ('jre', ",".join(jreVerSpec))
	records.append(record)

	snipLen = len(aDeltaPath) + 1
#	for root, dirNames, fileNames in os.walk(aDeltaPath, onerror=failTracker.recordError):
	for root, dirNames, fileNames in os.walk(aDeltaPath):

		# Presort the results alphabetically
		dirNames.sort()
		fileNames.sort()

		# Form the record for the current directory (PathNode)
		fullPath = root
		relPath = fullPath[snipLen:]
		if len(relPath) > 0:
			record = ('P', relPath)
			records.append(record)

		# Since we do not visit symbolic links, notify the user of all directories that are symbolic
		for dirName in dirNames:
			fullPath = os.path.join(root, dirName)
			if os.path.islink(fullPath) == True:
				print("Path links are not supported... Skipping: " + fullPath + "\n")

		# Record all of the file nodes
		for fileName in fileNames:
			fullPath = os.path.join(root, fileName)
			if os.path.islink(fullPath) == True:
				print("File links are not supported... Skipping: " + fullPath + "\n")
			elif os.path.isfile(fullPath) == True:
				# Gather the various stats of the specified file
				stat = os.stat(fullPath)
				digestVal = miscUtils.computeDigestForFile(fullPath, digestType)
				relPath = fullPath[snipLen:]
				record = ('F', digestVal, str(stat.st_size), relPath)
				records.append(record)
			else:
				print("Undefined node. Full path: " + fullPath + "\n")

	# Save the records to the catalog file
	dstPath = os.path.join(aDeltaPath, "catalog.txt")
	f = open(dstPath, 'wb')
	for aRecord in records:
		f.write(','.join(aRecord) + '\n')

	f.write('exit\n')
	f.close()


def checkForRequiredApplicationsAndExit():
	"""Method to ensure we have all of the required applications installed to support building of distributions.
	If there are mandatory applications that are missing then this will be printed to stderr and the program will exit.
	The current set of required applications are:
	java, jar, (genisoimage or hdiutil)"""
	# Check for java (version 1.8 or later)
	evalPath = distutils.spawn.find_executable('java')
	errL = []
	if evalPath == None:
		errL.append('Failed while trying to locate java. Please install Java')
	else:
		tmpStr = subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT)
		pattern = '\"(\d+\.\d+).*\"'
		verStr = re.search(pattern, tmpStr).groups()[0]
		verVal = float(verStr)
		if verVal < 1.8:
			errL.append('Installed version of Java is too old. Require Java 1.8. Installed version: {}'.format(verVal))

	# Check for jar
	evalPath = distutils.spawn.find_executable('jar')
	if evalPath == None:
		errL.append('Failed while trying to locate jar. Please install jar (typically included with Java)')

	# Check for genisoimage or hdiutil
	genisoimagePath = distutils.spawn.find_executable('genisoimage')
	hdiutilPath = distutils.spawn.find_executable('hdiutil')
	if genisoimagePath == None and hdiutilPath == None:
		if platform.system() == 'Darwin':
			errL.append('Failed while trying to locate executable hdiutil. Please install hdiutil')
		else:
			errL.append('Failed while trying to locate executable genisoimage. Please install genisoimage')

	# Bail if there are no issues
	if len(errL) == 0:
		return

	# Log the issues and exit
	print('There are configuration errors with the environment or system.')
#	print('System Path:' + str(sys.path))
	print('Please correct the following:')
	for aError in errL:
		print('\t' + aError)
	warnL = checkForSuggestedApplications()
	if len(warnL) > 0:
		print('In addition please fix the following for full program functionality:')
		for aWarn in warnL:
			print('\t' + aWarn)
	sys.exit(0)


def checkForSuggestedApplications():
	"""Method to check for any suggested missing applications. If all suggested applications are installed then this method will return None,
	otherwise it will return a list of messages which describe the missing applications and the corresponding missing functionality.
	The current set of suggested applications are:
	ImageMagick:convert"""
	retL = []
	evalPath = distutils.spawn.find_executable('convert')
	if evalPath == None:
		retL.append('Application \'convert\' was not found. Please install (ImageMagick) convert')
		retL.append('\tWindows icons will not be supported when using argument: -iconFile.')
	return retL


def checkReadable(src, names):
	"""Utility method that will ensure that all of the files formed by <src>/<aName> exist
	and are readable. An ErrorDM will be raised if any of the files are not readable. This method
	is passed into shutil.copytree() and provides a way to check for issues with any files that
	are about to be accessed."""
	for aName in names:
		tmpFile = os.path.join(src, aName)
		# Ensure that symbolic links are not broken
		if os.path.islink(tmpFile) == True and os.path.exists(tmpFile) == False:
			raise ErrorDM('Broken symbolic link: {}'.format(tmpFile))
		# Ensure that files are readable
		if os.path.isfile(tmpFile) == True and os.access(tmpFile, os.R_OK) == False:
			raise ErrorDM('File is not readable: {}'.format(tmpFile))

	# We actually do not do any filtering
	return []


def getClassPath(aJavaCodePath):
	retL = []

	# Ensure the aJavaCodePath has a trailing slash
	# to allow for proper computation of clipLen
	if aJavaCodePath.endswith('/') == False:
		aJavaCodePath += '/'
	clipLen = len(aJavaCodePath)

	# Form the default list of all jar files
	for path, dirs, files in os.walk(aJavaCodePath):
		files.sort()
		for file in files:
			if len(file) > 4 and file[-4:] == '.jar':
				filePath = os.path.join(path, file)
				filePath = filePath[clipLen:]
				retL.append(filePath)
#				print('Found jar file at: ' + filePath)

	return retL


if __name__ == "__main__":
	# Require Python version 2.7 or later
	targVer = (2, 7)
	miscUtils.requirePythonVersion(targVer)

	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, miscUtils.handleSignal)

	# Set up the argument parser
	parser = FancyArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('--help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('--name', help='The name of the application.')
	parser.add_argument('--version', default='0.0.1', help='The version of the application.')
	parser.add_argument('--mainClass', help='Application main entry point.')
	parser.add_argument('--appArgs', help='Application arguments. Note that this argument must ALWAYS be the last specified!', nargs=argparse.REMAINDER, default=[])
	parser.add_argument('--dataCode', help='A list of supporting files or folders for the application. All items will be copied to the data folder. Symbolic links will not be presereved.', nargs='+', default=[])
	parser.add_argument('--javaCode', help='A folder which contains the Java build.')
	parser.add_argument('--jreCatalog', help='A JRE catalog file. This file provides the listing of available JREs for DistMaker to utilize.')
	parser.add_argument('--jreVersion', dest='jreVerSpec', help='JRE version to utilize. This should be either 1 or 2 values where each value should be something like 1.7 or 1.8 or 1.8.0_34.'
							+ ' If 2 values are specified than the second value must be later than the first value. Any static build will be built with the latest allowable JRE.'
							+ ' Note there should be corresponding JREs for each relevant platform specified via arg: --jreCatalog', nargs='+', default=None)
	parser.add_argument('--jvmArgs', help='JVM arguments.', nargs='+', default=[])
	parser.add_argument('--classPath', help='Class path listing of jar files relative to javaCode. Leave blank for auto determination.', nargs='+', default=[])
	parser.add_argument('--debug', help='Turn on debug options for built applications.', action='store_true', default=False)
	parser.add_argument('--company', help='Company / Provider info.')
	parser.add_argument('--bgFile', help='Background file used for apple dmg file.')
	parser.add_argument('--iconFile', help='PNG file used for linux/windows icon.')
	parser.add_argument('--icnsFile', help='Icon file used for apple build.')
	parser.add_argument('--forceSingleInstance', help='Force the application to have only one instance.', default=False)
	parser.add_argument('--digest', help='Digest used to ensure integrity of application upgrades. Default: sha256', choices=['md5', 'sha256', 'sha512'], default='sha256')
	parser.add_argument('--enableJmx', help='Enables JMX technology on the target client. Allows one to attach jconsole, jvisualvm, or other JMX tools.', action='store_true', default=False)
	parser.add_argument('--platform', help='Target platforms to build. Choices are: [linux, macosx, windows]. Note the following (append) modifiers.'
		+ ' Modifier \'-\' results in only the non-JRE build. Modifier \'+\' results in only the JRE build. Default: linux, macosx+, windows', nargs='+', default=['linux', 'macosx+', 'windows'],
		choices=['linux', 'linux-', 'linux+', 'macosx', 'macosx-', 'macosx+', 'windows', 'windows-', 'windows+'], metavar='PLATFORM')
#	parser.add_argument('-bundleId', help='Apple specific id descriptor.')

	# Intercept any request for a  help message and bail
	argv = sys.argv;
	if '-h' in argv or '-help' in argv or '--help' in argv:
		parser.print_help()
		exit()

	# Check to ensure all of the required applications are installed before proceeding
	checkForRequiredApplicationsAndExit()

	# Parse the args
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()

	# Ensure the bare minimum options were specified
	errL = [];
	if args.name == None:
		errL.append('--name')
	if args.javaCode == None:
		errL.append('--javaCode')
	if args.jreCatalog == None:
		errL.append('--jreCatalog')
	if args.jreVerSpec == None:
		errL.append('--jreVersion')
	if args.mainClass == None:
		errL.append('--mainClass')
	if len(errL) != 0:
		print('At a minimum the following must be specified: ' + str(errL) + '.\nExiting...')
		exit()

	# Ensure the name is not reserved: ['jre', 'launcher']
	if args.name.lower() == 'jre' or args.name == 'launcher':
		print('The application can not be named: {}. That name is reserved.\n'.format(args.name))
		exit()

	# Load the JRE catalog
	errMsg = None
#	if args.jreCatalog == None:
#		errMsg = 'A JRE catalog must be specified! Please specify --jreCatalog'
	if os.path.exists(args.jreCatalog) == False:
		errMsg = 'The specified JRE catalog does not exist! File: ' + args.jreCatalog
	elif os.path.isfile(args.jreCatalog) == False:
		errMsg = 'The specified JRE catalog is not a valid file! File: ' + args.jreCatalog
	if errMsg != None:
		print(errMsg + '\n')
		exit()

	jreNodeL = jreUtils.loadJreCatalog(args.jreCatalog)
	if len(jreNodeL) == 0:
		print('Warning: Failed to load any JREs from the JRE catalog. Only non bundled JRE applications can be built!\n')

	# Warn if there are not any valid targets
	if args.platform == ['macosx-']:
		print('The only release specified is Macosx without JRE. This is currently unsupported.\nExiting...')
		exit()

#
#	# Ensure java options are specified properly
#	if (args.javaCode == None and args.mainClass != None) or (args.javaCode != None and args.mainClass == None):
#		print('Both javaCode and mainClass must be specified, if either are specified. Exiting...')
#		exit();

	# Validate the jreVerSpec argument
	try:
		jreUtils.validateJreVersionSpec(args.jreVerSpec)
	except ErrorDM as aExp:
		print('The specified jreVerVersion is invalid. Input: {}'.format(args.jreVerSpec))
		print('  ' + aExp.message + '\n', file=sys.stderr)
		exit()

	# Form the classPath if none specified
	if args.javaCode != None and len(args.classPath) == 0:
		args.classPath = getClassPath(args.javaCode)

	# Clean up the jvmArgs to replace the escape sequence '\-' to '-'
	# and to ensure that all the args start with the '-' character
	newJvmArgs = []
	for aJvmArg	in args.jvmArgs:
		if aJvmArg.startswith('\-') == True:
			newJvmArgs.append(aJvmArg[1:])
		elif aJvmArg.startswith('-') == False:
			aJvmArg = '-' + aJvmArg
			newJvmArgs.append(aJvmArg)
		else:
			newJvmArgs.append(aJvmArg)
	args.jvmArgs = newJvmArgs

	# Add the flag -Dcom.sun.management.jmxremote to allow JMX clients to attach to the Java application
	# Add the flag -Djava.rmi.server.hostname=localhost to allow connections when using VPN. Not sure why???
	# It appears that when the root class loader is replaced then JMX is disabled by default
	# See also: http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html
	if args.enableJmx == True:
		args.jvmArgs.append('-Dcom.sun.management.jmxremote')
		args.jvmArgs.append('-Djava.rmi.server.hostname=localhost')

	# Bail if the release has already been built
	buildPath = os.path.abspath(args.name + '-' + args.version)
	if (os.path.exists(buildPath) == True):
		print('   [ERROR] The release appears to be built. Path: ' + buildPath + '\n')
		exit(-1)

	# Let the user know of any missing functionality
	warnL = checkForSuggestedApplications()
	if len(warnL) > 0:
		print('All suggested applications are not installed. There will be reduced functionality:')
		for aWarn in warnL:
			print('\t' + aWarn)
		print()

	# Form the buildPath
	os.makedirs(buildPath)

	# Build the Delta (diffs) contents
	deltaPath = os.path.join(buildPath, "delta")
	deltaCodePath = os.path.join(deltaPath, "code")
	deltaDataPath = os.path.join(deltaPath, "data")

	# Ensure the user does not specify the top level data folder so that a ~/data/data folder is not inadvertently created
	if len(args.dataCode) == 1 and args.dataCode[0].rstrip('/').endswith('data'):
		srcPath = args.dataCode[0].rstrip('/')
		print('   [ERROR] The specified dataCode path will result in a data folder inside another data folder. Refusing action. Please specify the individual data files/folders.')
		print('           Consider using:')
		print('               --dataCode ' + srcPath + '/*')
		print('            instead of:')
		print('               --dataCode ' + args.dataCode[0] + '\n')
		shutil.rmtree(buildPath)
		exit(-1)

	# Copy the dataCode to the delta location
	os.makedirs(deltaDataPath)
	for aSrcPath in args.dataCode:
		if os.path.exists(aSrcPath) == False:
			print('   [ERROR] The dataCode path does not exist. Path: ' + aSrcPath + '\n')
			shutil.rmtree(buildPath)
			exit(-1)
		elif os.path.isfile(aSrcPath):
			dstPath = os.path.join(deltaDataPath, os.path.basename(aSrcPath))
			shutil.copy(aSrcPath, dstPath)
			continue
		elif os.path.isdir(aSrcPath):
			aSrcPath = aSrcPath.rstrip('/')
			dstPath = os.path.join(deltaDataPath, os.path.basename(aSrcPath))
			shutil.copytree(aSrcPath, dstPath, symlinks=False)
		else:
			print('   [ERROR] The dataCode path is not a valid file or folder. Path: ' + aSrcPath + '\n')
			shutil.rmtree(buildPath)
			exit(-1)

	# Build the java component of the distribution
	if args.javaCode != None:
		# Copy the javaCode to the proper location
		srcPath = args.javaCode
		if os.path.isdir(srcPath) == False:
			print('   [ERROR] The javaCode path does not exist. Path: ' + srcPath + '\n')
			shutil.rmtree(buildPath)
			exit(-1)
		dstPath = deltaCodePath;
		try:
			shutil.copytree(srcPath, dstPath, symlinks=False, ignore=checkReadable)
		except (ErrorDM, shutil.Error) as aExp:
			print('   [ERROR] There were issues while copying the javaCode files. Path: ' + srcPath)
			print('      {}\n'.format(aExp), file=sys.stderr)
			shutil.rmtree(buildPath)
			exit(-1)

	# Form the app.cfg file
	dstPath = os.path.join(buildPath, "delta/app.cfg")
	miscUtils.buildAppLauncherConfig(dstPath, args)

	# Build the delta catalog
	buildCatalogFile(args, deltaPath)

	# Build the Apple release
	appleUtils.buildRelease(args, buildPath, jreNodeL)

	# Build the Linux release
	linuxUtils.buildRelease(args, buildPath, jreNodeL)

	# Build the Windows release
	windowsUtils.buildRelease(args, buildPath, jreNodeL)

	# Copy over the deploy script
	srcPath = os.path.join(miscUtils.getInstallRoot(), "deployAppDist.py")
	shutil.copy(srcPath, buildPath)

	print('Finished building all distributions.\n')

