@echo off
setlocal
cd /d "%~dp0"
set "P4_JAVAC=javac"
set "P4_JAR=jar"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "P4_JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "P4_JAR=%JAVA_HOME%\bin\jar.exe"
)
"%P4_JAVAC%" -version >nul 2>&1
if errorlevel 1 (
    echo Installez un JDK 17 ou plus recent pour compiler ce projet.
    exit /b 1
)
if exist build\classes rmdir /s /q build\classes
if exist build\classes exit /b 1
mkdir build\classes
if not exist dist mkdir dist
(for /r src %%F in (*.java) do call :write_source "%%F") > build\sources.txt
"%P4_JAVAC%" -encoding UTF-8 --release 17 -Xlint:all -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
xcopy resources\Puissance4 build\classes\Puissance4 /E /I /Y >nul
if errorlevel 1 exit /b 1
"%P4_JAR%" --create --file dist\Puissance4.jar --main-class Puissance4.Main --manifest resources/MANIFEST.MF -C build\classes .
if errorlevel 1 exit /b 1
echo Pret : dist\Puissance4.jar

exit /b 0

:write_source
set "P4_SOURCE=%~1"
echo "%P4_SOURCE:\=/%"
exit /b 0
