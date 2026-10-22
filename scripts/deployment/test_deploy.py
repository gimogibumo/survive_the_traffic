"""Check failure gates without calling AWS, Kubernetes or the application."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
IMAGE = '248189924429.dkr.ecr.ap-northeast-2.amazonaws.com/survive-the-traffic:' + 'a' * 40


class DeploymentGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.log = self.directory / 'calls.jsonl'
        kubectl = self.directory / 'kubectl'
        kubectl.write_text('''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
with open(os.environ['CALL_LOG'], 'a') as log:
    log.write(json.dumps(args) + '\\n')
if args[0] == 'kustomize':
    print('apiVersion: v1\\nkind: List\\nitems: []')
elif args[0] == 'create' and '--dry-run=client' in args:
    print(json.dumps({'metadata': {}, 'spec': {'template': {'spec': {'containers': [{}]}}}}))
elif args[0] == 'wait':
    sys.exit(int(os.environ.get('MIGRATION_FAIL', '0')))
elif args[:2] == ['get', 'deployment']:
    print('previous:sha')
elif args[:2] == ['get', 'pods']:
    print(json.dumps({'items': [{'metadata': {}, 'status': {'containerStatuses': [
        {'ready': True, 'restartCount': 0}]}}]}))
elif args[:2] == ['get', 'ingress']:
    print('example.ap-northeast-2.elb.amazonaws.com')
''')
        kubectl.chmod(0o755)
        for name, body in [('sleep', 'exit 0'), ('curl', 'printf 200')]:
            executable = self.directory / name
            executable.write_text('#!/bin/sh\n' + body + '\n')
            executable.chmod(0o755)
        self.env = {**os.environ, 'PATH': str(self.directory) + ':' + os.environ['PATH'],
                    'CALL_LOG': str(self.log)}

    def run_deploy(self, mode='deploy', image=IMAGE):
        return subprocess.run(['bash', str(ROOT / 'scripts/deployment/deploy.sh'),
                               mode, image, 'test-1'], env=self.env,
                              text=True, capture_output=True)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_failed_migration_never_applies_or_rolls_out_application(self):
        self.env['MIGRATION_FAIL'] = '1'
        result = self.run_deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call[:2] == ['apply', '-f'] or call[0] == 'rollout'
                             for call in self.calls()))

    def test_successful_migration_precedes_apply_and_both_rollouts(self):
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        wait = next(i for i, call in enumerate(calls) if call[0] == 'wait')
        apply = next(i for i, call in enumerate(calls) if call[:2] == ['apply', '-f'])
        self.assertLess(wait, apply)
        rollouts = [call[2] for call in calls if call[:2] == ['rollout', 'status']]
        self.assertEqual(rollouts, ['deployment/ticketing-api',
                                    'deployment/ticketing-queue-worker'])

    def test_explicit_rollback_skips_migration_and_checks_rollouts(self):
        result = self.run_deploy('rollback', IMAGE.rsplit(':', 1)[0] + ':previous-tag')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(call[0] in ['create', 'wait'] for call in self.calls()))
        self.assertEqual(sum(call[0] == 'rollout' for call in self.calls()), 2)

    def test_non_sha_deployment_is_rejected_before_kubernetes_calls(self):
        result = self.run_deploy(image=IMAGE.rsplit(':', 1)[0] + ':latest')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())

    def test_wrong_repository_is_rejected_before_kubernetes_calls(self):
        result = self.run_deploy(image=IMAGE.replace('survive-the-traffic:', 'other:'))
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())


if __name__ == '__main__':
    unittest.main()
