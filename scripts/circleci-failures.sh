#!/bin/bash
# Fetch failed step output from a CircleCI pipeline
# Usage: ./scripts/circleci-failures.sh <pipeline-number>
# Example: ./scripts/circleci-failures.sh 2968
#
# Requires CIRCLECI_TOKEN env var (source ~/.zshrc if needed)

set -euo pipefail

PIPELINE_NUM="${1:?Usage: $0 <pipeline-number>}"
PROJECT_SLUG="gh/monowai/beancounter"

if [[ -z "${CIRCLECI_TOKEN:-}" ]]; then
  source ~/.zshrc 2>/dev/null || true
fi

if [[ -z "${CIRCLECI_TOKEN:-}" ]]; then
  echo "Error: CIRCLECI_TOKEN not set" >&2
  exit 1
fi

AUTH="-H Circle-Token:${CIRCLECI_TOKEN}"

# Resolve pipeline number to pipeline ID
PIPELINE_ID=$(curl -sf "https://circleci.com/api/v2/project/${PROJECT_SLUG}/pipeline?branch=main" ${AUTH} | python3 -c "
import sys, json
for p in json.load(sys.stdin).get('items', []):
    if p['number'] == ${PIPELINE_NUM}:
        print(p['id'])
        break
")

if [[ -z "${PIPELINE_ID}" ]]; then
  echo "Pipeline #${PIPELINE_NUM} not found" >&2
  exit 1
fi

echo "Pipeline #${PIPELINE_NUM} (${PIPELINE_ID})"

# Get workflows
curl -sf "https://circleci.com/api/v2/pipeline/${PIPELINE_ID}/workflow" ${AUTH} | python3 -c "
import sys, json
for w in json.load(sys.stdin).get('items', []):
    print(f\"Workflow: {w['name']} status={w['status']} id={w['id']}\")
"

# Get failed jobs from each workflow
WORKFLOW_IDS=$(curl -sf "https://circleci.com/api/v2/pipeline/${PIPELINE_ID}/workflow" ${AUTH} | python3 -c "
import sys, json
for w in json.load(sys.stdin).get('items', []):
    print(w['id'])
")

for WF_ID in ${WORKFLOW_IDS}; do
  FAILED_JOBS=$(curl -sf "https://circleci.com/api/v2/workflow/${WF_ID}/job" ${AUTH} | python3 -c "
import sys, json
for j in json.load(sys.stdin).get('items', []):
    if j['status'] == 'failed':
        print(j.get('job_number', ''))
")

  for JOB_NUM in ${FAILED_JOBS}; do
    echo ""
    echo "=== Failed Job #${JOB_NUM} ==="

    # Use v1.1 API for step-level output
    curl -sf "https://circleci.com/api/v1.1/project/github/monowai/beancounter/${JOB_NUM}?circle-token=${CIRCLECI_TOKEN}" | python3 -c "
import sys, json, urllib.request
data = json.load(sys.stdin)
print(f'Job: {data.get(\"build_parameters\",{}).get(\"CIRCLE_JOB\",\"unknown\")}')
print(f'Branch: {data.get(\"branch\",\"?\")}')
print(f'Subject: {data.get(\"subject\",\"?\")}')
for step in data.get('steps', []):
    for action in step.get('actions', []):
        if action.get('failed'):
            print(f'\\nFailed step: {step[\"name\"]}')
            url = action.get('output_url', '')
            if url:
                try:
                    resp = urllib.request.urlopen(url)
                    output = json.loads(resp.read())
                    for item in output:
                        msg = item.get('message', '')
                        lines = msg.split('\\n')
                        for line in lines[-100:]:
                            print(line)
                except Exception as e:
                    print(f'(Failed to fetch output: {e})')
"
  done
done

# Show running/pending jobs
curl -sf "https://circleci.com/api/v2/pipeline/${PIPELINE_ID}/workflow" ${AUTH} | python3 -c "
import sys, json
for w in json.load(sys.stdin).get('items', []):
    if w['status'] in ('running', 'on_hold', 'queued'):
        print(f\"\\nWorkflow '{w['name']}' is {w['status']}...\")
" 2>/dev/null
