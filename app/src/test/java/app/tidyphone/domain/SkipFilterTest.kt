package app.tidyphone.domain

import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.model.SkipFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkipFilterTest {
    private fun item(path: String, source: PlanSource, size: Long = 100) =
        PlanItem(path = "$ROOT/$path", action = ActionType.DELETE, source = source, size = size, reason = "")

    @Test fun `skipped suggestions stay hidden while the file is unchanged`() {
        val f = SkipFilter(listOf(SkipFilter.Skipped("$ROOT/DCIM/Camera/VID.mp4", PlanSource.LOCAL_LARGE, 100)))
        assertThat(f.isSkipped(item("DCIM/Camera/VID.mp4", PlanSource.LOCAL_LARGE))).isTrue()
        assertThat(f.isSkipped(item("dcim/camera/vid.mp4", PlanSource.LOCAL_LARGE))).isTrue()
    }

    @Test fun `a changed file or a different kind of suggestion shows again`() {
        val f = SkipFilter(listOf(SkipFilter.Skipped("$ROOT/DCIM/Camera/VID.mp4", PlanSource.LOCAL_LARGE, 100)))
        assertThat(f.isSkipped(item("DCIM/Camera/VID.mp4", PlanSource.LOCAL_LARGE, size = 200))).isFalse()
        assertThat(f.isSkipped(item("DCIM/Camera/VID.mp4", PlanSource.LOCAL_DUPLICATE))).isFalse()
        assertThat(f.filter(listOf(item("DCIM/Camera/VID.mp4", PlanSource.LOCAL_LARGE), item("x.tmp", PlanSource.LOCAL_TEMP))).map { it.fileName })
            .containsExactly("x.tmp")
    }
}
