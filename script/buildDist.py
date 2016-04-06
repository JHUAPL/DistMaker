#! /usr/bin/env python

import argparse
import distutils.spawn
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time

#import buildCatalog
import miscUtils
import appleUtils
import linuxUtils
import windowsUtils


class FancyArgumentParser(argparse.ArgumentParser):
	def __init__(self, *args, **kwargs):
		self._action_defaults = {}
		argparse.ArgumentParser.__init__(self, *args, **kwargs)

	def convert_arg_line_to_args(self, arg_line):
		# Add the line as an argument if it does not appear to be a comment
		if len(arg_line) > 0 and arg_line.strip()[0] != '#':
			yield arg_line
#			argsparse.ArgumentParser.convert_arg_line_to_args(arg_line)
#		else:
#			print('Skipping line: ' + arg_line)

#		# Example below will break all space separated lines into individual arguments
#		for arg in arg_line.split():
#			if not arg.strip():
#				continue
#			yield arg


def buildCatalogFile(args, deltaPath):
	# Build the delta catalog
	records = []

	# Record the digest used
	digestType = args.digest
	record = ('digest', digestType)
	records.append(record)

	# Record the JRE required
	jreVersion = args.jreVersion
	if jreVersion == None:
		jreVersion = jreUtils.getDefaultJrVersion()
	if jreVersion != None:
		record = ('jre', jreVersion)
		records.append(record)

	snipLen = len(deltaPath) + 1
#	for root, dirNames, fileNames in os.walk(deltaPath, onerror=failTracker.recordError):
	for root, dirNames, fileNames in os.walk(deltaPath):

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
	dstPath = os.path.join(deltaPath, "catalog.txt")
	f = open(dstPath, 'wb')
	for aRecord in records:
		f.write(','.join(aRecord) + '\n')

	f.write('exit\n')
	f.close()

def checkForRequiredApplications():
	"""Method to ensure we have all of the required applications installed to support building of distributions
	The current set of applications required are:
	java, jar, genisoimage, ImageMagick:convert"""
	evalPath = distutils.spawn.find_executable('java')
	errList = []
	warnList = []
	if evalPath == None:
		errList.append('Failed while trying to locate java. Please install Java')
	evalPath = distutils.spawn.find_executable('jar')
	if evalPath == None:
		errList.append('Failed while trying to locate jar. Please install jar (typically included with Java)')
	evalPath = distutils.spawn.find_executable('genisoimage')
	if evalPath == None:
		errList.append('Failed while trying to locate genisoimage. Please install genisoimage')
	evalPath = distutils.spawn.find_executable('convert')
	if evalPath == None:
		warnList.append('Application \'convert\' was not found. Please install (ImageMagick) convert')
		warnList.append('\tWindows icons will not be supported when using argument: -iconFile.')
	if len(errList) > 0:
		print('There are configuration errors with the environment or system.')
#		print('System Path:' + str(sys.path))
		print('Please correct the following:')
		for aError in errList:
			print('\t' + aError)
		if len(warnList) > 0:
			print('Please correct the following for full program functionality:')
			for aWarn in warnList:
				print('\t' + aWarn)
		sys.exit(0)
	if len(warnList) > 0:
		print('There are configuration issues with the environment or system.')
		print('Please correct the following for full program functionality:')
		for aWarn in warnList:
			print('\t' + aWarn)


def getClassPath(javaCodePath):
	retList = []

	# Ensure the javaCodePath has a trailing slash
	# to allow for proper computation of clipLen 
	if javaCodePath.endswith('/') == False:
		javaCodePath += '/'
	clipLen = len(javaCodePath)

	# Form the default list of all jar files
	for path, dirs, files in os.walk(javaCodePath):
		files.sort()
		for file in files:
			if len(file) > 4 and file[-4:] == '.jar':
				filePath = os.path.join(path, file)
				filePath = filePath[clipLen:]
				retList.append(filePath)
#				print('Found jar file at: ' + filePath)

	return retList


