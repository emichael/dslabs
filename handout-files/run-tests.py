#!/usr/bin/env python3

"""Runs JUnit tests with given options."""


import argparse
import platform
import shutil
import subprocess
import sys


__author__ = 'Ellis Michael (emichael@cs.washington.edu)'


RUNNER = 'dslabs.framework.testing.junit.DSLabsTestCore'
EXCLUDE_FILTER = 'org.junit.experimental.categories.ExcludeCategories'
RUN_CATEGORY = 'dslabs.framework.testing.junit.RunTests'
SEARCH_CATEGORY = 'dslabs.framework.testing.junit.SearchTests'

VIZ_DEBUGGER = 'dslabs.framework.testing.visualization.VizClient'

TRACE_REPLAY = 'dslabs.framework.testing.search.CheckSavedTraces'

BASE_COMMAND = (
    'java',
    '--add-opens', 'java.base/jdk.internal.reflect=ALL-UNNAMED',
    '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
    '--add-opens', 'java.base/java.util=ALL-UNNAMED',
    '--add-opens', 'java.base/java.util.concurrent.atomic=ALL-UNNAMED'
)

if platform.system() == 'Windows':
    CP_SEP = ';'
else:
    CP_SEP = ':'

RUNTIME_CLASSPATH = CP_SEP.join((
    'jars/framework.jar',
    'jars/framework-deps.jar',
    'jars/grader.jar',
    'jars/grader-deps.jar',
    'out/src/',
    'out/tst/'
))

def make():
    """Compile the source files, return True if successful."""
    try:
        subprocess.check_output(['make'], stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as ex:
        print("Could not compile sources.\n")
        print(ex.output.decode("utf-8"))
        shutil.rmtree('out')
        sys.exit(3)


def run_tests(lab, part=None, no_run=False, no_search=False,
              timers_disabled=False, log_level=None, single_threaded=False,
              start_viz=False, no_viz_server=False, do_checks=False,
              test_num=None, assertions=False, save_traces=False):
    """Run the specified tests."""
    make()

    command = list(BASE_COMMAND)

    if assertions:
        command.append('-ea')

    if timers_disabled:
        command.append('-DtestTimeoutsDisabled=true')

    if log_level:
        command.append('-DlogLevel=%s' % log_level)

    if single_threaded:
        command.append('-DsingleThreaded=true')

    if start_viz:
        command.append('-DstartViz=true')

    if no_viz_server:
        command.append('-DnoVizServer=true')

    if do_checks:
        command.append('-DdoChecks=true')

    if save_traces:
        command.append('-DsaveTraces=true')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        RUNNER
    ]

    command += ['--lab', str(lab)]

    if part is not None:
        command += ['--part', str(part)]

    if no_run:
        command.append('--exclude-run-tests')

    if no_search:
        command.append('--exclude-search-tests')

    if test_num:
        command += ['--test-num', str(test_num)]

    returncode = subprocess.call(command)
    sys.exit(returncode)


def run_viz_debugger(lab, args, no_viz_server=False):
    """Start the visual debugger."""
    make()

    command = list(BASE_COMMAND)

    if no_viz_server:
        command.append('-DnoVizServer=true')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        VIZ_DEBUGGER
    ]

    command += ['--lab', str(lab)]
    command += args

    returncode = subprocess.call(command)
    sys.exit(returncode)


def replay_traces(log_level=None, start_viz=False, no_viz_server=False,
                  do_checks=False, assertions=False):
    """Run the specified tests."""
    if not make():
        return

    command = list(BASE_COMMAND)

    if assertions:
        command.append('-ea')

    if log_level:
        command.append('-DlogLevel=%s' % log_level)

    if start_viz:
        command.append('-DstartViz=true')

    if no_viz_server:
        command.append('-DnoVizServer=true')

    if do_checks:
        command.append('-DdoChecks=true')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        RUNNER,
        TRACE_REPLAY
    ]

    subprocess.call(command)


def main():
    """Parse args and run tests."""
    parser = argparse.ArgumentParser()

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-l', '--lab', nargs=None,
                       help="lab to run tests for")
    parser.add_argument('-p', '--part', type=int, nargs='?', default=None,
                        help="part number for tests to run")

    parser.add_argument('--checks', action='store_true',
                        help="run checks on equals, hashCode, idempotence of "
                        "handlers, etc. when running tests")

    parser.add_argument('-n', '--test-num', nargs='?', default=None,
                        help="specific, comma-separated test numbers to run "
                        "(e.g., 2,5,7)")

    parser.add_argument('--no-run', action='store_true',
                        help="do not execute run tests")
    parser.add_argument('--no-search', action='store_true',
                        help="do not execure search tests")

    parser.add_argument('--no-timeouts', action='store_true',
                        help="stop tests from timing out")
    parser.add_argument('-g', '--log-level', nargs='?', type=str,
                        help="level the default Java util logging should use")
    parser.add_argument('-ea', '--assertions', action='store_true',
                        help="enable Java assertions")
    parser.add_argument('--single-threaded', action='store_true',
                        help="run the tests using only a single thread")

    group.add_argument('--replay-traces', action='store_true',
                       help="replay and recheck saved traces instead of "
                            "running provided tests")
    parser.add_argument('-s', '--save-traces', action='store_true',
                        help="save traces after search test failure")

    parser.add_argument('-z', '--start-viz', action='store_true',
                        help="start the visualization on invariant violation "
                             "or when the search is unable to find a "
                             "particular state")

    parser.add_argument('-d', '--debugger', nargs='*', metavar="ARG",
                        help="Don't run any tests, instead start the visual "
                        "debugger with the given args. By default, the args "
                        "should be: NUM_SERVERS NUM_CLIENTS WORKLOAD where "
                        "WORKLOAD is a comma-separated list of commands (e.g., "
                        "PUT:foo:bar,APPEND:foo:baz,GET:foo in the default "
                        "case of the KVStore); with these arguments, all "
                        "clients request the same workload. To give different "
                        "workloads for each client, the args should be: "
                        "NUM_SERVERS NUM_CLIENTS WORKLOAD_1 WORKLOAD_2 ... "
                        "WORKLOAD_NUM_CLIENTS where each WORLOAD_i is a "
                        "comma-separated list of commands and a workload is "
                        "provided for each client.")

    parser.add_argument('--no-viz-server', action='store_true',
                        help="do not automatically start the visualization "
                        "server; instead, the user starts the server  and "
                        "opens the browser manually")

    args = parser.parse_args()

    if args.debugger:
        if args.replay_traces:
            parser.error("starting the debugger with --replay-traces not "
                         "currently supported")
        run_viz_debugger(args.lab, args.debugger, args.no_viz_server)
    elif args.replay_traces:
        replay_traces(log_level=args.log_level,
                      start_viz=args.start_viz,
                      no_viz_server=args.no_viz_server,
                      do_checks=args.checks,
                      assertions=args.assertions)
    else:
        run_tests(args.lab,
                  part=args.part,
                  no_run=args.no_run,
                  no_search=args.no_search,
                  timers_disabled=args.no_timeouts,
                  log_level=args.log_level,
                  single_threaded=args.single_threaded,
                  start_viz=args.start_viz,
                  no_viz_server=args.no_viz_server,
                  do_checks=args.checks,
                  test_num=args.test_num,
                  assertions=args.assertions,
                  save_traces=args.save_traces)


if __name__ == '__main__':
    main()
