package com.lub.app

import com.lub.app.essence.LineNumberStripper
import com.lub.app.essence.NoiseFilter
import com.lub.app.github.RepoNamer
import com.lub.app.parser.PathSanitizer
import com.lub.app.parser.SecretScanner
import com.lub.app.parser.StackDetector
import com.lub.app.parser.TreeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LubUnitTest {

    @Test
    fun testPathSanitizer_preventsTraversal() {
        val safe1 = PathSanitizer.sanitize("../../etc/passwd")
        assertEquals("etc/passwd", safe1)

        val safe2 = PathSanitizer.sanitize("C:\\Windows\\System32\\file.txt")
        assertEquals("Windows/System32/file.txt", safe2)

        val safe3 = PathSanitizer.sanitize("./src/app/../index.js")
        assertEquals("src/index.js", safe3)
    }

    @Test
    fun testLineNumberStripper_removesSequentialNumbers() {
        val input = """
            1  const x = 10;
            2  const y = 20;
            3  console.log(x + y);
            4  return true;
        """.trimIndent()

        val (stripped, hadStripped) = LineNumberStripper.stripLineNumbers(input)
        assertTrue(hadStripped)
        assertEquals("const x = 10;\nconst y = 20;\nconsole.log(x + y);\nreturn true;", stripped)
    }

    @Test
    fun testRepoNamer_cleansNames() {
        val clean1 = RepoNamer.cleanRepoName("@scope/my_cool-app.v1")
        assertEquals("my_cool-app.v1", clean1)

        val clean2 = RepoNamer.cleanRepoName("   مشروع جديد   ")
        assertTrue(clean2.startsWith("lub-"))
    }

    @Test
    fun testSecretScanner_detectsSensitiveData() {
        val files = listOf(
            Pair(".env", "DATABASE_URL=postgres://localhost:5432"),
            Pair("src/config.js", "const apiKey = 'AIzaSy123456789012345678901234567890123';"),
            Pair("src/main.kt", "println(\"Hello world\");")
        )
        val findings = SecretScanner.scan(files)
        assertTrue(findings.any { it.filePath == ".env" })
        assertTrue(findings.any { it.secretType.contains("Gemini") || it.secretType.contains("Google") })
    }

    @Test
    fun testTreeParser_extractsPaths() {
        val tree = """
            ├── src
            │   ├── components
            │   │   └── Button.tsx
            │   └── App.tsx
            └── package.json
        """.trimIndent()

        val paths = TreeParser.parseTreePaths(tree)
        assertTrue(paths.contains("src/components/Button.tsx"))
        assertTrue(paths.contains("src/App.tsx"))
        assertTrue(paths.contains("package.json"))
    }

    @Test
    fun testNoiseFilter_detectsButtons() {
        assertTrue(NoiseFilter.isNoise("Copy"))
        assertTrue(NoiseFilter.isNoise("نسخ الكود"))
        assertTrue(NoiseFilter.isNoise("Download"))
        assertFalse(NoiseFilter.isNoise("fun copyItem(item: Item): Item {"))
    }

    @Test
    fun testStackDetector() {
        val stack1 = StackDetector.detect(listOf("app/src/main/AndroidManifest.xml", "build.gradle.kts"))
        assertEquals("Android (Kotlin/Java)", stack1)

        val stack2 = StackDetector.detect(listOf("package.json"), mapOf("package.json" to "{\"dependencies\": {\"react\": \"^18.0.0\"}}"))
        assertEquals("React", stack2)
    }
}
