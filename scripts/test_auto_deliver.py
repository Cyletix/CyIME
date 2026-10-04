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

spec = importlib.util.spec_from_file_location('auto_deliver', Path(__file__).with_name('auto-deliver.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

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
        if args[0] == 'fake-powershell': return ''
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
            if args[1:3] == ['release','upload']:
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
        self.aapt = 'fake-aapt'; self.serial = '' if getattr(self.o, 'build_only', False) else 'tablet'; self.repo = 'owner/project'
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
        elif name == 'publish-release.ps1':
            files = [self.load_artifact(x) for x in ('standard','full')]
            self.r.releases = [dict(tag_name=self.version,target_commitish=self.commit,draft=False,
                html_url='https://github.com/owner/project/releases/tag/1.6.0',
                assets=[dict(name=Path(a['path']).name,digest='sha256:'+a['sha256']) for a in files])]
            if self.r.fail_publish_once:
                self.r.fail_publish_once=False
                self.r.releases[0]['draft']=True
                self.r.releases[0]['assets']=self.r.releases[0]['assets'][:1]
                raise m.DeliveryError('upload interrupted')
            self.r.publish_count += 1
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
    def test_publish_builds_both_and_installs_once(self):
        self.execute(publish=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(2,1,1))
    def test_full_install_selects_full_receipt(self):
        self.execute(full=True)
        a=self.job().load_artifact('full');self.assertEqual(self.runner.installed,a['sha256'])
    def test_same_source_skips_build_and_install(self):
        self.execute();self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(1,1))
    def test_source_change_rebuilds_same_version_locally(self):
        self.execute();(self.root/'app/src/main/example.kt').write_text('new code');self.execute()
        self.assertEqual((self.runner.build_count,self.runner.install_count),(2,2))
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
        with self.assertRaises(m.DeliveryError): self.execute(publish=True)
        self.assertTrue(self.runner.releases[0]['draft'])
        self.execute(publish=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(2,1,1))
        self.assertFalse(self.runner.releases[0]['draft']);self.assertEqual(len(self.runner.releases[0]['assets']),2)
    def test_completed_release_repeat_is_noop(self):
        self.execute(publish=True);self.execute(publish=True)
        self.assertEqual((self.runner.build_count,self.runner.install_count,self.runner.publish_count),(2,1,1))
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

if __name__=='__main__':unittest.main(verbosity=2)
