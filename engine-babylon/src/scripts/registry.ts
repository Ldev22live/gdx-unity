import { ScriptRegistry } from "../runtime/ScriptComponent";
import { BreakoutBall, BreakoutPaddle } from "./breakout";
import { CollectorGame, CollectorPlayer, FollowCamera3D } from "./collector";
import { GrappleGame } from "./grapple";
import { HopperPlayer } from "./hopper";
import { FollowCamera2D, PlatformerPlayer } from "./platformer";
import { RuinsGame } from "./ruins";
import { RunnerGame, RunnerPlayer } from "./runner";
import { ShooterGame, ShooterShip } from "./shooter";
import { SideCamera25D } from "./side";
import { TrioGame } from "./trio";

/**
 * The scripts that have a web version. A scene file names its scripts by their Java class (for example
 * `samples.collector.CollectorGame`); the Java source cannot run in a browser, so each script is ported by hand and
 * registered here under the same name. Scripts missing here are listed by the player instead of being skipped quietly.
 */
export function createDefaultRegistry(): ScriptRegistry {
  const registry = new ScriptRegistry();
  registry.register("samples.collector.CollectorGame", () => new CollectorGame());
  registry.register("samples.collector.CollectorPlayer", () => new CollectorPlayer());
  registry.register("samples.common.FollowCamera3D", () => new FollowCamera3D());
  registry.register("samples.shooter.ShooterShip", () => new ShooterShip());
  registry.register("samples.shooter.ShooterGame", () => new ShooterGame());
  registry.register("samples.breakout.BreakoutPaddle", () => new BreakoutPaddle());
  registry.register("samples.breakout.BreakoutBall", () => new BreakoutBall());
  registry.register("samples.platformer.FollowCamera2D", () => new FollowCamera2D());
  registry.register("samples.platformer.PlatformerPlayer", () => new PlatformerPlayer());
  registry.register("samples.runner.RunnerPlayer", () => new RunnerPlayer());
  registry.register("samples.runner.RunnerGame", () => new RunnerGame());
  registry.register("samples.hopper.HopperPlayer", () => new HopperPlayer());
  registry.register("samples.common.SideCamera25D", () => new SideCamera25D());
  registry.register("samples.ruins.RuinsGame", () => new RuinsGame());
  registry.register("samples.grapple.GrappleGame", () => new GrappleGame());
  registry.register("samples.trio.TrioGame", () => new TrioGame());
  return registry;
}
