# Project-specific ProGuard rules.

# Gson payloads are deserialized reflectively from backend responses.
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keep class sk.uss.isac.chat.mobile.core.data.model.** { *; }
-keep class sk.uss.isac.chat.mobile.core.data.remote.** { *; }
-keep class sk.uss.isac.chat.mobile.core.notifications.ChatPushPayload { *; }

# Keep enum names stable for JSON serialization / deserialization.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
