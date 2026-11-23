package generated

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import io.github.ldev22live.gdxunity.engine.scene.Entity
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent

/**
 * Generated from the graph "Greeter" by Forgeroom (Visual Scripting > Generate Kotlin).
 * Edit it freely; generating again writes the file anew. Bind it to an entity with an Entity Script Binding
 * component named generated.Greeter.
 */
class Greeter : ScriptComponent() {
    override fun onStart() {
        super.onStart()
        runN1()
    }

    override fun onUpdate(deltaTime: Float) {
        super.onUpdate(deltaTime)
    }

    private fun runN1() {
        Gdx.app.log("Greeter", "Hello from a visual script!")
        Gdx.app.log("Greeter", ("I am " + getEntity().getName()))
    }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewogICJzY2hlbWFWZXJzaW9uIjogMSwKICAia2luZCI6ICJncmFwaCIsCiAgIm5hbWUiOiAiR3JlZXRlciIsCiAgInZhcmlhYmxl
// cyI6IFtdLAogICJub2RlcyI6IFsKICAgIHsKICAgICAgImlkIjogIm4xIiwKICAgICAgInR5cGUiOiAiZXZlbnQuYmVnaW5QbGF5
// IiwKICAgICAgIngiOiAwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFtd
// CiAgICB9LAogICAgewogICAgICAiaWQiOiAibjIiLAogICAgICAidHlwZSI6ICJhY3Rpb24ucHJpbnQiLAogICAgICAieCI6IDI2
// MCwKICAgICAgInkiOiAwLAogICAgICAibGl0ZXJhbHMiOiBbCiAgICAgICAgewogICAgICAgICAgInBpbiI6ICJtZXNzYWdlIiwK
// ICAgICAgICAgICJ0eXBlIjogIlNUUklORyIsCiAgICAgICAgICAidmFsdWUiOiAiSGVsbG8gZnJvbSBhIHZpc3VhbCBzY3JpcHQh
// IgogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm4zIiwK
// ICAgICAgInR5cGUiOiAiZGF0YS5nZXROYW1lIiwKICAgICAgIngiOiAyNjAsCiAgICAgICJ5IjogMjAwLAogICAgICAibGl0ZXJh
// bHMiOiBbXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm40IiwKICAgICAgInR5cGUi
// OiAidGV4dC5hcHBlbmQiLAogICAgICAieCI6IDUyMCwKICAgICAgInkiOiAyMDAsCiAgICAgICJsaXRlcmFscyI6IFsKICAgICAg
// ICB7CiAgICAgICAgICAicGluIjogImEiLAogICAgICAgICAgInR5cGUiOiAiU1RSSU5HIiwKICAgICAgICAgICJ2YWx1ZSI6ICJJ
// IGFtICIKICAgICAgICB9CiAgICAgIF0sCiAgICAgICJwcm9wZXJ0aWVzIjogW10KICAgIH0sCiAgICB7CiAgICAgICJpZCI6ICJu
// NSIsCiAgICAgICJ0eXBlIjogImFjdGlvbi5wcmludCIsCiAgICAgICJ4IjogNzgwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRl
// cmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFtdCiAgICB9CiAgXSwKICAibGlua3MiOiBbCiAgICB7CiAgICAgICJmcm9t
// IjogIm4xIiwKICAgICAgImZyb21QaW4iOiAidGhlbiIsCiAgICAgICJ0byI6ICJuMiIsCiAgICAgICJ0b1BpbiI6ICJleGVjIgog
// ICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjIiLAogICAgICAiZnJvbVBpbiI6ICJ0aGVuIiwKICAgICAgInRvIjogIm41IiwK
// ICAgICAgInRvUGluIjogImV4ZWMiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuMyIsCiAgICAgICJmcm9tUGluIjogIm5h
// bWUiLAogICAgICAidG8iOiAibjQiLAogICAgICAidG9QaW4iOiAiYiIKICAgIH0sCiAgICB7CiAgICAgICJmcm9tIjogIm40IiwK
// ICAgICAgImZyb21QaW4iOiAicmVzdWx0IiwKICAgICAgInRvIjogIm41IiwKICAgICAgInRvUGluIjogIm1lc3NhZ2UiCiAgICB9
// CiAgXQp9
// forgeroom-graph:end
