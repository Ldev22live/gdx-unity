# Character animation support

The desktop model loader discovers authored clips and bone counts, and ModelComponent exposes selection, playback speed, looping and time. The model renderer samples the selected clip. The editor can select and preview clips from the model Details and Timeline workflows.

The Platformer?s `CharacterVisualPlayback` lives on the visual child and reads resolved controller velocity and grounded state. Idle and walking use the corresponding authored clips. At run speed the visual uses the authored Walk at 1.5? because Run is absent. The authored Jump plays once. While falling the jump continues or holds its final pose. Walking off an edge retains Idle in the absence of Fall. Landing returns to Idle or Walk. Climb, Investigate and Talk are available for preview. The current profile supplies no climb or interact input. Transitions switch clips, with no blend graph, root-motion driving or completion callback introduced.

The existing physics controller owns locomotion, collisions and respawn. The biped is stylized. The quadrupeds display their original idle or sitting clips as scenery.
