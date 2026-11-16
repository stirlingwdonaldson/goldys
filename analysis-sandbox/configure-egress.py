"""Analysis VM only: restrict trusted worker UID 65532 to Spring, DeepSeek and DNS.

Run during deployment after reviewing DNS answers. No script sandbox gets networking.
Other UIDs and host ingress/routing are unchanged.
"""
import ipaddress
from pathlib import Path
import socket
import subprocess


def command(*args, check=True):
    return subprocess.run(args, check=check, capture_output=True)

addresses = {item[4][0] for item in socket.getaddrinfo('api.deepseek.com', 443, type=socket.SOCK_STREAM)}
assert addresses and all(ipaddress.ip_address(value).is_global for value in addresses)
resolvers = {line.split()[1] for line in Path('/etc/resolv.conf').read_text().splitlines() if line.startswith('nameserver ')}
for binary, version, loopback in [('iptables', 4, '127.0.0.1'), ('ip6tables', 6, '::1')]:
    chain = 'GOLDYS_PI_EGRESS'
    command(binary, '-N', chain, check=False)
    command(binary, '-F', chain)
    command(binary, '-A', chain, '-p', 'tcp', '-d', loopback, '--dport', '18443', '-j', 'ACCEPT')
    for value in sorted(addresses):
        if ipaddress.ip_address(value).version == version:
            command(binary, '-A', chain, '-p', 'tcp', '-d', value, '--dport', '443', '-j', 'ACCEPT')
    for value in sorted(resolvers):
        if ipaddress.ip_address(value).version == version:
            for protocol in ['udp', 'tcp']:
                command(binary, '-A', chain, '-p', protocol, '-d', value, '--dport', '53', '-j', 'ACCEPT')
    command(binary, '-A', chain, '-j', 'REJECT')
    rule = [binary, '-C', 'OUTPUT', '-m', 'owner', '--uid-owner', '65532', '-j', chain]
    if command(*rule, check=False).returncode:
        rule[1] = '-I'
        command(*rule)
print('Configured worker-only IPv4/IPv6 egress; provider addresses:', len(addresses))
