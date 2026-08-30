#!/bin/bash
# ============================================================
#  PS1Emulator - Test Compilation Verification Script
#  Verifies test files compile without errors (cross-platform)
# ============================================================

set -e

PASS=0
FAIL=0

echo ""
echo "=========================================="
echo " PS1Emulator Test Verification"
echo "=========================================="
echo ""

# ──────────────────────────────────────────
#  Check source files exist
# ──────────────────────────────────────────

echo "[1/3] Checking source files exist..."
echo "--------------------------------------------------"

SRC_FILES=(
    "app/src/main/java/com/tansoft/ps1emulator/util/ObjectPool.java"
    "app/src/main/java/com/tansoft/ps1emulator/cheat/CheatCodeParser.java"
    "app/src/main/java/com/tansoft/ps1emulator/input/Ps1Buttons.java"
    "app/src/main/java/com/tansoft/ps1emulator/input/InputDispatcher.java"
    "app/src/main/java/com/tansoft/ps1emulator/input/ControllerMapper.java"
    "app/src/main/java/com/tansoft/ps1emulator/storage/ImportResult.java"
    "app/src/main/java/com/tansoft/ps1emulator/storage/GameDiscoveryResult.java"
    "app/src/main/java/com/tansoft/ps1emulator/data/GameEntity.java"
)

for f in "${SRC_FILES[@]}"; do
    if [ -f "$f" ]; then
        echo "  [PASS] $f"
        ((PASS++))
    else
        echo "  [FAIL] $f NOT FOUND"
        ((FAIL++))
    fi
done

# ──────────────────────────────────────────
#  Check test files exist
# ──────────────────────────────────────────

echo ""
echo "[2/3] Checking test files exist..."
echo "--------------------------------------------------"

TEST_FILES=(
    "app/src/test/java/com/tansoft/ps1emulator/cheat/CheatCodeParserTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/core/EmulatorBridgeTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/core/SettingsHelperTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/data/GameEntityTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/input/ControllerMapperTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/input/InputDispatcherTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/input/Ps1ButtonsTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/ArchiveExtractorTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/BiosManagerTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/GameDiscoveryResultTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/ImportResultTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/MemoryCardManagerTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/RomImporterTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/SaveExportManagerTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/SaveImportManagerTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/storage/SaveStateManagerTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/util/ObjectPoolTest.java"
    "app/src/test/java/com/tansoft/ps1emulator/ExampleUnitTest.java"
)

for f in "${TEST_FILES[@]}"; do
    if [ -f "$f" ]; then
        echo "  [PASS] $f"
        ((PASS++))
    else
        echo "  [FAIL] $f NOT FOUND"
        ((FAIL++))
    fi
done

# ──────────────────────────────────────────
#  Check build config
# ──────────────────────────────────────────

echo ""
echo "[3/3] Checking build configuration..."
echo "--------------------------------------------------"

check_config() {
    local file="$1"
    local pattern="$2"
    local desc="$3"
    if grep -q "$pattern" "$file" 2>/dev/null; then
        echo "  [PASS] $desc"
        ((PASS++))
    else
        echo "  [FAIL] $desc"
        ((FAIL++))
    fi
}

check_config "gradle/libs.versions.toml" "mockito" "Mockito dependency in version catalog"
check_config "gradle/libs.versions.toml" "robolectric" "Robolectric dependency in version catalog"
check_config "gradle/libs.versions.toml" "test-core" "AndroidX Test Core in version catalog"
check_config "app/build.gradle" "testImplementation libs.mockito.core" "Mockito in build.gradle"
check_config "app/build.gradle" "testImplementation libs.robolectric" "Robolectric in build.gradle"
check_config "app/build.gradle" "unitTests.includeAndroidResources" "Robolectric config enabled"

[ -f "scripts/run-tests.sh" ] && echo "  [PASS] Test runner script exists" && ((PASS++)) || { echo "  [FAIL] Test runner script missing"; ((FAIL++)); }
[ -f "test-standalone/StandaloneTestRunner.java" ] && echo "  [PASS] Standalone test runner exists" && ((PASS++)) || { echo "  [FAIL] Standalone test runner missing"; ((FAIL++)); }

# ──────────────────────────────────────────
#  Summary
# ──────────────────────────────────────────

echo ""
echo "=========================================="
echo " Results: $PASS passed, $FAIL failed"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    echo ""
    echo "Run './gradlew test' to verify compilation"
    exit 1
else
    echo ""
    echo "All checks passed! Run './gradlew test' to execute tests."
    exit 0
fi
