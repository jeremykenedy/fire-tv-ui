import hashlib
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
JSON_SHA256 = '518080049ba83181914419d11a25d9bc9833a2d729b6a6e7469fa52851356da8'


def json_runtime():
    jar = ROOT / '.cache/test-json-20180813.jar'
    jar.parent.mkdir(exist_ok=True)
    if not jar.exists():
        with urllib.request.urlopen(
                'https://repo.maven.apache.org/maven2/org/json/json/20180813/json-20180813.jar',
                timeout=15) as response:
            jar.write_bytes(response.read(1048576))
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA256:
        raise ValueError('The JSON test runtime does not match its pinned checksum.')
    return jar
