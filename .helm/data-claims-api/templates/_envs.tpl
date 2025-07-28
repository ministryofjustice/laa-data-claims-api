{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "dbConnectionDetails" }}
{{/*
For the preview branches, set DB connection details to Bitnami Postgres specific values
*/}}
{{- if eq .Values.spring.profile "preview" }}
- name: DB_NAME
  value: "postgres"
- name: DB_USERNAME
  value: "postgres"
- name: DB_HOST
  value: {{ .Release.Name }}-postgresql
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Release.Name }}-postgresql
      key: postgres-password
{{- end }}

{{/*
For the main branch, extract DB environment variables from rds-postgresql-instance-output secret
*/}}
{{- if eq .Values.spring.profile "main" }}
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