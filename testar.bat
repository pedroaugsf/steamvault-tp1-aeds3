@echo off
REM ---------------------------------------------------------------
REM  SteamVault - TP1 AEDS III
REM  Roda a bateria de testes automatizados (sem interacao)
REM ---------------------------------------------------------------
chcp 65001 > nul

if not exist bin\tp1\app\Testes.class call compilar.bat
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

java -cp bin tp1.app.Testes
