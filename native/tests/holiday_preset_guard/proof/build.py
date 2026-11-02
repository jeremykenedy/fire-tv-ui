#!/usr/bin/env python3
from pathlib import Path
import argparse,hashlib,subprocess
from zipfile import ZipFile
h=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--candidate-classes',type=Path,required=True);p.add_argument('--keystore',type=Path,required=True);args=p.parse_args()
sdk=Path.home()/'Library/Android/sdk';tools=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar';w=h/'.build'
for d in ('classes','dex'):(w/d).mkdir(parents=True,exist_ok=True)
def run(*a):subprocess.run([str(v) for v in a],check=True)
run('javac','-source','8','-target','8','-Xlint:-options','-bootclasspath',android,'-cp',args.candidate_classes,'-d',w/'classes',h/'HolidayPresetInstrumentation.java',h/'HolidayThemeUiInstrumentation.java',h.parent/'JournalFixture.java',h.parent.parent/'state_guard/GuardStore.java')
run(tools/'d8','--release','--lib',android,'--min-api','27','--output',w/'dex',*sorted((w/'classes').rglob('*.class')))
run(tools/'aapt2','link','-I',android,'--manifest',h/'AndroidManifest.xml','-o',w/'base.apk')
with ZipFile(w/'base.apk','a') as z:z.write(w/'dex/classes.dex','classes.dex')
run(tools/'zipalign','-P','16','-f','4',w/'base.apk',w/'aligned.apk')
run(tools/'apksigner','sign','--ks',args.keystore,'--ks-pass','pass:android','--ks-key-alias','candidate-review','--v4-signing-enabled','false','--out',w/'holiday-preset-proof.apk',w/'aligned.apk')
run(tools/'apksigner','verify','--print-certs',w/'holiday-preset-proof.apk')
print('APK',w/'holiday-preset-proof.apk','SHA256',hashlib.sha256((w/'holiday-preset-proof.apk').read_bytes()).hexdigest())
