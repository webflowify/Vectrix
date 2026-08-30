@echo off
setlocal enabledelayedexpansion

echo.
echo ==========================================
echo  PS1Emulator Test Verification
echo ==========================================
echo.

set PASS=0
set FAIL=0

echo [1/3] Checking source files exist...
echo --------------------------------------------------

if exist "app\src\main\java\com\tansoft\ps1emulator\util\ObjectPool.java" (echo   [PASS] ObjectPool.java & set /a PASS+=1) else (echo   [FAIL] ObjectPool.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\cheat\CheatCodeParser.java" (echo   [PASS] CheatCodeParser.java & set /a PASS+=1) else (echo   [FAIL] CheatCodeParser.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\input\Ps1Buttons.java" (echo   [PASS] Ps1Buttons.java & set /a PASS+=1) else (echo   [FAIL] Ps1Buttons.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\input\InputDispatcher.java" (echo   [PASS] InputDispatcher.java & set /a PASS+=1) else (echo   [FAIL] InputDispatcher.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\input\ControllerMapper.java" (echo   [PASS] ControllerMapper.java & set /a PASS+=1) else (echo   [FAIL] ControllerMapper.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\storage\ImportResult.java" (echo   [PASS] ImportResult.java & set /a PASS+=1) else (echo   [FAIL] ImportResult.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\storage\GameDiscoveryResult.java" (echo   [PASS] GameDiscoveryResult.java & set /a PASS+=1) else (echo   [FAIL] GameDiscoveryResult.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\main\java\com\tansoft\ps1emulator\data\GameEntity.java" (echo   [PASS] GameEntity.java & set /a PASS+=1) else (echo   [FAIL] GameEntity.java NOT FOUND & set /a FAIL+=1)

echo.
echo [2/3] Checking test files exist...
echo --------------------------------------------------

if exist "app\src\test\java\com\tansoft\ps1emulator\cheat\CheatCodeParserTest.java" (echo   [PASS] CheatCodeParserTest.java & set /a PASS+=1) else (echo   [FAIL] CheatCodeParserTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\core\EmulatorBridgeTest.java" (echo   [PASS] EmulatorBridgeTest.java & set /a PASS+=1) else (echo   [FAIL] EmulatorBridgeTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\core\SettingsHelperTest.java" (echo   [PASS] SettingsHelperTest.java & set /a PASS+=1) else (echo   [FAIL] SettingsHelperTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\data\GameEntityTest.java" (echo   [PASS] GameEntityTest.java & set /a PASS+=1) else (echo   [FAIL] GameEntityTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\input\ControllerMapperTest.java" (echo   [PASS] ControllerMapperTest.java & set /a PASS+=1) else (echo   [FAIL] ControllerMapperTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\input\InputDispatcherTest.java" (echo   [PASS] InputDispatcherTest.java & set /a PASS+=1) else (echo   [FAIL] InputDispatcherTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\input\Ps1ButtonsTest.java" (echo   [PASS] Ps1ButtonsTest.java & set /a PASS+=1) else (echo   [FAIL] Ps1ButtonsTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\ArchiveExtractorTest.java" (echo   [PASS] ArchiveExtractorTest.java & set /a PASS+=1) else (echo   [FAIL] ArchiveExtractorTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\BiosManagerTest.java" (echo   [PASS] BiosManagerTest.java & set /a PASS+=1) else (echo   [FAIL] BiosManagerTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\GameDiscoveryResultTest.java" (echo   [PASS] GameDiscoveryResultTest.java & set /a PASS+=1) else (echo   [FAIL] GameDiscoveryResultTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\ImportResultTest.java" (echo   [PASS] ImportResultTest.java & set /a PASS+=1) else (echo   [FAIL] ImportResultTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\MemoryCardManagerTest.java" (echo   [PASS] MemoryCardManagerTest.java & set /a PASS+=1) else (echo   [FAIL] MemoryCardManagerTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\RomImporterTest.java" (echo   [PASS] RomImporterTest.java & set /a PASS+=1) else (echo   [FAIL] RomImporterTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\SaveExportManagerTest.java" (echo   [PASS] SaveExportManagerTest.java & set /a PASS+=1) else (echo   [FAIL] SaveExportManagerTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\SaveImportManagerTest.java" (echo   [PASS] SaveImportManagerTest.java & set /a PASS+=1) else (echo   [FAIL] SaveImportManagerTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\storage\SaveStateManagerTest.java" (echo   [PASS] SaveStateManagerTest.java & set /a PASS+=1) else (echo   [FAIL] SaveStateManagerTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\util\ObjectPoolTest.java" (echo   [PASS] ObjectPoolTest.java & set /a PASS+=1) else (echo   [FAIL] ObjectPoolTest.java NOT FOUND & set /a FAIL+=1)
if exist "app\src\test\java\com\tansoft\ps1emulator\ExampleUnitTest.java" (echo   [PASS] ExampleUnitTest.java & set /a PASS+=1) else (echo   [FAIL] ExampleUnitTest.java NOT FOUND & set /a FAIL+=1)

echo.
echo [3/3] Checking build configuration...
echo --------------------------------------------------

findstr /C:"mockito" gradle\libs.versions.toml >nul 2>&1 && (echo   [PASS] Mockito in version catalog & set /a PASS+=1) || (echo   [FAIL] Mockito missing & set /a FAIL+=1)
findstr /C:"robolectric" gradle\libs.versions.toml >nul 2>&1 && (echo   [PASS] Robolectric in version catalog & set /a PASS+=1) || (echo   [FAIL] Robolectric missing & set /a FAIL+=1)
findstr /C:"test-core" gradle\libs.versions.toml >nul 2>&1 && (echo   [PASS] AndroidX Test Core in version catalog & set /a PASS+=1) || (echo   [FAIL] AndroidX Test Core missing & set /a FAIL+=1)
findstr /C:"testImplementation libs.mockito.core" app\build.gradle >nul 2>&1 && (echo   [PASS] Mockito in build.gradle & set /a PASS+=1) || (echo   [FAIL] Mockito missing from build.gradle & set /a FAIL+=1)
findstr /C:"testImplementation libs.robolectric" app\build.gradle >nul 2>&1 && (echo   [PASS] Robolectric in build.gradle & set /a PASS+=1) || (echo   [FAIL] Robolectric missing from build.gradle & set /a FAIL+=1)
findstr /C:"unitTests.includeAndroidResources" app\build.gradle >nul 2>&1 && (echo   [PASS] Robolectric config enabled & set /a PASS+=1) || (echo   [FAIL] Robolectric config missing & set /a FAIL+=1)
if exist "scripts\run-tests.bat" (echo   [PASS] Test runner script exists & set /a PASS+=1) else (echo   [FAIL] Test runner script missing & set /a FAIL+=1)
if exist "test-standalone\StandaloneTestRunner.java" (echo   [PASS] Standalone test runner exists & set /a PASS+=1) else (echo   [FAIL] Standalone test runner missing & set /a FAIL+=1)

echo.
echo ==========================================
echo  Results: %PASS% passed, %FAIL% failed
echo ==========================================
echo.

if %FAIL% GTR 0 (
    echo Run 'gradlew.bat test' to verify compilation
) else (
    echo All checks passed!
)

echo.
endlocal
