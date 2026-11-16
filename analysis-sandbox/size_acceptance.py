"""Exercise the advertised total input/output limit using only synthetic bytes."""
import base64
import json
import socket
from acceptance import submit

payload = base64.b64encode(b'x' * (25 * 1024 * 1024)).decode()
inputs = [{'name': f'input-{i}.csv', 'base64': payload} for i in range(4)]
with submit("from pathlib import Path\nprint(sum(p.stat().st_size for p in Path('/inputs').iterdir()))", inputs=inputs) as connection:
    response = b''
    while chunk := connection.recv(65536): response += chunk
result = json.loads(response)
assert result['status'] == 'COMPLETED' and '104857600' in result['stdout'], result
print('PASS 100 MiB total staged input (four 25 MiB files)', flush=True)
del inputs, payload
code = "from pathlib import Path\nfor i in range(4):\n with open(f'/output/result-{i}.csv','wb') as f:\n  for _ in range(25): f.write(b'x' * 1048576)"
with submit(code, seconds=30) as connection:
    # Incremental receive into a temporary file avoids quadratic test-client allocation.
    import tempfile
    with tempfile.TemporaryFile() as stream:
        while chunk := connection.recv(65536): stream.write(chunk)
        stream.seek(0)
        result = json.load(stream)
assert result['status'] == 'COMPLETED', result.get('notice',result['status'])
assert sum(len(base64.b64decode(item['base64'])) for item in result['outputs']) == 100 * 1024 * 1024
print('PASS 100 MiB total output under broker memory cap', flush=True)
