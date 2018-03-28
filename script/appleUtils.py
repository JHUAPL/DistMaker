#! /usr/bin/env python

import copy
import math
import os
import platform
import shutil
import subprocess
import tempfile
import time
import glob

import jreUtils
import miscUtils
import deployJreDist


def buildRelease(args, buildPath):
	# We mutate args - thus make a custom copy
	args = copy.copy(args)

	# Retrieve vars of interest
	appName = args.name
	version = args.version
	jreVerSpec = args.jreVerSpec
	platformStr = 'apple'

	# Determine the types of builds we should do
	platformType = miscUtils.getPlatformTypes(args.platform, platformStr)
	if platformType.nonJre == False and platformType.withJre == False:
		return;
	# Warn if a request for a non-JRE build. We do not support that for the Apple platform.
	if 'apple-' in args.platform:
		print('Building an Apple release without a JRE is currently not supported. This release will not be made.')

	# Check our system environment before proceeding
	if checkSystemEnvironment() == False:
		return

	# Form the list of distributions to build (dynamic and static JREs)
	distList = []
# Note as of 2016May01 there is no longer support for a dynamic Apple release
#	if platformType.nonJre == True:
#		distList = [(appName + '-' + version, None)]
	if platformType.withJre == True:
		# Select the jreTarGzFile to utilize for static releases
		jreTarGzFile = jreUtils.getJreTarGzFile(platformStr, jreVerSpec)
		if jreTarGzFile == None:
			# Let the user know that a compatible JRE was not found - thus no static release will be made.
			print('[Warning] No compatible JRE ({0}) is available for the {1} platform. A static release will not be provided for the platform.'.format(jreVerSpec, platformStr.capitalize()))
			# Let the user know that a compatible JRE was not found - and thus no Apple builds will be made
			print('Only static Apple distributions are supported - thus there will be no Apple distribution of the application: ' + appName + '\n')
			return
		else:
			distList.append((appName + '-' + version + '-jre', jreTarGzFile))

	# Create the various distributions
	for (aDistName, aJreTarGzFile) in distList:
		print('Building {0} distribution: {1}'.format(platformStr.capitalize(), aDistName))
		# Let the user know of the JRE release we are going to build with
		if aJreTarGzFile != None:
			print('\tUtilizing JRE: ' + aJreTarGzFile)

		# Create a tmp folder and build the static release to the tmp folder
		tmpPath = tempfile.mkdtemp(prefix=platformStr, dir=buildPath)

		# Build the contents of the distribution folder
		buildDistTree(buildPath, tmpPath, args, aJreTarGzFile)

		# Create the DMG image via genisoimage or hdiutil
		dmgFile = os.path.join(buildPath, aDistName + '.dmg')
		if platform.system() == 'Darwin':
#			cmd = ['hdiutil', 'create', '-fs', 'HFS+'  '-o', dmgFile, '-quiet', '-volname', appName, '-srcfolder', tmpPath]
			cmd = ['hdiutil', 'makehybrid', '-hfs', '-o', dmgFile, '-default-volume-name', appName, tmpPath]
		else:
#			cmd = ['genisoimage', '-o', dmgFile, '-quiet', '-V', appName, '-max-iso9660-filenames', '-hfs-unlock', '-uid', '501', '-gid', '80', '-r', '-D', '-apple', tmpPath]
			cmd = ['genisoimage', '-o', dmgFile, '-quiet', '-V', appName, '-max-iso9660-filenames', '-hfs-unlock', '-D', '-r', '-apple', tmpPath]
		print('\tForming DMG image. File: ' + dmgFile)
		proc = miscUtils.executeAndLog(cmd, "\t\tgenisoimage: ")
		if proc.returncode != 0:
			print('\tError: Failed to form DMG image. Return code: ' + str(proc.returncode) + '\n')
		else:
			print('\tFinished building release: ' + os.path.basename(dmgFile) + '\n')

		# Perform cleanup
		shutil.rmtree(tmpPath)




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


