package com.agon.app.ui.theme

import org.junit.Test

class MaterialKolorRuntimeTest {
    @Test
    fun dynamicSchemePlatformIsOnRuntimeClasspath() {
        Class.forName("com.materialkolor.scheme.DynamicScheme\$Platform")
    }
}
