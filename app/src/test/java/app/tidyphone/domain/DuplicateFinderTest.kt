package app.tidyphone.domain

import app.tidyphone.domain.analysis.DuplicateFinder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DuplicateFinderTest {
    private val finder = DuplicateFinder()

    @Test fun `groups identical files by hash and only hashes same-size files`() = runTest {
        val files = listOf(
            file("DCIM/Camera/a.jpg", size = 500, modified = 1),
            file("Download/a (1).jpg", size = 500, modified = 5),
            file("Pictures/other.jpg", size = 500, modified = 2),
            file("Music/unique.mp3", size = 999),
        )
        val hashed = mutableListOf<String>()
        val hashes = mapOf(
            files[0].path to "H1", files[1].path to "H1", files[2].path to "H2",
        )
        val groups = finder.find(files) { hashed += it.path; hashes[it.path] }

        assertThat(hashed).doesNotContain(files[3].path)
        assertThat(groups).hasSize(1)
        val g = groups.single()
        assertThat(g.hash).isEqualTo("H1")
        assertThat(g.keep.path).isEqualTo(files[0].path)
        assertThat(g.duplicates.map { it.path }).containsExactly(files[1].path)
        assertThat(g.wastedBytes).isEqualTo(500)
    }

    @Test fun `same size but different content is not a duplicate`() = runTest {
        val files = listOf(file("a.txt", size = 10), file("b.txt", size = 10))
        val groups = finder.find(files) { if (it.name == "a.txt") "X" else "Y" }
        assertThat(groups).isEmpty()
    }

    @Test fun `empty files, directories and unreadable files are ignored`() = runTest {
        val files = listOf(
            file("a.txt", size = 0), file("b.txt", size = 0),
            file("dir1", size = 0, dir = true), file("dir2", size = 0, dir = true),
            file("c.bin", size = 7), file("d.bin", size = 7),
        )
        val groups = finder.find(files) { null }
        assertThat(groups).isEmpty()
    }

    @Test fun `cached hashes are reused without calling the hasher`() = runTest {
        val files = listOf(file("a.jpg", size = 3, hash = "Z"), file("b.jpg", size = 3, hash = "Z"))
        var calls = 0
        val groups = finder.find(files) { calls++; "other" }
        assertThat(calls).isEqualTo(0)
        assertThat(groups).hasSize(1)
    }

    @Test fun `three copies keep one and suggest deleting two, largest waste first`() = runTest {
        val files = listOf(
            file("Download/v.mp4", size = 100, modified = 10),
            file("Movies/v.mp4", size = 100, modified = 20),
            file("Movies/v copy.mp4", size = 100, modified = 5),
            file("s1.txt", size = 50), file("s2.txt", size = 50),
        )
        val groups = finder.find(files) { if (it.size == 100L) "V" else "S" }
        assertThat(groups.map { it.hash }).containsExactly("V", "S").inOrder()
        val v = groups.first()
        assertThat(v.wastedBytes).isEqualTo(200)
        // Outside Downloads and without a "copy" suffix wins, even though it is newer.
        assertThat(v.keep.path).isEqualTo("$ROOT/Movies/v.mp4")
        assertThat(v.duplicates).hasSize(2)
    }

    @Test fun `keeper prefers original names over numbered copies`() {
        val keep = DuplicateFinder.chooseKeeper(listOf(
            file("Pictures/photo (2).jpg", modified = 1),
            file("Pictures/photo.jpg", modified = 9),
        ))
        assertThat(keep.name).isEqualTo("photo.jpg")
    }
}
