#!/usr/bin/env python3

"""Runs JUnit tests with given options."""


import argparse
import platform
import shutil
import subprocess
import sys


__author__ = 'Ellis Michael (emichael@cs.washington.edu)'


RUNNER = 'dslabs.framework.testing.junit.DSLabsTestCore'
VIZ_DEBUGGER = 'dslabs.framework.testing.visualization.VizClient'
TRACE_VIZ = 'dslabs.framework.testing.visualization.SavedTraceViz'

EXCLUDE_FILTER = 'org.junit.experimental.categories.ExcludeCategories'
RUN_CATEGORY = 'dslabs.framework.testing.junit.RunTests'
SEARCH_CATEGORY = 'dslabs.framework.testing.junit.SearchTests'

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
    """Compile the source files, exit script if unsuccessful."""
    try:
        subprocess.check_output(['make'], stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as ex:
        print("Could not compile sources.\n")
        print(ex.output.decode("utf-8"))
        shutil.rmtree('out')
        sys.exit(3)


def run_tests(lab, part=None, no_run=False, no_search=False,
              timers_disabled=False, log_level=None, single_threaded=False,
              start_viz=False, do_checks=False, test_num=None, assertions=False,
              save_traces=False, jvm_properties=None):
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

    if do_checks:
        command.append('-DdoChecks=true')

    if save_traces:
        command.append('-DsaveTraces=true')

    if jvm_properties is not None:
        for prop in jvm_properties:
            command.append(f'-D{prop}')

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



def run_viz_debugger(lab, args, assertions=False, jvm_properties=None):
    """Start the visual debugger."""
    make()

    command = list(BASE_COMMAND)

    if assertions:
        command.append('-ea')

    if jvm_properties is not None:
        for prop in jvm_properties:
            command.append(f'-D{prop}')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        VIZ_DEBUGGER
    ]

    command += ['--lab', str(lab)]
    command += args

    returncode = subprocess.call(command)
    sys.exit(returncode)



def visualize_trace(trace_name, assertions=False, jvm_properties=None):
    """Visualize a trace."""
    make()

    command = list(BASE_COMMAND)

    if assertions:
        command.append('-ea')

    if jvm_properties is not None:
        for prop in jvm_properties:
            command.append(f'-D{prop}')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        TRACE_VIZ,
        trace_name
    ]

    returncode = subprocess.call(command)
    sys.exit(returncode)


def replay_traces(trace_names=None, lab=None, part=None, log_level=None,
                  start_viz=False, do_checks=False, assertions=False,
                  jvm_properties=None):
    """Replay traces."""
    make()

    command = list(BASE_COMMAND)

    if assertions:
        command.append('-ea')

    if log_level:
        command.append('-DlogLevel=%s' % log_level)

    if start_viz:
        command.append('-DstartViz=true')

    if do_checks:
        command.append('-DdoChecks=true')

    if jvm_properties is not None:
        for prop in jvm_properties:
            command.append(f'-D{prop}')

    command += [
        '-cp',
        RUNTIME_CLASSPATH,
        RUNNER,
        '--replay-traces'
    ]

    if lab is not None:
        command += ['--lab', lab]

    if part is not None:
        command += ['--part', str(part)]

    if trace_names:
        command += trace_names

    returncode = subprocess.call(command)
    sys.exit(returncode)


