package com.androidfung.departureboard.features.ai

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class DynamicLmEngineImplTest {

    @Test
    fun testDynamicLmEngineImpl_whenModelFileMissing_returnsNotReady() = runTest {
        val tempDir = File.createTempFile("test_dynamic_lm", "dir").apply {
            delete()
            mkdir()
        }
        val emptyDir = File(tempDir, "files").apply { mkdir() }

        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = emptyDir
            override fun getCacheDir(): File = emptyDir
            override fun getExternalFilesDir(type: String?): File? = null
            override fun getApplicationContext(): Context = this
        }

        val engine = DynamicLmEngineImpl(context)
        assertNotNull(engine)
        assertFalse("Engine should not be ready initially", engine.isReady)

        val loadSuccess = engine.loadModel()
        assertFalse("loadModel should fail gracefully when no weights file exists", loadSuccess)
        assertFalse("isReady should remain false when weights are missing", engine.isReady)

        engine.close()
        tempDir.deleteRecursively()
    }
}
