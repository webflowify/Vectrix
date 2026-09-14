package test;

import com.tansoft.ps1emulator.util.ObjectPool;
import com.tansoft.ps1emulator.cheat.CheatCodeParser;
import com.tansoft.ps1emulator.input.Ps1Buttons;
import com.tansoft.ps1emulator.input.InputDispatcher;
import com.tansoft.ps1emulator.storage.GameDiscoveryResult;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Standalone Java test runner for PS1Emulator pure Java classes.
 * No Android SDK required. Tests only pure Java logic.
 *
 * Compile: javac -encoding UTF-8 -d out -sourcepath app\src\main\java test\StandaloneTestRunner.java
 * Run:     java -cp out test.StandaloneTestRunner
 */
public class StandaloneTestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    private static void test(String name, Runnable r) {
        try {
            r.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            String msg = "  FAIL  " + name + "  ->  " + t.getMessage();
            System.out.println(msg);
            failures.add(msg);
        }
    }

    private static void check(String msg, boolean condition) {
        if (!condition) throw new AssertionError("Check failed: " + msg);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }

    private static void assertThrows(Class<? extends Throwable> cls, Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected " + cls.getSimpleName() + " but none thrown");
        } catch (Throwable t) {
            if (!cls.isInstance(t))
                throw new AssertionError("Expected " + cls.getSimpleName() + " but got " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // ══════════════════════════════════════════════════
    // ObjectPool
    // ══════════════════════════════════════════════════

    static void testObjectPool() {
        System.out.println("\n-- ObjectPool --");

        test("acquire from empty pool creates new instance", () -> {
            ObjectPool<StringBuilder> pool = new ObjectPool<StringBuilder>(4) {
                @Override protected StringBuilder createInstance() { return new StringBuilder(); }
            };
            StringBuilder sb = pool.acquire();
            check("should not be null", sb != null);
            check("should be empty", sb.length() == 0);
        });

        test("release and acquire returns same object", () -> {
            ObjectPool<StringBuilder> pool = new ObjectPool<StringBuilder>(4) {
                @Override protected StringBuilder createInstance() { return new StringBuilder(); }
            };
            StringBuilder original = pool.acquire();
            original.append("hello");
            pool.release(original);
            StringBuilder reused = pool.acquire();
            assertEquals(original, reused);
            assertEquals("hello", reused.toString());
        });

        test("LIFO order - last released is first acquired", () -> {
            ObjectPool<StringBuilder> pool = new ObjectPool<StringBuilder>(4) {
                @Override protected StringBuilder createInstance() { return new StringBuilder(); }
            };
            StringBuilder a = pool.acquire(); a.append("A");
            StringBuilder b = pool.acquire(); b.append("B");
            pool.release(a);
            pool.release(b);
            StringBuilder first = pool.acquire();
            assertEquals("B", first.toString());
        });

        test("pool exhaustion creates new instance", () -> {
            ObjectPool<StringBuilder> pool = new ObjectPool<StringBuilder>(2) {
                @Override protected StringBuilder createInstance() { return new StringBuilder(); }
            };
            StringBuilder a = pool.acquire();
            StringBuilder b = pool.acquire();
            StringBuilder c = pool.acquire();
            check("should not be null", c != null);
            check("should be distinct", c != a && c != b);
        });

        test("release beyond capacity is silently ignored", () -> {
            ObjectPool<StringBuilder> pool = new ObjectPool<StringBuilder>(2) {
                @Override protected StringBuilder createInstance() { return new StringBuilder(); }
            };
            StringBuilder a = pool.acquire();
            StringBuilder b = pool.acquire();
            StringBuilder c = pool.acquire();
            pool.release(a);
            pool.release(b);
            pool.release(c);
        });

        test("acquire after release reuses object", () -> {
            ObjectPool<int[]> pool = new ObjectPool<int[]>(8) {
                @Override protected int[] createInstance() { return new int[1]; }
            };
            int[] arr = pool.acquire();
            arr[0] = 42;
            pool.release(arr);
            int[] reused = pool.acquire();
            assertEquals(42, reused[0]);
        });
    }

    // ══════════════════════════════════════════════════
    // CheatCodeParser
    // ══════════════════════════════════════════════════

    static void testCheatCodeParser() {
        System.out.println("\n-- CheatCodeParser --");

        test("normalize single valid line", () -> {
            assertEquals("80100000 1234", CheatCodeParser.normalize("80100000 1234"));
        });

        test("normalize trims whitespace", () -> {
            assertEquals("80100000 1234", CheatCodeParser.normalize("  80100000  1234  "));
        });

        test("normalize uppercases lowercase hex", () -> {
            assertEquals("80100000 ABCD", CheatCodeParser.normalize("80100000 abcd"));
        });

        test("normalize multiple lines", () -> {
            assertEquals("80100000 1234\n80100004 5678", CheatCodeParser.normalize("80100000 1234\n80100004 5678"));
        });

        test("normalize skips blank lines", () -> {
            assertEquals("80100000 1234\n80100004 5678", CheatCodeParser.normalize("80100000 1234\n\n\n80100004 5678"));
        });

        test("normalize handles Windows line endings", () -> {
            assertEquals("80100000 1234\n80100004 5678", CheatCodeParser.normalize("80100000 1234\r\n80100004 5678"));
        });

        test("isValid returns true for valid code", () -> {
            check("should be valid", CheatCodeParser.isValid("80100000 1234"));
        });

        test("isValid returns false for null", () -> {
            check("null should be invalid", !CheatCodeParser.isValid(null));
        });

        test("isValid returns false for empty string", () -> {
            check("empty should be invalid", !CheatCodeParser.isValid(""));
        });

        test("isValid returns false for invalid hex", () -> {
            check("invalid hex", !CheatCodeParser.isValid("ZZZZZZZZ 1234"));
        });

        test("isValid returns false for address too low", () -> {
            check("low address", !CheatCodeParser.isValid("00000000 1234"));
        });

        test("isValid returns false for wrong token count", () -> {
            check("wrong tokens", !CheatCodeParser.isValid("80100000"));
        });

        test("isValid returns false for address too short", () -> {
            check("short address", !CheatCodeParser.isValid("8010000 1234"));
        });

        test("isValid returns false for value too short", () -> {
            check("short value", !CheatCodeParser.isValid("80100000 123"));
        });

        test("normalize throws on null", () -> {
            assertThrows(IllegalArgumentException.class, () -> CheatCodeParser.normalize(null));
        });

        test("normalize throws on empty input", () -> {
            assertThrows(IllegalArgumentException.class, () -> CheatCodeParser.normalize(""));
        });

        test("normalize throws on address below minimum", () -> {
            assertThrows(IllegalArgumentException.class, () -> CheatCodeParser.normalize("0FFFFFFF 1234"));
        });

        test("normalize throws on too many tokens", () -> {
            assertThrows(IllegalArgumentException.class, () -> CheatCodeParser.normalize("80100000 1234 5678"));
        });

        test("normalize throws on invalid hex chars", () -> {
            assertThrows(IllegalArgumentException.class, () -> CheatCodeParser.normalize("8010000G 1234"));
        });

        test("normalize handles max address 0xFFFFFFFF", () -> {
            assertEquals("FFFFFFFF FFFF", CheatCodeParser.normalize("FFFFFFFF FFFF"));
        });

        test("normalize handles min address 0x10000000", () -> {
            assertEquals("10000000 0000", CheatCodeParser.normalize("10000000 0000"));
        });
    }

    // ══════════════════════════════════════════════════
    // Ps1Buttons
    // ══════════════════════════════════════════════════

    static void testPs1Buttons() {
        System.out.println("\n-- Ps1Buttons --");

        test("all 16 buttons are unique powers of 2", () -> {
            int[] buttons = {
                Ps1Buttons.BUTTON_SELECT, Ps1Buttons.BUTTON_L3, Ps1Buttons.BUTTON_R3,
                Ps1Buttons.BUTTON_START, Ps1Buttons.BUTTON_UP, Ps1Buttons.BUTTON_RIGHT,
                Ps1Buttons.BUTTON_DOWN, Ps1Buttons.BUTTON_LEFT, Ps1Buttons.BUTTON_L2,
                Ps1Buttons.BUTTON_R2, Ps1Buttons.BUTTON_L1, Ps1Buttons.BUTTON_R1,
                Ps1Buttons.BUTTON_TRIANGLE, Ps1Buttons.BUTTON_CIRCLE,
                Ps1Buttons.BUTTON_CROSS, Ps1Buttons.BUTTON_SQUARE
            };
            Set<Integer> seen = new HashSet<>();
            for (int b : buttons) {
                check("0x" + Integer.toHexString(b) + " not unique", seen.add(b));
                check("0x" + Integer.toHexString(b) + " not power of 2", Integer.bitCount(b) == 1);
            }
        });

        test("all 16 buttons fit in 16 bits (0xFFFF)", () -> {
            int all = Ps1Buttons.BUTTON_SELECT | Ps1Buttons.BUTTON_L3 | Ps1Buttons.BUTTON_R3 |
                      Ps1Buttons.BUTTON_START | Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT |
                      Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT | Ps1Buttons.BUTTON_L2 |
                      Ps1Buttons.BUTTON_R2 | Ps1Buttons.BUTTON_L1 | Ps1Buttons.BUTTON_R1 |
                      Ps1Buttons.BUTTON_TRIANGLE | Ps1Buttons.BUTTON_CIRCLE |
                      Ps1Buttons.BUTTON_CROSS | Ps1Buttons.BUTTON_SQUARE;
            check("combined mask", all == 0xFFFF);
        });

        test("button constants match expected bit positions", () -> {
            assertEquals(1, Ps1Buttons.BUTTON_SELECT);
            assertEquals(2, Ps1Buttons.BUTTON_L3);
            assertEquals(4, Ps1Buttons.BUTTON_R3);
            assertEquals(8, Ps1Buttons.BUTTON_START);
            assertEquals(0x10, Ps1Buttons.BUTTON_UP);
            assertEquals(0x20, Ps1Buttons.BUTTON_RIGHT);
            assertEquals(0x40, Ps1Buttons.BUTTON_DOWN);
            assertEquals(0x80, Ps1Buttons.BUTTON_LEFT);
            assertEquals(0x100, Ps1Buttons.BUTTON_L2);
            assertEquals(0x200, Ps1Buttons.BUTTON_R2);
            assertEquals(0x400, Ps1Buttons.BUTTON_L1);
            assertEquals(0x800, Ps1Buttons.BUTTON_R1);
            assertEquals(0x1000, Ps1Buttons.BUTTON_TRIANGLE);
            assertEquals(0x2000, Ps1Buttons.BUTTON_CIRCLE);
            assertEquals(0x4000, Ps1Buttons.BUTTON_CROSS);
            assertEquals(0x8000, Ps1Buttons.BUTTON_SQUARE);
        });
    }

    // ══════════════════════════════════════════════════
    // InputDispatcher
    // ══════════════════════════════════════════════════

    static void testInputDispatcher() {
        System.out.println("\n-- InputDispatcher --");

        test("singleton returns same instance", () -> {
            InputDispatcher a = InputDispatcher.getInstance();
            InputDispatcher b = InputDispatcher.getInstance();
            check("same instance", a == b);
        });

        test("initial state is all zeros", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            d.setVirtualButtons(0);
            d.setPhysicalButtons(0);
            d.setAnalog(0, 0);
            d.setAnalogRight(0, 0);
            assertEquals(0, d.getVirtualButtons());
            assertEquals(0, d.getPhysicalButtons());
            assertEquals(0f, d.getAnalogX());
            assertEquals(0f, d.getAnalogY());
            assertEquals(0f, d.getRightAnalogX());
            assertEquals(0f, d.getRightAnalogY());
        });

        test("setVirtualButtons stores value", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            d.setVirtualButtons(0x00FF);
            assertEquals(0x00FF, d.getVirtualButtons());
            d.setVirtualButtons(0);
        });

        test("setPhysicalButtons stores value", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            d.setPhysicalButtons(0xFF00);
            assertEquals(0xFF00, d.getPhysicalButtons());
            d.setPhysicalButtons(0);
        });

        test("setAnalog stores values", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            d.setAnalog(0.5f, -0.75f);
            check("analogX", Math.abs(d.getAnalogX() - 0.5f) < 0.001f);
            check("analogY", Math.abs(d.getAnalogY() + 0.75f) < 0.001f);
            d.setAnalog(0, 0);
        });

        test("setAnalogRight stores values", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            d.setAnalogRight(-1.0f, 1.0f);
            check("rightAnalogX", Math.abs(d.getRightAnalogX() + 1.0f) < 0.001f);
            check("rightAnalogY", Math.abs(d.getRightAnalogY() - 1.0f) < 0.001f);
            d.setAnalogRight(0, 0);
        });

        test("buttons combined via bitwise OR", () -> {
            InputDispatcher d = InputDispatcher.getInstance();
            int mask = Ps1Buttons.BUTTON_CROSS | Ps1Buttons.BUTTON_START;
            d.setVirtualButtons(mask);
            assertEquals(mask, d.getVirtualButtons());
            d.setVirtualButtons(0);
        });
    }

    // ══════════════════════════════════════════════════
    // GameDiscoveryResult
    // ══════════════════════════════════════════════════

    static void testGameDiscoveryResult() {
        System.out.println("\n-- GameDiscoveryResult --");

        test("isoFound creates FOUND_ISO", () -> {
            File iso = new File("test.iso");
            GameDiscoveryResult r = GameDiscoveryResult.isoFound(iso);
            assertEquals(GameDiscoveryResult.Status.FOUND_ISO, r.status);
            assertEquals(iso, r.primaryFile);
            check("playable", r.isPlayable());
        });

        test("chdFound creates FOUND_CHD", () -> {
            GameDiscoveryResult r = GameDiscoveryResult.chdFound(new File("test.chd"));
            assertEquals(GameDiscoveryResult.Status.FOUND_CHD, r.status);
            check("playable", r.isPlayable());
        });

        test("executableFound creates FOUND_EXECUTABLE", () -> {
            GameDiscoveryResult r = GameDiscoveryResult.executableFound(new File("game.exe"));
            assertEquals(GameDiscoveryResult.Status.FOUND_EXECUTABLE, r.status);
            check("playable", r.isPlayable());
        });

        test("noGameFound creates NO_GAME_FOUND", () -> {
            GameDiscoveryResult r = GameDiscoveryResult.noGameFound("no roms");
            assertEquals(GameDiscoveryResult.Status.NO_GAME_FOUND, r.status);
            check("not playable", !r.isPlayable());
        });

        test("corrupted creates CORRUPTED", () -> {
            GameDiscoveryResult r = GameDiscoveryResult.corrupted("bad header");
            assertEquals(GameDiscoveryResult.Status.CORRUPTED, r.status);
            check("not playable", !r.isPlayable());
        });

        test("cueBinFound with bin files", () -> {
            File cue = new File("disc.cue");
            List<File> bins = List.of(new File("disc.bin"));
            GameDiscoveryResult r = GameDiscoveryResult.cueBinFound(cue, bins, null);
            assertEquals(GameDiscoveryResult.Status.FOUND_CUE_BIN, r.status);
            assertEquals(cue, r.primaryFile);
            assertEquals(cue, r.cueFile);
            assertEquals(1, r.binFiles.size());
        });

        test("binOnlyFound", () -> {
            File bin = new File("track.bin");
            GameDiscoveryResult r = GameDiscoveryResult.binOnlyFound(bin, null);
            assertEquals(GameDiscoveryResult.Status.FOUND_BIN_ONLY, r.status);
            assertEquals(1, r.binFiles.size());
        });

        test("multiDiscFound", () -> {
            File m3u = new File("game.m3u");
            List<File> bins = List.of(new File("d1.bin"), new File("d2.bin"));
            GameDiscoveryResult r = GameDiscoveryResult.multiDiscFound(m3u, bins);
            assertEquals(GameDiscoveryResult.Status.FOUND_MULTI_DISC, r.status);
            assertEquals(2, r.binFiles.size());
        });

        test("warnings list is unmodifiable", () -> {
            List<String> w = new ArrayList<>();
            w.add("test");
            GameDiscoveryResult r = GameDiscoveryResult.cueBinFound(new File("f.cue"), List.of(new File("f.bin")), w);
            try {
                r.warnings.add("fail");
                throw new AssertionError("Should have thrown UnsupportedOperationException");
            } catch (UnsupportedOperationException e) { /* expected */ }
        });
    }

    // ══════════════════════════════════════════════════
    // Main
    // ══════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("  PS1Emulator Standalone Test Runner");
        System.out.println("============================================");

        testObjectPool();
        testCheatCodeParser();
        testPs1Buttons();
        testInputDispatcher();
        testGameDiscoveryResult();

        System.out.println("\n============================================");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("============================================");

        if (!failures.isEmpty()) {
            System.out.println("\nFailed tests:");
            for (String f : failures) System.out.println(f);
        }

        System.exit(failed > 0 ? 1 : 0);
    }
}
