{{- define "alu-chess.namespace" -}}
{{- .Values.namespace.name -}}
{{- end -}}

{{- define "alu-chess.fullname" -}}
{{- .Chart.Name -}}
{{- end -}}
