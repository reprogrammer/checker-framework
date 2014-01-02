#!/usr/bin/env python
# encoding: utf-8
"""
releaseutils.py

Python Utils for releasing the Checker Framework
This contains no main method only utility functions 
Created by Jonathan Burke 11/21/2012

Copyright (c) 2012 University of Washington
"""

from release_vars  import *
from xml.dom import minidom
import sys
import getopt
import urllib2
import re
import subprocess
from subprocess import Popen, PIPE
import os
import pwd
import re
import shutil
import errno

#=========================================================================================
# Parse Args Utils #TODO: Perhaps use argparse module
def match_arg( arg ):
    matched_project = None
    for project in PROJECTS_TO_SHORTNAMES:
        if arg == project[0] or arg == project[1]:
            matched_project = project
    return matched_project

def read_projects( argv, error_call_back ):
    matched_projects = {
        LT_OPT  : False,
        AFU_OPT : False,
        CF_OPT  : False
    }

    auto = False

    arg_length = len(sys.argv)

    if arg_length < 2:
        print("You  must select at least one project!")
        error_call_back()
        sys.exit(1)

    error = False
    for index in range( 1, arg_length ):
        arg = argv[index]

        if arg == ALL_OPT:
            for project in PROJECTS_TO_SHORTNAMES:
                matched_projects[project[0]] = True
            return matched_projects

        matched_project = match_arg( argv[index] )
        if matched_project == None:
            print( "Unmatched project: " + argv[index] )
            error = True
        else:
            matched_projects[ matched_project[0] ] = True

    if error:
        error_call_back()
        sys.exit(1)

    return matched_projects

def add_project_dependencies( matched_projects ):
    if matched_projects[CF_OPT]:
        matched_projects[AFU_OPT] = True
        matched_projects[LT_OPT]  = True
    else:
        if matched_projects[AFU_OPT]:
            matched_projects[LT_OPT] = True


def print_projects( print_error_label, indent_level, indent_size ):
    indentation = duplicate( duplicate( " ", indent_size ), indent_level )
    project_to_short_cols = 27

    if( print_error_label ):
        print("projects:   You must specify at least one of the following projects or \"all\"")

    print( indentation + pad_to( "project", " ", project_to_short_cols ) + "short-name" )

    for project in PROJECTS_TO_SHORTNAMES:
        print(indentation + pad_to( project[0], " ", project_to_short_cols ) + project[1])

    print(indentation + ALL_OPT)

def duplicate( str, times ):
    dup_str = ""
    for index in range(0, times):
        dup_str += str
    return dup_str

def pad_to( original_str, filler, size ):
    missing = size - len(original_str)
    return original_str + duplicate( filler, missing )

def read_auto( argv ):
    for index in range( 1, len( argv ) ):
        if argv[index] == "--auto":
            return True

    return False

#=========================================================================================
# Command utils

#Execute the given command
def execute(command_args, halt_if_fail=True, capture_output=False, working_dir=None):
    print("Executing: %s" % (command_args))
    import shlex
    args = shlex.split(command_args) if isinstance(command_args, str) else command_args

    if capture_output:
        return subprocess.Popen(args, stdout=subprocess.PIPE, cwd=working_dir).communicate()[0]
    else:
        r = subprocess.call(args, cwd=working_dir)
        if halt_if_fail and r:
            raise Exception('Error %s while executing %s' % (r, command_args))
        return r

def check_command(command):
    p = execute(['which', command], False)
    if p:
        raise AssertionError('command not found: %s' % command)
    print ''

def prompt_yn(msg):
    y_or_n = 'z'
    while(y_or_n != 'y' and y_or_n != 'n'):
        print(msg + " [y|n]")
        y_or_n = raw_input().lower()

    return y_or_n == 'y'

def maybe_prompt_yn(msg, prompt):
    if not prompt:
        return True

    return prompt_yn(msg)

def prompt_w_suggestion(msg, suggestion, validRegex=None):
    answer = None
    while(answer is None):
        answer = raw_input(msg + " (%s): " % suggestion)

        if answer is None or answer == "":
            answer = suggestion
        else:
            answer = answer.strip()

            if validRegex is not None:
                m = re.match(validRegex, answer)
                if m is None:
                    answer = None
                    print "Invalid answer.  Validating regex: " + validRegex
            else:
                answer = suggestion

    return answer

