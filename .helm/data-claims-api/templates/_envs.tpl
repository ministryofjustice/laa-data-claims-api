{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "dbConnectionDetails" }}
{{- if eq .Values.spring.profile "preview" }}
{{/*
For the preview branches, set DB connection details to Bitnami Postgres specific values
*/}}
- name: DB_NAME
  value: "postgres"
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Release.Name }}-postgresql
      key: postgres-password
- name: DB_HOST
  value: {{ .Release.Name }}-postgresql
{{- else if eq .Values.spring.profile "main" }}
{{/*
For the main branch, extract DB environment variables from rds-postgresql-instance-output secret
*/}}
- name: DB_NAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: database_name
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
{{- end }}

{{/*
  Define environment variables for the Javers audit DB
*/}}
{{- define "auditDbConnectionDetails" }}
{{- if eq .Values.spring.profile "preview" }}
- name: AUDIT_DB_NAME
  value: "AUDIT_db"
- name: AUDIT_DB_HOST
  value: "{{ .Release.Name }}-audit-postgresql"
- name: AUDIT_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: "{{ .Release.Name }}-audit-postgresql"
      key: postgres-password

{{- else if eq .Values.spring.profile "main" }}

- name: AUDIT_DB_NAME
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: audit_database_name
- name: AUDIT_DB_HOST
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: audit_rds_instance_address
- name: AUDIT_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: rds-postgresql-instance-output
      key: audit_database_password

{{- end }}
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