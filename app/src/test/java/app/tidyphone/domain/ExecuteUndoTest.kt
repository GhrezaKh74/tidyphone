package app.tidyphone.domain

import app.tidyphone.data.fs.JavaFileOps
import app.tidyphone.domain.exec.ActionLog
import app.tidyphone.domain.exec.ItemStatus
import app.tidyphone.domain.exec.PlanExecutor
import app.tidyphone.domain.exec.TrashIndex
import app.tidyphone.domain.exec.UndoManager
import app.tidyphone.domain.exec.UndoResult
import app.tidyphone.domain.exec.uniquePath
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.model.TrashItem
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExecuteUndoTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var root: String
    private lateinit var trashDir: String
    private lateinit var guard: PathGuard
    private val fs = JavaFileOps()
    private val log = MemoryLog()
    private val trash = MemoryTrash()
    private var now = 1_000L

    class MemoryLog : ActionLog {
        val records = mutableListOf<HistoryRecord>()
        override suspend fun record(r: HistoryRecord): Long { val id = records.size + 1L; records += r.copy(id = id); return id }
        override suspend fun markUndone(id: Long) { records.replaceAll { if (it.id == id) it.copy(undone = true) else it } }
    }

    class MemoryTrash : TrashIndex {
        val items = mutableMapOf<String, TrashItem>()
        override suspend fun add(item: TrashItem): Long { items[item.trashPath] = item; return items.size.toLong() }
        override suspend fun remove(trashPath: String) { items.remove(trashPath) }
        override suspend fun find(trashPath: String) = items[trashPath]
    }

    @Before fun setUp() {
        root = tmp.root.absolutePath
        trashDir = "$root/.TidyPhoneTrash"
        guard = PathGuard(root, userExcludes = listOf("Work"), trashDir = trashDir)
    }

    private fun write(rel: String, text: String = "data"): String =
        File(root, rel).apply { parentFile!!.mkdirs(); writeText(text) }.absolutePath

    private fun executor() = PlanExecutor(fs, guard, trashDir, log, trash) { now++ }
    private fun undo() = UndoManager(fs, guard, log, trash)
    private fun del(path: String) = PlanItem(path = path, action = ActionType.DELETE, source = PlanSource.LOCAL_TEMP, size = 4, reason = "")
    private fun move(path: String, dir: String, name: String? = null) =
        PlanItem(path = path, action = ActionType.MOVE, source = PlanSource.LLM, size = 4, reason = "", targetDir = "$root/$dir", newName = name)
    private fun rename(path: String, name: String) =
        PlanItem(path = path, action = ActionType.RENAME, source = PlanSource.LLM, size = 4, reason = "", newName = name)

    @Test fun `delete moves to trash, never removes the file, and undo restores it`() = runTest {
        val f = write("Download/junk.tmp", "keep me")
        val report = executor().execute(listOf(del(f)), dryRun = false)
        val r = report.results.single()
        assertThat(r.status).isEqualTo(ItemStatus.DONE)
        assertThat(File(f).exists()).isFalse()
        assertThat(File(r.resultPath!!).readText()).isEqualTo("keep me")
        assertThat(r.resultPath).startsWith(trashDir)
        assertThat(trash.items).containsKey(r.resultPath)

        val u = undo().undo(log.records.single())
        assertThat(u).isEqualTo(UndoResult.Restored(f, renamed = false))
        assertThat(File(f).readText()).isEqualTo("keep me")
        assertThat(trash.items).isEmpty()
        assertThat(log.records.single().undone).isTrue()
        assertThat(undo().undo(log.records.single())).isEqualTo(UndoResult.AlreadyUndone)
    }

    @Test fun `move creates folders, avoids overwriting, and undo moves back`() = runTest {
        val f = write("Download/invoice.pdf", "new")
        write("Documents/Finance/invoice.pdf", "existing")
        val r = executor().execute(listOf(move(f, "Documents/Finance")), dryRun = false).results.single()
        assertThat(r.resultPath).isEqualTo("$root/Documents/Finance/invoice (1).pdf")
        assertThat(File("$root/Documents/Finance/invoice.pdf").readText()).isEqualTo("existing")

        undo().undo(log.records.single())
        assertThat(File(f).readText()).isEqualTo("new")
        assertThat(File("$root/Documents/Finance/invoice (1).pdf").exists()).isFalse()
    }

    @Test fun `rename and un-rename`() = runTest {
        val f = write("Documents/scan_8f7a6.pdf")
        val r = executor().execute(listOf(rename(f, "Passport.pdf")), dryRun = false).results.single()
        assertThat(r.resultPath).isEqualTo("$root/Documents/Passport.pdf")
        undo().undo(log.records.single())
        assertThat(File(f).exists()).isTrue()
        assertThat(File("$root/Documents/Passport.pdf").exists()).isFalse()
    }

    @Test fun `undo picks a free name if the original path was reused`() = runTest {
        val f = write("Pictures/a.jpg", "old")
        executor().execute(listOf(del(f)), dryRun = false)
        write("Pictures/a.jpg", "newer file")
        val u = undo().undo(log.records.single()) as UndoResult.Restored
        assertThat(u.renamed).isTrue()
        assertThat(u.path).isEqualTo("$root/Pictures/a (1).jpg")
        assertThat(File("$root/Pictures/a.jpg").readText()).isEqualTo("newer file")
        assertThat(File(u.path).readText()).isEqualTo("old")
    }

    @Test fun `undo reports missing when the result file is gone`() = runTest {
        val f = write("Download/x.tmp")
        val r = executor().execute(listOf(del(f)), dryRun = false).results.single()
        File(r.resultPath!!).delete()
        assertThat(undo().undo(log.records.single())).isEqualTo(UndoResult.Missing)
    }

    @Test fun `batch undo unwinds chained actions newest first`() = runTest {
        val f = write("Download/doc.txt")
        executor().execute(listOf(move(f, "Documents")), dryRun = false)
        executor().execute(listOf(rename("$root/Documents/doc.txt", "Notes.txt")), dryRun = false)
        val results = undo().undoBatch(log.records)
        assertThat(results.map { it.second }.all { it is UndoResult.Restored }).isTrue()
        assertThat(File(f).exists()).isTrue()
    }

    @Test fun `dry run checks everything but changes and records nothing`() = runTest {
        val f = write("Download/a.tmp")
        val g = write("Download/b.pdf")
        val report = executor().execute(listOf(del(f), move(g, "Documents")), dryRun = true)
        assertThat(report.results.map { it.status }).containsExactly(ItemStatus.WOULD_DO, ItemStatus.WOULD_DO)
        assertThat(report.dryRun).isTrue()
        assertThat(File(f).exists()).isTrue()
        assertThat(File(g).exists()).isTrue()
        assertThat(File(trashDir).exists()).isFalse()
        assertThat(log.records).isEmpty()
        assertThat(trash.items).isEmpty()
    }

    @Test fun `safety rules are enforced at execution time`() = runTest {
        val work = write("Work/plan.docx")
        val priv = write("Android/data/com.app/db")
        val ok = write("Download/ok.pdf")
        val report = executor().execute(listOf(
            del(work),
            del(priv),
            move(ok, "Android/obb"),
            move(ok, "Work/inbox"),
            PlanItem(path = ok, action = ActionType.MOVE, source = PlanSource.LLM, size = 1, reason = "", targetDir = "$root/../outside"),
            rename(ok, "../../escape.pdf"),
        ), dryRun = false)
        assertThat(report.results.map { it.status }.toSet()).containsExactly(ItemStatus.SKIPPED)
        assertThat(File(work).exists()).isTrue()
        assertThat(File(priv).exists()).isTrue()
        assertThat(File(ok).exists()).isTrue()
        assertThat(log.records).isEmpty()
    }

    @Test fun `only empty folders are trashed, missing files are skipped, duplicates handled once`() = runTest {
        File(root, "Old/Empty").mkdirs()
        write("Old/Full/file.txt")
        val f = write("Download/twice.tmp")
        val report = executor().execute(listOf(
            del("$root/Old/Empty"), del("$root/Old/Full"), del("$root/Download/gone.tmp"), del(f), move(f, "Documents"),
        ), dryRun = false)
        assertThat(report.results.map { it.status }).containsExactly(
            ItemStatus.DONE, ItemStatus.SKIPPED, ItemStatus.SKIPPED, ItemStatus.DONE, ItemStatus.SKIPPED,
        ).inOrder()
        assertThat(File(root, "Old/Full/file.txt").exists()).isTrue()
    }

    @Test fun `undo can restore into a folder that was excluded later`() = runTest {
        val f = write("Work/a.txt")
        val unguarded = PlanExecutor(fs, PathGuard(root, trashDir = trashDir), trashDir, log, trash) { now++ }
        unguarded.execute(listOf(del(f)), dryRun = false)
        assertThat(undo().undo(log.records.single())).isInstanceOf(UndoResult.Restored::class.java)
    }

    @Test fun `uniquePath numbers names`() {
        val taken = setOf("/a/photo.jpg", "/a/photo (1).jpg", "/a/README")
        assertThat(uniquePath("/a/photo.jpg") { it in taken }).isEqualTo("/a/photo (2).jpg")
        assertThat(uniquePath("/a/README") { it in taken }).isEqualTo("/a/README (1)")
        assertThat(uniquePath("/a/free.txt") { it in taken }).isEqualTo("/a/free.txt")
    }
}
