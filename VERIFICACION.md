# Verificación de Voca

El modelo y el preprocesamiento Kotlin coinciden con los vectores originales del notebook dentro de las tolerancias indicadas. Se verificaron once ejemplos: uno por comando y uno de la clase de rechazo `unknown`.

## Procedencia y método

- Modelo recibido: `B_aug_1.onnx`, 549.086 bytes. Se ejecutó únicamente el archivo ONNX; no se cargaron pesos `.pt` ni se entrenó ningún modelo.
- Referencias: los 34 archivos de `test_vectors` de la carpeta de Drive compartida por el usuario, exportados por la celda 41 del notebook. Incluyen waveform, log-mel, log-mel normalizado y logits de referencia en `vectors.json`.
- Cada waveform contiene 16.000 float32 little-endian. Cada espectrograma contiene 4.040 valores, ordenados por banda mel y después tiempo: `[40,101]`.
- Contrato: mono/16 kHz/1 segundo; FFT 512, Hann periódica de 400 muestras centrada en la FFT, salto 160, reflexión de 256 muestras, potencia 2, 40 filtros HTK entre 20 y 7.600 Hz, `log(mel + 1e-6)` y normalización con media -6.44285249710083/desviación 4.832808017730713.
- Se comparó un frontend NumPy independiente con los tensores del notebook. También se compiló y ejecutó el frontend real `AudioPreprocessor.kt` en JVM y se compararon sus tensores con las mismas referencias.
- ONNX Runtime Python 1.24.3 en CPU recibió tanto los tensores originales como los generados por NumPy y Kotlin. NumPy 2.5.4, Python 3.12.14. La app usa ONNX Android 1.31.0; su validación se realiza por separado en Android.

## Resultados ejecutados en esta PC

| Ejemplo | Diferencia máxima mel normalizado Kotlin | Diferencia máxima logits ONNX con frontend Kotlin | Predicción |
|---|---:|---:|---|
| yes | 1.12056732e-05 | 8.58306885e-06 | yes |
| no | 4.30345535e-05 | 7.62939453e-06 | no |
| up | 1.12652779e-04 | 2.28881836e-05 | up |
| down | 2.61068344e-05 | 1.14440918e-05 | down |
| left | 1.97291374e-05 | 1.04904175e-05 | left |
| right | 8.70227814e-05 | 3.43322754e-05 | right |
| on | 2.20537186e-05 | 1.00135803e-05 | on |
| off | 5.49554825e-05 | 1.43051147e-05 | off |
| stop | 2.40206718e-05 | 1.04904175e-05 | stop |
| go | 2.20537186e-05 | 1.07288361e-05 | go |
| unknown | 1.19149685e-04 | 3.35797668e-05 | unknown |

- Diferencia máxima del log-mel Kotlin: **5.76019287e-04**.
- Diferencia máxima del log-mel normalizado Kotlin: **1.19149685e-04**.
- Diferencia máxima de logits con features Kotlin: **3.43322754e-05**.
- Con los tensores originales, los logits ONNX coincidieron exactamente con los de `vectors.json`: diferencia máxima **0.0** en este entorno.
- Diferencia máxima de logits con frontend NumPy: **3.62396240e-05**.
- Coincidencia de clase elegida: **11/11**, incluido `unknown`.
- Las pequeñas diferencias de los frontends son compatibles con el redondeo de float32 y las implementaciones de FFT/filtros. No se exige igualdad bit a bit entre frontends; las tolerancias son absolutas y se comprueban también los logits y la clase final.

## Comprobación adicional con el dataset

Se tomaron los primeros archivos de cada uno de los diez comandos dentro de `testing_list.txt` del ZIP Speech Commands v0.02 recibido. El frontend NumPy y el ONNX produjeron el comando esperado en **10/10**. Esta comprobación es pequeña y coincide con la elección de ejemplos por comando del notebook; no constituye una evaluación independiente de generalización.

**Estos resultados verifican integración y paridad numérica. No representan accuracy sobre todo el dataset ni garantizan reconocimiento de voces nuevas, micrófonos reales o ambientes ruidosos.** La accuracy/F1 global registrada en el notebook no fue recalculada durante esta revisión.

## Prueba Android incluida

`app/src/androidTest/java/com/voca/voice/ModelParityTest.kt` utiliza AndroidJUnit4, el frontend real y el modelo local. Carga los once vectores desde `test_vectors/` en los assets de la variante debug. No activa el micrófono ni requiere tocar la interfaz.

