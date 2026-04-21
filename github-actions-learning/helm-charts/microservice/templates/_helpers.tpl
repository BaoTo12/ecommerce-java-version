{{/*
================================================================================
HELM HELPER TEMPLATES (_helpers.tpl)

These are named templates defined with {{ define "..." }} and called with
{{ include "..." . }} in other template files.

WHY use helpers?
- Avoid repeating the same code in deployment.yaml, service.yaml, hpa.yaml...
- Single place to change naming conventions
- Consistent labels across all Kubernetes resources
================================================================================
*/}}

{{/*
microservice.name — Chart name (truncated to 63 chars, Kubernetes limit)
*/}}
{{- define "microservice.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
microservice.fullname — Full release name used for resource names.
If the release name already contains the chart name, don't duplicate it.

Example:
  helm install order-service charts/microservice
  → Release.Name = "order-service", Chart.Name = "microservice"
  → fullname = "order-service" (not "order-service-microservice")
*/}}
{{- define "microservice.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
microservice.chart — Chart name+version for the helm.sh/chart label
*/}}
{{- define "microservice.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
microservice.labels — Standard Kubernetes recommended labels.
These go on ALL resources created by this chart.
Labels enable: kubectl get all -l app.kubernetes.io/name=order-service
*/}}
{{- define "microservice.labels" -}}
helm.sh/chart: {{ include "microservice.chart" . }}
{{ include "microservice.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
microservice.selectorLabels — Labels used in Deployment selector and Service selector.
MUST be stable (never change after first deploy — k8s requires selector immutability).
*/}}
{{- define "microservice.selectorLabels" -}}
app.kubernetes.io/name: {{ include "microservice.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
microservice.serviceAccountName — Name of the ServiceAccount to use.
If serviceAccount.create = true, use the release name (or explicit name if set).
If serviceAccount.create = false, use "default" (or explicit name if set).
*/}}
{{- define "microservice.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "microservice.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
