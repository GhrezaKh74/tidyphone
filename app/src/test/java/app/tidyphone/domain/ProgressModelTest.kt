package app.tidyphone.domain

import app.tidyphone.domain.progress.AnalysisProgress
import app.tidyphone.domain.progress.ScanProgress
import app.tidyphone.domain.progress.ScanProgress.Phase
import app.tidyphone.domain.progress.Throttle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressModelTest {
    @Test fun `first scan is indeterminate while indexing`() {
        assertThat(ScanProgress.percent(Phase.WALK, 5000, previousFileCount = 0, 0, 0)).isNull()
    }

    @Test fun `later scans estimate indexing from the previous count and never claim it is done`() {
        assertThat(ScanProgress.percent(Phase.WALK, 500, 1000, 0, 0)).isEqualTo(35)
        assertThat(ScanProgress.percent(Phase.WALK, 5000, 1000, 0, 0)).isEqualTo(69)
    }

    @Test fun `hashing maps to 70-98 percent and analyzing to 98`() {
        assertThat(ScanProgress.percent(Phase.HASH, 0, 0, 0, 0)).isEqualTo(70)
        assertThat(ScanProgress.percent(Phase.HASH, 0, 0, 50, 100)).isEqualTo(84)
        assertThat(ScanProgress.percent(Phase.HASH, 0, 0, 100, 100)).isEqualTo(98)
        assertThat(ScanProgress.percent(Phase.ANALYZE, 0, 0, 0, 0)).isEqualTo(98)
    }

    @Test fun `analysis percent`() {
        assertThat(AnalysisProgress.percent(30, 120)).isEqualTo(25)
        assertThat(AnalysisProgress.percent(0, 0)).isEqualTo(0)
    }

    @Test fun `throttle limits updates but lets forced ones through`() {
        var now = 0L
        val t = Throttle(1000) { now }
        assertThat(t.ready()).isTrue()
        now = 400; assertThat(t.ready()).isFalse()
        assertThat(t.ready(force = true)).isTrue()
        now = 1500; assertThat(t.ready()).isTrue()
    }
}
