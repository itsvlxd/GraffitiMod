package its.vlxd.graffiti.client;

import its.vlxd.graffiti.GraffitiMod;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class SpraySoundInstance extends AbstractSoundInstance {
    public SpraySoundInstance(double x, double y, double z) {
        super(GraffitiMod.SPRAY_CAN_PAINT.get(), SoundSource.PLAYERS, RandomSource.create());
        this.looping = true;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = 1.0f;
        this.pitch = 1.0f;
    }
}
