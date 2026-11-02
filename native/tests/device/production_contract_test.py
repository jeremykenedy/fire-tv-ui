#!/usr/bin/env python3
"""Prove production candidate drift and excess capabilities are rejected without building/installing."""
import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('production_build', ROOT / 'production-build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class ProductionContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='production-contract-')
        self.root = Path(self.temporary.name)
        (self.root / 'production-src').mkdir()
        shutil.copy(ROOT / 'AndroidManifest.xml', self.root)
        shutil.copy(ROOT / 'production-src/AndroidManifest.xml', self.root / 'production-src')
        shutil.copytree(ROOT / 'production-res', self.root / 'production-res')

    def tearDown(self):
        self.temporary.cleanup()

    def test_current_candidate_contract(self):
        build.validate(self.root)

    def test_production_activities_gate_before_screen_setup(self):
        tree = ET.parse(ROOT / 'production-src/AndroidManifest.xml')
        for activity in tree.getroot().findall('application/activity'):
            name = activity.get(build.attr('name'))
            if name.endswith('.MainActivity'):
                continue
            source = ROOT / 'src' / (name.replace('.', '/') + '.java')
            text = source.read_text()
            self.assertRegex(text, r'super\.onCreate\(\w+\);\s*if \(com\.jeremykenedy\.firetv\.ui\.nativeapp\.SourceStartup\.redirectIfBlocked\(this\)\)', name)

    def test_new_preview_route_cannot_be_silently_omitted(self):
        path = self.root / 'AndroidManifest.xml'
        tree = ET.parse(path)
        ET.SubElement(tree.getroot().find('application'), 'activity',
                      {build.attr('name'): 'example.NewScreen', build.attr('exported'): 'false'})
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'route drift'):
            build.validate(self.root)

    def test_preview_cannot_become_a_home_candidate(self):
        path = self.root / 'AndroidManifest.xml'
        tree = ET.parse(path)
        activity = tree.getroot().find('application/activity')
        intent_filter = ET.SubElement(activity, 'intent-filter')
        ET.SubElement(intent_filter, 'category', {build.attr('name'): 'android.intent.category.HOME'})
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Preview must remain'):
            build.validate(self.root)

    def test_secondary_service_cannot_compete_for_keys(self):
        path = self.root / 'production-res/xml/home_windows_service.xml'
        tree = ET.parse(path)
        tree.getroot().set(build.attr('canRequestFilterKeyEvents'), 'true')
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Unexpected service capability'):
            build.validate(self.root)

    def test_boot_receiver_cannot_be_public(self):
        path = self.root / 'production-src/AndroidManifest.xml'
        tree = ET.parse(path)
        receiver = next(node for node in tree.getroot().findall('application/receiver')
                        if node.get(build.attr('name')) == build.PACKAGE + '.DeviceBootReceiver')
        receiver.set(build.attr('exported'), 'true')
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Boot receiver'):
            build.validate(self.root)

    def test_unreviewed_production_component_is_rejected(self):
        path = self.root / 'production-src/AndroidManifest.xml'
        tree = ET.parse(path)
        ET.SubElement(tree.getroot().find('application'), 'receiver',
                      {build.attr('name'): 'example.Unreviewed', build.attr('exported'): 'true'})
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Unreviewed production component'):
            build.validate(self.root)

    def test_unreviewed_production_permission_is_rejected(self):
        path = self.root / 'production-src/AndroidManifest.xml'
        tree = ET.parse(path)
        ET.SubElement(tree.getroot(), 'uses-permission',
                      {build.attr('name'): 'android.permission.READ_CONTACTS'})
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'permission/feature drift'):
            build.validate(self.root)

    def test_cli_receiver_cannot_lose_its_dump_boundary(self):
        path = self.root / 'production-src/AndroidManifest.xml'
        tree = ET.parse(path)
        receiver = next(node for node in tree.getroot().findall('application/receiver')
                        if node.get(build.attr('name')) == build.PACKAGE + '.BackupReceiver')
        receiver.attrib.pop(build.attr('permission'))
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'DUMP caller boundary'):
            build.validate(self.root)

    def test_compatibility_service_cannot_be_unprotected(self):
        path = self.root / 'production-src/AndroidManifest.xml'
        tree = ET.parse(path)
        service = next(node for node in tree.getroot().findall('application/service')
                       if node.get(build.attr('name')) == build.PACKAGE + '.Hra')
        service.attrib.pop(build.attr('permission'))
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'protected compatibility service'):
            build.validate(self.root)


if __name__ == '__main__':
    unittest.main()
