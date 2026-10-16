#!/usr/bin/env python3
"""Local-only issuer fixture. No token issuance code or private key ships in the app."""
import base64,json,subprocess,time,sys
from pathlib import Path
root=Path(__file__).resolve().parent.parent
key=root/'.local/signing.pem'
def b64(b): return base64.urlsafe_b64encode(b).decode().rstrip('=')
if '--jwks' in sys.argv:
 modulus=subprocess.check_output(['openssl','rsa','-in',str(key),'-noout','-modulus'],stderr=subprocess.DEVNULL).decode().strip().split('=')[1]
 print(json.dumps({'keys':[{'kty':'RSA','use':'sig','kid':'lab-1','alg':'RS256','n':b64(bytes.fromhex(modulus)),'e':'AQAB'}]}))
else:
 now=int(time.time()); scope='identity:verify'
 if '--admin' in sys.argv: scope='identity:admin'
 claims={'iss':'http://issuer:8000','aud':'identitypulse','sub':'load-user','jti':'lab-probe','iat':now,'nbf':now-1,'exp':now+900,'scope':scope}
 if '--invalid' in sys.argv: claims['aud']='wrong-audience'
 message='.'.join(b64(json.dumps(x,separators=(',',':')).encode()) for x in [{'alg':'RS256','kid':'lab-1','typ':'JWT'},claims])
 signature=subprocess.check_output(['openssl','dgst','-sha256','-sign',str(key)],input=message.encode())
 print(message+'.'+b64(signature))