def maybe_prompt_w_suggestion(msg, suggestion, validRegex, prompt):
    if not prompt:
        return True
    return prompt_w_suggestion(msg, suggestion, validRegex, prompt)

def check_tools(tools):
    print("\nChecking to make sure the following programs are installed:")
    print(', '.join(tools))
    print('Note: If you are NOT working on buffalo.cs.washington.edu then you ' +
        'likely need to change the variables that are set in release.py \n'   +
        'search for "Set environment variables"')
    map(check_command, tools)
    print ''

def match_one(toTest, patternStrings):
    for patternStr in patternStrings:
        isMatch = re.match(patternStr, toTest)
        if isMatch is not None:
            return patternStr

    return None
        
#=========================================================================================
# Version Utils

def max_version( strs ):
    pattern = re.compile(r"""^\d+\.\d+(\.\d+)?(\.\d+)?""")
    version_as_str = None
    version_as_int = None

    for str in strs:
        if pattern.match( str ) is not None:
            if version_as_str == None:
                version_as_str = str
                version_as_int = version_to_integer( str )
            else:
                cur_version_as_int = version_to_integer( str )
                if cur_version_as_int > version_as_int:
                    version_as_str = str
                    version_as_int = cur_version_as_int

    return version_as_str


def current_distribution(site):
    """
    Reads the checker framework version from the checker framework website and
    returns the version of the current release
    """
    print 'Looking up checker-framework-version from %s\n' % site
    ver_re = re.compile(r"<!-- checker-framework-version -->(.*),")
    text = urllib2.urlopen(url=site).read()
    result = ver_re.search(text)
    return result.group(1)

def current_distribution(checker_framework_dir):
    """
    Reads the checker framework version from build-common.properties
    returns the version of the current release
    """
    ver_re = re.compile( r"""build.version = (\d\.\d\.\d(?:\.\d){0,1})""" )
    build_props_location = os.path.join( checker_framework_dir, "build-common.properties" )
    build_props = open( build_props_location )

    for line in build_props:
        match = ver_re.search(line)
        if match:
            return match.group(1)

    print( "Couldn't find checker framework version in file: " + build_props_location )
    sys.exit(1)

def extract_from_site( site, open_tag, close_tag ):
    """
    Reads a string from between open and close tag at the given url
    """
    regex_str = open_tag + "(.*)" + close_tag

    ver_re = re.compile(regex_str)
    text = urllib2.urlopen(url=site).read()
    result = ver_re.search(text)
    return result.group(1)

    
def latest_openjdk(site):
    ver_re = re.compile(r"Build b(\d+)")
    text = urllib2.urlopen(url=site).read()
    result = ver_re.search(text)
    return result.group(1)

def increment_version(version):
    """
    Returns a recommendation of the next incremental version based on the
    passed one.

    >>> increment_version('1.0.3')
    '1.0.4'
    >>> increment_version('1.0.9')
    '1.1.0'
    >>> increment_version('1.1.9')
    '1.2.0'
    >>> increment_version('1.3.1')
    '1.3.2'

    """
    parts = [int(x) for x in version.split('.')]

    #We suggest only 3 part versions with the fourth part dropped if present
    intVer = version_to_integer(version)
    return integer_to_version(intVer + 10)

def version_to_integer(version):
    parts = version.split('.')
    iVer  = int(parts[0]) * 1000
    iVer += int(parts[1]) * 100
    if len(parts) > 2:
        iVer += int(parts[2]) * 10
    if len(parts) > 3:
        iVer += int(parts[3])
    return iVer

def integer_to_version(intVer):
    parts = [0,0,0]
    if intVer >= 1000:
        parts[0] = intVer / 1000
        intVer = intVer % 1000

    if intVer >= 100:
        parts[1] = intVer / 100
        intVer = intVer % 100

    if intVer >= 10:
        parts[2] = intVer / 10
        intVer = intVer % 10

    version = ".".join([str(x) for x in parts])
    if intVer > 0:
        version = version + "." + str(intVer)

    return version

def is_version_increased(old_version, new_version):
    return version_to_integer(new_version) > version_to_integer(old_version)

def find_latest_version( version_dir ):
    return max_version( filter( os.path.isdir, os.listdir(version_dir) ) )

    
#=========================================================================================
# Mercurial Utils

