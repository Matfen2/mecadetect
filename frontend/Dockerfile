# syntax=docker/dockerfile:1

# Image de production du front MécaDétect : l'application Angular construite,
# servie par Nginx. L'API n'est pas servie ici : le proxy d'entrée (Caddy) envoie
# /api vers le conteneur de l'API.
# À construire depuis la racine du dépôt, car le contrat api/openapi.yaml est nécessaire :
#   docker build -f frontend/Dockerfile -t mecadetect-front .

# ---------------------------------------------------------------- 0. Java
# openapi-generator, qui produit le client Angular depuis le contrat, est un
# programme Java : on prend un JRE dans l'image officielle.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jre-noble AS java

# ---------------------------------------------------------------- 1. Construction
# Le JavaScript produit ne dépend pas du processeur : la construction tourne sur la
# plateforme de la machine de construction, sans émulation.
FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS construction

COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH

WORKDIR /construction/frontend

# Dépendances d'abord : cette couche n'est refaite que si package-lock.json change
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY api/openapi.yaml ../api/openapi.yaml
COPY frontend/ ./

# Le jar du générateur est gardé dans un cache de BuildKit : pas de nouveau
# téléchargement à chaque construction.
RUN --mount=type=cache,target=/construction/frontend/node_modules/@openapitools/openapi-generator-cli/versions \
    npm run generate:api \
 && npm run build \
 && mv dist/*/browser /site

# ---------------------------------------------------------------- 2. Exécution
# Nginx sans privilèges : utilisateur nginx (uid 101), écoute sur le port 8080.
FROM nginxinc/nginx-unprivileged:1.31-alpine

LABEL org.opencontainers.image.source="https://github.com/Matfen2/mecadetect" \
      org.opencontainers.image.title="mecadetect-front" \
      org.opencontainers.image.description="Interface web de MécaDétect (Angular, Nginx)"

# Correctifs de sécurité d'Alpine publiés depuis la construction de l'image de base
# (Trivy bloque la publication sur toute faille critique ou haute déjà corrigée).
# Root le temps de la mise à jour : l'image repasse en utilisateur 101 plus bas.
USER root
RUN apk upgrade --no-cache

COPY frontend/nginx/default.conf /etc/nginx/conf.d/default.conf
# Les fichiers appartiennent à root : Nginx les lit, mais ne peut pas les modifier
COPY --from=construction /site /usr/share/nginx/mecadetect

USER 101
EXPOSE 8080