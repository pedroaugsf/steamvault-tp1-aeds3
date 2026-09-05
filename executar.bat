@echo off
REM ---------------------------------------------------------------
REM  SteamVault - TP1 AEDS III
REM  Compila (se necessario) e abre o menu principal do sistema
REM ---------------------------------------------------------------
chcp 65001 > nul

if not exist bin\tp1\app\Main.class call compilar.bat
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

java -cp bin tp1.app.Main
