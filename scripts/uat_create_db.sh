#!/bin/bash

function _uat_create_db() {
  usage="uat_create_db -- create a database in the UAT RDS instance named after the release
  Usage: scripts/uat_create_db.sh <release-name>

  Example:
    scripts/uat_create_db.sh data-claims-api-feature-xyz
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
  else
    RELEASE_NAME=$1
  fi

  # Validate database name length (PostgreSQL limit is 63 characters)
  if [[ ${#RELEASE_NAME} -gt 63 ]]; then
    echo "ERROR: Database name '${RELEASE_NAME}' exceeds PostgreSQL's 63-character limit (${#RELEASE_NAME} chars)" >&2
    return 1
  fi

  echo 'Retrieve RDS credentials'
  DB_NAME=$(kubectl get secret rds-postgresql-instance-output -o jsonpath="{.data.database_name}" | base64 --decode)
  DB_USER=$(kubectl get secret rds-postgresql-instance-output -o jsonpath="{.data.database_username}" | base64 --decode)
  DB_PWD=$(kubectl get secret rds-postgresql-instance-output -o jsonpath="{.data.database_password}" | base64 --decode)

  PF_POD_NAME=$(kubectl get pod -l "run=port-forward-pod" -o jsonpath='{.items[0].metadata.name}')
  if [ -z "$PF_POD_NAME" ]; then
    echo "Unable to resolve pod for selector 'run=port-forward-pod' in namespace '$CURRENT_NAMESPACE'." >&2
    exit 1
  fi
  
  echo 'Starting port-forwarding as a background job'
  kubectl port-forward pod/"$PF_POD_NAME" 5433:5432 &
  PF_PID=$!

  sleep 5

  echo "Creating database: ${RELEASE_NAME}"
  for i in {1..3}; do
    echo "Attempt: $i"

    # Check if database already exists
    GET_QUERY="SELECT datname FROM pg_database WHERE datname = '${RELEASE_NAME}'"
    EXISTING_DB=$(psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -qtc "${GET_QUERY};" | xargs || true)

    if [[ -n "${EXISTING_DB}" ]]; then
      echo "Database ${RELEASE_NAME} already exists!"
      break
    else
      CREATE_DB_CMD="CREATE DATABASE \"${RELEASE_NAME}\";"
      CREATE_DB_OUTPUT=$(psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -c "${CREATE_DB_CMD};" || true)

      CREATED=$(echo "$CREATE_DB_OUTPUT" | grep -c 'CREATE DATABASE' || true)
      if [[ $CREATED == 1 ]]; then
        echo "Created database ${RELEASE_NAME}!"
        break
      else
        echo "Failed to create database, retrying..."
        sleep 2
      fi
    fi
  done

  echo 'Killing port-forwarding background job'
  kill $PF_PID || true

  echo 'Deleting port-forwarding pod'
  kubectl delete pod port-forward-pod --wait=false || true
}

#Call the above function with all the script arguments
_uat_create_db "$@"