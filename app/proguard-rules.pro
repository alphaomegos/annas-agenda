# What R8 must not touch when the release build is minified.
#
# Minification is off at the time of writing. These rules go in first and
# separately, so that turning it on is a one-line change that can be undone in
# one line — and so that the first minified build and the first signed build
# are not the same build, since each is its own way to fail.

# -----------------------------------------------------------------------------
# Enum names are data.
# -----------------------------------------------------------------------------
# Every enum in this app that reaches the stored state is written as its name
# and read back with valueOf: the repeat frequency, the shelf a book is on, the
# view mode of a tab, the field a list is sorted by, the theme. R8 can rename
# enum constants, and it usually notices valueOf and stops itself — usually.
#
# The failure if it does not is the worst kind available here: the app writes
# names nothing will read back, and the next launch decodes a year of the
# user's data into defaults. There is no crash and no message. An explicit rule
# costs a few bytes.
-keepclassmembers enum com.alphaomegos.annasagenda.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# -----------------------------------------------------------------------------
# kotlinx.serialization
# -----------------------------------------------------------------------------
# The runtime ships its own rules, and they have been enough in practice. These
# are the ones the library documents, written out because "enough in practice"
# is a statement about a version, and this app is built once every few months
# by hand.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# -----------------------------------------------------------------------------
# A stack trace has to be worth sending.
# -----------------------------------------------------------------------------
# This app is not on a store and has no crash reporting. If it ever falls over,
# what arrives is a screenshot of whatever the phone showed, sent by the person
# using it. Obfuscated line numbers would make that screenshot worth nothing,
# and the mapping file needed to undo them lives on whichever machine built
# that particular APK — which, as of this week, is not a reliable place.
#
# The class names still get shortened; only the file and line survive.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
