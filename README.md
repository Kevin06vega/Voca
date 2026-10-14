# Voca: hogar por voz

Aplicación Android nativa en Kotlin y Jetpack Compose para navegar y controlar una casa simulada mediante diez comandos de voz en inglés. Incluye el modelo `B_aug_1.onnx`, su configuración y el procesamiento de audio en Kotlin. La interfaz es una primera implementación funcional aproximada al prototipo de Figma.

Repositorio: [Kevin06vega/Voca](https://github.com/Kevin06vega/Voca).

## Clonar y continuar el desarrollo

```sh
git clone https://github.com/Kevin06vega/Voca.git
```

Abre la carpeta clonada en Android Studio, configura el SDK de Android y selecciona **Java 21** como Gradle JDK. El modelo y los audios de comprobación ya están incluidos en el código fuente. En la PC original de desarrollo, las rutas locales ya están configuradas.

Para guardar y subir cambios desde la terminal del proyecto:

```sh
git status
git add .
git commit -m "Describe el cambio realizado"
git push
```

También puedes usar **Commit** y **Push** desde Android Studio. `.gitignore` excluye las compilaciones, los instaladores, los cachés y las configuraciones locales. Este repositorio contiene la aplicación Android; el notebook de entrenamiento y el informe se preparan como entregables separados del proyecto académico.

## Abrir y ejecutar

1. En Android Studio, selecciona **File > Open** y abre esta carpeta `Voca`. No necesitas crear otro proyecto.
2. Deja terminar la sincronización de Gradle. El wrapper, el SDK local y un JDK 21 independiente ya están configurados en este equipo. Después de corregir la configuración, cierra y vuelve a abrir el proyecto si el IDE aún muestra Java 25.
3. Selecciona un emulador o un Android conectado y pulsa **Run**.
4. En la bienvenida, utiliza **Continuar con controles táctiles** si no tienes micrófono.
5. Abre **Ayuda** y pulsa **Probar modelo con audios**. Esta función ejecuta los once ejemplos originales del notebook y muestra si coinciden con sus resultados. No necesita micrófono y no modifica dispositivos.

La versión debug contiene estos ejemplos. `Voca.apk`, distribuido junto al proyecto, es el instalador de prueba ya compilado. Es compatible con Android 8.0 o posterior. No es necesario publicar la app en Google Play para probarla.

## Usar la voz

En un teléfono, pulsa el panel del micrófono y concede el permiso cuando Android lo solicite. Habla **en inglés, una palabra a la vez**, dejando una pausa entre comandos. El procesamiento y la inferencia ocurren dentro del teléfono. La app no solicita permiso de internet ni guarda grabaciones.

| Comandos | Resultado |
|---|---|
| up / down / left / right | Mover el borde de selección, incluyendo las opciones de navegación |
| yes | Abrir la opción seleccionada; aceptar una confirmación |
| no | Volver; cancelar una confirmación |
| on / off | Encender o apagar el dispositivo abierto |
| go / stop | Iniciar o detener una rutina desde la pantalla de rutinas |

Los dispositivos son simulados. El comando `stop` detiene la rutina; el botón del micrófono activa o desactiva la escucha. Al salir de la app se pausa la captura, y debe activarse de nuevo al regresar. Si se deniega el permiso, siguen disponibles los controles táctiles.

## Cómo está organizado el código

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Inicio, tema, permiso de micrófono y ciclo de vida |
| `VocaScreens.kt` | Pantallas de Compose y controles visibles |
| `ui/VocaState.kt` | Estado de la casa y reglas compartidas para voz y tacto |
| `VocaViewModel.kt` | Conecta la interfaz con el motor de voz |
| `voice/AppConfig.kt` | Lee y comprueba parámetros y orden de etiquetas |
| `voice/AudioRecorder.kt` | Captura audio mono PCM16 a 16 kHz |
| `voice/AudioPreprocessor.kt` | Convierte audio a log-mel normalizado de 40 x 101 |
| `voice/VoiceClassifier.kt` | Ejecuta ONNX y calcula confianza |
| `voice/VoiceEngine.kt` | Escucha en segundo plano, filtra silencio y limita repeticiones |
| `voice/SampleTester.kt` | Compara los audios originales con las salidas del notebook |

El recorrido es: **audio > espectrograma > normalización > ONNX > comando aceptado > cambio de estado > pantalla**. El ONNX recibe un tensor `[1,1,40,101]`; las once salidas siguen el orden de `app_config.json`. `unknown` y las predicciones de baja confianza no ejecutan acciones.

## Configuración preparada

- Gradle 8.14.3, Android Gradle Plugin 8.11.1.
- Kotlin y plugin Compose 2.2.21, Compose BOM 2025.05.01.
- ONNX Runtime Android 1.31.0.
- SDK de compilación/destino 36, mínimo 26; bytecode Java/Kotlin 17.
- En esta PC, Gradle utiliza Eclipse Temurin Java 21 descargado en `work/toolchains`. Android Studio Rabbit 1 usa Java 25 para su propia interfaz, mientras Voca compila con Java 21.

La carpeta del proyecto abierto contiene las rutas locales de SDK/JDK y el cache de Gradle. El ZIP de código fuente omite esas rutas y el JDK: al abrirlo en otra PC, Android Studio debe detectar el SDK de esa máquina y debes elegir Java 21 como **Gradle JDK**.

Si aparece **Incompatible Gradle JVM version**, entra en **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**. En **Gradle JDK**, usa `GRADLE_LOCAL_JAVA_HOME` en esta PC (apunta al JDK 21 configurado), o selecciona/descarga un JDK 21. Gradle 8.14.3 no puede ejecutarse con Java 25. No hace falta cambiar el modelo ni actualizar los plugins del proyecto.

## Pruebas y entrega

`VERIFICACION.md` registra las comprobaciones realizadas y sus límites. Los once audios comprueban la integración con el notebook; no reemplazan una evaluación completa del dataset ni las pruebas de voces reales.

Para recompilar en esta PC puedes ejecutar `Compilar.bat`. El APK también se genera desde el menú de Android Studio y queda en `app/build/outputs/apk/debug/app-debug.apk`.

Las pruebas incluidas comprueban navegación, confirmaciones, restauración de rutinas, paridad del modelo y el recorrido de la interfaz. Se ejecutan con `gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug` usando un emulador o teléfono conectado. Las pruebas no activan el micrófono.



## Referencias

- Prototipo del equipo: https://www.figma.com/design/zpy0b4Eh9UaTSauve3TsxA
- ONNX Runtime Mobile: https://onnxruntime.ai/docs/tutorials/mobile/
- Jetpack Compose: https://developer.android.com/develop/ui/compose/setup
- Configuración de Java de Android Studio: https://developer.android.com/build/jdks
- Audios de prueba derivados de Speech Commands v0.02, de Pete Warden, bajo CC BY 4.0; exportados por el notebook del equipo.
