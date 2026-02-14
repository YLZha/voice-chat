#!/usr/bin/env python3
"""JWT secret rotation helper.

Generates a new JWT secret and prints step-by-step instructions for
a zero-downtime rotation using the JWT_SECRET / JWT_SECRET_PREVIOUS
dual-secret mechanism.

Usage:
    python scripts/rotate_jwt_secret.py
"""

import secrets


def main():
    new_secret = secrets.token_urlsafe(32)

    print("=" * 64)
    print("  JWT Secret Rotation")
    print("=" * 64)
    print()
    print(f"  New secret: {new_secret}")
    print()
    print("  Steps for zero-downtime rotation:")
    print()
    print("  1. Copy your CURRENT jwt_secret value")
    print()
    print("  2. Set it as the previous secret (grace period):")
    print(f'     config.yaml  →  jwt_secret_previous: "{{}}"')
    print(f"     .env         →  JWT_SECRET_PREVIOUS=<current-secret>")
    print()
    print("  3. Set the NEW secret as the current secret:")
    print(f'     config.yaml  →  jwt_secret: "{new_secret}"')
    print(f"     .env         →  JWT_SECRET={new_secret}")
    print()
    print("  4. Restart the server")
    print("     New tokens will be signed with the new secret.")
    print("     Existing tokens signed with the old secret still work.")
    print()
    print("  5. After all old tokens have expired (access: 1h, refresh: 30d),")
    print("     remove jwt_secret_previous / JWT_SECRET_PREVIOUS")
    print()
    print("=" * 64)


if __name__ == "__main__":
    main()
