#!/usr/bin/env python3
"""Build a pinned maintenance repair without changing the native build.

This temporary repair retains the historical launcher base. It is not the
independent replacement. Output must be a new directory outside the checkout.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile


ROOT = Path(__file__).resolve().parents[1]
RECIPE = Path(__file__).resolve()
DEFAULT_VERSION = '1.18.0'
VERSIONS = ('1.17.1', DEFAULT_VERSION)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(*args, **options):
    return subprocess.run([str(arg) for arg in args], check=True, **options)


def capture(*args):
    return run(*args, capture_output=True, text=True).stdout.strip()


def run_logged(path, *args, cwd):
    try:
        with path.open('w') as stream:
            run(*args, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError:
        print('\n'.join(path.read_text(errors='replace').splitlines()[-60:]), file=sys.stderr)
        raise


def reconstruct(output, metadata, patch):
    if digest(patch) != metadata['patchSha256']:
        raise ValueError('The reviewed maintenance patch checksum does not match.')
    if capture('git', '-C', ROOT, 'cat-file', '-t', metadata['baseCommit']) != 'commit':
        raise ValueError('Fetch the public v1.17.0 history before building this repair.')
    git_dir = capture('git', '-C', ROOT, 'rev-parse', '--absolute-git-dir')
    source = output / 'source'
    source.mkdir()
    archive = output / 'source.tar'
    with archive.open('wb') as stream:
        run('git', '-C', ROOT, 'archive', metadata['baseCommit'], stdout=stream)
    with tarfile.open(archive) as package:
        package.extractall(source, filter='data')
    archive.unlink()

    environment = os.environ.copy()
    environment['GIT_INDEX_FILE'] = str(output / 'source.index')
    command = ['git', '-c', 'core.autocrlf=false', '-c', 'core.fsmonitor=false',
               '--git-dir', git_dir, '--work-tree', source]

    def git(*args):
        return run(*command, *args, cwd=source, env=environment,
                   capture_output=True, text=True).stdout.strip()

    git('read-tree', metadata['baseCommit'])
    git('update-index', '--refresh')
    git('apply', '--index', patch)
    tree = git('write-tree')
    if tree != metadata['expectedSourceTree'] or git('diff-files', '--name-only'):
        raise ValueError('Reconstructed source does not match the reviewed repair.')
    config = json.loads((source / 'build.json').read_text())
    if (config['version'], config['version_code'], config['package']) != (
            metadata['version'], metadata['versionCode'], metadata['package']):
        raise ValueError('The maintenance version or package identity differs.')
    if digest(source / 'vendor/launcher-base.apk') != metadata['launcherBaseSha256']:
        raise ValueError('The historical launcher base checksum does not match.')
    if config['apktool_sha256'] != metadata['apktoolSha256']:
        raise ValueError('The declared Apktool checksum differs from this repair.')
    return source, tree, git


def build(output, verify_only, version=DEFAULT_VERSION):
    if sys.version_info < (3, 12):
        raise ValueError('Python 3.12 or newer is required for safe archive extraction.')
    output = output.expanduser().resolve()
    if output == ROOT or ROOT in output.parents or output in ROOT.parents:
        raise ValueError('Maintenance output must be outside the checkout.')
    if version not in VERSIONS:
        raise ValueError('Choose a reviewed maintenance version.')
    output.mkdir(parents=True, exist_ok=False)
    metadata_path = ROOT / 'maintenance' / version / 'release.json'
    patch = metadata_path.with_name('repair.patch')
    metadata = json.loads(metadata_path.read_text())
    if metadata['version'] != version:
        raise ValueError('The maintenance metadata does not match the selected version.')
    inputs = {str(path.relative_to(ROOT)): digest(path) for path in (RECIPE, metadata_path, patch)}
    source, tree, git = reconstruct(output, metadata, patch)
    provenance = {
        'version': metadata['version'], 'versionCode': metadata['versionCode'],
        'package': metadata['package'], 'recipeCommit': capture('git', '-C', ROOT, 'rev-parse', 'HEAD'),
        'recipeTreeDirty': bool(capture('git', '-C', ROOT, 'status', '--porcelain')),
        'baseCommit': metadata['baseCommit'], 'reviewedRepairCommit': metadata['reviewedRepairCommit'],
        'verifiedSourceTree': tree, 'recipeInputs': inputs,
        'launcherBaseSha256': metadata['launcherBaseSha256'],
        'apktoolSha256': metadata['apktoolSha256'],
        'independentReplacement': False, 'signed': False,
        'phase': 'source-verified',
    }
    if not verify_only:
        logs = output / 'logs'
        logs.mkdir()
        cached_tool = ROOT / '.cache/apktool.jar'
        if cached_tool.is_file() and digest(cached_tool) == metadata['apktoolSha256']:
            (source / '.cache').mkdir(exist_ok=True)
            shutil.copyfile(cached_tool, source / '.cache/apktool.jar')
        run_logged(logs / 'build.log', sys.executable, 'scripts/build.py', '--unsigned', cwd=source)
        run_logged(logs / 'tests.log', 'bash', 'scripts/test.sh', cwd=source)
        if git('diff-files', '--name-only'):
            raise ValueError('Tracked maintenance source changed during checks or compilation.')
        artifacts = output / 'artifacts'
        artifacts.mkdir()
        files = {}
        for path in sorted((source / 'dist').iterdir()):
            if path.is_file():
                shutil.copyfile(path, artifacts / path.name)
                files[path.name] = digest(path)
        apk = 'fire-tv-ui-' + metadata['version'] + '-unsigned.apk'
        if apk not in files:
            raise ValueError('The maintenance builder did not produce its declared unsigned APK.')
        provenance.update({
            'phase': 'build-complete', 'artifacts': files,
            'toolchain': {
                'python': platform.python_version(),
                'java': run('java', '-version', capture_output=True, text=True).stderr.strip(),
                'javac': capture('javac', '-version'),
            },
        })
    if any(digest(ROOT / name) != checksum for name, checksum in inputs.items()):
        raise ValueError('Maintenance recipe inputs changed during verification.')
    (output / 'build-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print('Verified maintenance source tree: ' + tree)
    print('Maintenance provenance: ' + str(output / 'build-provenance.json'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True,
                        help='New isolated directory outside the checkout; never cleared or reused.')
    parser.add_argument('--version', choices=VERSIONS, default=DEFAULT_VERSION,
                        help='Reviewed maintenance version (default: %(default)s).')
    parser.add_argument('--verify-only', action='store_true',
                        help='Reconstruct and verify the fixed source without building or signing.')
    arguments = parser.parse_args()
    try:
        build(arguments.output_dir, arguments.verify_only, arguments.version)
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        if getattr(failure, 'stderr', None):
            print(failure.stderr, file=sys.stderr)
        print('Maintenance build failed: ' + str(failure), file=sys.stderr)
        sys.exit(1)
