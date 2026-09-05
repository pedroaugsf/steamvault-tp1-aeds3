#!/usr/bin/env bash
# ---------------------------------------------------------------
#  SteamVault - publica o caderno de trabalho no Netlify
#
#  Primeira vez:
#    1. npx netlify-cli login    (abre o navegador, só precisa uma vez)
#    2. ./publicar.sh            (a CLI pergunta se cria um site novo)
# ---------------------------------------------------------------
set -e
cd "$(dirname "$0")"

echo "Publicando a pasta site/ no Netlify..."
npx --yes netlify-cli deploy --dir=site --prod
echo
echo "No ar. A URL aparece acima como \"Website URL\"."