def buildDistTree(buildPath, rootPath, args, jreTarGzFile):
	# Retrieve vars of interest
	appInstallRoot = miscUtils.getInstallRoot()
	appInstallRoot = os.path.dirname(appInstallRoot)
	appTemplatePath = os.path.join(appInstallRoot, 'template')
	appName = args.name
	bgFile = args.bgFile
	icnsFile = args.icnsFile

	# Form the symbolic link which points to /Applications
	srcPath = '/Applications'
	dstPath = os.path.join(rootPath, 'Applications');
	os.symlink(srcPath, dstPath)

	# Construct the app folder
	appNodes = ['MacOS', 'Resources']
	if jreTarGzFile != None:
		appNodes.append('PlugIns')
	for aPath in appNodes:
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', aPath)
		os.makedirs(dstPath)

	if jreTarGzFile != None:
		# Copy over the executable launcher
		srcPath = os.path.join(appTemplatePath, 'apple', 'JavaAppLauncher')
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'MacOS')
		shutil.copy(srcPath, dstPath)

		# Unpack the JRE and set up the JRE tree
		destPath = os.path.join(rootPath, appName + '.app', 'Contents', 'PlugIns')
		jreUtils.unpackAndRenameToStandard(jreTarGzFile, destPath)
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
	if jreTarGzFile == None:
		payloadPath = os.path.join(rootPath, appName + '.app', 'Contents', 'Resources')

	# Form the app contents folder
	srcPath = os.path.join(buildPath, "delta")
	dstPath = os.path.join(payloadPath, 'app')
	shutil.copytree(srcPath, dstPath, symlinks=True)

	# Link dlls to the MacOS directory so they can be found at launch
	jarDir = os.path.join(rootPath, appName + '.app', 'Contents', 'app', 'code', 'osx')
	dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'MacOS')
	for jniPath in glob.iglob(os.path.join(jarDir, "*.jnilib")):
		jniFileName = os.path.basename(jniPath)
		srcPath = os.path.join('..', 'app', 'code', 'osx', jniFileName)
		linkPath = os.path.join(dstPath, jniFileName)
		os.symlink(srcPath, linkPath)
	for dylPath in glob.iglob(os.path.join(jarDir, "*.dylib")):
		dylFileName = os.path.basename(dylPath)
		srcPath = os.path.join('..', 'app', 'code', 'osx', dylFileName)
		linkPath = os.path.join(dstPath, dylFileName)
		os.symlink(srcPath, linkPath)


	# Setup the launcher contents
	dstPath = os.path.join(payloadPath, "Java/" + deployJreDist.getAppLauncherFileName())
	srcPath = os.path.join(appInstallRoot, "template/appLauncher.jar")
	os.makedirs(os.path.dirname(dstPath))
	shutil.copy(srcPath, dstPath);

	# Build the java component of the distribution
	if args.javaCode != None:
		# Form the Info.plist file
		dstPath = os.path.join(rootPath, appName + '.app', 'Contents', 'Info.plist')
		if jreTarGzFile != None:
			buildPListInfoStatic(dstPath, args, jreTarGzFile)
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

	jvmArgsStr = ''
	for aStr in args.jvmArgs:
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
	for (key, val) in tupList:
		writeln(f, 2, '<key>' + key + '</key>')
		writeln(f, 2, '<string>' + str(val) + '</string>')

	# JVM configuration
	writeln(f, 2, '<key>Java</key>')
	writeln(f, 2, '<dict>')

	classPathStr = '$JAVAROOT/' + deployJreDist.getAppLauncherFileName()

	tupList = []
	tupList.append(('JVMVersion', '1.7+'))
	tupList.append(('MainClass', 'appLauncher.AppLauncher'))
	tupList.append(('WorkingDirectory', '$APP_PACKAGE/Contents/Resources/app'))
	tupList.append(('ClassPath', classPathStr))
	tupList.append(('VMOptions', jvmArgsStr))

	for (key, val) in tupList:
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

def buildPListInfoStatic(destFile, args, jreTarGzFile):
	# Retrieve vars of interest
	icnsStr = None
	if args.icnsFile != None:
		icnsStr = os.path.basename(args.icnsFile)

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
	tupList.append(('NSHighResolutionCapable', 'true'))
	tupList.append(('NSHumanReadableCopyright', ''))

	jrePath = jreUtils.getBasePathForJreTarGzFile(jreTarGzFile)
	tupList.append(('JVMRuntime', jrePath))

	tupList.append(('JVMMainClassName', 'appLauncher.AppLauncher'))

	cwdPath = os.path.join('/Applications', args.name + '.app', 'Contents', 'app')
	tupList.append(('WorkingDirectory', cwdPath))

	# Application configuration
	for (key, val) in tupList:
		writeln(f, 2, '<key>' + key + '</key>')
		writeln(f, 2, '<string>' + str(val) + '</string>')

	# JVM options
	jvmArgs = list(args.jvmArgs)
	if any(aStr.startswith('-Dapple.laf.useScreenMenuBar') == False for aStr in jvmArgs) == True:
		jvmArgs.append('-Dapple.laf.useScreenMenuBar=true')
	if any(aStr.startswith('-Dcom.apple.macos.useScreenMenuBar') == False for aStr in jvmArgs) == True:
		jvmArgs.append('-Dcom.apple.macos.useScreenMenuBar=true')
	if any(aStr.startswith('-Dcom.apple.macos.use-file-dialog-packages') == False for aStr in jvmArgs) == True:
		jvmArgs.append('-Dcom.apple.macos.use-file-dialog-packages=true')
	jvmArgs.append('-Dcom.apple.mrj.application.apple.menu.about.name=' + args.name)
	jvmArgs.append('-Dapple.awt.application.name=' + args.name)
	jvmArgs.append('-Djava.system.class.loader=appLauncher.RootClassLoader')
#	if icnsStr != None:
#		jvmArgs.append('-Xdock:icon=Contents/Resources/' + icnsStr)

	writeln(f, 2, '<key>JVMOptions</key>')
	writeln(f, 2, '<array>')
	for aStr in jvmArgs:
		writeln(f, 3, '<string>' + aStr + '</string>')
	writeln(f, 2, '</array>')

	# JVM configuration
	writeln(f, 2, '<key>Java</key>')
	writeln(f, 2, '<dict>')

	classPathStr = '$JAVAROOT/' + deployJreDist.getAppLauncherFileName()

	tupList = []
	tupList.append(('ClassPath', classPathStr))

	for (key, val) in tupList:
		writeln(f, 3, '<key>' + key + '</key>')
		writeln(f, 3, '<string>' + str(val) + '</string>')

	writeln(f, 2, '</dict>')
	writeln(f, 1, '</dict>')
	writeln(f, 0, '</plist>')

	f.close()


def checkSystemEnvironment():
	"""Checks to ensure that all system application / environment variables needed to build a Apple distribution are installed
	and properly configured. Returns False if the system environment is insufficient"""
	return True


def writeln(f, tabL, aStr, tabStr='   '):
	tStr = ''
	for i in range(tabL):
		tStr += tabStr
	f.write(tStr + aStr + '\n')

