#!/bin/sh
kotlinc composeApp/src/jvmMain/kotlin/org/bytefred/ktoy/MiniSim.kt -include-runtime -d build/minisim.jar && java -jar build/minisim.jar "$@"
