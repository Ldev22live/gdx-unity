# OpenGameArt conversion

Blender 5.2.2 opened all five local Blender sources with script auto-execution disabled. ForgeRoom already loads glTF 2.0 and GLB with gdx-gltf. The desktop asset catalog also recognizes PNG, JPEG and native sprite documents. FBX, OBJ and Blender source files need a conversion step for this runtime model workflow.

Run `tools/convert_opengameart.py` through Blender with `-b --disable-autoexec --python-exit-code 1 --python tools/convert_opengameart.py -- KIND SOURCE OUTPUT`. Choose `bunny`, `fox`, `rabbit` or `boulder`. For the boulder supply `--stone-color` with the recorded Rocky Terrain 02 base-color file. Export into a staging directory before publishing. The GLBs embed textures and buffer data and keep the authored rig hierarchy needed for skinning. Blender baked constraints and uses the strongest four influences where a source exceeds four.

The paver archive contained only six flat PNG members. The three used maps were decoded and resized from 2048? to 1024? using Lanczos. They remain PNGs for material assignment. The archive, unused maps and original files stay under `asset-sources/opengameart/`.

Desktop editor model scaling uses the existing import-scale control. The platformer visual model is offset to the controller?s foot level and rotated to align the model?s +Z facing with the profile?s -Z direction.

For an approved 2D sheet, Sprite Studio already imports a PNG in equal-size cells through SpriteSheet.importSheet. Fox Bases presents several variants and directions, so the approved source would need its frame regions mapped and its run/jump sequences evaluated before assigning a sprite document.
