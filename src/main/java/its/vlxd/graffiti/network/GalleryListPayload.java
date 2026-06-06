package its.vlxd.graffiti.network;

import its.vlxd.graffiti.gallery.SavedDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record GalleryListPayload(List<SavedDesign> designs) implements CustomPacketPayload {
    public static final Type<GalleryListPayload> TYPE = new Type<>(ResourceLocation.parse("graffiti:gallery_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GalleryListPayload> CODEC = StreamCodec.of(
            GalleryListPayload::encode, GalleryListPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, GalleryListPayload val) {
        buf.writeInt(val.designs().size());
        for (var design : val.designs()) {
            buf.writeLong(design.id().getMostSignificantBits());
            buf.writeLong(design.id().getLeastSignificantBits());
            buf.writeUtf(design.name());
            buf.writeLong(design.authorId().getMostSignificantBits());
            buf.writeLong(design.authorId().getLeastSignificantBits());
            buf.writeLong(design.timestamp());
            buf.writeInt(design.radius());
            buf.writeInt(design.facing().get2DDataValue());
            buf.writeInt(design.blocks().size());
            for (var blockEntry : design.blocks().entrySet()) {
                buf.writeInt(blockEntry.getKey().getX());
                buf.writeInt(blockEntry.getKey().getY());
                buf.writeInt(blockEntry.getKey().getZ());
                var faces = blockEntry.getValue();
                buf.writeInt(faces.size());
                for (var faceEntry : faces.entrySet()) {
                    buf.writeByte(faceEntry.getKey().ordinal());
                    int[][] grid = faceEntry.getValue();
                    for (int u = 0; u < 16; u++) {
                        for (int v = 0; v < 16; v++) {
                            buf.writeInt(grid[u][v]);
                        }
                    }
                }
            }
        }
    }

    private static GalleryListPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        List<SavedDesign> designs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            String name = buf.readUtf();
            UUID authorId = new UUID(buf.readLong(), buf.readLong());
            long timestamp = buf.readLong();
            int radius = buf.readInt();
            Direction facing = Direction.from2DDataValue(buf.readInt());
            int blockCount = buf.readInt();
            Map<BlockPos, Map<Direction, int[][]>> blocks = new LinkedHashMap<>();
            for (int b = 0; b < blockCount; b++) {
                BlockPos pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                int faceCount = buf.readInt();
                Map<Direction, int[][]> faces = new EnumMap<>(Direction.class);
                for (int f = 0; f < faceCount; f++) {
                    Direction dir = Direction.values()[buf.readByte() & 0xFF];
                    int[][] grid = new int[16][16];
                    for (int u = 0; u < 16; u++) {
                        for (int v = 0; v < 16; v++) {
                            grid[u][v] = buf.readInt();
                        }
                    }
                    faces.put(dir, grid);
                }
                blocks.put(pos, faces);
            }
            designs.add(new SavedDesign(id, name, authorId, timestamp, radius, facing, blocks));
        }
        return new GalleryListPayload(designs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
