{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "dbConnectionDetails" }}
{{/*
All profiles use the same RDS instance with database name passed from deploy action
*/}}
- name: DB_NAME
  value: {{ .Values.database.name | quote }}
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_username
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_password
- name: DB_HOST
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: rds_instance_address
{{- end }}

{{/*
  Define Sentry environment variables
*/}}
{{- define "sentryConfig" }}
{{- if .Values.sentry.enabled }}
- name: SENTRY_ENABLED
  value: "true"
- name: SENTRY_DSN
  valueFrom:
    secretKeyRef:
      name: laa-data-claims-api-secrets
      key: sentry-dsn
- name: SENTRY_ENVIRONMENT
  value: {{ .Values.sentry.environment | quote }}
- name: SENTRY_TRACES_SAMPLE_RATE
  value: {{ .Values.sentry.tracesSampleRate | quote }}
{{- else }}
- name: SENTRY_ENABLED
  value: "false"
{{- end }}
{{- end }}