#!/usr/bin/env sh
# Lanza FisioAgenda (requiere Java 17 o superior en el PATH)
cd "$(dirname "$0")"
if [ -f FisioAgenda.jar ]; then
  exec java -jar FisioAgenda.jar
else
  exec java -jar target/FisioAgenda.jar
fi
