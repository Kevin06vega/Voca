@echo off
setlocal
set "VOCA_ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%VOCA_ADB%" (
    echo No se encontro adb en el SDK Android de esta PC.
    echo Activa Enable Host Microphone Access en los controles del emulador.
    pause
    exit /b 1
)
"%VOCA_ADB%" -e emu avd hostmicon
set "VOCA_MIC_RESULT=%ERRORLEVEL%"
if "%VOCA_MIC_RESULT%"=="0" (
    echo Entrada de audio de Windows habilitada en el emulador.
    echo Abre Voca, activa su microfono y di una palabra en ingles.
) else (
    echo Abre un solo emulador de Android Studio y vuelve a ejecutar este archivo.
)
pause
exit /b %VOCA_MIC_RESULT%
