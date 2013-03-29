#! /usr/bin/env python

import argparse
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


def getClassPath(javaCodePath):
	retList = []
	
	# Ensure the javaCodePath has a trailing slash
	# to allow for proper computation of clipLen 
	if javaCodePath.endswith('/') == False:
		javaCodePath += '/'
	clipLen = len(javaCodePath)
	
	# Form the default list of all jar files
	for path, dirs, files in os.walk(javaCodePath):
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
	parser.add_argument('-appArgs', help='Application arguments.', nargs='+', default=[])
	parser.add_argument('-dataCode', '-dc', help='A list of supporting folders for the application.', nargs='+', default=[])
	parser.add_argument('-javaCode', '-jc', help='A folder which contains the Java build.')
	parser.add_argument('-jvmArgs', help='JVM arguments.', nargs='+', default=[])
	parser.add_argument('-classPath', help='Class path listing of jar files relative to javaCode. Leave blank for auto determination.', nargs='+', default=[])
	parser.add_argument('-company', help='Company / Provider info.')
	parser.add_argument('-bgFile',  help='Background file used for apple dmg file.')
	parser.add_argument('-iconFile', help='PNG file used for linux/windows icon.')
	parser.add_argument('-icnsFile', help='Icon file used for apple build.')
	parser.add_argument('-forceSingleInstance', help='Force the application to have only one instance..', default=False)
#	parser.add_argument('-bundleId', help='Apple specific id descriptor.')

	# Intercept any request for a  help message and bail
	for aArg in argv:
		if aArg == '-h' or aArg == '-help':
			parser.print_help()
			exit()

	# Parse the args		
	parser.formatter_class.max_help_position = 50	
	args = parser.parse_args()
#	print args
	
	# Ensure we are getting the bare minimum options
	if args.name == None:
		print('At a minimum the application name must be specified. Exiting...')
		exit();
		
	# Ensure java options are specified properly
	if (args.javaCode == None and args.mainClass != None) or (args.javaCode != None and args.mainClass == None):
		print('Both javaCode and mainClass must be specified, if either are specified. Exiting...')
		exit();
	
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
	deltaCodePath = os.path.join(buildPath, "delta/code")
	deltaDataPath = os.path.join(buildPath, "delta/data")
	os.makedirs(deltaCodePath)
	os.makedirs(deltaDataPath)
	
	# Copy the dataCode to the delta location
	for aPath in args.dataCode:
		srcPath = aPath
		dstPath = os.path.join(deltaDataPath, os.path.basename(aPath))
		shutil.copytree(srcPath, dstPath, symlinks=True)
		
	# Build the java component of the distribution
	if args.javaCode != None:
		# Copy the javaCode to the proper location
		srcPath = args.javaCode
		dstPath = os.path.join(deltaCodePath, 'java')
		shutil.copytree(srcPath, dstPath, symlinks=True)
	
	# Build the delta catalog
	destPath = os.path.join(buildPath, "delta")
#	buildCatalog.buildCatalog(destPath) 
		
	# Build the Apple release	
	appleUtils.buildRelease(args, buildPath)
	
	# Build the Linux release
	linuxUtils.buildRelease(args, buildPath)
	
	# Build the Windows release
	windowsUtils.buildRelease(args, buildPath)
	