import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parent.parent
LAUNCHER_PATH = REPO_ROOT / 'handout-files' / 'run-tests.py'
NONREPARENTING_VAR = '_JAVA_AWT_WM_NONREPARENTING'


def _load_launcher():
    prev_dont_write_bytecode = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        spec = importlib.util.spec_from_file_location(
            'dslabs_handout_run_tests', LAUNCHER_PATH)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        sys.dont_write_bytecode = prev_dont_write_bytecode


launcher = _load_launcher()


class VisualizerLauncherEnvTest(unittest.TestCase):

    def _run_cli(self, cli_args, env, system='Linux', run_side_effect=None):
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch.object(launcher.platform, 'system', return_value=system), \
                mock.patch.object(launcher, 'make') as mock_make, \
                mock.patch.object(launcher.subprocess, 'call', return_value=0) as mock_call, \
                mock.patch.object(launcher.subprocess, 'run', side_effect=run_side_effect) as mock_run, \
                mock.patch.object(sys, 'argv', ['run-tests.py', *cli_args]):
            with self.assertRaises(SystemExit) as ctx:
                launcher.main()
            self.assertEqual(ctx.exception.code, 0)
            mock_make.assert_called_once_with()
            mock_call.assert_called_once()
            command = mock_call.call_args.args[0]
            child_env = mock_call.call_args.kwargs.get('env')
            if child_env is None:
                child_env = dict(os.environ)
            return command, child_env, mock_run

    def test_debugger_sets_nonreparenting_for_xmonad_xdg_current_desktop(self):
        command, child_env, mock_run = self._run_cli(
            ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
            {'XDG_CURRENT_DESKTOP': 'XMonad', 'CUSTOM_VAR': 'keep'},
        )
        self.assertIn(launcher.VIZ_DEBUGGER, command)
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        mock_run.assert_not_called()

    def test_start_viz_sets_nonreparenting_for_xmonad_desktop_session(self):
        command, child_env, mock_run = self._run_cli(
            ['-l', '1', '-z'],
            {'DESKTOP_SESSION': 'xmonad', 'CUSTOM_VAR': 'keep'},
        )
        self.assertIn('-DstartViz=true', command)
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        mock_run.assert_not_called()

    def test_visualize_trace_sets_nonreparenting_for_xmonad(self):
        command, child_env, mock_run = self._run_cli(
            ['--visualize-trace', 'sample.trace'],
            {'XDG_CURRENT_DESKTOP': 'none+XMONAD', 'CUSTOM_VAR': 'keep'},
        )
        self.assertIn(launcher.TRACE_VIZ, command)
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        mock_run.assert_not_called()

    def test_replay_traces_sets_nonreparenting_only_with_start_viz(self):
        env = {'XDG_CURRENT_DESKTOP': 'xmonad', 'CUSTOM_VAR': 'keep'}

        command, child_env, mock_run = self._run_cli(
            ['-z', '--replay-traces', 'sample.trace'],
            env,
        )
        self.assertIn('--replay-traces', command)
        self.assertIn('-DstartViz=true', command)
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        mock_run.assert_not_called()

        _, plain_env, plain_run = self._run_cli(
            ['--replay-traces', 'sample.trace'],
            env,
        )
        self.assertNotIn(NONREPARENTING_VAR, plain_env)
        self.assertEqual(plain_env.get('CUSTOM_VAR'), 'keep')
        plain_run.assert_not_called()

    def test_explicit_override_and_unrelated_env_preserved(self):
        for override in ('0', ''):
            with self.subTest(override=override):
                _, child_env, mock_run = self._run_cli(
                    ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
                    {
                        'XDG_CURRENT_DESKTOP': 'xmonad',
                        NONREPARENTING_VAR: override,
                        'CUSTOM_VAR': 'keep',
                    },
                )
                self.assertEqual(child_env.get(NONREPARENTING_VAR), override)
                self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
                mock_run.assert_not_called()

    def test_unidentified_x11_session_with_positive_xprop_identification(self):
        responses = [
            subprocess.CompletedProcess(
                args=['xprop'],
                returncode=0,
                stdout='_NET_SUPPORTING_WM_CHECK: window id # 0x400001\n',
                stderr='',
            ),
            subprocess.CompletedProcess(
                args=['xprop'],
                returncode=0,
                stdout='_NET_WM_NAME = "XMonad"\n',
                stderr='',
            ),
        ]
        _, child_env, mock_run = self._run_cli(
            ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
            {'DISPLAY': ':0', 'CUSTOM_VAR': 'keep'},
            run_side_effect=responses,
        )
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        self.assertEqual(mock_run.call_count, 2)
        for call in mock_run.call_args_list:
            self.assertGreater(call.kwargs.get('timeout', 0), 0)

    def test_unidentified_x11_session_with_wmctrl_fallback(self):
        responses = [
            FileNotFoundError('xprop'),
            subprocess.CompletedProcess(
                args=['wmctrl', '-m'],
                returncode=0,
                stdout='Name: xmonad\nClass: N/A\n',
                stderr='',
            ),
        ]
        _, child_env, mock_run = self._run_cli(
            ['-l', '1', '-z'],
            {'DISPLAY': ':0'},
            run_side_effect=responses,
        )
        self.assertEqual(child_env.get(NONREPARENTING_VAR), '1')
        self.assertEqual(mock_run.call_count, 2)

    def test_unidentified_x11_session_with_missing_or_timed_out_lookup(self):
        for effect in (
            FileNotFoundError('missing'),
            subprocess.TimeoutExpired(cmd=['xprop'], timeout=1.0),
        ):
            with self.subTest(effect=type(effect).__name__):
                _, child_env, _ = self._run_cli(
                    ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
                    {'DISPLAY': ':0'},
                    run_side_effect=effect,
                )
                self.assertNotIn(NONREPARENTING_VAR, child_env)

    def test_unidentified_x11_session_with_non_xmonad_wm(self):
        responses = [
            subprocess.CompletedProcess(
                args=['xprop'],
                returncode=0,
                stdout='_NET_SUPPORTING_WM_CHECK: window id # 0x400001\n',
                stderr='',
            ),
            subprocess.CompletedProcess(
                args=['xprop'],
                returncode=0,
                stdout='_NET_WM_NAME = "i3"\n',
                stderr='',
            ),
        ]
        _, child_env, _ = self._run_cli(
            ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
            {'DISPLAY': ':0'},
            run_side_effect=responses,
        )
        self.assertNotIn(NONREPARENTING_VAR, child_env)

    def test_other_desktop_does_not_set_nonreparenting(self):
        _, child_env, mock_run = self._run_cli(
            ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
            {'XDG_CURRENT_DESKTOP': 'ubuntu:GNOME', 'DISPLAY': ':0'},
        )
        self.assertNotIn(NONREPARENTING_VAR, child_env)
        mock_run.assert_not_called()

    def test_non_linux_host_does_not_set_nonreparenting(self):
        for system in ('Darwin', 'Windows'):
            with self.subTest(system=system):
                _, child_env, mock_run = self._run_cli(
                    ['-l', '1', '-d', '1', '1', 'PUT:foo:bar'],
                    {'XDG_CURRENT_DESKTOP': 'xmonad', 'DISPLAY': ':0'},
                    system=system,
                )
                self.assertNotIn(NONREPARENTING_VAR, child_env)
                mock_run.assert_not_called()

    def test_ordinary_test_run_does_not_set_nonreparenting(self):
        _, child_env, mock_run = self._run_cli(
            ['-l', '1'],
            {'XDG_CURRENT_DESKTOP': 'xmonad', 'CUSTOM_VAR': 'keep'},
        )
        self.assertNotIn(NONREPARENTING_VAR, child_env)
        self.assertEqual(child_env.get('CUSTOM_VAR'), 'keep')
        mock_run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
