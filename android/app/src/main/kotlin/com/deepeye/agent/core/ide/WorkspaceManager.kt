package com.deepeye.agent.core.ide

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workspaceDir: File by lazy {
        File(context.filesDir, "workspace").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun getWorkspaceRoot(): File = workspaceDir

    fun listFiles(): List<File> {
        return workspaceDir.walkTopDown().filter { it.isFile }.toList()
    }

    fun readFile(relativePath: String): String? {
        val file = File(workspaceDir, relativePath)
        if (!file.exists() || !file.canonicalPath.startsWith(workspaceDir.canonicalPath)) {
            return null // Prevent directory traversal
        }
        return file.readText()
    }

    fun writeFile(relativePath: String, content: String): Boolean {
        val file = File(workspaceDir, relativePath)
        if (!file.canonicalPath.startsWith(workspaceDir.canonicalPath)) {
            return false // Prevent directory traversal
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
        return true
    }

    fun resolveFile(relativePath: String): File? {
        val file = File(workspaceDir, relativePath)
        if (!file.canonicalPath.startsWith(workspaceDir.canonicalPath)) {
            return null // Prevent directory traversal
        }
        return file
    }
}