def main():
    """Parse args and run tests."""
    parser = argparse.ArgumentParser()
    run_modes = parser.add_argument_group("Alternate run modes",
        "Execute one of the following options instead of running tests.")
    group = run_modes.add_mutually_exclusive_group()

    parser.add_argument('-l', '--lab', help="lab to run tests for")

    parser.add_argument('-p', '--part',
                        help="part number(s) to run tests for (comma-separated)")

    parser.add_argument('-n', '--test-num',
                        help="specific, comma-separated test numbers to run "
                        "(e.g., 2,5,7 or 2.2,2.5,2.7)")
    parser.add_argument('--no-run', action='store_true',
                        help="do not execute run tests")
    parser.add_argument('--no-search', action='store_true',
                        help="do not execute search tests")
    parser.add_argument('--checks', action='store_true',
                        help="run checks on equals, hashCode, idempotence of "
                        "handlers, etc. when running tests")
    parser.add_argument('--no-timeouts', action='store_true',
                        help="stop tests from timing out")
    parser.add_argument('-g', '--log-level',
                        help="level the default Java util logging should use")
    parser.add_argument('-ea', '--assertions', action='store_true',
                        help="enable Java assertions")
    parser.add_argument('--single-threaded', action='store_true',
                        help="run the tests using only a single thread")
    parser.add_argument('-s', '--save-traces', action='store_true',
                        help="save traces after search test failure")
    parser.add_argument('-z', '--start-viz', action='store_true',
                        help="start the visualization on invariant violation "
                             "or when the search is unable to find a "
                             "particular state")

    group.add_argument('-d', '--debugger', nargs='*', metavar="ARG",
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

    group.add_argument('--replay-traces', nargs='*', default=None,
                       metavar="TRACE_NAME",
                       help="Replay and recheck saved traces. Can specify "
                       "--lab and --part to restrict which traces to run or "
                       "supply one or more filenames of specific traces.")

    group.add_argument('--visualize-trace', metavar="TRACE_NAME",
                       help="immediately start the visual debugger with the "
                       "specified trace, regardless of whether it still causes "
                       "an invariant violation")

    parser.add_argument('-D', action="append", type=str,
                        metavar="PROPERTY=VALUE", dest="jvm_properties",
                        help="command-line property to pass to the JVM, can be "
                        "repeated (e.g. -Dfoo=val1 -Dbar=val2)")

    args = parser.parse_args()

    def disallow_arguments(current_option, disallowed_options):
        for option in disallowed_options:
            arg_name = option.split('/')[-1].lstrip('-').replace('-', '_')
            if getattr(args, arg_name):
                parser.error(
                    f"argument {option} not allowed with argument "
                    f"{current_option}")

    if args.debugger:
        if args.lab is None:
            parser.error("-l/--lab is required with -d/--debugger")
        disallow_arguments('-d/--debugger',
            ('-p/--part', '--checks', '-n/--test-num', '--no-run',
             '--no-search', '--no-timeouts', '-g/--log-level',
             '--single-threaded', '-s/--save-traces', '-z/--start-viz'))
        run_viz_debugger(args.lab, args.debugger, assertions=args.assertions,
                         jvm_properties=args.jvm_properties)

    elif args.replay_traces is not None:
        if args.part is not None and args.lab is None:
            parser.error("cannot specify -p/--part without -l/--lab")
        if args.lab is not None and args.replay_traces:
            parser.error("cannot specify both -l/--lab and TRACE_NAME")
        disallow_arguments('--replay-traces',
            ('-n/--test-num', '--no-run', '--no-search', '--no-timeouts',
             '--single-threaded', '-s/--save-traces'))
        replay_traces(trace_names=args.replay_traces,
                      lab=args.lab,
                      part=args.part,
                      log_level=args.log_level,
                      start_viz=args.start_viz,
                      do_checks=args.checks,
                      assertions=args.assertions,
                      jvm_properties=args.jvm_properties)

    elif args.visualize_trace is not None:
        disallow_arguments('--visualize-trace',
            ('-l/--lab', '-p/--part', '--checks', '-n/--test-num', '--no-run',
             '--no-search', '--no-timeouts', '-g/--log-level',
             '--single-threaded', '-s/--save-traces', '-z/--start-viz'))
        visualize_trace(args.visualize_trace, assertions=args.assertions,
                        jvm_properties=args.jvm_properties)

    else:
        if args.lab is None:
            parser.error("-l/--lab is required")
        run_tests(args.lab,
                  part=args.part,
                  no_run=args.no_run,
                  no_search=args.no_search,
                  timers_disabled=args.no_timeouts,
                  log_level=args.log_level,
                  single_threaded=args.single_threaded,
                  start_viz=args.start_viz,
                  do_checks=args.checks,
                  test_num=args.test_num,
                  assertions=args.assertions,
                  save_traces=args.save_traces,
                  jvm_properties=args.jvm_properties)


if __name__ == '__main__':
    main()
