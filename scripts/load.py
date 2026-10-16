#!/usr/bin/env python3
"""Bounded closed-loop load; JSON evidence, no tokens or subjects in output."""
import argparse,concurrent.futures,datetime,json,math,os,time,urllib.request,urllib.error
p=argparse.ArgumentParser();p.add_argument('--requests',type=int,default=1000);p.add_argument('--concurrency',type=int,default=10);p.add_argument('--url',default='http://127.0.0.1:8080/v1/auth/verify');p.add_argument('--output',default='results/load.json');p.add_argument('--expect',type=int,default=200);a=p.parse_args()
assert 0<a.requests<=1000000 and 0<a.concurrency<=256
TOKEN=os.environ['TOKEN']
def request(_):
 start=time.monotonic();code=0
 try:
  req=urllib.request.Request(a.url,data=b'',headers={'Authorization':'Bearer '+TOKEN},method='POST')
  with urllib.request.urlopen(req,timeout=5) as r: code=r.status;r.read()
 except urllib.error.HTTPError as e: code=e.code;e.close()
 except OSError: code=0
 return code,(time.monotonic()-start)*1000
start=time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as pool: rows=list(pool.map(request,range(a.requests)))
elapsed=time.monotonic()-start;lat=sorted(x[1] for x in rows);counts={str(c):sum(x[0]==c for x in rows) for c in sorted(set(x[0] for x in rows))}
result=dict(timestamp_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),requests=a.requests,concurrency=a.concurrency,elapsed_seconds=elapsed,throughput_rps=a.requests/elapsed,status_counts=counts,latency_ms={str(q):lat[min(len(lat)-1,math.ceil(q/100*len(lat))-1)] for q in [50,95,99]},expected_status=a.expect,passed=all(x[0]==a.expect for x in rows),method='closed-loop urllib; end-to-end client wall-clock; no warmup')
os.makedirs(os.path.dirname(a.output) or '.',exist_ok=True)
with open(a.output,'w') as f: json.dump(result,f,indent=2)
print(json.dumps(result,indent=2));raise SystemExit(0 if result['passed'] else 1)
