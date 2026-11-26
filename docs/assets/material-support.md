# Material support for the sample refresh

ForgeRoom uses glTF PBR materials and shared editable `.material` assets. Desktop supports base color, normal, metallic and roughness, occlusion, emissive channels and alpha modes. Native materials use trilinear mipmap minification and linear magnification. GLB materials use their exported samplers. The import adds no engine-wide compression or filtering setting.

Hex Paver B uses its base-color, normal and AO PNG maps. Its roughness is a scalar 0.88 and its metallic factor is zero. The source archive supplied no roughness or metallic map. Normal-map channel orientation was retained from the source. The animal exports retain the intended textured materials. Bunny?s author material is unlit and the fox and rabbit skin materials use their normal maps. The existing painted Valley heightfield remains editable and the boulders draw independently as textured props.
