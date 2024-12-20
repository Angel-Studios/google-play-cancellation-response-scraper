#!/bin/bash

# Initialize output directories
for i in csv db; do
  mkdir -p /output/$i
done

# Activate Google Cloud service account
KEYFILE=$(mktemp)
echo ${SERVICE_ACCOUNT_JSON} | base64 -d > $KEYFILE
gcloud auth activate-service-account --key-file=$KEYFILE || (rm -f $KEYFILE; exit 1)
rm -f $KEYFILE

for i in $(cat /config/package-map.txt | cut -d':' -f1); do
  # Download responses from Google Cloud Storage
  gsutil cp gs://${GCS_BUCKET_ID}/subscriptions/cancellations/freeform_$i.csv - | gunzip > /output/csv/$i.csv

  # Run script to parse responses and notify Slack
  /scripts/parser.main.kts \
    --csv-path /output/csv/$i.csv \
    --db-path /output/db/$i.db \
    --package-name $i \
    --package-map-path /config/package-map.txt \
    --sku-map-path /config/sku-map.txt \
    --slack-channel-map-path /config/slack-channel-map.txt \
    --slack-api-token ${SLACK_API_TOKEN}
done
