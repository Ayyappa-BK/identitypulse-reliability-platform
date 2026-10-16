#!/usr/bin/env python3
"""Convert a local fault experiment to a structured exercise report; not a production incident."""
import argparse,datetime,json,pathlib
p=argparse.ArgumentParser();p.add_argument('evidence');p.add_argument('--output',required=True);a=p.parse_args()
evidence=json.loads(pathlib.Path(a.evidence).read_text())
report={'schema_version':1,'type':'reliability_exercise','generated_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'incident_id':'EXERCISE-'+evidence['scenario'],'severity':'exercise','status':'recovered' if evidence.get('recovered') else 'investigate','scenario':evidence['scenario'],'impact':{'production_customers':0,'local_fault_probe_statuses':evidence.get('fault_probe_statuses',[])},'timing':{k:evidence.get(k) for k in ['recovery_seconds','total_seconds','max_fault_request_seconds']},'evidence_file':a.evidence,'root_cause':'Deliberate local '+evidence['scenario']+' injection','corrective_actions':[],'limitations':['Controlled local experiment; not a production incident or availability estimate.']}
out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n')
print(str(out))
