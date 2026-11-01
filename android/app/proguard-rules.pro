# The app's own code is small; keep it whole so saved items (kotlinx.serialization) and the
# notification receivers are never renamed or stripped. R8 still shrinks the libraries.
-keep class com.dnlfx.remindergenie.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