def hg_push( repo_root ):
    execute('hg -R %s push' % repo_root)

#Pull the latest changes and update
def update_project(path):
    execute('hg -R %s pull' % path)
    execute('hg -R %s update' % path)

def update_projects(paths):
    for path in paths:
        update_project( path )
        #print("Checking changes")
        # execute('hg -R %s outgoing' % path)

#Commit the changes we made for this release
#Then add a tag for this release
#And push these changes 
def commit_tag_and_push(version, path, tag_prefix):
    execute('hg -R %s commit -m "new release %s"' % (path, version))
    execute('hg -R %s tag %s%s' % (path, tag_prefix, version))
    execute('hg -R %s push' % path)
    
# Retrive the changes since the tag (prefix + prev_version)
def retrieve_changes(root, prev_version, prefix):
    return execute(
            "hg -R %s log -r %s%s:tip --template ' * {desc}\n'" %
                (root, prefix, prev_version),
                capture_output=True)

def clone_or_update_repo(src_repo, dst_repo):
    if os.path.isdir(os.path.abspath(dst_repo)):
        update_project( dst_repo )
    else:
        execute('hg clone %s %s' % (src_repo, dst_repo))

def is_repo_cleaned_and_updated(repo):
    summary = execute('hg -R %s summary' % (repo), capture_output=True)
    if not "commit: (clean)" in summary:
        return False
    if not "update: (current)" in summary:
        return False
    return True

def repo_exists(repo):
    print ('Does repo exist: %s' % repo)
    failed = execute('hg -R %s root' % (repo), False, False)
    print ''
    return not failed

