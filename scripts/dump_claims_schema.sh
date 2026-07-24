#!/bin/bash

function _dump_claims_schema() {
  usage="dump_claims_schema -- dump the claims schema from the UAT API database into the data factory UAT database.
  Uses a postgres:18 pod in the data factory namespace (required as local pg_dump may not match the RDS version).

  Usage: scripts/dump_claims_schema.sh <source-db-name>

  Example:
    scripts/dump_claims_schema.sh pr-371
    scripts/dump_claims_schema.sh main
  "

  set -e
  trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
  trap 'echo "\"${last_command}\" command completed with exit code $?."' EXIT

  CURRENT_NAMESPACE=$(kubectl config view --minify --output 'jsonpath={..namespace}'; echo)
  if [[ ! $CURRENT_NAMESPACE =~ -uat$ ]]; then
    echo "namespace must be UAT!" >&2
    return 1
  fi

  if [[ $# -ne 1 ]]; then
    echo "$usage"
    return 1
  fi

  SOURCE_DB=$1

  echo "Retrieving API UAT RDS credentials..."
  API_HOST=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-api-uat \
    -o jsonpath='{.data.rds_instance_address}' | base64 --decode)
  API_USER=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-api-uat \
    -o jsonpath='{.data.database_username}' | base64 --decode)
  API_PWD=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-api-uat \
    -o jsonpath='{.data.database_password}' | base64 --decode)

  echo "Retrieving data factory UAT RDS credentials..."
  DF_HOST=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-data-factory-uat \
    -o jsonpath='{.data.rds_instance_address}' | base64 --decode)
  DF_USER=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-data-factory-uat \
    -o jsonpath='{.data.database_username}' | base64 --decode)
  DF_PWD=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-data-factory-uat \
    -o jsonpath='{.data.database_password}' | base64 --decode)
  DF_DBNAME=$(kubectl get secret rds-postgresql-instance-output -n laa-data-claims-data-factory-uat \
    -o jsonpath='{.data.database_name}' | base64 --decode)

  echo "Ensuring psql-tmp pod is running in data factory namespace..."
  if ! kubectl get pod psql-tmp -n laa-data-claims-data-factory-uat --no-headers 2>/dev/null | grep -q Running; then
    kubectl delete pod psql-tmp -n laa-data-claims-data-factory-uat --ignore-not-found
    kubectl run psql-tmp -n laa-data-claims-data-factory-uat --restart=Never \
      --image=postgres:18 \
      --overrides='{
        "spec": {
          "securityContext": {"runAsNonRoot": true, "runAsUser": 999},
          "containers": [{
            "name": "psql-tmp",
            "image": "postgres:18",
            "command": ["sleep", "600"],
            "securityContext": {
              "allowPrivilegeEscalation": false,
              "runAsNonRoot": true,
              "runAsUser": 999
            },
            "resources": {
              "requests": {"memory": "64Mi", "cpu": "50m"},
              "limits":   {"memory": "128Mi", "cpu": "100m"}
            }
          }]
        }
      }'
    echo "Waiting for psql-tmp pod to be ready..."
    kubectl wait --for=condition=ready pod/psql-tmp -n laa-data-claims-data-factory-uat --timeout=90s
  fi

  echo "Dumping claims schema from '${SOURCE_DB}' and applying to data factory database '${DF_DBNAME}'..."
  kubectl exec pod/psql-tmp -n laa-data-claims-data-factory-uat -- \
    pg_dump "postgres://${API_USER}:${API_PWD}@${API_HOST}/${SOURCE_DB}" \
      --schema=claims \
      --schema-only \
      --no-owner \
      --no-privileges \
      --no-publications \
      --no-subscriptions | \
  kubectl exec -i pod/psql-tmp -n laa-data-claims-data-factory-uat -- \
    psql "postgres://${DF_USER}:${DF_PWD}@${DF_HOST}/${DF_DBNAME}"

  echo "Verifying tables created in data factory database..."
  kubectl exec pod/psql-tmp -n laa-data-claims-data-factory-uat -- \
    psql "postgres://${DF_USER}:${DF_PWD}@${DF_HOST}/${DF_DBNAME}" \
    -c "SELECT schemaname, tablename FROM pg_tables WHERE schemaname = 'claims' ORDER BY tablename;"

  echo "Done. claims schema is ready in the data factory database."
}

_dump_claims_schema "$@"
