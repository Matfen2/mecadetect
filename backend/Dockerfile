# syntax=docker/dockerfile:1

# Image de production de l'API MécaDétect.
# À construire depuis la racine du dépôt, car le contrat api/openapi.yaml est nécessaire :
#   docker build -f backend/Dockerfile -t mecadetect-api .
# Pour la VM Oracle Cloud (processeur Ampere, arm64) :
#   docker buildx build --platform linux/arm64 -f backend/Dockerfile -t mecadetect-api .

# ---------------------------------------------------------------- 1. Construction
# Le bytecode Java ne dépend pas du processeur : Maven tourne sur la plateforme de
# la machine de construction, sans émulation, même quand l'image visée est arm64.
FROM --platform=$BUILDPLATFORM maven:3.10.0-eclipse-temurin-25-noble AS construction
WORKDIR /construction

COPY api/openapi.yaml api/openapi.yaml
COPY backend/pom.xml backend/pom.xml
COPY backend/src/main backend/src/main

# Les tests ne tournent pas ici : ils ont besoin de Docker (Testcontainers) et
# passent dans la CI avant toute construction d'image.
# Le dépôt Maven local est un cache de BuildKit, conservé d'une construction à l'autre.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f backend/pom.xml package -Dmaven.test.skip=true \
 && cp backend/target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extraction \
 && mkdir -p extraction/dependencies extraction/spring-boot-loader \
             extraction/snapshot-dependencies extraction/application

# ---------------------------------------------------------------- 2. Exécution
# JRE seul (pas de JDK, pas de Maven) : l'image ne contient que ce qui sert à exécuter.
FROM eclipse-temurin:25-jre-noble

LABEL org.opencontainers.image.source="https://github.com/Matfen2/mecadetect" \
      org.opencontainers.image.title="mecadetect-api" \
      org.opencontainers.image.description="API REST de MécaDétect (Spring Boot, Oracle)"

# Utilisateur dédié, sans shell ni dossier personnel
RUN groupadd --system --gid 10001 mecadetect \
 && useradd --system --uid 10001 --gid mecadetect --no-create-home \
            --shell /usr/sbin/nologin mecadetect

WORKDIR /application

# Une couche par famille de fichiers, de la plus stable à la plus changeante :
# une nouvelle version du code ne fait retélécharger que la dernière couche
# (quelques centaines de Ko), pas les dépendances.
# Les fichiers appartiennent à root : l'application ne peut pas les modifier.
COPY --from=construction /construction/extraction/dependencies/ ./
COPY --from=construction /construction/extraction/spring-boot-loader/ ./
COPY --from=construction /construction/extraction/snapshot-dependencies/ ./
COPY --from=construction /construction/extraction/application/ ./

# Identifiant numérique : vérifiable sans lire /etc/passwd (Kubernetes runAsNonRoot)
USER 10001:10001
EXPOSE 8080

# Mémoire réglée d'après la limite du conteneur ; arrêt net si elle est dépassée,
# pour que Docker redémarre un processus sain plutôt que de garder une JVM bancale.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Forme exec : java est le processus 1 et reçoit directement SIGTERM (arrêt propre)
ENTRYPOINT ["java", "-jar", "application.jar"]