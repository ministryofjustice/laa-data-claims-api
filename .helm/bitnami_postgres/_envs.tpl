{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "envsFromRdsSecret" }}
{{/*
Only for the main branch, extract DB environment variables from rds-postgresql-instance-output secret
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
{{- end }}