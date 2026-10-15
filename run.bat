@echo off
setlocal
cd /d "%~dp0"
set "P4_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "P4_JAVA=%JAVA_HOME%\bin\java.exe"
"%P4_JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Installez Java 17 ou plus recent, puis relancez run.bat.
    pause
    exit /b 1
)
if not exist dist\Puissance4.jar (
    call build.bat
    if errorlevel 1 (
        pause
        exit /b 1
    )
)
"%P4_JAVA%" -jar dist\Puissance4.jar
if errorlevel 1 (
    echo Le lancement a echoue. Verifiez que Java 17 ou plus recent est installe.
    pause
    exit /b 1
)
