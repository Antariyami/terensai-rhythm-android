package kz.terensai.rhythm.data.room

import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.StagePlan
import kz.terensai.rhythm.domain.Track
import org.junit.Assert.*
import org.junit.Test

class ProgramSnapshotCodecTest {
    @Test fun roundtripKeepsImmutableOffsetsAndParallelTracks() {
        val original = ProgramPlan("custom", "Демо B2", 900000L, listOf(
            StagePlan("g", "Отдых гостей", Track.GUEST, 0, 600000),
            StagePlan("m", "Подготовка", Track.MASTER, 0, 300000),
            StagePlan("s", "Парение", Track.GUEST, 600000, 300000)
        ))
        val copied = ProgramSnapshotCodec.decode(ProgramSnapshotCodec.encode(original))
        assertEquals(original, copied)
        assertEquals(900000L, copied.plannedDurationMs)
        assertEquals(3, copied.stages.size)
        assertEquals(Track.MASTER, copied.stages[1].track)
    }

    @Test fun unknownSnapshotSchemaIsRejected() {
        try {
            ProgramSnapshotCodec.decode(
                "{\"schemaVersion\":44,\"id\":\"x\",\"title\":\"X\",\"plannedDurationMs\":100,\"stages\":[]}"
            )
            fail("must reject unknown format")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun corruptSnapshotIsRejectedWithoutDataLoss() {
        try {
            ProgramSnapshotCodec.decode("broken JSON")
            fail("must report error")
        } catch (_: org.json.JSONException) { }
    }
}
