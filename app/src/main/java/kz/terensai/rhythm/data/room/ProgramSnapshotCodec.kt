package kz.terensai.rhythm.data.room

import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.StagePlan
import kz.terensai.rhythm.domain.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned immutable snapshot: B2 sessions never reference a mutable template
 * for reconstruction. Keep old snapshot versions readable when the editor arrives.
 */
object ProgramSnapshotCodec {
    private const val VERSION = 1

    fun encode(plan: ProgramPlan): String {
        val stages = JSONArray()
        plan.stages.forEach { stage ->
            stages.put(JSONObject()
                .put("id", stage.id)
                .put("title", stage.title)
                .put("track", stage.track.name)
                .put("startOffsetMs", stage.startOffsetMs)
                .put("plannedDurationMs", stage.plannedDurationMs))
        }
        return JSONObject()
            .put("schemaVersion", VERSION)
            .put("id", plan.id)
            .put("title", plan.title)
            .put("plannedDurationMs", plan.plannedDurationMs)
            .put("stages", stages)
            .toString()
    }

    fun decode(snapshot: String): ProgramPlan {
        val root = JSONObject(snapshot)
        require(root.optInt("schemaVersion", -1) == VERSION) { "Unsupported programme snapshot" }
        val items = root.getJSONArray("stages")
        val stages = (0 until items.length()).map { i ->
            val row = items.getJSONObject(i)
            StagePlan(
                id = row.getString("id"),
                title = row.getString("title"),
                track = Track.valueOf(row.getString("track")),
                startOffsetMs = row.getLong("startOffsetMs"),
                plannedDurationMs = row.getLong("plannedDurationMs")
            )
        }
        return ProgramPlan(
            id = root.getString("id"),
            title = root.getString("title"),
            plannedDurationMs = root.getLong("plannedDurationMs"),
            stages = stages
        )
    }
}
