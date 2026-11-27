package com.agon.app.ui.theme

import org.junit.Test

class MaterialKolorRuntimeTest {
    @Test
    fun dynamicSchemePlatformIsOnRuntimeClasspath() {
        try {
            Class.forName("com.materialkolor.scheme.DynamicScheme\$Platform")
        } catch (failure: Throwable) {
            System.err.println(
                "::error title=MaterialKolor runtime class::${failure.javaClass.name}: ${failure.message}"
            )
            throw failure
        }
    }
}
