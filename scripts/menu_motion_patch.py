"""Include the optional native settings transition resources."""
from pathlib import Path
import shutil


def patch_menu_motion(decoded):
    source = Path(__file__).resolve().parents[1] / 'app/res/anim'
    target = decoded / 'res/anim'
    target.mkdir(parents=True, exist_ok=True)
    for resource in sorted(source.glob('menu_*.xml')):
        if (target / resource.name).exists():
            raise ValueError('Settings animation already exists: ' + resource.name)
        shutil.copyfile(resource, target / resource.name)