def strip(repo):
    strip_args = ['hg', '-R', repo, 'strip', '--no-backup', 'roots(outgoing())']
    out, err = subprocess.Popen(strip_args, stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()

    match = re.match("\d+ files updated, \d+ files merged, \d+ files removed, \d+ files unresolved", out)
    if match is None:
        match = re.match("abort: empty revision set", err)
        if match is None:
            raise Exception("Could not recognize strip output: (%s, %s)" % (out, err))
        else:
            print err
    else:
        print out
    print ""

def revert(repo):
    execute('hg -R %s revert --all' % repo)

def purge(repo):
    execute('hg -R %s purge' % repo)

def clean_repo(repo, prompt):
    if maybe_prompt_yn( 'Remove all modified files, untracked files and outgoing commits from %s ?' % repo, prompt ):
        strip(repo)
        revert(repo)
        purge(repo)
    print ''

def clean_repos(repos, prompt):
    if maybe_prompt_yn( 'Remove all modified files, untracked files and outgoing commits from:\n%s ?' % '\n'.join(repos), prompt ):
        for repo in repos:
            if repo_exists(repo):
                clean_repo(repo, False)

def check_repos(repos, fail_on_error):
    for repo in repos:
        if repo_exists(repo):
            if not is_repo_cleaned_and_updated(repo):
                if(fail_on_error):
                    raise Exception('repo %s is not cleaned and updated!' % repo)
                else:
                    if not prompt_yn( '%s is not clean and up to date! Continue?' % repo):
                        raise Exception( '%s is not clean and up to date! Halting!' % repo )

#=========================================================================================
# File Utils

def download_binary( source_url, destination, max_size ):
    http_response = urllib2.urlopen(url=source_url)
    content_length = http_response.headers['content-length']

    if content_length is None:
        raise Exception( "No content-length when downloading: " + source_url )

    if int( content_length ) > max_size:
        raise Exception( "Content-length ( " + content_length + " ) greater than max_size ( " + max_size + " ) " )

    dest_file = open(destination,'wb')
    dest_file.write(http_response.read())
    dest_file.close()

def file_contains(path, text):
    f = open(path, 'r')
    contents = f.read()
    f.close()
    return text in contents

def file_prepend(path, text):
    f = open(path)
    contents = f.read()
    f.close()

    f = open(path, 'w')
    f.write(text)
    f.write(contents)
    f.close()

def first_line_containing(value, file):
    p1 = Popen(["grep", "-m", "1", "-n", value, file], stdout=PIPE)
    p2 = Popen(["sed", "-n", 's/^\\([0-9]*\\)[:].*/\\1/p'], stdin=p1.stdout, stdout=PIPE)
    return int(p2.communicate()[0])

#Give group access to the specified path
def ensure_group_access(path):
    # Errs for any file not owned by this user.
    # But, the point is to set group writeability of any *new* files.
    execute('chmod -f -R g+w %s' % path, halt_if_fail=False)

def find_first_instance(regex, file, delim=""):
    with open(file, 'r') as f:
        pattern = re.compile(regex)
        for line in f:
            m = pattern.match(line)
            if m is not None:
                if pattern.groups > 0:
                    groups = m.groups()
                    useDel = False
                    res = ""
                    for g in m.groups():
                        if useDel:
                            res = res + delim
                        else:
                            useDel = True
                        res = res + g
                    return res
                else:
                    return m.group(0)
    return None

def delete_path( path ):
    shutil.rmtree(path)

def prompt_or_auto_delete( path, auto ):
    if not auto:
        prompt_to_delete(path)
    else:
        print
        delete_path( path )

def prompt_to_delete(path):
    if os.path.exists(path):
        result = prompt_w_suggestion("Delete the following file:\n %s [Yes|No]" % path, "no", "^(Yes|yes|No|no)$")
        if result == "Yes" or result == "yes":
            shutil.rmtree(path)

def force_symlink( source, symlink_path ):
    try:
        os.symlink( source, symlink_path )
    except OSError, e:
        if e.errno == errno.EEXIST:
            os.remove( symlink_path )
            os.symlink( source, symlink_path )

#note strs to find is mutated
def are_in_file( file_path, strs_to_find ):
    file = open( file_path )

    for line in file:
        if len( strs_to_find ) == 0:
                    return True

        index = 0
        while index < len( strs_to_find ):
            if strs_to_find[index] in line:
                del strs_to_find[index]
            else:
                index = index + 1

    return len( strs_to_find ) == 0

#=========================================================================================
# Change Log utils

#Read every line in the file until we reach a line with at least 8 dashes("-") in it
def changelog_header(filename):
    f = open(filename, 'r')
    header = []

    for line in f:
        if '-------' in line:
            break
        header.append(line)

    return ''.join(header)
    
def get_changelog_date():
    import datetime
    return datetime.datetime.now().strftime("%d %b %Y")

#Ask the user whether they want to edit the changelog
#Prepend a changelog message to the changelog and then open the passed in editor
def edit_changelog(projectName, path, version, description, editor):
    edit = raw_input("Edit the %s changelog? [Y/n]" % projectName)
    if not (edit == "n"):
        if not file_contains(path, version):
            file_prepend(path, description)
            execute([editor, path])


#Create a message to add to the current checker framework change log
def make_checkers_change_desc(version, changes):
    """Version %s, %s


%s

----------------------------------------------------------------------
"""  % (version, get_changelog_date(), changes)


#Create a message to add to the current JSR308 change log
def make_jsr308_change_desc(version, changes, latest_jdk):
    """Version %s, %s

Base build
  Updated to OpenJDK langtools build b%s

%s

----------------------------------------------------------------------
""" % (version, get_changelog_date(), latest_jdk, changes)
    
#Checker Framework Specific Change log method
#Queries whether or not the user wants to update the checker framework changelog
#then opens the changelog in the supplied editor
def edit_checkers_changelog(version, path, editor, changes=""):
    desc = make_checkers_change_desc(version, changes)
    edit_changelog("Checker Framework", path, version, desc, editor)
    

#JSR308 Specific Change log method
#Queries whether or not the user wants to update the checker framework changelog
#then opens the changelog in the supplied editor
def edit_langtools_changelog(version, openJdkReleaseSite, path, editor, changes=""):
    latest_jdk = latest_openjdk(openJdkReleaseSite)
    print("Latest OpenJDK release is b%s" % latest_jdk)
    desc = make_jsr308_change_desc(version, changes, latest_jdk)
    edit_changelog("JSR308 Langtools", path, version, desc, editor)

#=========================================================================================
# Maven Utils

def mvn_deploy_file(name, binary, version, dest_repo, group_id, pom=None):
    pomOps = "" 
    if pom is None:
        pomOps= "-DgeneratePom=True"
    else:
        pomOps="""-DgeneratePom=false -DpomFile=%s""" % (pom)

    command = """
    mvn deploy:deploy-file
        -DartifactId=%s
        -Dfile=%s
        -Dversion=%s
        -Durl=%s
        -DgroupId=%s
        -Dpackaging=jar
        """ % (name, binary, version, dest_repo, group_id) + pomOps
    return execute(command)

def mvn_deploy(binary, pom, url):
    command = """
    mvn deploy:deploy-file
        -Dfile=%s
        -DpomFile=%s
        -DgeneratePom=false
        -Durl=%s
        """ % (binary, pom, url)
    return execute(command)

def mvn_install(pluginDir):
    pom=pluginDirToPom(pluginDir)
    execute("mvn -f %s clean package" % pom)
    execute("mvn -f %s install" % pom)

def pluginDirToPom(pluginDir):
    return os.path.join(pluginDir, 'pom.xml')

def mvn_plugin_version(pluginDir):
    "Extract the version number from pom.xml in pluginDir/pom.xml"
    pomLoc = pluginDirToPom(pluginDir) 
    dom = minidom.parse(pomLoc)
    version = ""
    project = dom.getElementsByTagName("project").item(0)
    for i in range( 0, project.childNodes.length):
        childNode = project.childNodes.item(i)
        if childNode.nodeName == 'version':
            if childNode.hasChildNodes():
                version = childNode.firstChild.nodeValue
            else: 
                print "Empty Maven plugin version!"
                sys.exit(1)
            break
    else:
        print "Could not find Maven plugin version!"
        sys.exit(1)
    return version

def mvn_deploy_mvn_plugin(pluginDir, pom, version, mavenRepo):
    jarFile = "%s/target/checkers-maven-plugin-%s.jar" % (pluginDir, version)
    return mvn_deploy(jarFile, pom, mavenRepo)

#=========================================================================================
# Misc. Utils

def checklinks(makeFile, site_url=None):
    os.putenv('jsr308_www_online', site_url) # set environment var for subshell
    return execute('make -f %s checklinks' % makeFile, halt_if_fail=False)

#def find_project_locations( ):
#    afu_version       = max_version( AFU_INTERM_RELEASES_DIR    )
#    jsr308_cf_version = max_version( JSR308_INTERM_RELEASES_DIR )
#
#    return {
#        LT_OPT : {
#            project_name : LT_OPT,
#
#            repo_source  : INTERM_JSR308_REPO,
#            repo_dest    : LIVE_JSR308_REPO,
#
#            deployment_source : os.path.join( JSR308_INTERM_RELEASES_DIR, jsr308_cf_version )
#            deployment_dest   : os.path.join( JSR308_LIVE_RELEASES_DIR,   jsr308_cf_version )
#        },
#
#        AFU_OPT : {
#            project_name : AFU_OPT,
#
#            repo_source  : INTERM_AFU_REPO,
#            repo_dest    : LIVE_AFU_REPO,
#
#            deployment_source : os.path.join( AFU_INTERM_RELEASES_DIR, afu_version )
#            deployment_dest   : os.path.join( AFU_LIVE_RELEASES_DIR,   afu_version )
#        },
#
#        CF_OPT : {
#            project_name : CF_OPT,
#
#            repo_source : INTERM_CHECKER_REPO,
#            repo_dest   : LIVE_CHECKER_REPO,
#
#            deployment_source : os.path.join( CHECKER_INTERM_RELEASES_DIR, jsr308_cf_version )
#            deployment_dest   : os.path.join( CHECKER_LIVE_RELEASES_DIR,   jsr308_cf_version )
#        }
#    }
     #project_name, repo_source, repo_dest, deployment_source, deployment_dest

def get_announcement_email( version ):
    return """
    To:  jsr308-discuss@googlegroups.com, checker-framework-discuss@googlegroups.com
    Subject: Release %s of the Checker Framework and Type Annotations compiler

    We have released a new version of the Type Annotations (JSR 308) compiler,
    the Checker Framework, and the Eclipse plugin for the Checker Framework.
     * The Type Annotations compiler supports the type annotation syntax that is
       planned for a future version of the Java language.
     * The Checker Framework lets you create and/or run pluggable type-checkers,
       in order to detect and prevent bugs in your code.
     * The Eclipse plugin makes it more convenient to run the Checker Framework.

    You can find documentation and download links for these projects at:
    http://types.cs.washington.edu/jsr308/

    Changes for the Checker Framework
    <<Insert latest Checker Framework changelog update>>

    Changes for the Type Annotations Compiler
    <<Insert latest Jsr308-langtool changelog update>>
    """ % ( version )