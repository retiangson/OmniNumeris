cmake_minimum_required(VERSION 3.22)
project(omninumeris_inference_spike LANGUAGES C CXX)

include(FetchContent)
set(LLAMA_ARCHIVE "https://codeload.github.com/ggml-org/llama.cpp/tar.gz/a6d1a74dbebce3a16f4f2ceddbf38c21a08d8674"
    CACHE STRING "Pinned llama.cpp source archive URL or local path")

# Keep measurements representative even though the APK is debuggable for staging.
set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "" FORCE)
set(CMAKE_JOB_POOLS compile_pool=3)
set(CMAKE_JOB_POOL_COMPILE compile_pool)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMIT a6d1a74dbebce3a16f4f2ceddbf38c21a08d8674 CACHE STRING "" FORCE)
set(CMAKE_DISABLE_FIND_PACKAGE_Git TRUE)
set(GIT_EXECUTABLE "" CACHE FILEPATH "" FORCE)

FetchContent_Declare(llama
    URL "${LLAMA_ARCHIVE}"
    URL_HASH SHA256=5f2e8db8f5ddac1582bc702c0c22b59f1795a7e6de8387c3db22e5840725665b
    TLS_VERIFY TRUE)
FetchContent_MakeAvailable(llama)

add_library(omninumeris_inference_spike SHARED inference_bridge.cpp)
target_compile_features(omninumeris_inference_spike PRIVATE cxx_std_17)
target_link_libraries(omninumeris_inference_spike PRIVATE llama)
