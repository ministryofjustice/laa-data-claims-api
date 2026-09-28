#!/bin/bash

#  Copyright 2018 Snyk Ltd.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Permalink: https://github.com/snyk-tech-services/snyk-delta/blob/1a45cc1ec6b390d8e1b266b157e00453a4d12eb5/snyk_delta_all_projects.sh

# Call this script as you would call snyk test | snyk-delta, minus the --all-projects and --json flags
# This is an interim fix until snyk-delta supports all projects itself (or snyk supports a --new flag)
# example: /bin/bash snyk_delta_all_projects.sh --severity=high --exclude=tests,resources -- -s config.yaml
# runs snyk test --all-projects --json $*
# requires jq to be installed
#
# Locally modified: in addition to the checked-out workspace, this scans the
# other laa-data-claims repositories listed in SNYK_ADDITIONAL_REPOS. Each is
# shallow-cloned to a temporary directory and scanned in turn, and the highest
# exit code across every repository is returned.

set -euo pipefail

exit_code=0
snyk_test_json=''
formatted_json=''
args=("$*")

# Repositories scanned in addition to the current workspace (space separated).
GITHUB_ORG="${GITHUB_ORG:-ministryofjustice}"
SNYK_ADDITIONAL_REPOS="${SNYK_ADDITIONAL_REPOS:-laa-data-claims-event-service}"

workspace_dir="$(pwd)"
clone_root=''

cleanup() {
    if [ -n "${clone_root}" ] && [ -d "${clone_root}" ]
    then
        rm -rf "${clone_root}"
    fi
}
trap cleanup EXIT

run_snyk_delta () {
    # add in any other arguments you would like to use
    snyk-delta
}

run_snyk_test () {
    local scan_dir="$1"
    echo "Running: snyk test --all-projects --json" $args "(in ${scan_dir})"
    local snyk_exit_code=0
    {

        snyk_test_json=`cd "${scan_dir}" && snyk test --all-projects --json $args`

        } || {
        snyk_exit_code=$?
        if [ $snyk_exit_code -eq 2 ]
        then
            echo 'snyk test command was not successful, retry with -d to see more information'
            exit 2
        fi
    }


}

clone_repo () {
    local repo="$1"
    local dest="$2"
    local url="https://github.com/${GITHUB_ORG}/${repo}.git"

    echo "Cloning ${GITHUB_ORG}/${repo}"
    if [ -n "${GITHUB_TOKEN:-}" ]
    then
        # Matches actions/checkout: the token is sent as a header rather than
        # embedded in the remote URL, so it is not written into .git/config.
        local basic_auth
        basic_auth=`printf 'x-access-token:%s' "${GITHUB_TOKEN}" | base64 | tr -d '\n'`
        git -c http.extraheader="AUTHORIZATION: basic ${basic_auth}" \
            clone --depth 1 --quiet "${url}" "${dest}"
    else
        git clone --depth 1 --quiet "${url}" "${dest}"
    fi
}

format_snyk_test_output() {
    echo "Processing snyk test --json output"
    {
        formatted_json=`echo $snyk_test_json | jq -r 'if type=="array" then .[] else . end | @base64'`
        } || {
        echo 'failed to process snyk-test result'
        exit 2
    }
}


#######
# Scan a single checkout: snyk test, format the results, then run snyk-delta
# against each project found. Updates the overall exit_code.
scan_directory () {
    local scan_dir="$1"
    local label="$2"

    echo "=== Scanning ${label} ==="

    # 1. run snyk test
    run_snyk_test "${scan_dir}"

    # 2. format results to support single & multiple results returned
    format_snyk_test_output

    # 3. call snyk-delta for each result
    for test in `echo $formatted_json`; do
        single_result="$(echo ${test} | base64 -d)" # use "base64 -d -i" on Windows, which will ignore any "gardage" characters echoing may add
        project_name="$(echo ${single_result} | jq -r '.displayTargetFile')"
        echo 'Processing: '  ${label}/${project_name}
        if echo ${single_result} | run_snyk_delta
        then
            project_exit_code=$?
            echo 'Finished processing'
        else
            project_exit_code=$?
            if [ $project_exit_code -gt 1 ]
            then
                echo 'snyk-delta encountered an error, retrying.'
                echo ${single_result} | run_snyk_delta
            fi
            echo 'Finished processing'
        fi

        if [ $project_exit_code -gt $exit_code ]
        then
            exit_code=$project_exit_code
        fi
        echo "Project: ${label}/${project_name} | Exit code: ${project_exit_code}"
    done
}

# Scan the checked-out workspace first.
scan_directory "${workspace_dir}" "$(basename "${workspace_dir}")"

# Then scan each additional laa-data-claims repository.
if [ -n "${SNYK_ADDITIONAL_REPOS}" ]
then
    clone_root=`mktemp -d`
    for repo in ${SNYK_ADDITIONAL_REPOS}; do
        clone_repo "${repo}" "${clone_root}/${repo}"
        scan_directory "${clone_root}/${repo}" "${repo}"
    done
fi

echo "Overall exit code for snyk-delta-all-projects.sh: ${exit_code}"
exit $exit_code