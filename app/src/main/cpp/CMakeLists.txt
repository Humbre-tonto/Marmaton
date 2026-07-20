cmake_minimum_required(VERSION 3.22.1)
project(marmaton_llm CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable llama.cpp features we don't need
set(LLAMA_CURL OFF CACHE BOOL "llama: disable curl" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "llama: disable examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "llama: disable tests" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "llama: disable openmp" FORCE)

include(FetchContent)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG b4400
)

# Pull down the content
FetchContent_MakeAvailable(llama)

# Define our shared JNI library
add_library(marmaton_llm SHARED llama-jni.cpp)

# Link JNI library with llama, ggml and Android log
find_library(log-lib log)
target_link_libraries(marmaton_llm PRIVATE llama ggml ${log-lib})
