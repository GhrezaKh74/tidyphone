package app.tidyphone.domain.llm

import app.tidyphone.domain.ROOT
import app.tidyphone.domain.file
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SuggestionMapperTest {
    private val guard = PathGuard(ROOT, userExcludes = listOf("Work"), trashDir = "$ROOT/.TidyPhoneTrash")
    private val mapper = SuggestionMapper(guard)
    private val files = mapOf(
        0 to file("Download/inv_83748.pdf"),
        1 to file("Download/setup.exe"),
        2 to file("Work/secret.docx"),
        3 to file("Documents/Finance/ok.pdf"),
        4 to file("Android/data/com.x/a.db"),
    )

    private fun s(id: Int, target: String = "", delete: Boolean = false, rename: String = "") =
        Suggestion(id, "documents", target, delete, "because", rename)

    @Test fun `valid move with rename becomes one MOVE item`() {
        val r = mapper.map(listOf(s(0, "Documents/Finance", rename = "Invoice 2024")), files)
        val item = r.items.single()
        assertThat(item.action).isEqualTo(ActionType.MOVE)
        assertThat(item.targetDir).isEqualTo("$ROOT/Documents/Finance")
        assertThat(item.newName).isEqualTo("Invoice 2024.pdf") // extension kept
        assertThat(item.destinationPath).isEqualTo("$ROOT/Documents/Finance/Invoice 2024.pdf")
    }

    @Test fun `targets outside allowed paths are dropped`() {
        val bad = listOf("../../system", "/data/data/x", "Android/data/evil", "Android/obb", "Work/Inbox", ".TidyPhoneTrash", ".hidden/stuff", "/storage/emulated/0/../../etc", "a/b/c/d/e/f/g")
        for (t in bad) {
            val r = mapper.map(listOf(s(0, t)), files)
            assertThat(r.items).isEmpty()
            assertThat(r.dropped.single()).contains("not allowed")
        }
    }

    @Test fun `absolute target inside the root is accepted`() {
        val r = mapper.map(listOf(s(0, "$ROOT/Documents/Bills")), files)
        assertThat(r.items.single().targetDir).isEqualTo("$ROOT/Documents/Bills")
    }

    @Test fun `files in excluded or protected folders are never touched`() {
        val r = mapper.map(listOf(s(2, "Documents", delete = true), s(4, delete = true)), files)
        assertThat(r.items).isEmpty()
        assertThat(r.dropped).hasSize(2)
    }

    @Test fun `unknown ids are dropped`() {
        assertThat(mapper.map(listOf(s(99, "Documents")), files).items).isEmpty()
    }

    @Test fun `safe to delete becomes an unselected DELETE`() {
        val item = mapper.map(listOf(s(1, delete = true)), files).items.single()
        assertThat(item.action).isEqualTo(ActionType.DELETE)
        assertThat(item.selected).isFalse()
    }

    @Test fun `moving into the current folder is a no-op, rename alone becomes RENAME`() {
        assertThat(mapper.map(listOf(s(3, "Documents/Finance")), files).items).isEmpty()
        val r = mapper.map(listOf(s(3, "documents/finance", rename = "Bank statement.pdf")), files).items.single()
        assertThat(r.action).isEqualTo(ActionType.RENAME)
        assertThat(r.destinationPath).isEqualTo("$ROOT/Documents/Finance/Bank statement.pdf")
    }

    @Test fun `unsafe renames are dropped`() {
        for (bad in listOf("../x.pdf", "a/b.pdf", ".pdf", "con\u0000.pdf")) {
            val r = mapper.map(listOf(s(0, rename = bad)), files)
            assertThat(r.items).isEmpty()
        }
    }
}
