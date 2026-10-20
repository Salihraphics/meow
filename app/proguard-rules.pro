# Project-specific R8 rules. Compose, DataStore and Navigation ship their own consumer rules.

# PdfBox-Android: JPEG 2000 decoding is an optional extra library MEOW doesn't include (only text
# is read, images are not decoded).
-dontwarn com.gemalto.jp2.**
# PdfBox finds some classes (fonts, filters, encodings) by name; keep it whole rather than risk a
# release-only failure in answer-key detection that the JVM tests could not catch.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
