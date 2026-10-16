#!/usr/bin/env python3
import json,time,urllib.request,urllib.error
for name,listen,upstream in [('postgres','0.0.0.0:15432','postgres:5432'),('redis','0.0.0.0:16379','redis:6379')]:
 for attempt in range(40):
  try:
   req=urllib.request.Request('http://127.0.0.1:8474/proxies',json.dumps(dict(name=name,listen=listen,upstream=upstream)).encode(),{'Content-Type':'application/json'})
   urllib.request.urlopen(req,timeout=2).close();break
  except urllib.error.HTTPError as e:
   if e.code==409: break
   raise
  except OSError:
   if attempt==39: raise
   time.sleep(.5)
