package sky;

import arc.graphics.Color;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.*;

public class Config {

    public Common common = new Common();
    public Authorization authorization = new Authorization();
    public DynamicDays dynamicDays = new DynamicDays();
    public UpdateDesc updateDesc = new UpdateDesc();
    public UpdateChat updateChat = new UpdateChat();
    public AutoGc autoGc = new AutoGc();
    public AdvertisingBanner advertisingBanner = new AdvertisingBanner();

    @Override
    public String toString() {
        return "Config{" +
                "common=" + common +
                ", authorization=" + authorization +
                ", dynamicDays=" + dynamicDays +
                ", updateDesc=" + updateDesc +
                ", updateChat=" + updateChat +
                ", autoGc=" + autoGc +
                ", advertisingBanner=" + advertisingBanner +
                '}';
    }

    public enum Mode {
        queue, // вывод поочереди всех банеров; timeLife не должен быть отрицательным и он обозначает время в секундах
        all // вывод всего и сразу
    }

    public static class PluginShard {
        public boolean enabled;
    }

    public static class Common {
        public String hubAddress;

        @Override
        public String toString() {
            return "Common{" +
                    "hubAddress='" + hubAddress + '\'' +
                    '}';
        }
    }

    public static class Tips extends PluginShard {
        public long tipsShowInterval = 120000;
        public List<String> tips = Collections.emptyList();

        @Override
        public String toString() {
            return "Tips{" +
                    "tipsShowInterval=" + tipsShowInterval +
                    ", tips=" + tips +
                    ", enabled=" + enabled +
                    '}';
        }
    }

    public static class Authorization extends PluginShard {
        public String kickMessage = "[scarlet]Превышено время авторизации!";
        public long kickDurationMillis = 1000 * 60 * 2; // 2 минуты
        public long timeoutMillis = 180000; // 3 минуты

        @Override
        public String toString() {
            return "Authorization{" +
                    "kickMessage='" + kickMessage + '\'' +
                    ", kickDurationMillis=" + kickDurationMillis +
                    ", timeoutMillis=" + timeoutMillis +
                    '}';
        }
    }

    public static class AutoGc extends PluginShard {
        public long updateInterval = 3600000; // 1 hour

        @Override
        public String toString() {
            return "AutoGc{" +
                    "enabled=" + enabled +
                    ", updateInterval=" + updateInterval +
                    '}';
        }
    }

    public static class DynamicDays extends PluginShard {
        public long updateInterval = 30000; // 30 seconds
        public Map<String, Color> lightingParameters = mapOf(
                "morning", new Color(200f, 200f, 200f, 0.2f),
                "day", new Color(0.005f, 0.0f, 0.02f, 0.0f),
                "evening", new Color(0.0f, 0.0f, 0.0f, 0.56f),
                "night", new Color(0.005f, 0.0f, 0.02f, 0.8f)
        );
        public Map<String, List<Integer>> timeParameters = mapOf(
                "morning", Arrays.asList(6, 7, 8, 9, 10, 11),
                "day", Arrays.asList(12, 13, 14, 15, 16, 17),
                "evening", Arrays.asList(18, 19, 20, 21, 22, 23),
                "night", Arrays.asList(24, 0, 1, 2, 3, 4, 5)
        );

        @Override
        public String toString() {
            return "DynamicDays{" +
                    "enabled=" + enabled +
                    ", updateInterval=" + updateInterval +
                    ", lightingParameters=" + lightingParameters +
                    ", timeParameters=" + timeParameters +
                    '}';
        }
    }

    public static class UpdateDesc extends PluginShard {
        public boolean enabled = true;
        public long updateInterval = 30000; // 30 seconds
        public String desc = "[blue]vk[gold].[blue]com[gold]/[green]rcrms [pink]# [gray]%localtime% (%status%)";

        @Override
        public String toString() {
            return "UpdateDesc{" +
                    "enabled=" + enabled +
                    ", updateInterval=" + updateInterval +
                    ", desc='" + desc + '\'' +
                    '}';
        }
    }

    public static class UpdateChat extends PluginShard {
        public long updateInterval = 30000; // in millis
        public String chat = "[scarlet][[Server]:[] [red]Подпишитесь [gold]на наш [blue]ВК[gold]: [blue]vk[pink].[blue]com[pink]/[green]rcrms [pink]# [gold]Хотите пропустить карту[blue]? [gold]Команда[blue]: [orange]/rtv";

        @Override
        public String toString() {
            return "UpdateChat{" +
                    "enabled=" + enabled +
                    ", updateInterval=" + updateInterval +
                    ", chat='" + chat + '\'' +
                    '}';
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = WorldBanner.class, name = "world"),
            @JsonSubTypes.Type(value = ScreenBanner.class, name = "screen")
    }) // будущего не случилось и ещё нет поддержки sealed'ов
    public abstract static sealed class Banner {
        public String text = "text";
        public long timeLife = 3; // -1 для бесконечно висящего банера

        @Override
        public String toString() {
            return "Banner{" +
                    "text='" + text + '\'' +
                    ", timeLife=" + timeLife +
                    '}';
        }
    }

    public static final class WorldBanner extends Banner {
        public int x, y;

        @Override
        public String toString() {
            return "WorldBanner{" +
                    "x=" + x +
                    ", y=" + y +
                    "} " + super.toString();
        }
    }

    public static final class ScreenBanner extends Banner {
        public int align, top, left, bottom, right;

        @Override
        public String toString() {
            return "ScreenBanner{" +
                    "align=" + align +
                    ", top=" + top +
                    ", left=" + left +
                    ", bottom=" + bottom +
                    ", right=" + right +
                    "} " + super.toString();
        }
    }

    public static class AdvertisingBanner extends PluginShard {
        public Mode mode = Mode.queue;
        public List<Banner> banners = Arrays.asList(
                new WorldBanner(),
                new ScreenBanner()
        );

        @Override
        public String toString() {
            return "AdvertisingBanner{" +
                    "mode=" + mode +
                    ", banners=" + banners +
                    ", enabled=" + enabled +
                    '}';
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... values) {
        Map<K, V> map = new LinkedHashMap<>();

        for (int i = 0; i < values.length / 2; ++i) {
            map.put((K) values[i * 2], (V) values[i * 2 + 1]);
        }

        return map;
    }
}
