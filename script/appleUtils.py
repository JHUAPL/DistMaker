#! /usr/bin/env python

import argparse
import copy
import getpass
import math
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import glob

import jreUtils
import miscUtils


def buildRelease(args, buildPath):
	# We may mutate args - thus make a custom copy  
	args = copy.copy(args)
	
	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreRelease = args.jreRelease
	
	# Attempt to locate a default JRE if none is specified in the args.
	if jreRelease == None:
		jreRelease = jreUtils.getDefaultJreRelease('apple')
	# Let the user know if the 'user' specified JRE is not available and locate an alternative
	elif jreUtils.isJreAvailable('apple', jreRelease) == False:
		print('[Warning] User specified JRE (' + jreRelease + ') is not available for Apple platform. Searching for alternative...')
		jreRelease = jreUtils.getDefaultJreRelease('apple')
	args.jreRelease = jreRelease

	# Form the list of distributions to build
	distList = [(appName + '-' + version, False)]
	if jreUtils.isJreAvailable('apple', jreRelease) == True:
		distList.append((appName + '-' + version + '-jre', True))

	# Create the various Apple distributions	
	for (distName, isStaticRelease) in distList:
		print('Building Apple distribution: ' + distName)
		# Let the user know of the JRE release we are going to build with
		if isStaticRelease == True:
			print('\tUtilizing jreRelease: ' + jreRelease)
		
		# Create a tmp folder and build the static release to the tmp folder 
		tmpPath = tempfile.mkdtemp(dir=buildPath)
		
		# Build the contents of the distribution folder
		buildDistTree(buildPath, tmpPath, args, isStaticRelease)
		
		# Create the DMG image via genisoimage
		dmgFile = os.path.join(buildPath, distName + '.dmg')
#		cmd = ['genisoimage', '-o', dmgFile, '-quiet', '-V', appName, '-max-iso9660-filenames', '-hfs-unlock', '-uid', '501', '-gid', '80', '-r', '-D', '-apple', tmpPath]
		cmd = ['genisoimage', '-o', dmgFile, '-quiet', '-V', appName, '-max-iso9660-filenames', '-hfs-unlock', '-D', '-r', '-apple', tmpPath]
		print('\tForming DMG image. File: ' + dmgFile)
		proc = miscUtils.executeAndLog(cmd, "\t\tgenisoimage: ")
		if proc.returncode != 0:
			print('\tError: Failed to form DMG image. Return code: ' + str(proc.returncode))

		# Perform cleanup: Remove the tmp folder
		shutil.rmtree(tmpPath)
		print('\tFinished building release: ' + os.path.basename(dmgFile))
	
	
	
	
#	# Compute the space needed for the entire dmg package
#	fileSize = miscUtils.getPathSize(javaCodePath)
#	fileSize += miscUtils.getPathSize(args.bgFile)
#	fileSize += miscUtils.getPathSize(args.icnsFile)
#	# Add extra space for Generic.app template and nominal Info.plist
#	fileSize + 100 * 1024;
	
#	# Determine the 1 megabyte blocks that we need
#	numBlocks = math.ceil(fileSize / (1024 * 1024.0));
#	numBlocks = int(numBlocks)
	
#	# Extra padding in case files take up slightly more space
#	numBlocks += 1;  
	
#	# Create the empty file which will be used for the dmg file
#	# dd if=/dev/zero of=FireFoxRedo.dmg bs=1M count=128
#	print('Creating a ' + str(numBlocks) + ' MB file: ' + dmgFile)
#	cmd = ['dd', 'if=/dev/zero', 'of=' + dmgFile, 'bs=1M', 'count=' + str(numBlocks)]
#	subprocess.check_call(cmd, stderr=None, stdout=None)

#	# Format the newly created file to have a single partition of hfs-plus
#	# TODO: Add the proper method call for Apple...
#	print('Formating the file as a disk partion of HFS-Plus')
#	exeTool = os.path.join(appInstallRoot, 'tools', 'mkfs.hfsplus')
#	cmd = [exeTool, '-v', appName, dmgFile]
#	subprocess.call(cmd, stderr=subprocess.STDOUT)
	
	
#	# Mount the dmgFile
#	mount(dmgFile, tmpPath)
	
#	# Build the contents of the DMG file
#	buildSharedTree(tmpPath, args)
	
	
#	# Change the group/user info to Apple specific guid
#	normGuid(tmpPath)

#	# Unmount the dmgFile
#	umount(tmpPath)
	
