#!/bin/bash

if [ ! $# -eq 1 ]
then
	echo -e "Usage:\n\ninsert.sh <hostname>\n\nSends each OpenAPI description contained in descriptions.zip to http://<hostname>/insertDescription"
	exit 1
fi

unzip -q descriptions.zip -d tmp

for entry in tmp/*
do
  echo "Inserting: ${entry:4}"
  curl -d "@./$entry" -H "Content-Type: application/json" -X POST http://"$1"/insertDescription
done

rm -rf tmp
