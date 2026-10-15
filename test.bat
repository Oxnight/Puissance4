@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
set "P4_JAVA=java"
set "P4_JAVAC=javac"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "P4_JAVA=%JAVA_HOME%\bin\java.exe"
    set "P4_JAVAC=%JAVA_HOME%\bin\javac.exe"
)
if not exist build\tests mkdir build\tests
(for /r tests %%F in (*.java) do call :write_source "%%F") > build\test-sources.txt
"%P4_JAVAC%" -encoding UTF-8 --release 17 -Xlint:all -cp build\classes -d build\tests @build\test-sources.txt
if errorlevel 1 exit /b 1
"%P4_JAVA%" -ea -cp "build\classes;build\tests" Puissance4.EngineTests
if errorlevel 1 exit /b 1
"%P4_JAVA%" -ea -cp "build\classes;build\tests" Puissance4.AdvancedTests
if errorlevel 1 exit /b 1
"%P4_JAVA%" -Xmx32m -ea -cp "build\classes;build\tests" Puissance4.LimitedHeapTests
if errorlevel 1 exit /b 1
if "%~1"=="--gui" (
    "%P4_JAVA%" -ea -cp "build\classes;build\tests" Puissance4.GuiTests
    if errorlevel 1 exit /b 1
    "%P4_JAVA%" -ea -Dpuissance4.appearance=light -Dpuissance4.reducedMotion=true -cp "build\classes;build\tests" Puissance4.AtelierTests
    if errorlevel 1 exit /b 1
    "%P4_JAVA%" -ea -Dpuissance4.appearance=dark -Dpuissance4.reducedMotion=true -cp "build\classes;build\tests" Puissance4.AtelierTests
    if errorlevel 1 exit /b 1
    "%P4_JAVA%" -ea -cp "build\classes;build\tests" Puissance4.gui.InteractionAuditTests
    if errorlevel 1 exit /b 1
    "%P4_JAVA%" -ea -Dpuissance4.appearance=light -Dpuissance4.reducedMotion=true -cp "build\classes;build\tests" Puissance4.gui.LayoutAuditTests
    if errorlevel 1 exit /b 1
    "%P4_JAVA%" -ea -Dpuissance4.appearance=dark -Dpuissance4.reducedMotion=true -cp "build\classes;build\tests" Puissance4.gui.LayoutAuditTests
    if errorlevel 1 exit /b 1
)

exit /b 0

:write_source
set "P4_SOURCE=%~1"
echo "%P4_SOURCE:\=/%"
exit /b 0
