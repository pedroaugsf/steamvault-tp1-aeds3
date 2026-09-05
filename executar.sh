#!/usr/bin/env bash
# ---------------------------------------------------------------
#  SteamVault - TP1 AEDS III
#  Compila e executa o sistema (Linux / macOS / Git Bash)
#
#  Uso:  ./executar.sh          -> abre o menu principal
#        ./executar.sh testes   -> roda a bateria de testes
# ---------------------------------------------------------------
set -e
cd "$(dirname "$0")"

echo "Compilando..."
mkdir -p bin
find src -name "*.java" > /tmp/fontes_tp1.txt
javac -encoding UTF-8 -d bin @/tmp/fontes_tp1.txt
rm -f /tmp/fontes_tp1.txt
echo "Compilado com sucesso."
echo

if [ "$1" = "testes" ]; then
  java -cp bin tp1.app.Testes
else
  java -cp bin tp1.app.Main
fi