#	# Let the umount command finish - Bail after 10 seconds
#	for c1 in range(40):
#		if os.path.ismount(tmpPath) == False:
#			break;
#		time.sleep(0.25)

#	# Bail if the mount still exists. User will have to clean up tmpPath
#	if os.path.ismount(tmpPath) == True:
#		print('Failed to properly umount: ' + tmpPath)
#		return;

#	# Perform cleanup: Remove the tmp folder
#	os.rmdir(tmpPath)
	

def buildDistTree(buildPath, rootPath, args, isStaticRelease):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appTemplatePath = os.path.join(appInstallRoot, 'template')
	appName = args.name
	bgFile = args.bgFile
	icnsFile = args.icnsFile
	jreRelease = args.jreRelease
	
	# Form the symbolic link which points to /Applications 
	srcPath = '/Applications'
	dstPath = os.path.join(rootPath, 'Applications');
	os.symlink(srcPath, dstPath)
	
	# Construct the app folder
	appNodes = ['MacOS', 'Resources']
	if isStaticRelease == True:
		appNodes.append('PlugIns')
	for aPath in appNodes:
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', aPath)
		os.makedirs(dstPath)
		
	if isStaticRelease == True:
		# Copy over the executable launcher
		srcPath = os.path.join(appTemplatePath, 'apple', 'JavaAppLauncher')
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'MacOS')
		shutil.copy(srcPath, dstPath)
	
		# Copy over the JRE tree
		srcPath = os.path.join(appInstallRoot, 'jre', 'apple', jreRelease)
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'PlugIns', jreRelease)
		shutil.copytree(srcPath, dstPath, symlinks=True)
	else:
		# Copy over the executable launcher
		srcPath = os.path.join(appTemplatePath, 'apple', 'JavaApplicationStub')
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'MacOS')
		shutil.copy(srcPath, dstPath)
	
	# Write out the PkgInfo file	
	dstPath = os.path.join(rootPath, appName + '.app', 'Contents', "PkgInfo")
	f = open(dstPath, 'wb')
	f.write('APPL????')
	f.close()
	
	# Determine the payloadPath for where to store the appLauncher
	payloadPath = os.path.join(rootPath, appName + '.app', 'Contents')
	if isStaticRelease == False:
		payloadPath = os.path.join(rootPath, appName + '.app', 'Contents', 'Resources')
	
	# Form the app contents folder
	srcPath = os.path.join(buildPath, "delta")
	dstPath = os.path.join(payloadPath, 'app')
	shutil.copytree(srcPath, dstPath, symlinks=True)

	#Link dlls to the MacOS directory so they can be found at launch
	jarDir = os.path.join(rootPath, appName + '.app', 'Contents', 'app', 'code','osx')
	dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'MacOS')
	for jniPath in glob.iglob(os.path.join(jarDir,"*.jnilib")):
		jniFileName = os.path.basename(jniPath)
		srcPath = os.path.join('..','app','code','osx',jniFileName)
		linkPath = os.path.join(dstPath,jniFileName)
		os.symlink(srcPath,linkPath)
	for dylPath in glob.iglob(os.path.join(jarDir,"*.dylib")):
		dylFileName = os.path.basename(dylPath)
		srcPath = os.path.join('..','app','code','osx',dylFileName)
		linkPath = os.path.join(dstPath,dylFileName)
		os.symlink(srcPath,linkPath)



	
	# Setup the launcher contents
	exePath = os.path.join(payloadPath, 'Java')
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(exePath)
	shutil.copy(srcPath, exePath);

	# Build the java component of the distribution
	if args.javaCode != None:
		# Form the Info.plist file
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'Info.plist')
		if isStaticRelease == True:
			buildPListInfoStatic(dstPath, args)
		else:
			buildPListInfoShared(dstPath, args)		
	
	# Copy over the icon file *.icns
	if icnsFile != None and os.path.exists(icnsFile) == True: 
		srcPath = icnsFile
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'Resources')
		shutil.copy(srcPath, dstPath)
	
	# Copy over the background file
	srcPath = bgFile
	if srcPath == None:
		srcPath = os.path.join(appTemplatePath, 'background', 'background.png');
	dstPath = os.path.join(rootPath, '.background')
	os.mkdir(dstPath)
	dstPath = os.path.join(rootPath, '.background', 'background.png')
	shutil.copy(srcPath, dstPath)
	
	# Copy over the .DS_Store
	srcPath = os.path.join(appTemplatePath, '.DS_Store.template')
	dstPath = os.path.join(rootPath, '.DS_Store')
	shutil.copy(srcPath, dstPath)
	
	# Update the .DS_Store file to reflect the new volume name
	srcPath = os.path.join(rootPath, '.DS_Store')
	classPath = appInstallRoot + '/lib/glum.jar:' + appInstallRoot + '/lib/distMaker.jar:' + appInstallRoot + '/lib/guava-18.0.jar'
	cmd = ['java', '-cp', classPath, 'dsstore.MainApp', srcPath, appName]
	proc = miscUtils.executeAndLog(cmd, "\t\tdsstore.MainApp: ")
	if proc.returncode != 0:
		print('\tError: Failed to update .DS_Store. Return code: ' + str(proc.returncode))

	





	
