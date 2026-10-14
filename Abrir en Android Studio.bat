@echo off
setlocal
set "VOCA_STUDIO=C:\Program Files\Android\Android Studio1\bin\studio64.exe"
if not exist "%VOCA_STUDIO%" set "VOCA_STUDIO=C:\Program Files\Android\Android Studio\bin\studio64.exe"
if not exist "%VOCA_STUDIO%" (
    echo Abre Android Studio y usa File - Open para seleccionar esta carpeta Voca.
    exit /b 1
)
start "" "%VOCA_STUDIO%" "%~dp0."
