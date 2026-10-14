@echo off
"C:\\Users\\sdc99\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\sdc99\\StudioProjects\\Yougram\\cpp\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=31" ^
  "-DANDROID_PLATFORM=android-31" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=C:\\Users\\sdc99\\AppData\\Local\\Android\\Sdk\\ndk\\28.2.13676358" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\sdc99\\AppData\\Local\\Android\\Sdk\\ndk\\28.2.13676358" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\sdc99\\AppData\\Local\\Android\\Sdk\\ndk\\28.2.13676358\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\sdc99\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_CXX_FLAGS=-std=c++17 -frtti -fexceptions" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\sdc99\\StudioProjects\\Yougram\\cpp\\build\\intermediates\\cxx\\Debug\\6ya4a4v1\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\sdc99\\StudioProjects\\Yougram\\cpp\\build\\intermediates\\cxx\\Debug\\6ya4a4v1\\obj\\arm64-v8a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BC:\\Users\\sdc99\\StudioProjects\\Yougram\\cpp\\.cxx\\Debug\\6ya4a4v1\\arm64-v8a" ^
  -GNinja ^
  "-DANDROID_STL=c++_static" ^
  "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
