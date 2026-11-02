#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$TASK_ROOT"
TASK_TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TASK_TEST_DIR"' EXIT
python3 native/build.py --release-config native/preview-release.json --unsigned --output-dir "$TASK_TEST_DIR/preview"
python3 native/tests/run_host_checks.py --classes-dir "$TASK_TEST_DIR/preview/.build/classes"
python3 -m unittest discover -s tests/production_release -v
python3 native/tests/device/production_contract_test.py
python3 native/tests/device/run.py
python3 -m unittest discover -s tests -p 'test_installer.py' -v
python3 -m unittest discover -s tests -p 'test_build.py' -v
