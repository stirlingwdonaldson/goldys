import json
import socket
import subprocess
import time

connection=socket.socket(socket.AF_UNIX)
connection.connect('/run/goldys-sandbox/broker.sock')
body=json.dumps({'code':'import os,time\nif os.fork() == 0: time.sleep(300)\nelse: time.sleep(300)','seconds':60}).encode()
connection.sendall(str(len(body)).encode()+b'\n'+body)
time.sleep(2)
subprocess.run(['systemctl','kill','--kill-whom=main','--signal=SIGKILL','goldys-sandbox'],check=True)
start=time.monotonic()
while time.monotonic()-start < 12:
    ids=subprocess.check_output(['docker','ps','-aq','--filter=label=goldys.analysis.sandbox=true']).strip()
    if not ids:
        print('PASS broker-loss orphan cleanup in',round(time.monotonic()-start,2),'seconds')
        break
    time.sleep(.2)
else:
    raise AssertionError('Orphan sandbox survived broker loss')
connection.close()
