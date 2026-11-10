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
// ewoic2NoZW1hVmVyc2lvbiI6IDEsCiJraW5kIjogImdyYXBoIiwKIm5hbWUiOiAiSG9wIiwKInZhcmlhYmxlcyI6IFsKCXsgIm5h
// bWUiOiAidXAiLCAidHlwZSI6ICJCT09MRUFOIiwgInZhbHVlIjogZmFsc2UgfQpdLAoibm9kZXMiOiBbCgl7CgkJImlkIjogIm4x
// IiwKCQkidHlwZSI6ICJldmVudC50aWNrIiwKCQkieCI6IDAuMCwKCQkieSI6IDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJv
// cGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMiIsCgkJInR5cGUiOiAiZmxvdy5kZWxheSIsCgkJIngiOiAyNDAuMCwKCQki
// eSI6IDAuMCwKCQkibGl0ZXJhbHMiOiBbCgkJCXsgInBpbiI6ICJzZWNvbmRzIiwgInR5cGUiOiAiRkxPQVQiLCAidmFsdWUiOiAw
// LjgwMDAwMDAxMTkyMDkyOSB9CgkJXSwKCQkicHJvcGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMyIsCgkJInR5cGUiOiAi
// dmFyaWFibGUuc2V0IiwKCQkieCI6IDUyMC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVzIjog
// WwoJCQl7ICJuYW1lIjogInZhcmlhYmxlIiwgInR5cGUiOiAiU1RSSU5HIiwgInZhbHVlIjogInVwIiB9CgkJXQoJfSwKCXsKCQki
// aWQiOiAibjQiLAoJCSJ0eXBlIjogImxvZ2ljLm5vdCIsCgkJIngiOiAzMDAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6
// IFtdLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm41IiwKCQkidHlwZSI6ICJ2YXJpYWJsZS5nZXQiLAoJCSJ4
// IjogODAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVzIjogWwoJCQl7ICJuYW1lIjogInZh
// cmlhYmxlIiwgInR5cGUiOiAiU1RSSU5HIiwgInZhbHVlIjogInVwIiB9CgkJXQoJfSwKCXsKCQkiaWQiOiAibjYiLAoJCSJ0eXBl
// IjogImZsb3cuYnJhbmNoIiwKCQkieCI6IDgwMC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVz
// IjogW10KCX0sCgl7CgkJImlkIjogIm43IiwKCQkidHlwZSI6ICJ2YXJpYWJsZS5nZXQiLAoJCSJ4IjogNTYwLjAsCgkJInkiOiAy
// MDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFsKCQkJeyAibmFtZSI6ICJ2YXJpYWJsZSIsICJ0eXBlIjog
// IlNUUklORyIsICJ2YWx1ZSI6ICJ1cCIgfQoJCV0KCX0sCgl7CgkJImlkIjogIm44IiwKCQkidHlwZSI6ICJ2ZWN0b3IubWFrZSIs
// CgkJIngiOiA4MDAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6IFsKCQkJeyAicGluIjogInkiLCAidHlwZSI6ICJGTE9B
// VCIsICJ2YWx1ZSI6IDEyMC4wIH0KCQldLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm45IiwKCQkidHlwZSI6
// ICJ2ZWN0b3IubWFrZSIsCgkJIngiOiA4MDAuMCwKCQkieSI6IDI4MC4wLAoJCSJsaXRlcmFscyI6IFsKCQkJeyAicGluIjogInki
// LCAidHlwZSI6ICJGTE9BVCIsICJ2YWx1ZSI6IC0xMjAuMCB9CgkJXSwKCQkicHJvcGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6
// ICJuMTAiLAoJCSJ0eXBlIjogImFjdGlvbi5tb3ZlQnkiLAoJCSJ4IjogMTA4MC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6
// IFtdLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm4xMSIsCgkJInR5cGUiOiAiYWN0aW9uLm1vdmVCeSIsCgkJ
// IngiOiAxMDgwLjAsCgkJInkiOiAxNDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFtdCgl9Cl0sCiJsaW5r
// cyI6IFsKCXsgImZyb20iOiAibjEiLCAiZnJvbVBpbiI6ICJ0aGVuIiwgInRvIjogIm4yIiwgInRvUGluIjogImV4ZWMiIH0sCgl7
// ICJmcm9tIjogIm4yIiwgImZyb21QaW4iOiAiY29tcGxldGVkIiwgInRvIjogIm4zIiwgInRvUGluIjogImV4ZWMiIH0sCgl7ICJm
// cm9tIjogIm4zIiwgImZyb21QaW4iOiAidGhlbiIsICJ0byI6ICJuNiIsICJ0b1BpbiI6ICJleGVjIiB9LAoJeyAiZnJvbSI6ICJu
// NSIsICJmcm9tUGluIjogInZhbHVlIiwgInRvIjogIm40IiwgInRvUGluIjogInZhbHVlIiB9LAoJeyAiZnJvbSI6ICJuNCIsICJm
// cm9tUGluIjogInJlc3VsdCIsICJ0byI6ICJuMyIsICJ0b1BpbiI6ICJ2YWx1ZSIgfSwKCXsgImZyb20iOiAibjciLCAiZnJvbVBp
// biI6ICJ2YWx1ZSIsICJ0byI6ICJuNiIsICJ0b1BpbiI6ICJjb25kaXRpb24iIH0sCgl7ICJmcm9tIjogIm44IiwgImZyb21QaW4i
// OiAidmVjdG9yIiwgInRvIjogIm4xMCIsICJ0b1BpbiI6ICJvZmZzZXQiIH0sCgl7ICJmcm9tIjogIm45IiwgImZyb21QaW4iOiAi
// dmVjdG9yIiwgInRvIjogIm4xMSIsICJ0b1BpbiI6ICJvZmZzZXQiIH0sCgl7ICJmcm9tIjogIm42IiwgImZyb21QaW4iOiAidHJ1
// ZSIsICJ0byI6ICJuMTAiLCAidG9QaW4iOiAiZXhlYyIgfSwKCXsgImZyb20iOiAibjYiLCAiZnJvbVBpbiI6ICJmYWxzZSIsICJ0
// byI6ICJuMTEiLCAidG9QaW4iOiAiZXhlYyIgfQpdCn0=
// forgeroom-graph:end