def normGuid(mountPt):
	# Ensure we are running as root
	miscUtils.checkRoot()
	
	# Ensure mountPt is a loopback mountPt
	# TODO

	# The standard Apple group/user id is 501:80
	cmd = ['chown', '-R', '501:80', mountPt]
	subprocess.check_call(cmd, stderr=None, stdout=None)
#	# The more pythonic way below is disabled because the command does not handle paths with spaces
#	for path, dirs, files in os.walk(rootPath):
#		for d in dirs:
#			tmpPath = '\'' + os.path.join(path, d) + '\''
#			os.chown(tmpPath, 501, 80)
#		for f in files:
#			tmpPath = '\'' + os.path.join(path, f) + '\''
#			os.chown(tmpPath, 501, 80)
	


def mount(dmgFile, mountPt):
	# Ensure we are running as root
	miscUtils.checkRoot()
	
	# Mount the dmgFile (silently ???)
#	mount -o loop,ro -t hfsplus imagefile.dmg /mnt/mountpoint
	cmd = ['mount', '-n', '-o', 'loop,rw', '-t', 'hfsplus', dmgFile, mountPt]
	subprocess.call(cmd, stderr=subprocess.STDOUT)
	

def umount(mountPt):
	# Ensure we are running as root
	miscUtils.checkRoot()

	# Release the mount	
	cmd = ['umount', mountPt]
	subprocess.call(cmd, stderr=subprocess.STDOUT)
	

def buildPListInfoShared(destFile, args):
	# Retrieve vars of interest
	icnsStr = None
	if args.icnsFile != None:
		icnsStr = os.path.basename(args.icnsFile)
	
	classPathStr = '$JAVAROOT/appLauncher.jar'
#	classPathStr = ''
#	for aStr in args.classPath:
#		classPathStr += '$JAVAROOT/' + aStr + ':'
#	if len(classPathStr) > 0:
#		classPathStr = classPathStr[0:-1]
		
	jvmArgsStr = ''
	for aStr in args.jvmArgs:
		if len(aStr) > 2 and aStr[0:1] == '\\':
			aStr = aStr[1:]
		jvmArgsStr += aStr + ' '
	jvmArgsStr += '-Djava.system.class.loader=appLauncher.RootClassLoader'

	f = open(destFile, 'wb')
	writeln(f, 0, '<?xml version="1.0" encoding="UTF-8" standalone="no"?>')
#	writeln(f, 0, '<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">')
	writeln(f, 0, '<plist version="1.0">')
	writeln(f, 1, '<dict>')
	
	tupList = []
	tupList.append(('CFBundleExecutable', 'JavaApplicationStub'))
	tupList.append(('CFBundleGetInfoString', args.company))
	tupList.append(('CFBundleInfoDictionaryVersion', 6.0))
	tupList.append(('CFBundleIconFile', icnsStr))
	tupList.append(('CFBundleIdentifier', args.name.lower()))
	tupList.append(('CFBundleName', args.name))
	tupList.append(('CFBundlePackageType', 'APPL'))
	tupList.append(('CFBundleSignature', '????'))
	tupList.append(('CFBundleVersion', args.version))
	
	# Application configuration
	for (key,val) in tupList:
		writeln(f, 2, '<key>' + key + '</key>')
		writeln(f, 2, '<string>' + str(val) + '</string>')
	
	# JVM configuration
	writeln(f, 2, '<key>Java</key>')
	writeln(f, 2, '<dict>')
	
	tupList = []
	tupList.append(('JVMVersion', '1.6+'))
	tupList.append(('MainClass', 'appLauncher.AppLauncher'))
	tupList.append(('WorkingDirectory', '$APP_PACKAGE/Contents/Resources/app'))
	tupList.append(('ClassPath', classPathStr))
	tupList.append(('VMOptions', jvmArgsStr))
	
	for (key,val) in tupList:
		writeln(f, 3, '<key>' + key + '</key>')
		writeln(f, 3, '<string>' + str(val) + '</string>')

	writeln(f, 3, '<key>Arguments</key>')
	writeln(f, 3, '<array>')
