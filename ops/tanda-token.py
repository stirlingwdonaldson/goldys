#!/usr/bin/env python3
"""Enter a Tanda token without terminal echo, command arguments or repository storage."""
import getpass
import os
from pathlib import Path
import re
import stat
import sys
import tempfile


def save_token(destination: Path, token: str) -> None:
    destination = destination.expanduser().absolute()
    repository = Path(__file__).resolve().parents[1]
    if destination.resolve().is_relative_to(repository):
        raise ValueError("Choose a private directory outside the checkout.")
    if not re.fullmatch(r"[A-Za-z0-9._~-]{16,4096}", token):
        raise ValueError("Token format is invalid; nothing was saved.")
    parent = destination.parent
    parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    info = parent.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700:
        raise ValueError("Destination directory must be owned by you with mode 0700.")
    if destination.exists() or destination.is_symlink():
        raise ValueError("Destination already exists. Choose a new filename for token rotation.")
    fd, temporary = tempfile.mkstemp(prefix=".tanda-", dir=parent)
    try:
        with os.fdopen(fd, "w") as output:
            output.write(token + "\n")
            output.flush()
            os.fsync(output.fileno())
        # Atomic creation without overwriting any existing file or following a symlink.
        os.link(temporary, destination)
    finally:
        os.unlink(temporary)


def main() -> None:
    if len(sys.argv) != 2 or not sys.stdin.isatty():
        raise ValueError("Run interactively: python3 ops/tanda-token.py /private/directory/tanda-token")
    token = getpass.getpass("Tanda API token (hidden): ").strip()
    save_token(Path(sys.argv[1]), token)
    print("Token saved with owner-only access. Configure TANDA_TOKEN_FILE for the backend; do not paste the token into chat.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
