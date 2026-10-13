#!/usr/bin/env bash
# Creates the single-table used by the API in DynamoDB Local.
set -euo pipefail

ENDPOINT="${DYNAMODB_ENDPOINT:-http://localhost:8000}"
TABLE="${DYNAMODB_TABLE:-franchises}"

# Dummy credentials: DynamoDB Local does not validate them.
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION="${AWS_REGION:-us-east-1}"

aws dynamodb create-table \
  --endpoint-url "$ENDPOINT" \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager

echo "Table '$TABLE' created at $ENDPOINT"
