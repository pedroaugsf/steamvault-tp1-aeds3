@echo off
REM ---------------------------------------------------------------
REM  SteamVault - TP1 AEDS III
REM  Compila todo o codigo-fonte de src/ para a pasta bin/
REM ---------------------------------------------------------------
chcp 65001 > nul
setlocal

echo.
echo  Compilando o projeto...

if not exist bin mkdir bin
dir /s /b src\*.java > fontes.tmp
javac -encoding UTF-8 -d bin @fontes.tmp
set ERRO=%ERRORLEVEL%
del fontes.tmp

if %ERRO% NEQ 0 (
    echo.
    echo  [ERRO] A compilacao falhou.
    exit /b %ERRO%
)

echo  [OK] Compilado com sucesso em bin\
echo.
endlocal
