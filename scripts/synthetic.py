#!/usr/bin/env python3
"""Independent scheduled client SLI; provision/rotate a dedicated token file externally."""
import argparse,http.server,math,pathlib,threading,time,urllib.request,urllib.error
p=argparse.ArgumentParser();p.add_argument('--url',required=True);p.add_argument('--token-file',required=True);p.add_argument('--interval',type=float,default=10);p.add_argument('--listen',default='127.0.0.1');p.add_argument('--port',type=int,default=9115);a=p.parse_args()
assert a.interval>=1
lock=threading.Lock();counts={'success':0,'failure':0,'missed':0};last=0.
def loop():
 global last
 scheduled=time.monotonic()
 while True:
  delay=time.monotonic()-scheduled
  missed=max(0,math.floor(delay/a.interval))
  with lock: counts['missed']+=missed
  scheduled+=missed*a.interval
  outcome='failure'
  try:
   token=pathlib.Path(a.token_file).read_text().strip()
   request=urllib.request.Request(a.url,data=b'',headers={'Authorization':'Bearer '+token})
   with urllib.request.urlopen(request,timeout=min(5,a.interval)) as r:
    if r.status==200: outcome='success'
  except (OSError,ValueError): pass
  with lock: counts[outcome]+=1;last=time.time()
  scheduled+=a.interval;time.sleep(max(0,scheduled-time.monotonic()))
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  if self.path!='/metrics': self.send_error(404);return
  with lock:
   text='# TYPE identity_synthetic_probes_total counter\n'+''.join(f'identity_synthetic_probes_total{{outcome="{k}"}} {v}\n' for k,v in counts.items())
   text+=f'# TYPE identity_synthetic_last_observation_seconds gauge\nidentity_synthetic_last_observation_seconds {last}\n'
  body=text.encode();self.send_response(200);self.send_header('Content-Type','text/plain; version=0.0.4');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args): pass
threading.Thread(target=loop,daemon=True).start()
http.server.ThreadingHTTPServer((a.listen,a.port),Handler).serve_forever()
