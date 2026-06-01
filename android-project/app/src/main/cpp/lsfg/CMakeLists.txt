cmake_minimum_required(VERSION 3.10)
project(lsfg-integration)

# Find Vulkan
find_package(Vulkan REQUIRED)

# Volk Vulkan loader
set(VOLK_SOURCES
    volk.c
)

# Core framegen library sources
file(GLOB_RECURSE FRAMEGEN_CORE_SOURCES
    "framegen/src/*.cpp"
)

# v3.1 sources
file(GLOB_RECURSE FRAMEGEN_V3_1_SOURCES
    "framegen/v3.1_src/*.cpp"
)

# v3.1p sources
file(GLOB_RECURSE FRAMEGEN_V3_1P_SOURCES
    "framegen/v3.1p_src/*.cpp"
)

# Integration sources
set(LSFG_SOURCES
    lsfg_integration.cpp
    lsfg_jni.cpp
)

add_library(lsfg-integration SHARED
    ${VOLK_SOURCES}
    ${FRAMEGEN_CORE_SOURCES}
    ${FRAMEGEN_V3_1_SOURCES}
    ${FRAMEGEN_V3_1P_SOURCES}
    ${LSFG_SOURCES}
)

target_include_directories(lsfg-integration PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    framegen/include
    framegen/public
    framegen/v3.1_include
    framegen/v3.1p_include
    ${Vulkan_INCLUDE_DIRS}
)

target_link_libraries(lsfg-integration PRIVATE
    Vulkan::Vulkan
    android
    log
)

target_compile_definitions(lsfg-integration PRIVATE
    VK_USE_PLATFORM_ANDROID_KHR
)

target_compile_features(lsfg-integration PUBLIC cxx_std_20)