- Comprueba tamaños, valores finitos, orden completo de clases, log-mel y normalización.
- Tolerancia máxima absoluta para tensores y logits: `1e-3`. Tolerancia para confianza softmax: `1e-4`.
- Comprueba la misma predicción que las referencias y que `unknown` no acepte una acción.
- Ejecutar en un emulador o Android conectado: `gradlew.bat connectedDebugAndroidTest`.

**Estado: pruebas ejecutadas y superadas en el emulador Medium_Phone_API_36.1, Android 16 (API 36.1).** La prueba de paridad ejecutó el frontend Kotlin y ONNX Runtime Android 1.31.0 sobre los once audios originales; pasó todas las tolerancias de tensores, logits, confianza y rechazo de unknown. La segunda prueba recorrió bienvenida, hogar, dispositivo, confirmación, rutinas y ayuda, y ejecutó la autoprueba sin micrófono. También pasaron cinco pruebas unitarias del estado y Android Lint sin errores. Los resultados XML y el resumen están en `pruebas/`. Las advertencias de Lint se documentan abajo. Sigue pendiente probar la captura y los diez comandos con el micrófono de un teléfono físico; el emulador no sustituye esa comprobación.

## Identidad de los artefactos

- SHA-256 del modelo: `a2ba7913b4ceb72e8ea5eb840a5c7399da64c8fe44ff7968b5451e1335b85f78`.
- SHA-256 de `vectors.json` original: `a9c26a0d980628355af143abf62f663e167c14551e683de163db9d4862ed850f`.

Los scripts de verificación y los resultados JSON completos quedan en `work/validate_onnx_samples.py`, `work/verify_original_vectors.py` y `work/original_vectors_validation.json` del espacio de trabajo. No forman parte de la app distribuida.


## Compilación e interfaz

El APK debug se compiló inicialmente con el wrapper de Gradle y Java 21 de Android Studio de esta PC. AndroidX Test usa runner 1.7.0, JUnit 1.3.0 y Espresso 3.7.0, compatible con Android 16. La interfaz es aproximada al Figma por autorización del usuario; comparte las reglas de acciones entre controles táctiles y comandos reconocidos. Se permite continuar con controles táctiles cuando no hay micrófono.

Android Lint pasó sin errores. Se conservaron las versiones de dependencias comprobadas en la compilación; el analizador avisa de versiones más nuevas y dos recomendaciones cosméticas del icono del lanzador. El proyecto incluye reglas de respaldo y no guarda grabaciones.

## Corrección después de actualizar Android Studio (14 de octubre de 2026)

Android Studio Rabbit 1 incluyó Java 25.0.3 y lo seleccionó para Gradle, produciendo `Incompatible Gradle JVM version` con Gradle 8.14.3. La instalación anterior de Java había quedado incompleta. Se descargó Eclipse Temurin JDK 21.0.12.1+1 desde el API oficial de Adoptium a `work/toolchains`, comprobando el SHA-256 del ZIP contra el publicado por el proveedor.

Se fijó `GRADLE_LOCAL_JAVA_HOME` a ese JDK en la configuración local del proyecto y se corrigieron los archivos para abrir Android Studio y compilar. `gradlew.bat --version` confirmó Launcher JVM y Daemon JVM 21.0.12.1. Se ejecutó `Compilar.bat` y `:app:assembleDebug` terminó con **BUILD SUCCESSFUL en 26 segundos**, con 37 tareas (1 ejecutada, 36 al día). La sincronización visual del IDE debe reintentarse al volver a abrir el proyecto; esta comprobación corresponde a la compilación por terminal. No cambió el código de la app ni el modelo.

## Prueba manual de voz en el emulador (14 de octubre de 2026)

El usuario conectó el micrófono de su iPhone a Windows mediante WO Mic en la red local, con la PC conectada por Ethernet. Se comprobó que WO Mic era la entrada predeterminada de Windows y que Voca tenía permiso de grabación. Se habilitó la entrada del host en el emulador Pixel_8_Pro con `adb emu avd hostmicon`; el usuario confirmó movimiento del indicador de audio de Voca.

El usuario reportó funcionamiento correcto de **up, down, left, right y go** con su voz. Esta comprobación manual preliminar no incluye un recuento de intentos ni constituye una medición de accuracy. Quedan por confirmar **yes, no, on, off y stop**, repeticiones, silencio, ruido y el comportamiento con el micrófono de un Android físico. El resumen de pruebas conserva la procedencia manual de estos resultados.