#	for aStr in args.appArgs:
#		writeln(f, 4, '<string>' + aStr + '</string>')
	writeln(f, 3, '</array>')
	writeln(f, 2, '</dict>')
	writeln(f, 1, '</dict>')
	writeln(f, 0, '</plist>')
	
	f.close()

def buildPListInfoStatic(destFile, args):
	# Retrieve vars of interest
	icnsStr = None
	if args.icnsFile != None:
		icnsStr = os.path.basename(args.icnsFile)
	
	classPathStr = '$JAVAROOT/appLauncher.jar'
#	classPathStr = ''
#	for aStr in args.classPath:
#		classPathStr += '$JAVAROOT/' + aStr + ':'
#	if len(classPathStr) > 0:
#		classPathStr = classPathStr[0:-1]
		
	f = open(destFile, 'wb')
	writeln(f, 0, '<?xml version="1.0" ?>')
#	writeln(f, 0, '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">')
	writeln(f, 0, '<plist version="1.0">')
	writeln(f, 1, '<dict>')
	
	tupList = []
	tupList.append(('CFBundleDevelopmentRegion', 'English'))
	tupList.append(('CFBundleExecutable', 'JavaAppLauncher'))
	tupList.append(('CFBundleGetInfoString', args.company))
	tupList.append(('CFBundleInfoDictionaryVersion', 6.0))
	tupList.append(('CFBundleIconFile', icnsStr))
	tupList.append(('CFBundleIdentifier', args.name.lower()))
	tupList.append(('CFBundleDisplayName', args.name))
	tupList.append(('CFBundleName', args.name))
	tupList.append(('CFBundlePackageType', 'APPL'))
	tupList.append(('CFBundleSignature', '????'))
	tupList.append(('CFBundleVersion', args.version))
	tupList.append(('NSHumanReadableCopyright', ''))
	tupList.append(('JVMRuntime', args.jreRelease))
	
	tupList.append(('JVMMainClassName', 'appLauncher.AppLauncher'))
	
	
	# Application configuration
	for (key,val) in tupList:
		writeln(f, 2, '<key>' + key + '</key>')
		writeln(f, 2, '<string>' + str(val) + '</string>')
		
	# JVM options
	writeln(f, 2, '<key>JVMOptions</key>')
	writeln(f, 3, '<array>')
	for aStr in args.jvmArgs:
		writeln(f, 4, '<string>' + aStr + '</string>')
#	if icnsStr != None:
#		writeln(f, 4, '<string>-Xdock:icon=Contents/Resources/' + icnsStr + '</string>')
	writeln(f, 4, '<string>-Dapple.laf.useScreenMenuBar=true</string>')
	writeln(f, 4, '<string>-Dcom.apple.macos.use-file-dialog-packages=true</string>')
	writeln(f, 4, '<string>-Dcom.apple.macos.useScreenMenuBar=true</string>')
	writeln(f, 4, '<string>-Dcom.apple.mrj.application.apple.menu.about.name=' + args.name + '</string>')
	writeln(f, 4, '<string>-Dapple.awt.application.name=' + args.name + '</string>')
	writeln(f, 4, '<string>-Djava.system.class.loader=appLauncher.RootClassLoader</string>')
	writeln(f, 3, '</array>')
	
#	# App arguments
#	writeln(f, 2, '<key>JVMArguments</key>')
#	writeln(f, 3, '<array>')
#	for aStr in args.appArgs:
#		writeln(f, 4, '<string>' + args + '</string>')
#	writeln(f, 3, '</array>')
	

	# JVM configuration
	writeln(f, 2, '<key>Java</key>')
	writeln(f, 2, '<dict>')
	
	tupList = []
#	tupList.append(('JVMVersion', '1.6+'))
#	tupList.append(('MainClass', args.mainClass))
	tupList.append(('WorkingDirectory', '$APP_PACKAGE/Contents/app'))

	tupList.append(('ClassPath', classPathStr))

	for (key,val) in tupList:
		writeln(f, 3, '<key>' + key + '</key>')
		writeln(f, 3, '<string>' + str(val) + '</string>')
	
	writeln(f, 2, '</dict>')
	writeln(f, 1, '</dict>')
	writeln(f, 0, '</plist>')
	
	f.close()



def writeln(f, tabL, aStr, tabStr='   '):
	tStr = ''
	for i in range(tabL):
		tStr += tabStr
	f.write(tStr + aStr + '\n')
	
	
