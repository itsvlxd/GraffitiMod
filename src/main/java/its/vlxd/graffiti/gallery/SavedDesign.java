package its.vlxd.graffiti.gallery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public record SavedDesign(
    UUID id,
    String name,
    UUID authorId,
    long timestamp,
    int radius,
    Direction facing,
    Map<BlockPos, Map<Direction, int[][]>> blocks
) {
    public void write(DataOutputStream out) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        out.writeUTF(name);
        out.writeLong(authorId.getMostSignificantBits());
        out.writeLong(authorId.getLeastSignificantBits());
        out.writeLong(timestamp);
        out.writeInt(radius);
        out.writeInt(facing.get2DDataValue());
        out.writeInt(blocks.size());
        for (var blockEntry : blocks.entrySet()) {
            BlockPos pos = blockEntry.getKey();
            out.writeInt(pos.getX());
            out.writeInt(pos.getY());
            out.writeInt(pos.getZ());
            var faces = blockEntry.getValue();
            out.writeInt(faces.size());
            for (var faceEntry : faces.entrySet()) {
                out.writeByte(faceEntry.getKey().ordinal());
                int[][] grid = faceEntry.getValue();
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        out.writeInt(grid[u][v]);
                    }
                }
            }
        }
    }

    public static SavedDesign read(DataInputStream in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        String name = in.readUTF();
        UUID authorId = new UUID(in.readLong(), in.readLong());
        long timestamp = in.readLong();
        int radius = in.readInt();
        Direction facing = Direction.from2DDataValue(in.readInt());
        int blockCount = in.readInt();
        Map<BlockPos, Map<Direction, int[][]>> blocks = new LinkedHashMap<>();
        for (int b = 0; b < blockCount; b++) {
            BlockPos pos = new BlockPos(in.readInt(), in.readInt(), in.readInt());
            int faceCount = in.readInt();
            Map<Direction, int[][]> faces = new EnumMap<>(Direction.class);
            for (int f = 0; f < faceCount; f++) {
                Direction dir = Direction.values()[in.readByte() & 0xFF];
                int[][] grid = new int[16][16];
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        grid[u][v] = in.readInt();
                    }
                }
                faces.put(dir, grid);
            }
            blocks.put(pos, faces);
        }
        return new SavedDesign(id, name, authorId, timestamp, radius, facing, blocks);
    }

    public int pixelCount() {
        int count = 0;
        for (var faces : blocks.values()) {
            for (var grid : faces.values()) {
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        if (grid[u][v] != 0) count++;
                    }
                }
            }
        }
        return count;
    }
}
