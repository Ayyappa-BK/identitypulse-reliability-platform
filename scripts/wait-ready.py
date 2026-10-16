#!/usr/bin/env python3
import urllib.request,time,sys
for i in range(120):
 try:
  with urllib.request.urlopen('http://127.0.0.1:9090/actuator/health/readiness',timeout=2) as r:
   if r.status==200: sys.exit(0)
 except OSError: pass
 time.sleep(1)
raise SystemExit('Readiness did not recover within 120s')
