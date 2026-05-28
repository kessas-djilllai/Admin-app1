import os

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

# The imports should stay up, we move lines 71 to 285
# Note: lines list is 0-indexed. line 72 is index 71.
# Let's verify what's at index 71.
assert lines[71].strip() == "@Composable"
assert "fun DeviceMediaGalleryTab" in lines[72]
assert lines[284].strip() == "}"
assert "fun ExoPlayerVideoView" in lines[255]

block_to_move = lines[71:285]
del lines[71:285]

lines.extend(block_to_move)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(lines)

print("Done")
