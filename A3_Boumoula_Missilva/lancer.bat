@echo off
chcp 65001 >nul
cls
echo ╔═══════════════════════════════════════════════════════╗
echo ║   ANALYSEUR LEXICAL ET SYNTAXIQUE PHP - IF/ELSE      ║
echo ╚═══════════════════════════════════════════════════════╝
echo.

REM Vérifier Java
echo Verification de Java...
java -version 2>&1 | findstr /i "version" >nul
if errorlevel 1 (
    echo.
    echo ❌ ERREUR: Java n'est pas installe ou pas dans le PATH
    echo.
    echo Pour installer Java:
    echo 1. Telechargez Java depuis https://www.java.com
    echo 2. Installez Java
    echo 3. Relancez ce programme
    echo.
    pause
    exit /b 1
)

REM Vérifier que le JAR existe
if not exist PHPCompilateurIFELSE.jar (
    echo.
    echo ❌ ERREUR: Le fichier PHPCompilateurIFELSE.jar est introuvable
    echo.
    echo Assurez-vous que lancer.bat et PHPCompilateurIFELSE.jar
    echo sont dans le meme dossier.
    echo.
    pause
    exit /b 1
)

echo ✅ Java detecte
echo ✅ JAR trouve
echo.
echo Lancement du programme...
echo.

REM Lancer le programme
java -jar PHPCompilateurIFELSE.jar

REM Vérifier le code de sortie
if errorlevel 1 (
    echo.
    echo ⚠️  Le programme s'est termine avec des erreurs
)

echo.
echo ════════════════════════════════════════════════════════
echo Programme termine
pause