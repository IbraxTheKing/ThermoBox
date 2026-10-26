#!/bin/sh
# Creates .env from .env.example with random passwords and JWT secret.
# Usage, from the root of the repository:  sh scripts/init-env.sh
# (on Windows: Git Bash or WSL; otherwise copy .env.example to .env and fill it in by hand)


# shellcheck disable=SC2164
cd "$(dirname "$0")/.."

if [ -f .env ]; then
    echo ".env already exists: nothing was changed."
    exit 0
fi

command -v openssl >/dev/null 2>&1 || { echo "openssl is required to generate the secrets." >&2; exit 1; }

# IMPORTANT: EclipseLink takes a password made only of hexadecimal digits (0-9, a-f) for an
# encrypted password and tries to decrypt it (EclipseLink-7107 error). The "tb" prefix holds a
# non-hexadecimal letter (t): the password is read as is.
ROOT_PASSWORD="tb$(openssl rand -hex 16)"
DB_PASSWORD="tb$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 32)"   # 64 characters (pure hex is no problem here)

sed -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}|" \
    -e "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=${DB_PASSWORD}|" \
    -e "s|^THERMOBOX_JWT_SECRET=.*|THERMOBOX_JWT_SECRET=${JWT_SECRET}|" \
    .env.example > .env

chmod 600 .env
echo ".env created with random secrets."
