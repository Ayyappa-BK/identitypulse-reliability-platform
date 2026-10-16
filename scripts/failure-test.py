#!/usr/bin/env python3
"""Destructive only to this Compose lab. Always restore state; assert recovery."""
import argparse,json,os,subprocess,time,urllib.request,urllib.error
p=argparse.ArgumentParser();p.add_argument('scenario',choices=['crash','database','redis','latency']);a=p.parse_args()
root=os.path.dirname(os.path.dirname(os.path.abspath(__file__)));os.chdir(root)
def cmd(*args): subprocess.run(args,check=True,stdout=subprocess.DEVNULL)
def api(path,data=None,method='POST'):
 req=urllib.request.Request('http://127.0.0.1:8474'+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json'},method=method)
 with urllib.request.urlopen(req,timeout=3) as r: return r.read()
def probe():
 req=urllib.request.Request('http://127.0.0.1:8080/v1/auth/verify',data=b'',headers={'Authorization':'Bearer '+os.environ['TOKEN']})
 start=time.monotonic()
 try:
  with urllib.request.urlopen(req,timeout=5) as r: return r.status,time.monotonic()-start
 except urllib.error.HTTPError as e: return e.code,time.monotonic()-start
 except OSError: return 0,time.monotonic()-start
assert probe()[0]==200,'Baseline must pass'
start=time.monotonic();during=[]
try:
 if a.scenario=='crash': cmd('docker','compose','kill','-s','SIGKILL','app')
 elif a.scenario in ['database','redis']: api('/proxies/'+('postgres' if a.scenario=='database' else 'redis'),{'enabled':False})
 else: api('/proxies/postgres/toxics',{'name':'latency','type':'latency','stream':'downstream','toxicity':1,'attributes':{'latency':1500,'jitter':0}})
 for i in range(12): during.append(probe());time.sleep(.2)
 if a.scenario!='crash': assert all(c==503 for c,t in during),'Dependency faults must fail closed'
 else: assert any(c!=200 for c,t in during),'Crash was not observed'
finally:
 if a.scenario=='crash': cmd('docker','compose','up','-d','app')
 elif a.scenario in ['database','redis']: api('/proxies/'+('postgres' if a.scenario=='database' else 'redis'),{'enabled':True})
 else: api('/proxies/postgres/toxics/latency',method='DELETE')
recovery_start=time.monotonic()
while time.monotonic()-recovery_start<120:
 if probe()[0]==200: break
 time.sleep(1)
else: raise SystemExit('Recovery exceeded 120s')
cmd('python3','scripts/wait-ready.py')
report={'scenario':a.scenario,'fault_probe_statuses':[c for c,t in during],'max_fault_request_seconds':max(t for c,t in during),'recovery_seconds':time.monotonic()-recovery_start,'total_seconds':time.monotonic()-start,'recovered':True}
os.makedirs('results',exist_ok=True)
with open('results/failure-'+a.scenario+'.json','w') as f:json.dump(report,f,indent=2)
print(json.dumps(report,indent=2))
