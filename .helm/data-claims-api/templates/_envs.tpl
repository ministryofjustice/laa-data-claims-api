{{/*
  Define environment variables that can be "included" in deployment.yaml
*/}}
{{- define "envsFromRdsSecret" }}
{{/*
Only for the main branch, extract DB environment variables from rds-postgresql-instance-output secret
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