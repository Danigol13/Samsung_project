#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Copia as evidências da ÚLTIMA execução de cada cenário (target/evidence)
# para docs/evidence/, que é versionado e vai na entrega.
#  - inclui report.html + screenshots (trace.zip fica de fora: tamanho)
#  - gera docs/evidence/index.html com status e link de cada cenário
# Uso (Git Bash / Linux / macOS):  ./scripts/collect-evidence.sh
# ---------------------------------------------------------------------------
set -euo pipefail
SRC="target/evidence"
DST="docs/evidence"
[ -d "$SRC" ] || { echo "Nada em $SRC - rode os testes antes."; exit 1; }

mkdir -p "$DST"
find "$DST" -mindepth 1 -maxdepth 1 -type d -exec rm -r {} +

# pasta = <yyyyMMdd-HHmmss>_<cenário>; ordenação lexical = cronológica -> fica a última de cada cenário
declare -A latest
for d in $(ls -1d "$SRC"/*/ | sort); do
  name="$(basename "$d")"
  scenario="${name#*_}"
  latest["$scenario"]="$d"
done

rows=""
for scenario in $(printf '%s\n' "${!latest[@]}" | sort); do
  d="${latest[$scenario]}"
  [ -f "$d/report.html" ] || continue
  mkdir -p "$DST/$scenario"
  cp "$d/report.html" "$DST/$scenario/"
  [ -d "$d/screenshots" ] && cp -r "$d/screenshots" "$DST/$scenario/"
  if grep -q "class='pass'>PASSED" "$d/report.html"; then status="✅ PASSED"; else status="❌ FAILED"; fi
  order="$(grep -oE 'PE[0-9]{6}-[0-9]{6,}' "$d/report.html" | head -1 || true)"
  rows+="<tr><td><a href='$scenario/report.html'>$scenario</a></td><td>$status</td><td>${order:--}</td><td>$(basename "$d" | cut -d_ -f1)</td></tr>"
done

cat > "$DST/index.html" <<HTML
<!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>Evidências - Samsung_project</title>
<style>body{font-family:system-ui,Arial,sans-serif;margin:24px}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:6px 10px;text-align:left}th{background:#f3f3f3}</style>
</head><body><h1>Evidências de execução - Guest Checkout (stg2)</h1>
<p>Última execução de cada cenário. Cada relatório contém os passos, URL, duração, dados usados (e-mail, cartão, Order Number) e o screenshot de cada tela.</p>
<table><tr><th>Cenário</th><th>Status</th><th>Order Number</th><th>Execução</th></tr>$rows</table>
</body></html>
HTML
echo "Evidências copiadas para $DST (abra $DST/index.html)"
