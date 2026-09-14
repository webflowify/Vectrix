# ============================================================================
# PS1 Emulator — ProGuard / R8 rules
# ============================================================================
# NOTE: R8 optimizations are disabled (-dontoptimize) because the aggressive
# inlining/removal passes in proguard-android-optimize.txt break the AdMob
# SDK (v23.6.0) which relies heavily on reflection and dynamic class loading.
# Shrinking and obfuscation are still applied; only the optimization pass is off.
# ============================================================================

# ── Disable R8 optimization pass ──────────────────────────────────────────────
-dontoptimize

# ── Keep this file in sync with every new class that uses JNI, Room, Gson,
# reflection, or is referenced from native code.

# ── Required attributes (do NOT remove) ────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-renamesourcefileattribute SourceFile

# ── JNI / native bridge ────────────────────────────────────────────────────
# C++ JNI calls into Java by fully-qualified method name, e.g.
#   Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeInit
# R8 must never rename the class or its native methods.
-keep class com.tansoft.ps1emulator.core.EmulatorBridge {
    native <methods>;
    public static final int FB_WIDTH;
    public static final int FB_HEIGHT;
    public static * getFramebufferBuffer();
    public static * getAudioBuffer();
    public static void swapFramebuffers(int, int);
    public static void withFrontFramebuffer(*);
    public static void clearFramebufferBuffer();
    public static void allocateFramebufferBuffer(int);
    public static void allocateAudioBuffer(int);
    public static void setAudioBufferSize(int);
    public static float getResolutionScale();
}

# ── Room database ───────────────────────────────────────────────────────────
# Room uses reflection to instantiate the database and DAOs, and the
# annotation processor generates _Impl classes that Room loads at runtime
# via Class.forName() + getDeclaredConstructor(). R8 must never strip,
# rename, or merge these generated classes or their constructors.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
}
-keep class com.tansoft.ps1emulator.data.GameEntity { *; }

# ── Service binder (used via instanceof cast) ───────────────────────────────
-keep class com.tansoft.ps1emulator.core.EmulatorService$LocalBinder {
    <init>(com.tansoft.ps1emulator.core.EmulatorService);
    public com.tansoft.ps1emulator.core.EmulatorService getService();
}

# ── Functional interface (called from emulation thread) ─────────────────────
-keep interface com.tansoft.ps1emulator.core.EmulatorBridge$FramebufferConsumer {
    void accept(java.nio.Buffer, int, int);
}

# ── Glide ───────────────────────────────────────────────────────────────────
# Glide bundles its own consumer ProGuard rules, but keep the generated
# AppGlideModule / GlideModule entry points if present.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
# Keep generated Glide API classes
-keep public class * extends com.bumptech.glide.GeneratedAppGlideModule

# ── Android components (Activities, Services, Fragments) ─────────────────────
# proguard-android-optimize.txt already covers Activities/Services declared
# in the manifest, but be explicit for fragments created programmatically
# and inner classes that R8 might miss.
-keep class com.tansoft.ps1emulator.core.EmulatorService { *; }
-keep class com.tansoft.ps1emulator.ui.emulation.EmulationActivity { *; }

# Fragments instantiated via newInstance() or XML — keep no-arg constructors
# for Android's fragment recreation during configuration changes.
-keep class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keep class * extends androidx.preference.PreferenceFragmentCompat {
    public <init>();
}
-keep class * extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {
    public <init>();
}

# ── Input dispatcher (singleton, called from native thread) ─────────────────
-keep class com.tansoft.ps1emulator.input.InputDispatcher {
    public static * getInstance();
    public void setVirtualButtons(int);
    public void setPhysicalButtons(int);
    public void setAnalog(float, float);
    public void setAnalogRight(float, float);
    public int getPhysicalButtons();
    public int getVirtualButtons();
    public float getAnalogX();
    public float getAnalogY();
    public float getRightAnalogX();
    public float getRightAnalogY();
}

# ── Enums (generic rule — future-proof) ─────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable (generic rule — future-proof) ────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Serializable (generic rule — future-proof) ──────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Don't warn about missing classes from optional / platform libraries ─────
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Unsafe

# ── AdMob / Google Mobile Ads ──────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.ads.internal.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.common.internal.zzb { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.gms.internal.ads.**
-dontwarn com.google.android.gms.tasks.**

# ── App ad management classes ──────────────────────────────
-keep class com.tansoft.ps1emulator.ads.** { *; }
-keep class com.tansoft.ps1emulator.PS1EmulatorApp { *; }

# ── Keep all Activities (needed for Activity context in full-screen ads) ──
-keep class * extends android.app.Activity {
    public <init>();
}

# AdMob ad unit IDs referenced as strings
-keepclassmembers class com.tansoft.ps1emulator.ads.AdsConfig {
    public static final *;
}
