#!/bin/bash

echo "Initializing localstack SQS"


awslocal sqs create-queue --queue-name claims-api-queue --attributes VisibilityTimeout=1200