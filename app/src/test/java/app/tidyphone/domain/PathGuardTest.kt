package app.tidyphone.domain

import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PathGuardTest {
    private val guard = PathGuard(ROOT, userExcludes = listOf("Work", "$ROOT/Documents/Private/"), trashDir = "$ROOT/.TidyPhoneTrash")

    @Test fun `normal user folders are allowed`() {
        assertThat(guard.canModify("$ROOT/Download/file.pdf")).isTrue()
        assertThat(guard.canModify("$ROOT/DCIM/Camera/IMG_1.jpg")).isTrue()
        assertThat(guard.canWriteTo("$ROOT/Documents/Finance")).isTrue()
    }

    @Test fun `paths outside storage root are rejected`() {
        assertThat(guard.canModify("/system/build.prop")).isFalse()
        assertThat(guard.canModify("/data/data/com.app/db")).isFalse()
        assertThat(guard.canWriteTo("/sdcard2/x")).isFalse()
        assertThat(guard.canScan("/storage/emulated/1/x")).isFalse()
    }

    @Test fun `traversal cannot escape the root or reach protected folders`() {
        assertThat(guard.canModify("$ROOT/Download/../../../../system/x")).isFalse()
        assertThat(guard.canModify("$ROOT/Download/../Android/data/com.x/file")).isFalse()
        assertThat(guard.resolve("../../etc/passwd")).isNull()
        assertThat(guard.resolve("Documents/../Android/obb")).isEqualTo("$ROOT/Android/obb")
        assertThat(guard.canWriteTo(guard.resolve("Documents/../Android/obb")!!)).isFalse()
    }

    @Test fun `Android data obb and app private dirs are blocked but Android media is allowed`() {
        assertThat(guard.canModify("$ROOT/Android/data/com.whatsapp/f")).isFalse()
        assertThat(guard.canModify("$ROOT/Android/obb/game/main.obb")).isFalse()
        assertThat(guard.canModify("$ROOT/android/DATA/x")).isFalse()
        assertThat(guard.canModify("$ROOT/Android/somethingElse/x")).isFalse()
        assertThat(guard.canScan("$ROOT/Android/data")).isFalse()
        assertThat(guard.canModify("$ROOT/Android/media/com.whatsapp/WhatsApp/Media/a.jpg")).isTrue()
    }

    @Test fun `user excludes are enforced relative and absolute, case-insensitive`() {
        assertThat(guard.canModify("$ROOT/Work/report.docx")).isFalse()
        assertThat(guard.canModify("$ROOT/work/sub/report.docx")).isFalse()
        assertThat(guard.canWriteTo("$ROOT/Work/New")).isFalse()
        assertThat(guard.canScan("$ROOT/Documents/Private/a.txt")).isFalse()
        assertThat(guard.canModify("$ROOT/Workshop/a.txt")).isTrue() // prefix of a name, not a folder
        assertThat(guard.isExcluded("$ROOT/Work")).isTrue()
    }

    @Test fun `trash folder is protected from plans`() {
        assertThat(guard.canModify("$ROOT/.TidyPhoneTrash/123__a.jpg")).isFalse()
        assertThat(guard.canWriteTo("$ROOT/.TidyPhoneTrash")).isFalse()
        assertThat(guard.isTrash("$ROOT/.TidyPhoneTrash/x")).isTrue()
    }

    @Test fun `root and standard top-level folders cannot be modified`() {
        assertThat(guard.canModify(ROOT)).isFalse()
        assertThat(guard.canModify("$ROOT/DCIM")).isFalse()
        assertThat(guard.canModify("$ROOT/download")).isFalse()
        assertThat(guard.canModify("$ROOT/OldStuff")).isTrue()
    }

    @Test fun `file names are validated`() {
        assertThat(guard.isValidFileName("Invoice 2024-03.pdf")).isTrue()
        assertThat(guard.isValidFileName("فاکتور.pdf")).isTrue()
        assertThat(guard.isValidFileName("../evil.pdf")).isFalse()
        assertThat(guard.isValidFileName("a/b.pdf")).isFalse()
        assertThat(guard.isValidFileName("..")).isFalse()
        assertThat(guard.isValidFileName(".hidden")).isFalse()
        assertThat(guard.isValidFileName("bad\u0000name")).isFalse()
        assertThat(guard.isValidFileName("")).isFalse()
    }

    @Test fun `normalize handles dots and duplicate slashes`() {
        assertThat(PathGuard.normalize("/a//b/./c/../d")).isEqualTo("/a/b/d")
        assertThat(PathGuard.normalize("/..")).isNull()
        assertThat(PathGuard.normalize("relative/path")).isNull()
    }
}
