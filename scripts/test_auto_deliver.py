"""Local control-flow tests. SDK, Gradle, ADB and GitHub calls are simulated.
Run: python scripts/test_auto_deliver.py
No APK build, device installation or network publication is performed.
"""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location('auto_deliver', Path(__file__).with_name('auto-deliver.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

class JavaHomeTest(unittest.TestCase):
    def test_language_server_jre_is_not_a_build_jdk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/'bin').mkdir()
            (root/'release').write_text('JAVA_VERSION="21.0.12"')
            for name in ['java.exe', 'javac.exe']:
                (root/'bin'/name).touch()
            self.assertFalse(m.complete_java_home(root))
            (root/'bin/jlink.exe').touch()
            self.assertTrue(m.complete_java_home(root))

class SignatureSchemeTest(unittest.TestCase):
    V1 = 'Verified using v1 scheme (JAR signing): true\n'
    V2 = 'Verified using v2 scheme (APK Signature Scheme v2): true\n'

    def test_v2_only_apk_is_rejected_because_vivo_needs_v1(self):
        with self.assertRaisesRegex(m.DeliveryError, 'missing v1 signature'):
            m.check_signature_schemes(self.V1.replace('true', 'false') + self.V2)

    def test_v1_and_v2_pass(self):
        m.check_signature_schemes(self.V1 + self.V2 + 'Verified using v3 scheme (APK Signature Scheme v3): false\n')

    def test_output_without_verbose_lines_is_not_a_pass(self):
        with self.assertRaisesRegex(m.DeliveryError, 'missing v1/v2 signature'):
            m.check_signature_schemes('Signer #1 certificate DN: CN=Android Debug, O=Android, C=US\n')

class AdbHandshakeTest(unittest.TestCase):
    def test_reserved_superdisplay_port_stops_before_any_adb_command(self):
        runner = Mock()
        runner.run.return_value = 'Start Port    End Port\n----------    --------\n      1355        1454\n'
        with self.assertRaisesRegex(m.DeliveryError, 'reserves SuperDisplay TCP 1445'):
            m.check_adb_forward_port(runner)
        self.assertEqual([['netsh.exe', 'interface', 'ipv4', 'show', 'excludedportrange', 'protocol=tcp']],
                         [call.args[0] for call in runner.run.call_args_list])

    def test_reservation_checks_ipv6_and_inclusive_boundaries(self):
        for range_text in ('1445 1544', '1346 1445 *'):
            runner = Mock()
            runner.run.side_effect = ['5357 5357', range_text]
            with self.assertRaisesRegex(m.DeliveryError, 'Windows ipv6 reserves'):
                m.check_adb_forward_port(runner)

    def test_unrelated_reservations_do_not_change_services_or_ranges(self):
        runner = Mock()
        runner.run.return_value = '起始端口 结束端口\n 1345 1444\n 1446 1545\n 50000 50059 *\n'
        m.check_adb_forward_port(runner)
        self.assertEqual(2, runner.run.call_count)
        self.assertTrue(all(call.args[0][3] == 'show' for call in runner.run.call_args_list))

    def test_private_old_adb_is_rejected_without_starting_or_stopping_the_daemon(self):
        with tempfile.TemporaryDirectory() as tmp:
            companion = Path(tmp) / 'adb.exe'
            companion.touch()
            runner = Mock()
            runner.run.side_effect = ['Android Debug Bridge version 1.0.41', 'Android Debug Bridge version 1.0.40']
            with self.assertRaisesRegex(m.DeliveryError, 'SuperDisplay ADB 1.0.40 conflicts'):
                m.check_adb_compatibility(runner, 'sdk-adb', companion)
            self.assertEqual([['sdk-adb', 'version'], [str(companion), 'version']],
                             [call.args[0] for call in runner.run.call_args_list])

    def test_matching_protocols_allow_both_clients_without_changing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            companion = Path(tmp) / 'adb.exe'
            companion.write_bytes(b'preserve')
            runner = Mock()
            runner.run.return_value = 'Android Debug Bridge version 1.0.41'
            m.check_adb_compatibility(runner, 'sdk-adb', companion)
            self.assertEqual(b'preserve', companion.read_bytes())
            self.assertEqual(2, runner.run.call_count)

    def test_daemon_version_restart_waits_only_for_the_selected_device_then_retries_the_read(self):
        runner = Mock()
        runner.run.side_effect = [m.DeliveryError("adb server version (40) doesn't match client (41); device not found"), '', 'arm64-v8a']
        self.assertEqual('arm64-v8a', m.read_adb_device(runner, 'sdk-adb', 'tablet', 'getprop', 'ro.product.cpu.abilist'))
        command = ['sdk-adb', '-s', 'tablet', 'shell', 'getprop', 'ro.product.cpu.abilist']
        self.assertEqual([command, ['sdk-adb', '-s', 'tablet', 'wait-for-device'], command],
                         [call.args[0] for call in runner.run.call_args_list])
        self.assertEqual(10, runner.run.call_args_list[1].kwargs['timeout'])

    def test_read_does_not_retry_permission_denial_or_switch_devices(self):
        runner = Mock()
        runner.run.side_effect = m.DeliveryError('permission denied')
        with self.assertRaises(m.DeliveryError):
            m.read_adb_device(runner, 'sdk-adb', 'tablet', 'getprop', 'ro.product.cpu.abilist')
        self.assertEqual(1, runner.run.call_count)

    def test_transient_daemon_restart_retries_only_the_device_read_once(self):
        runner = Mock()
        runner.run.side_effect = [m.DeliveryError('protocol fault: connection reset'), '', 'device-list']
        self.assertEqual('device-list', m.list_adb_devices(runner, 'sdk-adb'))
        self.assertEqual([['sdk-adb', 'devices'], ['sdk-adb', 'start-server'], ['sdk-adb', 'devices']],
                         [call.args[0] for call in runner.run.call_args_list])

    def test_permanent_failure_is_not_retried_forever(self):
        runner = Mock()
        runner.run.side_effect = [m.DeliveryError('protocol fault'), '', m.DeliveryError('protocol fault')]
        with self.assertRaises(m.DeliveryError): m.list_adb_devices(runner, 'sdk-adb')
        self.assertEqual(3, runner.run.call_count)

    def test_unrelated_error_does_not_restart_adb(self):
        runner = Mock()
        runner.run.side_effect = m.DeliveryError('permission denied')
        with self.assertRaises(m.DeliveryError): m.list_adb_devices(runner, 'sdk-adb')
        self.assertEqual(1, runner.run.call_count)

class FakeRunner(m.Runner):
    def __init__(self, root):
        super().__init__(root)
        self.calls = []; self.installed = None; self.install_count = 0
        self.releases = []; self.build_count = 0; self.publish_count = 0
        self.fail_build = False; self.fail_install = False; self.fail_publish_once = False
        self.bad_package = False; self.modify_during_build = False; self.remote_commit = None
        self.network_failure = False; self.submodule_status = None
    def run(self, args, **kw):
        args = [str(a) for a in args]; self.calls.append(args)
        if args[0] == 'git':
            if args[1:3] == ['ls-remote', 'origin']: return ''
            if 'apply' in args: return ''
            if args[1:] == ['submodule','status','--recursive'] and self.submodule_status is not None:
                return self.submodule_status
            if ['submodule','update'] in [args[i:i+2] for i in range(len(args)-1)]:
                self.submodule_status = self.submodule_status.replace('-', ' ', 1)
                return ''
            return super().run(args, **kw)
        if args[0] == 'fake-powershell':
            if 'fake-signer' in args[-1]:
                # Like apksigner: without --min-sdk-version below 24 it skips v1 and reports it false.
                v1 = 'true' if '--min-sdk-version 23' in args[-1] else 'false'
                return (f'Verified using v1 scheme (JAR signing): {v1}\n'
                        'Verified using v2 scheme (APK Signature Scheme v2): true\n')
            return ''
        if args[0] == 'fake-aapt':
            pkg = 'wrong.package' if self.bad_package else m.PACKAGE
            return f"package: name='{pkg}' versionCode='20261071' versionName='1.6.0' platformBuildVersionName='1'\nnative-code: 'arm64-v8a'"
        if args[0] == 'fake-adb':
            if 'pm' in args:
                return 'package:/data/app/cyime/base.apk' if self.installed else ''
            if 'sha256sum' in args:
                return f'{self.installed}  /data/app/cyime/base.apk'
            if 'install' in args:
                if self.fail_install: raise m.DeliveryError('INSTALL_FAILED_UPDATE_INCOMPATIBLE')
                self.installed = m.digest(Path(args[-1])); self.install_count += 1
                return 'Performing Streamed Install\nSuccess'
        if args[0] == 'gh':
            if self.network_failure: raise m.DeliveryError('network failure')
            if args[1] == 'api':
                if '--paginate' in args: return json.dumps(self.releases)
                if any('/commits/' in x for x in args):
                    return self.remote_commit or super().run(['git','rev-parse','HEAD'])
            if args[1:3] == ['release','create']:
                self.releases = [dict(tag_name=args[3], target_commitish=args[args.index('--target')+1],
                    draft=True, html_url='https://github.com/owner/project/releases/tag/1.6.0', assets=[])]
                return ''
            if args[1:3] == ['release','upload']:
                if self.fail_publish_once and self.releases[0]['assets']:
                    self.fail_publish_once=False
                    raise m.DeliveryError('upload interrupted')
                path = Path(args[4])
                self.releases[0]['assets'].append({'name':path.name, 'digest':'sha256:'+m.digest(path)})
                return ''
            if args[1:3] == ['release','edit']:
                self.releases[0]['draft'] = False; self.publish_count += 1; return ''
        raise AssertionError('Unexpected external command: ' + repr(args))

class FakeDelivery(m.Delivery):
    def preflight(self):
        self.stage('CHECK')
        self.ps = 'fake-powershell'; self.adb = 'fake-adb'; self.signer = 'fake-signer'
        self.aapt = 'fake-aapt'; self.serial = '' if getattr(self.o, 'build_only', False) or getattr(self.o, 'no_device', False) else 'tablet'; self.repo = 'owner/project'
        if self.o.publish: self.require_committed_sources()
    def script(self, name, parameters='', live=True):
        if name == 'build-apk.ps1':
            self.r.build_count += 1
            if self.r.fail_build: raise m.DeliveryError('build failed')
            edition = 'full' if '-BundleModels' in parameters else 'standard'
            directory = self.receipt_path(edition).parent
            directory.mkdir(parents=True, exist_ok=True)
            apk = directory / f"CyIME-1.6.0{'-full' if edition=='full' else ''}-arm64-v8a.apk"
            apk.write_bytes(('APK '+edition+' '+self.fingerprint()).encode())
            m.write_json(self.receipt_path(edition), dict(version=self.version, versionCode=self.code,
                buildType='release', bundledModels=edition=='full',sourceCommit=self.commit,
                layoutGate='not-run',files=[dict(path=str(apk),sha256=m.digest(apk))]))
            if self.r.modify_during_build:
                (self.root/'app/src/main/example.kt').write_text('changed concurrently')
        else: raise AssertionError(name)

class PureTests(unittest.TestCase):
    def test_literal_version(self):
        self.assertEqual(m.parse_version('versionCode = 123\nversionName = "1.6.0"\n'),('1.6.0',123))
    def test_comment_ignored(self):
        self.assertEqual(m.parse_version('//versionCode = 99\nversionCode = 123\nversionName = "1.6.0"'),('1.6.0',123))
    def test_missing_version(self):
        with self.assertRaises(m.DeliveryError): m.parse_version('versionCode = 123')
    def test_duplicate_version(self):
        with self.assertRaises(m.DeliveryError): m.parse_version('versionCode = 1\nversionCode = 2\nversionName = "1.2.3"')
    def test_bad_version_limit(self):
        with self.assertRaises(m.DeliveryError): m.parse_version('versionCode = 9999999999\nversionName = "1.2.3"')
    def test_single_device(self): self.assertEqual(m.choose_device('List of devices attached\nABC\tdevice\n'),'ABC')
    def test_no_device(self):
        with self.assertRaises(m.DeliveryError): m.choose_device('List of devices attached\n')
    def test_unauthorized_device(self):
        with self.assertRaises(m.DeliveryError): m.choose_device('ABC unauthorized','ABC')
    def test_multiple_devices(self):
        with self.assertRaises(m.DeliveryError): m.choose_device('A device\nB device')
    def test_explicit_device(self): self.assertEqual(m.choose_device('A device\nB device','B'),'B')
    def test_quote_powershell_path(self): self.assertEqual(m.ps_quote("c:/a'b"), "'c:/a''b'")
    def test_lock_excludes_second_run(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'lock'
            with m.delivery_lock(p):
                with self.assertRaises(m.DeliveryError):
                    with m.delivery_lock(p): pass
            with m.delivery_lock(p): pass

class FlowTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        files={'app/build.gradle.kts':'versionCode = 20261071\nversionName = "1.6.0"\n',
               'app/src/main/example.kt':'original', '.gitignore':'/.gradle/\n/app/build/\n',
               'scripts/build-apk.ps1':'# dummy','scripts/release-abis.gradle':'# dummy',
               'app/keystore.properties':'# no credentials in tests'}
        for plugin in ('librime-predict','librime-octagram','librime-lua','librime-t9'):
            files['app/src/main/jni/'+plugin+'/CMakeLists.txt']='# fixture'
        for name,text in files.items():
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
        def git(*args): subprocess.run(['git',*args],cwd=self.root,check=True,capture_output=True)
        git('init','-q');git('config','user.email','test@example.invalid');git('config','user.name','Local Test')
        git('add','.');git('commit','-qm','fixture')
        self.runner=FakeRunner(self.root)
    def job(self, **kwargs):
        o=SimpleNamespace(publish=False,full=False,rebuild=False,serial='')
        for key,value in kwargs.items(): setattr(o,key,value)
        return FakeDelivery(self.root,o,self.runner)
    def execute(self, **kw):
        with contextlib.redirect_stdout(io.StringIO()): self.job(**kw).execute()
    def test_standard_build_install(self):
        self.execute();self.assertEqual(self.runner.build_count,1);self.assertEqual(self.runner.install_count,1)
    def test_default_never_publishes(self):
        self.execute();self.assertEqual(self.runner.publish_count,0);self.assertFalse(self.runner.releases)
    def test_build_only_produces_verified_apk_without_installing(self):
        self.execute(build_only=True)
        self.assertEqual(self.runner.build_count,1)
        self.assertEqual(self.runner.install_count,0)
        self.assertEqual(self.runner.publish_count,0)
        self.assertIsNotNone(self.job().load_artifact('standard'))
    def test_build_only_failure_does_not_install_an_old_package(self):
        self.execute(build_only=True)
        self.runner.fail_build=True
        with self.assertRaises(m.DeliveryError): self.execute(build_only=True,rebuild=True)
        self.assertEqual(self.runner.install_count,0)
    def test_changed_uninstalled_draft_is_archived_without_a_version_bump(self):
        self.execute(build_only=True)
        old = self.job().load_artifact('standard')
        content = Path(old['path']).read_bytes()
        (self.root/'app/src/main/example.kt').write_text('same batch, improved implementation')
        self.execute(build_only=True)
        archived = list((self.root/'app/build/outputs/apk/archive').rglob('*.apk'))
        self.assertEqual(len(archived), 1)
        self.assertEqual(archived[0].read_bytes(), content)
        self.assertEqual((self.runner.build_count, self.runner.install_count), (2, 0))
        self.assertNotEqual(m.digest(Path(old['path'])), old['sha256'])
    def test_publish_builds_both_and_installs_once(self):
        self.execute(publish=True,both=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(2,1,1))

    def test_rejected_concurrent_draft_is_archived_before_retry(self):
        self.execute(build_only=True)
        original = Path(self.job().load_artifact('standard')['path']).read_bytes()
        (self.root/'app/src/main/example.kt').write_text('second draft')
        self.runner.modify_during_build = True
        with self.assertRaisesRegex(m.DeliveryError, 'Source changed while building'):
            self.execute(build_only=True)
        rejected = Path(self.job().load_artifact('standard', require_source=False)['path']).read_bytes()
        self.runner.modify_during_build = False
        self.execute(build_only=True)
        archived = list((self.root/'app/build/outputs/apk/archive').rglob('*.apk'))
        self.assertEqual({p.read_bytes() for p in archived}, {original, rejected})
        self.assertEqual((self.runner.build_count, self.runner.install_count), (3, 0))
    def test_publish_defaults_to_lightweight_only(self):
        self.execute(publish=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(1,1,1))
        self.assertEqual(len(self.runner.releases[0]['assets']),1)
        create = next(x for x in self.runner.calls if x[:3]==['gh','release','create'])
        self.assertIn('--draft',create)
    def test_full_install_selects_full_receipt(self):
        self.execute(full=True)
        a=self.job().load_artifact('full');self.assertEqual(self.runner.installed,a['sha256'])
    def test_same_source_skips_build_and_install(self):
        self.execute();self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))
    def test_changed_source_must_not_replace_a_delivered_version_code(self):
        self.execute();(self.root/'app/src/main/example.kt').write_text('new code')
        with self.assertRaisesRegex(m.DeliveryError,'already has a different source delivery'):self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))

    def test_new_delivery_cannot_only_increment_android_code(self):
        self.execute()
        path = self.root/'app/build.gradle.kts'
        path.write_text('versionCode = 20261072\nversionName = "1.6.0"\n')
        with self.assertRaisesRegex(m.DeliveryError, 'Advance versionName'): self.execute()
        self.assertEqual((self.runner.build_count, self.runner.install_count), (1, 1))

    def test_patch_two_three_four_five_and_ten_are_numeric_versions(self):
        job = self.job()
        for patch in [1, 2, 3, 4, 5, 10]:
            job.state['deliveredVersions'] = {'100': f'1.8.{patch - 1}'}
            job.state['installed'] = {}
            job.version, job.code = f'1.8.{patch}', 101
            job.check_delivered_version()

    def test_legacy_installed_hash_defines_the_delivery_boundary(self):
        self.execute()
        job = self.job()
        job.state.pop('deliveredVersions', None)
        job.code += 1
        with self.assertRaisesRegex(m.DeliveryError, 'already delivered'): job.check_delivered_version()

    def test_new_install_preserves_the_legacy_delivered_version(self):
        self.execute()
        job = self.job()
        job.state.pop('deliveredVersions', None)
        job.version, job.code = '1.6.1', 20261072
        job.installed_hash = lambda: 'new-hash'
        with contextlib.redirect_stdout(io.StringIO()):
            job.install(dict(path='verified.apk', sha256='new-hash', edition='standard'))
        self.assertEqual({'20261071': '1.6.0', '20261072': '1.6.1'}, job.state['deliveredVersions'])

    def test_failed_install_is_not_a_completed_delivery(self):
        self.runner.fail_install = True
        with self.assertRaises(m.DeliveryError): self.execute()
        self.assertFalse(self.job().delivered_versions())
    def test_build_failure_does_not_install_old_apk(self):
        self.execute();self.runner.fail_build=True
        with self.assertRaises(m.DeliveryError): self.execute(rebuild=True)
        self.assertEqual(self.runner.install_count,1)
    def test_signature_error_stops_install(self):
        d=self.job();d.verify_apk=lambda _: (_ for _ in ()).throw(m.DeliveryError('signature invalid'))
        with self.assertRaises(m.DeliveryError),contextlib.redirect_stdout(io.StringIO()): d.execute()
        self.assertEqual(self.runner.install_count,0)
    def test_package_mismatch_stops_install(self):
        self.runner.bad_package=True
        with self.assertRaises(m.DeliveryError):self.execute()
        self.assertEqual(self.runner.install_count,0)
    def test_receipt_wrong_version_rejected(self):
        self.execute();j=self.job();p=j.receipt_path('standard');data=m.read_json(p);data['versionCode']+=1;m.write_json(p,data)
        with self.assertRaises(m.DeliveryError):j.load_artifact('standard')
    def test_receipt_hash_tampering_rejected(self):
        self.execute();j=self.job();a=j.load_artifact('standard');Path(a['path']).write_bytes(b'altered')
        with self.assertRaises(m.DeliveryError):j.load_artifact('standard')
    def test_corrupt_cached_apk_is_rebuilt(self):
        self.execute();j=self.job();a=j.load_artifact('standard');Path(a['path']).write_bytes(b'altered');self.execute()
        self.assertEqual(self.runner.build_count,2)
    def test_concurrent_source_change_stops_install(self):
        self.runner.modify_during_build=True
        with self.assertRaises(m.DeliveryError):self.execute()
        self.assertEqual(self.runner.install_count,0)
    def test_install_failure_does_not_publish(self):
        self.runner.fail_install=True
        with self.assertRaises(m.DeliveryError):self.execute(publish=True)
        self.assertEqual(self.runner.publish_count,0)
    def test_retry_install_reuses_build(self):
        self.runner.fail_install=True
        with self.assertRaises(m.DeliveryError):self.execute()
        self.runner.fail_install=False;self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))
    def test_partial_release_resumes_without_rebuild(self):
        self.runner.fail_publish_once=True
        with self.assertRaises(m.DeliveryError): self.execute(publish=True,both=True)
        self.assertTrue(self.runner.releases[0]['draft'])
        self.execute(publish=True,both=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(2,1,1))
        self.assertFalse(self.runner.releases[0]['draft']);self.assertEqual(len(self.runner.releases[0]['assets']),2)
    def test_completed_release_repeat_is_noop(self):
        self.execute(publish=True);self.execute(publish=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(1,1,1))
    def test_dirty_source_rejected_for_publish_but_not_update(self):
        (self.root/'app/src/main/example.kt').write_text('local edit')
        with self.assertRaises(m.DeliveryError):self.execute(publish=True)
        self.assertEqual(self.runner.build_count,0);self.execute()
    def test_wrong_remote_release_commit_stops_early(self):
        self.runner.remote_commit='f'*40
        self.runner.releases=[dict(tag_name='1.6.0',target_commitish='wrong',draft=False,assets=[])]
        with self.assertRaises(m.DeliveryError):self.execute(publish=True)
        self.assertEqual(self.runner.build_count,0)
    def test_remote_hash_conflict_not_overwritten(self):
        self.execute(publish=True);self.runner.releases[0]['assets'][0]['digest']='sha256:'+'0'*64
        with self.assertRaises(m.DeliveryError):self.execute(publish=True)
        self.assertFalse(any('--clobber' in x for x in self.runner.calls))
    def test_network_error_not_misread_as_absent_release(self):
        self.runner.network_failure=True
        with self.assertRaises(m.DeliveryError):self.execute(publish=True)
        self.assertEqual(self.runner.build_count,0)
    def test_missing_submodule_initialized(self):
        self.runner.submodule_status='-'+'a'*40+' app/src/main/jni/librime-predict\n'
        with contextlib.redirect_stdout(io.StringIO()):self.job().dependencies()
        updates = [x for x in self.runner.calls if ['submodule','update'] in [x[i:i+2] for i in range(len(x)-1)]]
        self.assertEqual(1, len(updates))
        self.assertIn('url.https://github.com/.insteadOf=git@github.com:', updates[0])
    def test_different_submodule_not_force_reset(self):
        self.runner.submodule_status='+'+'a'*40+' app/src/main/jni/librime-predict\n'
        with self.assertRaises(m.DeliveryError),contextlib.redirect_stdout(io.StringIO()):self.job().dependencies()
        self.assertFalse(any('--force' in x for x in self.runner.calls))
    def test_no_uninstall_or_commit_or_push(self):
        self.execute(publish=True)
        for x in self.runner.calls:
            self.assertNotIn('uninstall',x);self.assertNotIn('reset',x);self.assertNotIn('commit',x);self.assertNotIn('push',x)
    def test_state_written(self):
        self.execute();state=m.read_json(self.root/'.gradle/auto-deliver/state.json')
        self.assertEqual(state['stage'],'DONE');self.assertEqual(state['installed']['serial'],'tablet')

    def test_committing_identical_source_does_not_rebuild(self):
        self.execute()
        subprocess.run(['git','commit','--allow-empty','-qm','record identical sources'],cwd=self.root,check=True)
        self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))

    def test_generated_python_and_fixture_cache_does_not_rebuild(self):
        self.execute()
        for name in ('scripts/__pycache__/delivery.pyc','tools/clipboard-sync-fixture/obj/cache'):
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'generated')
        self.execute()
        self.assertEqual(self.runner.build_count,1)
        self.job().require_committed_sources()

    def test_no_device_builds_once_and_reconnect_resumes_install(self):
        self.execute(no_device=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,0))
        self.assertEqual(m.read_json(self.root/'.gradle/auto-deliver/state.json')['stage'],'INSTALL-PENDING')
        self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))

    def test_check_does_not_build_install_or_initialize_dependencies(self):
        self.execute(check_only=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(0,0,0))
        self.runner.submodule_status='-'+'a'*40+' app/src/main/jni/librime-predict\n'
        with self.assertRaises(m.DeliveryError):self.execute(check_only=True)
        self.assertFalse(any('update' in x for x in self.runner.calls))

    def test_install_only_is_explicitly_existing_apk_not_current_edits(self):
        self.execute(build_only=True)
        (self.root/'app/src/main/example.kt').write_text('unbuilt edit')
        self.execute(install_only=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))

    def test_install_only_without_receipt_does_not_build(self):
        with self.assertRaises(FileNotFoundError):self.execute(install_only=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count),(0,0))

    def test_configured_entry_stays_within_the_same_repository(self):
        subprocess.run(['git','config','cyime.integrationRoot',str(self.root/'missing')],cwd=self.root,check=True)
        with self.assertRaises(m.DeliveryError):m.integration_root(self.root)

    def test_real_cmd_launcher_help_preserves_error_exit(self):
        import os
        if os.name!='nt':self.skipTest('Windows entry-point check')
        launcher=Path(__file__).resolve().parents[1]/'auto.cmd'
        result=subprocess.run(['cmd.exe','/d','/c',str(launcher),'--not-a-valid-option'],capture_output=True)
        self.assertNotEqual(result.returncode,0)

if __name__=='__main__':unittest.main(verbosity=2)
