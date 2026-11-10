import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";

/**
 * Generated from the graph "Hop" by Forgeroom (Visual Scripting > Generate TypeScript).
 * Save it as src/scripts/Hop.ts and register it in the script registry, for example
 * registry.register("generated.Hop", () => new Hop()), then bind it to an entity with an Entity Script
 * Binding component named generated.Hop. Edit it freely; generating again writes the text anew.
 */
export class Hop extends ScriptComponent {
  private up: boolean = false;
  private n1DeltaTime = 0;
  private n2Timer = -1;

  onStart(): void {
    super.onStart();
  }

  onUpdate(deltaTime: number): void {
    super.onUpdate(deltaTime);
    if (this.n2Timer >= 0) {
      this.n2Timer -= deltaTime;
      if (this.n2Timer <= 0) {
        this.n2Timer = -1;
        this.runN2Completed();
      }
    }
    this.n1DeltaTime = deltaTime;
    this.runN1();
  }

  private runN1(): void {
    if (this.n2Timer < 0) this.n2Timer = 0.8;
  }

  private runN2Completed(): void {
    this.up = (!this.up);
    if (this.up) {
      this.entity.node.setAbsolutePosition(this.entity.getWorldPosition().add(new Vector3(0, 120, 0)));
    } else {
      this.entity.node.setAbsolutePosition(this.entity.getWorldPosition().add(new Vector3(0, -120, 0)));
    }
  }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewogICJzY2hlbWFWZXJzaW9uIjogMSwKICAia2luZCI6ICJncmFwaCIsCiAgIm5hbWUiOiAiSG9wIiwKICAidmFyaWFibGVzIjog
// WwogICAgewogICAgICAibmFtZSI6ICJ1cCIsCiAgICAgICJ0eXBlIjogIkJPT0xFQU4iLAogICAgICAidmFsdWUiOiBmYWxzZQog
// ICAgfQogIF0sCiAgIm5vZGVzIjogWwogICAgewogICAgICAiaWQiOiAibjEiLAogICAgICAidHlwZSI6ICJldmVudC50aWNrIiwK
// ICAgICAgIngiOiAwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFtdCiAg
// ICB9LAogICAgewogICAgICAiaWQiOiAibjIiLAogICAgICAidHlwZSI6ICJmbG93LmRlbGF5IiwKICAgICAgIngiOiAyNDAsCiAg
// ICAgICJ5IjogMCwKICAgICAgImxpdGVyYWxzIjogWwogICAgICAgIHsKICAgICAgICAgICJwaW4iOiAic2Vjb25kcyIsCiAgICAg
// ICAgICAidHlwZSI6ICJGTE9BVCIsCiAgICAgICAgICAidmFsdWUiOiAwLjgKICAgICAgICB9CiAgICAgIF0sCiAgICAgICJwcm9w
// ZXJ0aWVzIjogW10KICAgIH0sCiAgICB7CiAgICAgICJpZCI6ICJuMyIsCiAgICAgICJ0eXBlIjogInZhcmlhYmxlLnNldCIsCiAg
// ICAgICJ4IjogNTIwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFsKICAg
// ICAgICB7CiAgICAgICAgICAibmFtZSI6ICJ2YXJpYWJsZSIsCiAgICAgICAgICAidHlwZSI6ICJTVFJJTkciLAogICAgICAgICAg
// InZhbHVlIjogInVwIgogICAgICAgIH0KICAgICAgXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm40IiwKICAgICAgInR5cGUi
// OiAibG9naWMubm90IiwKICAgICAgIngiOiAzMDAsCiAgICAgICJ5IjogMTYwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAg
// InByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm41IiwKICAgICAgInR5cGUiOiAidmFyaWFibGUuZ2V0
// IiwKICAgICAgIngiOiA4MCwKICAgICAgInkiOiAxNjAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6
// IFsKICAgICAgICB7CiAgICAgICAgICAibmFtZSI6ICJ2YXJpYWJsZSIsCiAgICAgICAgICAidHlwZSI6ICJTVFJJTkciLAogICAg
// ICAgICAgInZhbHVlIjogInVwIgogICAgICAgIH0KICAgICAgXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm42IiwKICAgICAg
// InR5cGUiOiAiZmxvdy5icmFuY2giLAogICAgICAieCI6IDgwMCwKICAgICAgInkiOiAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwK
// ICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm43IiwKICAgICAgInR5cGUiOiAidmFyaWFi
// bGUuZ2V0IiwKICAgICAgIngiOiA1NjAsCiAgICAgICJ5IjogMjAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAgInByb3Bl
// cnRpZXMiOiBbCiAgICAgICAgewogICAgICAgICAgIm5hbWUiOiAidmFyaWFibGUiLAogICAgICAgICAgInR5cGUiOiAiU1RSSU5H
// IiwKICAgICAgICAgICJ2YWx1ZSI6ICJ1cCIKICAgICAgICB9CiAgICAgIF0KICAgIH0sCiAgICB7CiAgICAgICJpZCI6ICJuOCIs
// CiAgICAgICJ0eXBlIjogInZlY3Rvci5tYWtlIiwKICAgICAgIngiOiA4MDAsCiAgICAgICJ5IjogMTYwLAogICAgICAibGl0ZXJh
// bHMiOiBbCiAgICAgICAgewogICAgICAgICAgInBpbiI6ICJ5IiwKICAgICAgICAgICJ0eXBlIjogIkZMT0FUIiwKICAgICAgICAg
// ICJ2YWx1ZSI6IDEyMAogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAg
// ImlkIjogIm45IiwKICAgICAgInR5cGUiOiAidmVjdG9yLm1ha2UiLAogICAgICAieCI6IDgwMCwKICAgICAgInkiOiAyODAsCiAg
// ICAgICJsaXRlcmFscyI6IFsKICAgICAgICB7CiAgICAgICAgICAicGluIjogInkiLAogICAgICAgICAgInR5cGUiOiAiRkxPQVQi
// LAogICAgICAgICAgInZhbHVlIjogLTEyMAogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwK
// ICAgIHsKICAgICAgImlkIjogIm4xMCIsCiAgICAgICJ0eXBlIjogImFjdGlvbi5tb3ZlQnkiLAogICAgICAieCI6IDEwODAsCiAg
// ICAgICJ5IjogMCwKICAgICAgImxpdGVyYWxzIjogW10sCiAgICAgICJwcm9wZXJ0aWVzIjogW10KICAgIH0sCiAgICB7CiAgICAg
// ICJpZCI6ICJuMTEiLAogICAgICAidHlwZSI6ICJhY3Rpb24ubW92ZUJ5IiwKICAgICAgIngiOiAxMDgwLAogICAgICAieSI6IDE0
// MCwKICAgICAgImxpdGVyYWxzIjogW10sCiAgICAgICJwcm9wZXJ0aWVzIjogW10KICAgIH0KICBdLAogICJsaW5rcyI6IFsKICAg
// IHsKICAgICAgImZyb20iOiAibjEiLAogICAgICAiZnJvbVBpbiI6ICJ0aGVuIiwKICAgICAgInRvIjogIm4yIiwKICAgICAgInRv
// UGluIjogImV4ZWMiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuMiIsCiAgICAgICJmcm9tUGluIjogImNvbXBsZXRlZCIs
// CiAgICAgICJ0byI6ICJuMyIsCiAgICAgICJ0b1BpbiI6ICJleGVjIgogICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjMiLAog
// ICAgICAiZnJvbVBpbiI6ICJ0aGVuIiwKICAgICAgInRvIjogIm42IiwKICAgICAgInRvUGluIjogImV4ZWMiCiAgICB9LAogICAg
// ewogICAgICAiZnJvbSI6ICJuNSIsCiAgICAgICJmcm9tUGluIjogInZhbHVlIiwKICAgICAgInRvIjogIm40IiwKICAgICAgInRv
// UGluIjogInZhbHVlIgogICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjQiLAogICAgICAiZnJvbVBpbiI6ICJyZXN1bHQiLAog
// ICAgICAidG8iOiAibjMiLAogICAgICAidG9QaW4iOiAidmFsdWUiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuNyIsCiAg
// ICAgICJmcm9tUGluIjogInZhbHVlIiwKICAgICAgInRvIjogIm42IiwKICAgICAgInRvUGluIjogImNvbmRpdGlvbiIKICAgIH0s
// CiAgICB7CiAgICAgICJmcm9tIjogIm44IiwKICAgICAgImZyb21QaW4iOiAidmVjdG9yIiwKICAgICAgInRvIjogIm4xMCIsCiAg
// ICAgICJ0b1BpbiI6ICJvZmZzZXQiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuOSIsCiAgICAgICJmcm9tUGluIjogInZl
// Y3RvciIsCiAgICAgICJ0byI6ICJuMTEiLAogICAgICAidG9QaW4iOiAib2Zmc2V0IgogICAgfSwKICAgIHsKICAgICAgImZyb20i
// OiAibjYiLAogICAgICAiZnJvbVBpbiI6ICJ0cnVlIiwKICAgICAgInRvIjogIm4xMCIsCiAgICAgICJ0b1BpbiI6ICJleGVjIgog
// ICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjYiLAogICAgICAiZnJvbVBpbiI6ICJmYWxzZSIsCiAgICAgICJ0byI6ICJuMTEi
// LAogICAgICAidG9QaW4iOiAiZXhlYyIKICAgIH0KICBdCn0=
// forgeroom-graph:end
