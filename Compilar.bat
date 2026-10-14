@echo off
setlocal
if exist "%~dp0..\..\work\toolchains\jdk-21.0.12.1+1\bin\java.exe" set "JAVA_HOME=%~dp0..\..\work\toolchains\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Configura JAVA_HOME con un JDK 21 para compilar desde este archivo.
    echo En Android Studio selecciona Java 21 en Settings - Build Tools - Gradle - Gradle JDK.
    exit /b 1
)
pushd "%~dp0"
set "VOCA_GRADLE_CACHE=%~dp0.local-gradle"
if exist "%~dp0..\..\work\gradle-home" set "VOCA_GRADLE_CACHE=%~dp0..\..\work\gradle-home"
call gradlew.bat --gradle-user-home "%VOCA_GRADLE_CACHE%" --console=plain :app:assembleDebug
set "VOCA_BUILD_RESULT=%ERRORLEVEL%"
popd
exit /b %VOCA_BUILD_RESULT%
