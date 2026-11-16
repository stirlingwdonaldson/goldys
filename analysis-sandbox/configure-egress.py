"""Analysis VM only: restrict trusted worker UID 65532 to Spring, DeepSeek and DNS.

Run during deployment after reviewing DNS answers. No script sandbox gets networking.
Other UIDs and host ingress/routing are unchanged.
"""
import ipaddress
import os
from urllib.parse import urlsplit
from pathlib import Path
import socket
import subprocess


def command(*args, check=True):
    return subprocess.run(args, check=check, capture_output=True)

def spring_destination(origin):
    url = urlsplit(origin)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Spring must be a fixed HTTPS origin without credentials or path')
    port = url.port or 443
    addresses = {item[4][0] for item in socket.getaddrinfo(url.hostname, port, type=socket.SOCK_STREAM)}
    # Only the dedicated preview/production TLS tunnels may target loopback.
    # Any other HTTPS origin must resolve publicly, never to an arbitrary LAN host.
    local_bridge = url.hostname == 'localhost' and port in (18443, 18444)
    if not addresses or not all(ipaddress.ip_address(value).is_loopback if local_bridge
                                else ipaddress.ip_address(value).is_global for value in addresses):
        raise ValueError('Spring DNS must resolve to public addresses (or a dedicated loopback bridge)')
    return addresses, port


def configure():
    spring, spring_port = spring_destination(os.environ.get('GOLDYS_SPRING_ORIGIN', 'https://localhost:18443'))
    addresses = {item[4][0] for item in socket.getaddrinfo('api.deepseek.com', 443, type=socket.SOCK_STREAM)}
    if not addresses or not all(ipaddress.ip_address(value).is_global for value in addresses):
        raise ValueError('Provider DNS must resolve to public addresses')
    resolvers = {line.split()[1] for line in Path('/etc/resolv.conf').read_text().splitlines() if line.startswith('nameserver ')}
    for binary, version in [('iptables', 4), ('ip6tables', 6)]:
        chain = 'GOLDYS_PI_EGRESS'
        command(binary, '-N', chain, check=False)
        command(binary, '-F', chain)
        for targets, port in [(spring, spring_port), (addresses, 443)]:
            for value in sorted(targets):
                if ipaddress.ip_address(value).version == version:
                    command(binary, '-A', chain, '-p', 'tcp', '-d', value, '--dport', str(port), '-j', 'ACCEPT')
        for value in sorted(resolvers):
            if ipaddress.ip_address(value).version == version:
                for protocol in ['udp', 'tcp']:
                    command(binary, '-A', chain, '-p', protocol, '-d', value, '--dport', '53', '-j', 'ACCEPT')
        command(binary, '-A', chain, '-j', 'REJECT')
        rule = [binary, '-C', 'OUTPUT', '-m', 'owner', '--uid-owner', '65532', '-j', chain]
        if command(*rule, check=False).returncode:
            rule[1] = '-I'
            command(*rule)
    print('Configured worker-only IPv4/IPv6 egress')


if __name__ == '__main__':
    configure()
