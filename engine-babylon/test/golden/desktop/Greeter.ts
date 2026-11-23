import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";

/**
 * Generated from the graph "Greeter" by Forgeroom (Visual Scripting > Generate TypeScript).
 * Save it as src/scripts/Greeter.ts and register it in the script registry, for example
 * registry.register("generated.Greeter", () => new Greeter()), then bind it to an entity with an Entity Script
 * Binding component named generated.Greeter. Edit it freely; generating again writes the text anew.
 */
export class Greeter extends ScriptComponent {
  onStart(): void {
    super.onStart();
    this.runN1();
  }

  onUpdate(deltaTime: number): void {
    super.onUpdate(deltaTime);
  }

  private runN1(): void {
    console.log("Hello from a visual script!");
    console.log(("I am " + this.entity.name));
  }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewoic2NoZW1hVmVyc2lvbiI6IDEsCiJraW5kIjogImdyYXBoIiwKIm5hbWUiOiAiR3JlZXRlciIsCiJ2YXJpYWJsZXMiOiBbXSwK
// Im5vZGVzIjogWwoJewoJCSJpZCI6ICJuMSIsCgkJInR5cGUiOiAiZXZlbnQuYmVnaW5QbGF5IiwKCQkieCI6IDAuMCwKCQkieSI6
// IDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMiIsCgkJInR5cGUiOiAi
// YWN0aW9uLnByaW50IiwKCQkieCI6IDI2MC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6IFsKCQkJeyAicGluIjogIm1lc3Nh
// Z2UiLCAidHlwZSI6ICJTVFJJTkciLCAidmFsdWUiOiAiSGVsbG8gZnJvbSBhIHZpc3VhbCBzY3JpcHQhIiB9CgkJXSwKCQkicHJv
// cGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMyIsCgkJInR5cGUiOiAiZGF0YS5nZXROYW1lIiwKCQkieCI6IDI2MC4wLAoJ
// CSJ5IjogMjAwLjAsCgkJImxpdGVyYWxzIjogW10sCgkJInByb3BlcnRpZXMiOiBbXQoJfSwKCXsKCQkiaWQiOiAibjQiLAoJCSJ0
// eXBlIjogInRleHQuYXBwZW5kIiwKCQkieCI6IDUyMC4wLAoJCSJ5IjogMjAwLjAsCgkJImxpdGVyYWxzIjogWwoJCQl7ICJwaW4i
// OiAiYSIsICJ0eXBlIjogIlNUUklORyIsICJ2YWx1ZSI6ICJJIGFtICIgfQoJCV0sCgkJInByb3BlcnRpZXMiOiBbXQoJfSwKCXsK
// CQkiaWQiOiAibjUiLAoJCSJ0eXBlIjogImFjdGlvbi5wcmludCIsCgkJIngiOiA3ODAuMCwKCQkieSI6IDAuMCwKCQkibGl0ZXJh
// bHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFtdCgl9Cl0sCiJsaW5rcyI6IFsKCXsgImZyb20iOiAibjEiLCAiZnJvbVBpbiI6ICJ0
// aGVuIiwgInRvIjogIm4yIiwgInRvUGluIjogImV4ZWMiIH0sCgl7ICJmcm9tIjogIm4yIiwgImZyb21QaW4iOiAidGhlbiIsICJ0
// byI6ICJuNSIsICJ0b1BpbiI6ICJleGVjIiB9LAoJeyAiZnJvbSI6ICJuMyIsICJmcm9tUGluIjogIm5hbWUiLCAidG8iOiAibjQi
// LCAidG9QaW4iOiAiYiIgfSwKCXsgImZyb20iOiAibjQiLCAiZnJvbVBpbiI6ICJyZXN1bHQiLCAidG8iOiAibjUiLCAidG9QaW4i
// OiAibWVzc2FnZSIgfQpdCn0=
// forgeroom-graph:end
