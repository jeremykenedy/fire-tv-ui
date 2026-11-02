#!/usr/bin/env python3
"""Build the source-native production candidate."""
import argparse
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unsigned', action='store_true', help='Build an unsigned, nondeployable CI candidate.')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'dist/production',
                        help='Empty directory for the APK and build manifest.')
    args = parser.parse_args()
    mode = '--unsigned-release' if args.unsigned else '--release'
    subprocess.run([sys.executable, str(ROOT / 'native/production-build.py'), mode,
                    '--output-dir', str(args.output_dir)], check=True)


if __name__ == '__main__':
    main()
