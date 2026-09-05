@echo off
REM ---------------------------------------------------------------
REM  SteamVault - publica o caderno de trabalho no Netlify
REM
REM  Primeira vez:
REM    1. npx netlify-cli login      (abre o navegador, so precisa uma vez)
REM    2. publicar.bat               (a CLI pergunta se cria um site novo)
REM
REM  Depois disso, basta rodar publicar.bat de novo a cada alteracao.
REM ---------------------------------------------------------------
chcp 65001 > nul

echo.
echo  Publicando a pasta site\ no Netlify...
echo.

npx --yes netlify-cli deploy --dir=site --prod

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [ERRO] O deploy falhou.
    echo  Se a mensagem for sobre autenticacao, rode antes:  npx netlify-cli login
    exit /b %ERRORLEVEL%
)

echo.
echo  [OK] No ar. A URL aparece acima como "Website URL".
echo.
