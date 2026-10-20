#!/bin/sh
# Démarre en root le temps de préparer le volume, puis lance l'API sans privilèges.
set -eu
mkdir -p /app/data
chown -R appuser:appuser /app/data
# JAVA_OPTS est volontairement non quoté (plusieurs options JVM).
# shellcheck disable=SC2086
exec runuser -u appuser -- java ${JAVA_OPTS:-} -jar /app/app.jar
