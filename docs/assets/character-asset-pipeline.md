# Character asset pipeline

Use a separate visual child under the scene's player entity. Keep the existing Character Controller 3D on the gameplay root and author its dimensions to match the intended interaction envelope. Keep movement and blocking dimensions independent of the character's visible equipment.

1. Preserve the source and license evidence separately from runtime files. Check archive paths, dependencies, file types and hashes before extraction into a staging area. Use the existing TD-094 importer and runtime manifest for approved files.
2. Blender 5.2 is installed on this workstation. Check older files for unsupported materials and modifiers. Inspect skeleton rest transforms, bone weights, bind pose, UVs, normals and tangent-space normal maps. Establish Y-up orientation, floor contact and a consistent forward axis in the exported GLB. Apply the scene-specific scale through the visual child.
3. Author or retarget in-place clips. Platformer needs idle, walk, run, jump, fall and land before combat states. RPG needs locomotion and dodge plus attack, block, hit and death clips. Check foot sliding and retargeting in the exported file.
4. Check that ForgeRoom loads the GLB, resolves all textures and skins, and can sample every required animation. Test pose selection and transition timing through actual input, then save and reopen the scene.

## Character gaps

The CC0 Mansedj listing identifies a genuinely anthropomorphic lion and a rig. Runtime suitability, armor, locomotion and combat clips still need evaluation. The asset has not been acquired. The platformer search found quadrupeds and an anthropomorphic 2D base offered under attribution licenses. Detective Bunny is a CC0 bipedal rabbit candidate with an authored rig and several clips. Its fidelity, runtime fitness and platformer animation coverage need evaluation. The built-in MakeHuman data is a human base. Adapting it would require modeling the species anatomy, retopology, fur and clothing materials, repainted weights and animation validation.
