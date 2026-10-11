cmake_minimum_required(VERSION 3.22.1)
project("pargelium-dsp" C CXX)

include_directories(
        jamesdsp
        jamesdsp/jdsp
        jamesdsp/jdsp/Effects
        jamesdsp/jdsp/Effects/eel2/numericSys
        jamesdsp/jdsp/generalDSP
)

file(GLOB_RECURSE JDSP_SOURCES
        "jamesdsp/*.c"
        "jamesdsp/*.cpp"
        "jamesdsp/*.cc"
)

list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/y\\.tab\\.c$")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/s_str\\.c$")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/cpthread\\.c$")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/fft\\.c$")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/ns-eel.*")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/nseel.*")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/eel2/asm-.*")
list(FILTER JDSP_SOURCES EXCLUDE REGEX ".*/liveprogWrapper\\.c$")

add_library(jamesdsp-jni SHARED
        jamesdsp-bridge.cpp
        liveprog_stub.c
        ${JDSP_SOURCES}
)

target_compile_definitions(jamesdsp-jni PRIVATE
        _GNU_SOURCE
        _DEFAULT_SOURCE
        _POSIX_C_SOURCE=200809L
)

target_compile_options(jamesdsp-jni PRIVATE
        -O3
        -ffast-math
        -funroll-loops
        -Wno-error=incompatible-function-pointer-types
        -Wno-incompatible-function-pointer-types
        -Wno-error=implicit-function-declaration
        -Wno-implicit-function-declaration
        -Wno-incompatible-pointer-types
        -Wno-incompatible-pointer-types-discards-qualifiers
        -Wno-visibility
        -Wno-unused-value
        -Wno-return-type
)

target_link_libraries(jamesdsp-jni
        log
        m
        atomic
)