#!/bin/bash

echo "Initializing localstack SNS"

awslocal sns create-topic --name claims-events