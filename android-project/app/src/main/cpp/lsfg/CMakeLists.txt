cmake_minimum_required(VERSION 3.10)
project(lsfg-integration)

# Find Vulkan
find_package(Vulkan REQUIRED)

# Core framegen library sources
set(FRAMEGEN_SOURCES
    framegen/v3.1_src/context.cpp
    framegen/v3.1_src/lsfg.cpp
    framegen/v3.1p_src/context.cpp
    framegen/v3.1p_src/lsfg.cpp
)

# Integration sources
set(LSFG_SOURCES
    lsfg_integration.cpp
    lsfg_jni.cpp
)

add_library(lsfg-integration SHARED
    ${FRAMEGEN_SOURCES}
    ${LSFG_SOURCES}
)

target_include_directories(lsfg-integration PRIVATE
    framegen/include
    ${Vulkan_INCLUDE_DIRS}
)

target_link_libraries(lsfg-integration PRIVATE
    Vulkan::Vulkan
    android
    log
)

target_compile_features(lsfg-integration PUBLIC cxx_std_20)
