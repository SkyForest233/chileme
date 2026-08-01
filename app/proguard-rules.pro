# ============================================================
# 吃了么 · R8 规则
# 目标：开启代码压缩（shrinking）与资源压缩，但【不混淆】——
# 保留全部类名 / 方法名 / 字段名，保证崩溃堆栈可读、调试友好。
# ============================================================

# 不混淆：保留所有名称（shrinking 仍然生效，未使用代码会被移除）
-dontobfuscate

# 保留源文件名与行号，崩溃堆栈精确到行
-keepattributes SourceFile,LineNumberTable

# 保留注解、泛型签名等元数据（kotlinx-serialization / 反射需要）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---- kotlinx-serialization ----
# 保留 @Serializable 类的序列化器与伴生对象（数据模型经反射查找 serializer）
-keepclassmembers class com.agon.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.agon.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.agon.app.**$$serializer { *; }

# ---- OkHttp（坚果云 WebDAV 同步）----
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Coil ----
-dontwarn coil3.**
