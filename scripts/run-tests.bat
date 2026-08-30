@echo off
REM ============================================================
REM  PS1Emulator - Test Runner Script
REM  Runs all unit tests and reports results
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ==========================================
echo  PS1Emulator Test Suite
echo ==========================================
echo.

set "PASSED=0"
set "FAILED=0"
set "ERRORS=0"

echo [1/3] Running JVM Unit Tests (src/test/)...
echo --------------------------------------------------
call gradlew.bat test --rerun-tasks --info 2>&1 | findstr /I "Tests PASSED FAILED test(s)"
echo.

echo [2/3] Generating Test Report...
echo --------------------------------------------------
if exist "app\build\reports\tests\test\index.html" (
    echo Test report generated at: app\build\reports\tests\test\index.html
) else (
    echo No test report found. Check build output for errors.
)
echo.

echo [3/3] Summary
echo --------------------------------------------------
echo.
echo Test files written:
echo   src/test/.../cheat/CheatCodeParserTest.java        (26 tests)
echo   src/test/.../core/EmulatorBridgeTest.java          (7 tests)
echo   src/test/.../core/SettingsHelperTest.java          (19 tests)
echo   src/test/.../data/GameEntityTest.java              (10 tests)
echo   src/test/.../input/ControllerMapperTest.java       (12 tests)
echo   src/test/.../input/InputDispatcherTest.java        (7 tests)
echo   src/test/.../input/Ps1ButtonsTest.java             (3 tests)
echo   src/test/.../storage/ArchiveExtractorTest.java     (19 tests)
echo   src/test/.../storage/BiosManagerTest.java          (10 tests)
echo   src/test/.../storage/GameDiscoveryResultTest.java  (12 tests)
echo   src/test/.../storage/ImportResultTest.java         (9 tests)
echo   src/test/.../storage/MemoryCardManagerTest.java    (7 tests)
echo   src/test/.../storage/RomImporterTest.java          (19 tests)
echo   src/test/.../storage/SaveExportManagerTest.java    (5 tests)
echo   src/test/.../storage/SaveImportManagerTest.java    (8 tests)
echo   src/test/.../storage/SaveStateManagerTest.java     (9 tests)
echo   src/test/.../util/ObjectPoolTest.java              (6 tests)
echo   src/test/.../ExampleUnitTest.java                  (1 test)
echo.
echo Total: ~200 test methods across 18 test classes
echo.
echo ==========================================
echo  To run: gradlew.bat test
echo  Report: app\build\reports\tests\test\index.html
echo ==========================================
echo.

endlocal
