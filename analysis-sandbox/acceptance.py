"""Run on the analysis VM as goldys-worker. No providers or application data."""
import base64
import json
import socket
import time


def submit(code, seconds=10, inputs=None):
    connection = socket.socket(socket.AF_UNIX)
    connection.settimeout(75)
    connection.connect('/run/goldys-sandbox/broker.sock')
    body = json.dumps({'code': code, 'seconds': seconds, 'inputs': inputs or []}).encode()
    connection.sendall(str(len(body)).encode() + b'\n' + body)
    return connection


def run(code, seconds=10, inputs=None):
    with submit(code, seconds, inputs) as connection:
        result = b''
        while chunk := connection.recv(65536):
            result += chunk
    return json.loads(result)


def main():
    start = time.monotonic()
    result = run("print('sandbox-ok')\nopen('/output/result.csv','w').write('value\\n42\\n')")
    assert result['status'] == 'COMPLETED', result
    assert 'sandbox-ok' in result['stdout'], result
    assert base64.b64decode(result['outputs'][0]['base64']) == b'value\n42\n'
    print('PASS execution and private output')
    code = '''
import os, socket
assert os.getuid() == 65532
for path in ['/etc/goldys-agent/deepseek-api-key', '/var/run/docker.sock', '/home/goldys/.ssh', '/srv/ai/projects/goldys']:
    assert not os.path.exists(path), path
try:
    open('/root-write', 'w').write('bad')
    raise AssertionError('writable root')
except OSError:
    pass
assert not any('KEY' in key or 'TOKEN' in key for key in os.environ)
for address in ['1.1.1.1', '192.168.5.42', '192.168.5.1', '169.254.169.254', '127.0.0.1', '::1', '2606:4700:4700::1111', 'fd00::1']:
    family = socket.AF_INET6 if ':' in address else socket.AF_INET
    with socket.socket(family) as connection:
        connection.settimeout(.2)
        assert connection.connect_ex((address, 443)) != 0, address
try:
    socket.getaddrinfo('example.com', 443)
    raise AssertionError('DNS resolved')
except socket.gaierror:
    pass
print('denied-all')
'''
    result = run(code)
    assert result['status'] == 'COMPLETED' and 'denied-all' in result['stdout'], result
    print('PASS filesystem, identity, environment, IPv4/IPv6/private/metadata/loopback/DNS denial')
    result = run("import os\nos.symlink('/etc/passwd','/output/leak.csv')")
    assert result['status'] == 'REJECTED', result
    print('PASS output symlink rejection')
    result = run('while True: pass', seconds=1)
    assert result['status'] == 'LIMIT_EXCEEDED', result
    print('PASS deadline termination')
    with submit('import os,time\nif os.fork() == 0: time.sleep(300)\nelse: time.sleep(300)', seconds=60):
        time.sleep(2)
    time.sleep(2)
    result = run("print('slot-recovered')")
    assert result['status'] == 'COMPLETED', result
    print('PASS disconnect/descendant termination and slot recovery')
    result = run("x=[]\nwhile True: x.append(bytearray(16*1024*1024))", seconds=15)
    assert result['status'] == 'FAILED', result
    print('PASS memory exhaustion')
    result = run("with open('/output/huge.csv','wb') as f:\n while True: f.write(b'x'*1024*1024)")
    assert result['status'] == 'FAILED', result
    print('PASS file size limit')
    print('Elapsed seconds:', round(time.monotonic() - start, 1))


if __name__ == '__main__':
    main()
