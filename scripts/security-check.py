#!/usr/bin/env python3
"""Small regression guards; CodeQL and Trivy supply broader static/dependency analysis."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
java='\n'.join(p.read_text() for p in (root/'src/main/java').rglob('*.java'))
for banned in ['System.out.print', 'printStackTrace(', 'NoOpPasswordEncoder', 'setHostnameVerifier']:
 assert banned not in java, banned
assert '.anyRequest().denyAll()' in java
assert 'SignatureAlgorithm.RS256' in java
assert '.getExpiresAt() != null' in java
for p in root.rglob('*'):
 if p.is_file() and not any(x in p.parts for x in ['.git','target','.local']):
  assert p.suffix not in ['.pem','.key'],str(p)
print('Security regression guards passed')
