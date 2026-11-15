package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway.EditOffer;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway.Kind;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.DynamicMeshComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteAnimatorComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Customize gateway (roadmap M1b.7): workspaces and the "Edit Selected" entries. */
class CustomizeGatewayTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static ComponentSerializerRegistry serializers() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new MusicSourceComponentSerializer());
        registry.register(new AudioSourceComponentSerializer());
        registry.register(new ModelComponentSerializer());
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new DynamicMeshComponentSerializer());
        registry.register(new TerrainComponentSerializer());
        registry.register(new SpriteAnimatorComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        return registry;
    }

    @Test
    void shouldMapEveryCentreTabToItsWorkspaceAndBack() {
        assertEquals(Workspace.WORLD, Workspace.forCenterTab(Workspace.TAB_VIEWPORT, false));
        assertEquals(Workspace.MODELING_3D, Workspace.forCenterTab(Workspace.TAB_VIEWPORT, true));
        assertEquals(Workspace.WORLD, Workspace.forCenterTab(Workspace.TAB_SCENE, false), "the game view during Play is the World");
        assertEquals(Workspace.STUDIO_2D, Workspace.forCenterTab(Workspace.TAB_TILES, false));
        assertThrows(IllegalArgumentException.class, () -> Workspace.forCenterTab("Inspector", false));
        for (Workspace workspace : Workspace.values()) {
            String tab = workspace.centerTab();
            if (workspace == Workspace.ASSETS) {
                assertEquals(null, tab, "Assets is the Content Browser, not a tab");
                continue;
            }
            assertEquals(workspace, Workspace.forCenterTab(tab, workspace == Workspace.MODELING_3D), workspace.name());
        }
        assertEquals("Audio & Music", Workspace.AUDIO_MUSIC.getLabel());
    }

    @Test
    void shouldReachEveryCentreTabButTheGameViewThroughADestination() {
        List<String> tabs = Workspace.destinations().stream().map(Workspace.Destination::tab).filter(Objects::nonNull)
            .distinct().toList();

        assertEquals(List.of(Workspace.TAB_VIEWPORT, Workspace.TAB_SPRITES, Workspace.TAB_TILES, Workspace.TAB_ANIMATIONS,
            Workspace.TAB_MUSIC, Workspace.TAB_VISUAL_SCRIPTING), tabs,
            "with the tabs gone from the header (M1b.2) every editing tab needs a Customize entry; the Scene tab is the game during Play");
        assertEquals(8, Workspace.destinations().size(), "seven workspaces, 2D Studio twice");
        for (Workspace.Destination destination : Workspace.destinations()) {
            if (destination.tab() != null) {
                assertEquals(destination.workspace(), Workspace.forCenterTab(destination.tab(),
                    destination.workspace() == Workspace.MODELING_3D), destination.label());
            }
        }
    }

    @Test
    void shouldOfferToEditASongAndLabelARecordedSoundAsHavingNoEditorYet() {
        Entity speaker = new Entity("Speaker");
        speaker.addComponent(new MusicSourceComponent("music/theme.song"));
        speaker.addComponent(new AudioSourceComponent("door", "sounds/door.wav"));

        List<EditOffer> offers = CustomizeGateway.editSelected(speaker, serializers());

        assertEquals(List.of("music/theme.song", "sounds/door.wav"), CustomizeGateway.assetPathsUsedBy(speaker, serializers()));
        assertEquals(3, offers.size(), offers.toString());
        EditOffer song = offers.get(0);
        assertEquals(new EditOffer("Edit Music theme.song", Workspace.AUDIO_MUSIC, Kind.OPEN_ASSET, "music/theme.song", null, null), song);
        assertEquals(CustomizeGateway.MOVE_TOOL_ACTION, offers.get(1).actionId(), "transforming comes after the files it uses");
        EditOffer sound = offers.get(2);
        assertEquals(Kind.UNAVAILABLE, sound.kind(), "unsupported, labelled, never faked");
        assertTrue(sound.unavailableReason().contains("MC.5"), sound.unavailableReason());
    }

    @Test
    void shouldOfferToSculptASculptAndASculptCopyOfAModelOrShape() {
        Entity rock = new Entity("Rock");
        rock.addComponent(new DynamicMeshComponent("sculpts/rock.sculpt"));
        Entity house = new Entity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        Entity box = new Entity("Box");
        MeshPrimitiveComponent shape = new MeshPrimitiveComponent();
        shape.setMaterialPath("materials/brick.material");
        box.addComponent(shape);

        List<EditOffer> rockOffers = CustomizeGateway.editSelected(rock, serializers());
        List<EditOffer> houseOffers = CustomizeGateway.editSelected(house, serializers());
        List<EditOffer> boxOffers = CustomizeGateway.editSelected(box, serializers());

        assertEquals(List.of(new EditOffer("Edit Rock in 3D", Workspace.MODELING_3D, Kind.RUN_ACTION, null,
            CustomizeGateway.SCULPT_ENTER_ACTION, null), transform("Rock")), rockOffers, "the sculpt file needs no second entry");
        assertEquals("Edit House in 3D (sculpt a copy)", houseOffers.get(0).label());
        assertEquals(CustomizeGateway.SCULPT_FROM_SELECTED_ACTION, houseOffers.get(0).actionId());
        assertEquals(new EditOffer("Edit Mesh Geometry of house.glb", Workspace.MODELING_3D, Kind.UNAVAILABLE, "models/house.glb", null,
            CustomizeGateway.MESH_GEOMETRY_REASON), houseOffers.get(2), "the model's own geometry has no editor yet (R7a)");
        assertEquals(new EditOffer("Edit Material brick.material in Details (shared by every surface that uses it)", Workspace.WORLD,
            Kind.SHOW_DETAILS, "materials/brick.material", null, null), boxOffers.get(1), "a material is edited in Details");
    }

    @Test
    void shouldOfferNothingWithoutASelectionAndOnlyTheTransformForAnEntityThatUsesNoFile() {
        assertEquals(List.of(), CustomizeGateway.editSelected(null, serializers()));
        assertEquals(List.of(transform("Empty")), CustomizeGateway.editSelected(new Entity("Empty"), serializers()));
    }

    private static EditOffer transform(String name) {
        return new EditOffer("Move, Rotate or Scale " + name + " (transform only, the mesh is unchanged)", Workspace.WORLD,
            Kind.RUN_ACTION, null, CustomizeGateway.MOVE_TOOL_ACTION, null);
    }

    private static List<String> labels(List<EditOffer> offers) {
        return offers.stream().map(offer -> offer.label() + (offer.isAvailable() ? "" : " [unavailable]")).toList();
    }

    /**
     * Roadmap M1b.4: what Customize offers for each kind of selection, keeping transforming, editing the material, editing the shared
     * prefab and editing mesh geometry apart. Mesh geometry is never offered as available: that is R7a.
     */
    @Test
    void shouldOfferTheRightEditsForEachKindOfSelection() {
        Entity ground = new Entity("Ground");
        ground.addComponent(new TerrainComponent("terrain/valley.terrain"));
        Entity tree = new Entity("Tree");
        tree.addComponent(new MeshPrimitiveComponent());
        tree.addComponent(new PrefabInstanceComponent("prefabs/level/oak.prefab", java.util.UUID.randomUUID(), true));
        Entity house = new Entity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        Entity hero = new Entity("Hero");
        hero.addComponent(new ModelComponent("characters/hero.glb"));
        hero.addComponent(new CharacterController3DComponent());
        Entity coin = new Entity("Coin");
        coin.addComponent(new SpriteAnimatorComponent(new AssetReference("sprites/coin.sprite")));
        Entity door = new Entity("Door");
        door.addComponent(new AudioSourceComponent("door", "sounds/door.wav"));

        assertEquals(List.of("Sculpt and Paint Terrain valley.terrain (World strip: Terrain)",
            "Move, Rotate or Scale Ground (transform only, the mesh is unchanged)"), labels(CustomizeGateway.editSelected(ground, serializers())));
        assertEquals(Kind.OPEN_TERRAIN_TOOLS, CustomizeGateway.editSelected(ground, serializers()).get(0).kind());

        assertEquals(List.of("Edit Tree in 3D (sculpt a copy)", "Move, Rotate or Scale Tree (transform only, the mesh is unchanged)",
            "Edit Shared Prefab oak.prefab [unavailable]"), labels(CustomizeGateway.editSelected(tree, serializers())));
        assertEquals(CustomizeGateway.SHARED_PREFAB_REASON, CustomizeGateway.editSelected(tree, serializers()).get(2).unavailableReason());

        assertEquals(List.of("Edit House in 3D (sculpt a copy)", "Move, Rotate or Scale House (transform only, the mesh is unchanged)",
            "Edit Mesh Geometry of house.glb [unavailable]"), labels(CustomizeGateway.editSelected(house, serializers())));
        assertEquals(List.of("Edit Hero in 3D (sculpt a copy)", "Move, Rotate or Scale Hero (transform only, the mesh is unchanged)",
            "Edit Mesh Geometry of hero.glb [unavailable]"), labels(CustomizeGateway.editSelected(hero, serializers())));
        assertEquals(List.of("Edit Sprite coin.sprite", "Move, Rotate or Scale Coin (transform only, the mesh is unchanged)"),
            labels(CustomizeGateway.editSelected(coin, serializers())));
        assertEquals(List.of("Move, Rotate or Scale Door (transform only, the mesh is unchanged)", "Edit Sound door.wav [unavailable]"),
            labels(CustomizeGateway.editSelected(door, serializers())));
        assertEquals(List.of(), CustomizeGateway.editSelected(null, serializers()));

        for (Entity entity : List.of(ground, tree, house, hero, coin, door)) {
            for (EditOffer offer : CustomizeGateway.editSelected(entity, serializers())) {
                assertTrue(!offer.label().startsWith("Edit Mesh Geometry") || !offer.isAvailable(), "mesh editing is R7a: " + offer);
                assertTrue(offer.isAvailable() || !offer.unavailableReason().isBlank(), "every unavailable entry says why: " + offer);
            }
        }
    }
}
