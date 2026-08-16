cmake_minimum_required(VERSION 3.22.1)
project("pargelium_dsp")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -ftree-vectorize")

add_library(pargelium_dsp SHARED native-dsp.cpp)
target_link_libraries(pargelium_dsp log)