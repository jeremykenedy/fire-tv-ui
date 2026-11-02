#!/usr/bin/env python3
"""Check original calculations against saved authoritative USNO data and published worked examples."""
from pathlib import Path
import json
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/widget/astronomy'
ZONES = {'greenwich-equinox': 'Europe/London', 'new-york-dst': 'America/New_York',
         'sydney-solstice': 'Australia/Sydney', 'tromso-summer': 'Europe/Oslo',
         'tromso-winter': 'Europe/Oslo', 'kiritimati': 'Pacific/Kiritimati'}
cases = []
for fixture in json.loads((HERE / 'usno-fixtures.json').read_text()):
    data = fixture['data']['properties']['data']
    longitude, latitude = fixture['data']['geometry']['coordinates']
    day = f"{data['year']:04}-{data['month']:02}-{data['day']:02}"
    events = {event['phen']: event['time'] for event in data['sundata']}
    rise = json.dumps(events.get('Rise')) if events.get('Rise') else 'null'
    setting = json.dumps(events.get('Set')) if events.get('Set') else 'null'
    polar = 'ABOVE_HORIZON' if 'Object continuously above the Horizon' in events else 'BELOW_HORIZON'
    percent = int(data['fracillum'].rstrip('%'))
    cases.append(f'check("{fixture["name"]}", "{day}", {latitude}, {longitude}, "{ZONES[fixture["name"]]}", {rise}, {setting}, "{polar}", {percent});')
fixture_java = '''package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;
import java.text.*; import java.util.*;
public final class FixtureTest {
 static void check(String name,String day,double lat,double lon,String zone,String rise,String set,String polar,int illuminated)throws Exception {
  TimeZone tz=TimeZone.getTimeZone(zone); SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);date.setTimeZone(tz);
  long noon=date.parse(day+" 12:00").getTime(); Astronomy.SunDay sun=Astronomy.sunDay(noon,lat,lon,tz);
  if(rise==null){if(!sun.state.name().equals(polar))throw new AssertionError(name+": polar state "+sun.state);}
  else {
   long expectedRise=date.parse(day+" "+rise).getTime(),expectedSet=date.parse(day+" "+set).getTime();
   double riseError=Math.abs(sun.sunrise-expectedRise)/60000.0,setError=Math.abs(sun.sunset-expectedSet)/60000.0;
   if(riseError>2||setError>2)throw new AssertionError(name+": solar errors "+riseError+", "+setError+" min");
   System.out.println(name+": sunrise error "+riseError+" min; sunset error "+setError+" min");
  }
  double moon=Astronomy.moon(noon).illumination*100;
  if(Math.abs(moon-illuminated)>1)throw new AssertionError(name+": moon "+moon+" expected "+illuminated);
  System.out.println(name+": moon "+moon+"%, USNO "+illuminated+"%");
 }
 public static void main(String[] args)throws Exception { CASES }
}'''.replace('CASES', '\n'.join(cases))
with tempfile.TemporaryDirectory() as folder:
    work = Path(folder)
    fixture = work / 'FixtureTest.java'
    fixture.write_text(fixture_java)
    subprocess.run(['javac', '-d', work, SOURCE / 'Astronomy.java', SOURCE / 'AstronomyLocation.java',
                    HERE / 'AstronomyTest.java', fixture], check=True)
    for test in ('AstronomyTest', 'FixtureTest'):
        subprocess.run(['java', '-cp', work,
                        'com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy.' + test], check=True)
