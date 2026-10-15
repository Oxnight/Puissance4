@echo off
setlocal
cd /d "%~dp0"
set "P4_JAVA=java"
set "P4_JAVAC=javac"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "P4_JAVA=%JAVA_HOME%\bin\java.exe"
    set "P4_JAVAC=%JAVA_HOME%\bin\javac.exe"
)
set "P4_MODE=%~1"
if not defined P4_MODE set "P4_MODE=time"
if not "%P4_MODE%"=="time" if not "%P4_MODE%"=="fixed" if not "%P4_MODE%"=="book" (
    echo Usage: benchmark.bat [fixed^|time^|book] [1..20] [ancien.jar] >&2
    exit /b 1
)
set "P4_REPEATS=%~2"
if not defined P4_REPEATS set "P4_REPEATS=3"
set "P4_BENCH_JAR=%~3"
if not defined P4_BENCH_JAR (
    call build.bat >&2
    if errorlevel 1 exit /b 1
    set "P4_BENCH_JAR=dist\Puissance4.jar"
)
if not exist "%P4_BENCH_JAR%" (
    echo JAR introuvable : %P4_BENCH_JAR% >&2
    exit /b 1
)
if not exist build\benchmarks mkdir build\benchmarks
"%P4_JAVAC%" -encoding UTF-8 --release 17 -cp "%P4_BENCH_JAR%" -d build\benchmarks benchmarks\Puissance4\Benchmark.java
if errorlevel 1 exit /b 1
"%P4_JAVA%" -Xms64m -Xmx512m -cp "%P4_BENCH_JAR%;build\benchmarks" Puissance4.Benchmark "%P4_MODE%" tests/data/exact-positions.csv "%P4_REPEATS%"
exit /b %errorlevel%
