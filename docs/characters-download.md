# Character downloads for ForgeRoom samples

Open each OpenGameArt listing in your browser and select the named file under **File(s)**. Start with the first two entries. Each listing identifies its asset as [CC0](https://creativecommons.org/publicdomain/zero/1.0/).

| Sample | Character | File to download | What to check before import |
|---|---|---|---|
| 2.5D and 3D Platformer profiles | **Detective Bunny** by atornity | [`bun_tective.blend` (3.8 MB) on OpenGameArt](https://opengameart.org/content/detective-bunny) | The bipedal rabbit comes rigged with idle, walk and climb among its listed clips. Check the silhouette at platformer camera distance and add run, jump, fall and land clips as needed. |
| Action RPG Valley and isometric courtyard | **Mansedj** by Djsedj | [`miguel_blender3-6-1.zip` on OpenGameArt](https://opengameart.org/content/mansedj-blender-279-blender-361) | Start with the Blender 3.6.1 archive and check its rig, texture dependencies and geometry. The same listing offers the older `Mansc_v24-Mar-2020.zip` and separate `Mansc_Texts.zip` as fallback sources. The older mesh is dense, and warrior equipment and combat clips still need review. |
| Action RPG fallback | **Mast** by Djsedj | [`mast.zip` on OpenGameArt](https://opengameart.org/content/mast-blender-425) | Rigged anthropomorphic lion base for Blender 4.2.5. The listing describes vertex paint and UVs that need reworking. Budget for UVs, textures, equipment and animation before adopting it. |

Keep the original downloads together and share their local paths for validation. ForgeRoom uses glTF/GLB for runtime models. The Blender sources need inspection and export before integration. The 2D Platformer would need a sprite sheet rendered or authored for its orthographic view.

See the [character asset pipeline](assets/character-asset-pipeline.md) for conversion and [TD-094](TECHNICAL_DECISIONS.md#td-094-third-party-asset-manifest-and-download-rules-roadmap-m2b1-first-part) for manual download and provenance requirements.