if __name__ == "__main__":
	argv = sys.argv;
	argc = len(argv);

	# Logic to capture Ctrl-C and bail
	signal.signal(signal.SIGINT, miscUtils.handleSignal)

	# Set up the argument parser	
	parser = FancyArgumentParser(prefix_chars='-', add_help=False, fromfile_prefix_chars='@')
	parser.add_argument('-help', '-h', help='Show this help message and exit.', action='help')
	parser.add_argument('-name', help='The name of the application.')
	parser.add_argument('-version', default='0.0.1', help='The version of the application.')
	parser.add_argument('-mainClass', help='Application main entry point.')
	parser.add_argument('-appArgs', help='Application arguments. Note that this argument must ALWAYS be the last specified!', nargs=argparse.REMAINDER, default=[])
	parser.add_argument('-dataCode', '-dc', help='A list of supporting folders for the application.', nargs='+', default=[])
	parser.add_argument('-javaCode', '-jc', help='A folder which contains the Java build.')
	parser.add_argument('-jreVersion', help='JRE version to utilize. This should be a value like 1.7 or 1.8 or 1.8.34. Note there should be corresponding tar.gz JREs for each platform in the folder ~/jre/', default=None)
	parser.add_argument('-jvmArgs', help='JVM arguments.', nargs='+', default=[])
	parser.add_argument('-classPath', help='Class path listing of jar files relative to javaCode. Leave blank for auto determination.', nargs='+', default=[])
	parser.add_argument('-debug', help='Turn on debug options for built applications.', action='store_true', default=False)
	parser.add_argument('-company', help='Company / Provider info.')
	parser.add_argument('-bgFile', help='Background file used for apple dmg file.')
	parser.add_argument('-iconFile', help='PNG file used for linux/windows icon.')
	parser.add_argument('-icnsFile', help='Icon file used for apple build.')
	parser.add_argument('-forceSingleInstance', help='Force the application to have only one instance.', default=False)
	parser.add_argument('-digest', help='Digest used to ensure integrity of application upgrades. Default: sha256', choices=['md5', 'sha256', 'sha512'], default='sha256')
#	parser.add_argument('-bundleId', help='Apple specific id descriptor.')

	# Intercept any request for a  help message and bail
	for aArg in argv:
		if aArg == '-h' or aArg == '-help':
			parser.print_help()
			exit()

	# Check to ensure all of the required applications are installed
	checkForRequiredApplications()

	# Parse the args		
	parser.formatter_class.max_help_position = 50
	args = parser.parse_args()
#	print args

	# Ensure we are getting the bare minimum options
	errList = [];
	if args.name == None:
		errList.append('-name')
	if args.javaCode == None:
		errList.append('-javaCode')
	if args.mainClass == None:
		errList.append('-mainClass')
	if len(errList) != 0:
		print('At a minimum the following must be specified: ' + str(errList) +  '.\nExiting...')
		exit();
#
#	# Ensure java options are specified properly
#	if (args.javaCode == None and args.mainClass != None) or (args.javaCode != None and args.mainClass == None):
#		print('Both javaCode and mainClass must be specified, if either are specified. Exiting...')
#		exit();

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

	# Bail if the release has already been built
	buildPath = os.path.abspath(args.name + '-' + args.version)
	if (os.path.exists(buildPath) == True):
		print('   [ERROR] The release appears to be built. Path: ' + buildPath)
		exit(-1)

	# Form the buildPath
	os.makedirs(buildPath)

	# Build the Delta (diffs) contents
	deltaPath = os.path.join(buildPath, "delta")
	deltaCodePath = os.path.join(deltaPath, "code")
	deltaDataPath = os.path.join(deltaPath, "data")

	# Copy the dataCode to the delta location
	os.makedirs(deltaDataPath)
	for aPath in args.dataCode:
		srcPath = aPath
		if os.path.isdir(srcPath) == False:
			print('   [ERROR] The dataCode path does not exist. Path: ' + srcPath)
			shutil.rmtree(buildPath)
			exit(-1)
		dstPath = os.path.join(deltaDataPath, os.path.basename(aPath))
		shutil.copytree(srcPath, dstPath, symlinks=False)

	# Build the java component of the distribution
	if args.javaCode != None:
		# Copy the javaCode to the proper location
		srcPath = args.javaCode
		if os.path.isdir(srcPath) == False:
			print('   [ERROR] The javaCode path does not exist. Path: ' + srcPath)
			shutil.rmtree(buildPath)
			exit(-1)
		dstPath = deltaCodePath;
		shutil.copytree(srcPath, dstPath, symlinks=False)

	# Form the app.cfg file	
	dstPath = os.path.join(buildPath, "delta/app.cfg")
	miscUtils.buildAppLauncherConfig(dstPath, args)

	# Build the delta catalog
	buildCatalogFile(args, deltaPath)

	# Build the Apple release
	appleUtils.buildRelease(args, buildPath)

	# Build the Linux release
	linuxUtils.buildRelease(args, buildPath)

	# Build the Windows release
	windowsUtils.buildRelease(args, buildPath)

	# Copy over the deploy script
	srcPath = os.path.join(miscUtils.getInstallRoot(), "deployAppDist.py")
	shutil.copy(srcPath, buildPath)

