package com.agon.app.ui.theme

import org.junit.Test

class MaterialKolorRuntimeTest {
    @Test
    fun dynamicSchemePlatformIsOnRuntimeClasspath() {
        try {
            Class.forName("com.materialkolor.scheme.DynamicScheme\$Platform")
        } catch (failure: Throwable) {
            val classLoader = Thread.currentThread().contextClassLoader
            val schemeResource = classLoader?.getResource("com/materialkolor/scheme/DynamicScheme.class")
            val platformResource = classLoader?.getResource(
                "com/materialkolor/scheme/DynamicScheme\$Platform.class"
            )
            System.err.println(
                "::notice title=MaterialKolor classpath::scheme=$schemeResource; platform=$platformResource"
            )
            System.err.println(
                "::error title=MaterialKolor runtime class::${failure.javaClass.name}: ${failure.message}"
            )
            throw failure
        }
    }
}
