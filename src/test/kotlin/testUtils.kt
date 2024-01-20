package com.github.zetten.bazeldeps

import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.readBytes

class TestUtils {
    companion object {
        fun assertOutputEqualsResource(output: Path, resource: String) =
            Assertions.assertEquals(
                TestUtils::class.java.getResourceAsStream(resource).use { it!!.readAllBytes() }.decodeToString(),
                output.readBytes().decodeToString()
            )
    }
}