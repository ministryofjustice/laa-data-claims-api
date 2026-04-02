#!/bin/bash

function _uat_drop_db() {
  usage="uat_drop_db -- drop a database in the UAT RDS instance
  Usage: scripts/uat_drop_db.sh <release-name>
  DANGER: This will delete the database permanently

  Example:
    scripts/uat_drop_db.sh data-claims-api-feature-xyz
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

  # Prevent dropping critical databases
  case "$RELEASE_NAME" in
    rdsadmin | template0 | template1 | postgres | main | $DB_NAME)
      echo "ERROR: Cannot drop protected database \"$RELEASE_NAME\"!" >&2
      return 1
      ;;
    *)
      ;;
  esac

  PF_POD_NAME=$(kubectl get pod -l "run=port-forward-pod" -o jsonpath='{.items[0].metadata.name}')
  if [ -z "$PF_POD_NAME" ]; then
    echo "Unable to resolve pod for selector 'run=port-forward-pod' in namespace '$CURRENT_NAMESPACE'." >&2
    exit 1
  fi

  echo 'Starting port-forwarding as a background job'
  kubectl port-forward pod/"$PF_POD_NAME" 5433:5432 &
  PF_PID=$!

  sleep 5

  echo "Dropping database: ${RELEASE_NAME}"
  for i in {1..3}; do
    echo "Attempt: $i"

    GET_QUERY="SELECT datname FROM pg_database WHERE datname = '${RELEASE_NAME}'"
    DATABASE_TO_DROP=$(psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -qtc "${GET_QUERY};" | xargs || true)

    if [[ -z "${DATABASE_TO_DROP}" ]]; then
      echo "Database ${RELEASE_NAME} not found!"
      break
    fi

    # Prevent new connections
    ALTER_CONN_LIMIT="ALTER DATABASE \"${DATABASE_TO_DROP}\" CONNECTION LIMIT 0;"
    psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -qtc "${ALTER_CONN_LIMIT};" || true

    # Terminate existing connections
    CLOSE_CONN_QUERY="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DATABASE_TO_DROP}'"
    psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -qtc "${CLOSE_CONN_QUERY};" || true

    sleep 2

    # Drop the database
    DROP_DATABASE_CMD="DROP DATABASE \"${DATABASE_TO_DROP}\";"
    DROP_DATABASE_OUTPUT=$(psql postgres://"${DB_USER}":"${DB_PWD}"@localhost:5433/"${DB_NAME}" -c "${DROP_DATABASE_CMD};" || true)

    DROPPED=$(echo "$DROP_DATABASE_OUTPUT" | grep -c 'DROP DATABASE' || true)
    if [[ $DROPPED == 1 ]]; then
      echo "Successfully dropped database ${DATABASE_TO_DROP}!"
      break
    else
      echo "Failed to drop database, retrying..."
      sleep 2
    fi
  done

  echo 'Killing port-forwarding background job'
  kill $PF_PID || true

  echo 'Deleting port-forwarding pod'
  kubectl delete pod port-forward-pod --wait=false || true
}

#Call the above function with all the script arguments
_uat_drop_db "$@"