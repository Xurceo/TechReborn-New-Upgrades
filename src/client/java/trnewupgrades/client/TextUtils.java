package trnewupgrades.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class TextUtils {
    private static final int[] OMNI_COLORS = new int[] {
        0x00AD7F,
        0x51FFFF,
        0xA8FFF6
    };
    private static final long CYCLE_MS = 1800L;

    private TextUtils() {
    }

    public static MutableComponent makeOmni(String input) {
        MutableComponent result = Component.empty();
        if (input == null || input.isEmpty()) {
            return result;
        }

        int length = input.length();
        double cycle = (System.currentTimeMillis() % CYCLE_MS) / (double) CYCLE_MS;

        for (int i = 0; i < length; i++) {
            double pos = length <= 1 ? 0.0D : (double) i / (length - 1);
            double phase = ((cycle + pos) % 1.0D + 1.0D) % 1.0D;
            double pingPong = phase < 0.5D ? phase * 2.0D : (1.0D - phase) * 2.0D;
            int color = colorAtPingPong(pingPong);

            result.append(Component.literal(String.valueOf(input.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }

        return result;
    }

    private static int colorAtPingPong(double phase) {
        double scaled = phase * (OMNI_COLORS.length - 1);
        int index = Math.min((int) Math.floor(scaled), OMNI_COLORS.length - 1);
        int nextIndex = Math.min(index + 1, OMNI_COLORS.length - 1);
        double localProgress = scaled - index;
        localProgress = localProgress * localProgress * (3.0D - 2.0D * localProgress);
        return blend(OMNI_COLORS[index], OMNI_COLORS[nextIndex], localProgress);
    }

    private static int blend(int start, int end, double progress) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        int r = lerp((start >> 16) & 0xFF, (end >> 16) & 0xFF, t);
        int g = lerp((start >> 8) & 0xFF, (end >> 8) & 0xFF, t);
        int b = lerp(start & 0xFF, end & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}