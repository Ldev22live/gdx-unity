package generated

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import io.github.ldev22live.gdxunity.engine.scene.Entity
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent

/**
 * Generated from the graph "Spinner" by Forgeroom (Visual Scripting > Generate Kotlin).
 * Edit it freely; generating again writes the file anew. Bind it to an entity with an Entity Script Binding
 * component named generated.Spinner.
 */
class Spinner : ScriptComponent() {
    private var n1DeltaTime: Float = 0f

    override fun onStart() {
        super.onStart()
    }

    override fun onUpdate(deltaTime: Float) {
        super.onUpdate(deltaTime)
        n1DeltaTime = deltaTime
        runN1()
    }

    private fun runN1() {
        getEntity().getTransform().rotate((Vector3((n1DeltaTime * 90.0f), 0.0f, 0.0f)).x, (Vector3((n1DeltaTime * 90.0f), 0.0f, 0.0f)).y, (Vector3((n1DeltaTime * 90.0f), 0.0f, 0.0f)).z)
    }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewogICJzY2hlbWFWZXJzaW9uIjogMSwKICAia2luZCI6ICJncmFwaCIsCiAgIm5hbWUiOiAiU3Bpbm5lciIsCiAgInZhcmlhYmxl
// cyI6IFtdLAogICJub2RlcyI6IFsKICAgIHsKICAgICAgImlkIjogIm4xIiwKICAgICAgInR5cGUiOiAiZXZlbnQudGljayIsCiAg
// ICAgICJ4IjogMCwKICAgICAgInkiOiAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAg
// fSwKICAgIHsKICAgICAgImlkIjogIm4yIiwKICAgICAgInR5cGUiOiAibWF0aC5tdWx0aXBseSIsCiAgICAgICJ4IjogMjYwLAog
// ICAgICAieSI6IDEyMCwKICAgICAgImxpdGVyYWxzIjogWwogICAgICAgIHsKICAgICAgICAgICJwaW4iOiAiYiIsCiAgICAgICAg
// ICAidHlwZSI6ICJGTE9BVCIsCiAgICAgICAgICAidmFsdWUiOiA5MAogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRp
// ZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm4zIiwKICAgICAgInR5cGUiOiAidmVjdG9yLm1ha2UiLAogICAgICAi
// eCI6IDUyMCwKICAgICAgInkiOiAxMjAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFtdCiAgICB9
// LAogICAgewogICAgICAiaWQiOiAibjQiLAogICAgICAidHlwZSI6ICJhY3Rpb24ucm90YXRlQnkiLAogICAgICAieCI6IDc4MCwK
// ICAgICAgInkiOiAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfQogIF0sCiAgImxp
// bmtzIjogWwogICAgewogICAgICAiZnJvbSI6ICJuMSIsCiAgICAgICJmcm9tUGluIjogImRlbHRhVGltZSIsCiAgICAgICJ0byI6
// ICJuMiIsCiAgICAgICJ0b1BpbiI6ICJhIgogICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjIiLAogICAgICAiZnJvbVBpbiI6
// ICJyZXN1bHQiLAogICAgICAidG8iOiAibjMiLAogICAgICAidG9QaW4iOiAieCIKICAgIH0sCiAgICB7CiAgICAgICJmcm9tIjog
// Im4zIiwKICAgICAgImZyb21QaW4iOiAidmVjdG9yIiwKICAgICAgInRvIjogIm40IiwKICAgICAgInRvUGluIjogImRlZ3JlZXMi
// CiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuMSIsCiAgICAgICJmcm9tUGluIjogInRoZW4iLAogICAgICAidG8iOiAibjQi
// LAogICAgICAidG9QaW4iOiAiZXhlYyIKICAgIH0KICBdCn0=
// forgeroom-graph:end
