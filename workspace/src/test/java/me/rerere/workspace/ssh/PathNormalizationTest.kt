package me.rerere.workspace.ssh

import me.rerere.workspace.WorkspaceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路径归一化在 POSIX 和 Windows 两种形式下都要成立:
 * SSH 工作区既可能连 Linux/macOS, 也可能连 OpenSSH for Windows.
 */
class PathNormalizationTest {
    @Test
    fun posixPathUnchanged() {
        assertEquals("/data/data/com.termux/files/home", normalizeAbsolute("/data/data/com.termux/files/home"))
        assertEquals("/", normalizeAbsolute("/"))
        assertEquals("/tmp", normalizeAbsolute("/tmp/"))
    }

    @Test
    fun posixPathCollapsesDotsAndDuplicates() {
        assertEquals("/a/b/d", normalizeAbsolute("/a//b/./c/../d"))
        assertEquals("/x", normalizeAbsolute("/../../x"))
    }

    @Test
    fun relativePathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { normalizeAbsolute("foo/bar") }
        assertThrows(IllegalArgumentException::class.java) { normalizeAbsolute("") }
    }

    @Test
    fun windowsBackslashPathIsKeptAsDriveForm() {
        assertEquals("C:/Users/Meng", normalizeAbsolute("C:\\Users\\Meng"))
        assertEquals("C:/Users/Meng/workspace", normalizeAbsolute("C:\\Users\\Meng\\workspace"))
    }

    @Test
    fun windowsDriveRoot() {
        assertEquals("C:/", normalizeAbsolute("C:\\"))
        assertEquals("C:/", normalizeAbsolute("C:"))
    }

    @Test
    fun windowsDriveLetterIsUppercasedAndDotsCollapsed() {
        assertEquals("C:/Meng", normalizeAbsolute("c:/Users/../Meng"))
        assertEquals("D:/a/b", normalizeAbsolute("d:\\a\\.\\b\\"))
    }

    /** `/C:/...` 是为了通过旧的"必须以 / 开头"校验硬造出来的形式, 归一后要还原成 `C:/...` */
    @Test
    fun spuriousLeadingSlashBeforeDriveIsDropped() {
        assertEquals("C:/Users/Meng", normalizeAbsolute("/C:/Users/Meng"))
        assertEquals("C:/Users/Meng/Workspace", normalizeAbsolute("/C:/Users/Meng/Workspace"))
    }

    /** Git Bash 风格的 /c/... 是另一回事, 不能被误认成盘符 */
    @Test
    fun msysStylePathIsLeftAlone() {
        assertEquals("/c/Users/Meng", normalizeAbsolute("/c/Users/Meng"))
    }

    @Test
    fun layoutAcceptsWindowsRemoteDir() {
        val layout = SshWorkspaceBackend.layoutOf("C:\\Users\\Meng\\workspace")
        assertEquals("C:/Users/Meng/workspace", layout.workspaceDir)
        assertEquals(
            "C:/Users/Meng/workspace/sub",
            layout.absolutePath(me.rerere.workspace.WorkspaceStorageArea.FILES, "sub")
        )
        assertTrue(layout.isWritable("C:/Users/Meng/workspace/a.txt"))
        assertFalse(layout.isWritable("/etc/passwd"))
    }

    @Test
    fun layoutStillAcceptsPosixRemoteDir() {
        val layout = WorkspaceLayout("/home/u/workspace", listOf("/home/u/workspace", "/tmp"))
        assertEquals("/home/u/workspace", layout.workspaceDir)
        assertTrue(layout.isWritable("/tmp/x"))
        assertFalse(layout.isWritable("/etc/passwd"))
    }
}
