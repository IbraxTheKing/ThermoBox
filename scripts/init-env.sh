#!/bin/sh
# Crée .env à partir de .env.example avec des mots de passe et un secret JWT aléatoires.
# Utilisation, depuis la racine du dépôt :  sh scripts/init-env.sh
# (sous Windows : Git Bash ou WSL ; sinon copiez .env.example en .env et remplissez-le à la main)


# shellcheck disable=SC2164
cd "$(dirname "$0")/.."

if [ -f .env ]; then
    echo ".env existe déjà : rien n'a été modifié."
    exit 0
fi

command -v openssl >/dev/null 2>&1 || { echo "openssl est requis pour générer les secrets." >&2; exit 1; }

# IMPORTANT : EclipseLink considère un mot de passe composé uniquement de chiffres hexadécimaux
# (0-9, a-f) comme un mot de passe chiffré et tente de le déchiffrer (erreur EclipseLink-7107).
# Le préfixe "tb" contient une lettre non hexadécimale (t) : le mot de passe reste lisible tel quel.
ROOT_PASSWORD="tb$(openssl rand -hex 16)"
DB_PASSWORD="tb$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 32)"   # 64 caractères (un hexa pur ne pose pas de problème ici)

sed -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}|" \
    -e "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=${DB_PASSWORD}|" \
    -e "s|^THERMOBOX_JWT_SECRET=.*|THERMOBOX_JWT_SECRET=${JWT_SECRET}|" \
    .env.example > .env

chmod 600 .env
echo ".env créé avec des secrets aléatoires."
