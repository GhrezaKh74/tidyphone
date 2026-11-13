package app.tidyphone.data

import app.tidyphone.data.fs.FileScanner
import app.tidyphone.data.fs.Hasher
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun write(rel: String, text: String = "x"): File =
        File(tmp.root, rel).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun `scans user files, skips protected and hidden folders, reports empty folders`() = runTest {
        write("Download/a.pdf")
        write("DCIM/Camera/b.jpg", "img")
        write("Android/data/com.app/secret.db")
        write("Android/media/com.whatsapp/WhatsApp/Media/c.jpg")
        write("Work/plan.docx")
        write(".TidyPhoneTrash/old.jpg")
        write("Pictures/.thumbnails/t.jpg")
        write("Pictures/.nomedia", "")
        File(tmp.root, "Empty/Inner").mkdirs()

        val root = tmp.root.absolutePath
        val guard = PathGuard(root, userExcludes = listOf("Work"), trashDir = "$root/.TidyPhoneTrash")
        val found = mutableListOf<FileEntry>()
        FileScanner(guard) { null }.scan(onBatch = { found += it }, onProgress = {})

        val rel = found.map { it.path.removePrefix("$root/") }
        assertThat(rel).containsExactly(
            "Download/a.pdf", "DCIM/Camera/b.jpg", "Android/media/com.whatsapp/WhatsApp/Media/c.jpg", "Empty/Inner",
        )
        assertThat(found.single { it.name == "Inner" }.isDirectory).isTrue()
        assertThat(found.single { it.name == "b.jpg" }.size).isEqualTo(3)
    }

    @Test fun `sha256 matches a known value`() {
        val f = write("h.txt", "abc")
        assertThat(Hasher.sha256(f)).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(Hasher.sha256(File(tmp.root, "missing"))).isNull()
    }
}
