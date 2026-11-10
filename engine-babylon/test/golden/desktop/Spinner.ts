import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";

/**
 * Generated from the graph "Spinner" by Forgeroom (Visual Scripting > Generate TypeScript).
 * Save it as src/scripts/Spinner.ts and register it in the script registry, for example
 * registry.register("generated.Spinner", () => new Spinner()), then bind it to an entity with an Entity Script
 * Binding component named generated.Spinner. Edit it freely; generating again writes the text anew.
 */
export class Spinner extends ScriptComponent {
  private n1DeltaTime = 0;

  onStart(): void {
    super.onStart();
  }

  onUpdate(deltaTime: number): void {
    super.onUpdate(deltaTime);
    this.n1DeltaTime = deltaTime;
    this.runN1();
  }

  private runN1(): void {
    this.entity.rotate((new Vector3((this.n1DeltaTime * 90), 0, 0)).x, (new Vector3((this.n1DeltaTime * 90), 0, 0)).y, (new Vector3((this.n1DeltaTime * 90), 0, 0)).z);
  }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewoic2NoZW1hVmVyc2lvbiI6IDEsCiJraW5kIjogImdyYXBoIiwKIm5hbWUiOiAiU3Bpbm5lciIsCiJ2YXJpYWJsZXMiOiBbXSwK
// Im5vZGVzIjogWwoJewoJCSJpZCI6ICJuMSIsCgkJInR5cGUiOiAiZXZlbnQudGljayIsCgkJIngiOiAwLjAsCgkJInkiOiAwLjAs
// CgkJImxpdGVyYWxzIjogW10sCgkJInByb3BlcnRpZXMiOiBbXQoJfSwKCXsKCQkiaWQiOiAibjIiLAoJCSJ0eXBlIjogIm1hdGgu
// bXVsdGlwbHkiLAoJCSJ4IjogMjYwLjAsCgkJInkiOiAxMjAuMCwKCQkibGl0ZXJhbHMiOiBbCgkJCXsgInBpbiI6ICJiIiwgInR5
// cGUiOiAiRkxPQVQiLCAidmFsdWUiOiA5MC4wIH0KCQldLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm4zIiwK
// CQkidHlwZSI6ICJ2ZWN0b3IubWFrZSIsCgkJIngiOiA1MjAuMCwKCQkieSI6IDEyMC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJw
// cm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm40IiwKCQkidHlwZSI6ICJhY3Rpb24ucm90YXRlQnkiLAoJCSJ4IjogNzgw
// LjAsCgkJInkiOiAwLjAsCgkJImxpdGVyYWxzIjogW10sCgkJInByb3BlcnRpZXMiOiBbXQoJfQpdLAoibGlua3MiOiBbCgl7ICJm
// cm9tIjogIm4xIiwgImZyb21QaW4iOiAiZGVsdGFUaW1lIiwgInRvIjogIm4yIiwgInRvUGluIjogImEiIH0sCgl7ICJmcm9tIjog
// Im4yIiwgImZyb21QaW4iOiAicmVzdWx0IiwgInRvIjogIm4zIiwgInRvUGluIjogIngiIH0sCgl7ICJmcm9tIjogIm4zIiwgImZy
// b21QaW4iOiAidmVjdG9yIiwgInRvIjogIm40IiwgInRvUGluIjogImRlZ3JlZXMiIH0sCgl7ICJmcm9tIjogIm4xIiwgImZyb21Q
// aW4iOiAidGhlbiIsICJ0byI6ICJuNCIsICJ0b1BpbiI6ICJleGVjIiB9Cl0KfQ==
// forgeroom-graph:end
