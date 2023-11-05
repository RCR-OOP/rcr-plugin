package sky;

import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Loader extends Plugin {

    private final Seq<Timer.Task> tasks = new Seq<>();
    private final Seq<Tuple2<String, Effect>> effects = new Seq<>();
    private final Seq<AuthorizeEntry> authorization = new Seq<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    private final JsonMapper mapper = JsonMapper.builder()
            .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
            .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.ANY)
            .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .addMixIn(Color.class, SerializationSupport.ColorMixin.class)
            .defaultPrettyPrinter(new SerializationSupport.CorrectPrettyPrinter())
            .build();

    private Config config;
    @Nullable
    private String oldparameter;
    private Config.Tips tips;
    private int tipsIndex; // round-robin

    @Override
    public void init() {

        try {

            Fi configFi = Vars.dataDirectory.child("rcr-plugin.json");
            if (configFi.exists()) {
                config = mapper.readValue(configFi.readString(), Config.class);
                Log.info("Config loaded");
            } else {
                String json = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(config = new Config());

                configFi.writeString(json);
                Log.info("Config created (@)", configFi.absolutePath());
            }

            Fi tipsFi = Vars.dataDirectory.child("rcr-tips.json");
            if (tipsFi.exists()) {
                tips = mapper.readValue(tipsFi.readString(), Config.Tips.class);
                Log.info("@ tips loaded", tips.tips.size());
            } else {
                String json = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(tips = new Config.Tips());

                tipsFi.writeString(json);
                Log.info("Tips configuration created (@)", tipsFi.absolutePath());
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        Log.debug("Configuration : @", config);
        Log.debug("Tips          : @", tips);

        Events.run(EventType.Trigger.update, () -> {
            for (Player player : Groups.player) {
                if (!player.unit().moving()) continue;
                var pair = effects.find(t -> t.t1.equals(player.uuid()));
                if (pair != null) {
                    Call.effect(pair.t2, player.x, player.y, 0, Color.white);
                }
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> effects.remove(t -> t.t1.equals(event.player.uuid())));

        if (config.authorization.enabled) {
            Vars.netServer.admins.addActionFilter(action -> action.player != null &&
                    !authorization.contains(a -> a.uuid.equals(action.player.uuid())));

            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (authorization.contains(a -> a.uuid.equals(player.uuid()))) {
                    return null;
                }
                return message;
            });

            Events.on(EventType.PlayerLeave.class, event -> authorization.remove(a -> a.uuid.equals(event.player.uuid())));

            Events.on(EventType.PlayerConnect.class, event -> {
                String uuid = event.player.uuid();
                Administration.PlayerInfo playerInfo = Vars.netServer.admins.getInfo(uuid);
                if (playerInfo.admin) {
                    Vars.netServer.admins.adminPlayer(uuid, event.player.usid());
                    event.player.admin = true;
                }
            });

            Events.on(EventType.PlayerConnect.class, event -> {
                if (!authorization.contains(a -> a.uuid.equals(event.player.uuid())) && !event.player.admin) {

                    AuthorizeEntry authorizeEntry = new AuthorizeEntry(event.player.uuid(), Mathf.random(9999));
                    event.player.sendMessage("[gold]Ваш код [white]'[pink]" + authorizeEntry.code +
                            "[white]' # [gold]Пропишите команду [blue]/cch [white]<[pink]ваш код[white]> [gold]для того чтобы начать играть.");
                    authorization.add(authorizeEntry);
                    Timer.schedule(() -> {
                        Player player = Groups.player.find(p -> p.uuid().equals(authorizeEntry.uuid));
                        if (authorization.contains(authorizeEntry) && player != null) {
                            player.kick(config.authorization.kickMessage, config.authorization.kickDurationMillis);
                        }
                    }, config.authorization.timeoutMillis / 1000f);
                }
            });

            Events.run(EventType.Trigger.update, () -> {
                for (AuthorizeEntry a : authorization) {
                    Groups.player.each(p -> p.uuid().equals(a.uuid) && Time.timeSinceMillis(a.lastRememberTime) > 15000, p -> { // 15 секунд
                        a.lastRememberTime = Time.millis();
                        p.sendMessage("[gold]Ваш код [white]'[pink]" + a.code +
                                "[white]' # [gold]Пропишите команду [blue]/cch [white]<[pink]ваш код[white]> [gold]для того чтобы начать играть.");
                    });
                }
            });
        }

        Events.on(EventType.PlayerConnect.class, event -> {
            if (!config.advertisingBanner.enabled) {
                return;
            }

            if (config.advertisingBanner.mode == Config.Mode.queue) {
                Timer.schedule(new BannerDisplayer(event.player.uuid()), 3);
            } else {
                for (var b : config.advertisingBanner.banners) {
                    float ttl = b.timeLife == -1 ? Float.MAX_VALUE : b.timeLife;
                    if (b instanceof Config.WorldBanner w) {
                        Call.label(event.player.con, w.text, ttl, w.x * Vars.tilesize, w.y * Vars.tilesize);
                    } else if (b instanceof Config.ScreenBanner s) {
                        Call.infoPopup(event.player.con, s.text, ttl, s.align, s.top, s.left, s.bottom, s.right);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        });

        bootstrapSchedulers();
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {

        handler.<Player>register("js", "<code...>", "Execute JavaScript code.", (args, player) -> {
            if (player.admin) {
                String output = Vars.mods.getScripts().runConsole(args[0]);
                player.sendMessage("[white]> " + (isError(output) ? "[#ff341c]" + output : output));
            } else {
                player.sendMessage("[scarlet]You must be admin to use this command.");
            }
        });

        handler.<Player>register("effect", "[off/effectname]", "Включить эффекты движения.", (args, player) -> {
            if (!player.admin) {
                player.sendMessage("[scarlet]Ты должен быть админом чтобы использовать эту команду.");
                return;
            }

            if (args[0].equalsIgnoreCase("off")) {
                effects.remove(t -> t.t1.equals(player.uuid()));
                player.sendMessage("[accent]Эффекты выключены");
                return;
            }

            Effect fx;
            try {
                fx = Reflect.get(Fx.class, args[0]);
            } catch (Throwable t) {
                player.sendMessage("[scarlet]Эффект не найден.");
                return;
            }

            effects.remove(t -> t.t1.equals(player.uuid())); // потому что иммутабельны
            effects.add(new Tuple2<>(player.uuid(), fx));
        });

        if (config.authorization.enabled) {
            CommandHandler.Command votekick = handler.getCommandList().find(c -> c.text.equals("votekick"));
            handler.removeCommand(votekick.text);
            CommandHandler.CommandRunner<Player> votekickRunner = Reflect.get(votekick, "runner");
            handler.<Player>register(votekick.text, votekick.paramText, votekick.description, (args, player) -> {
                if (!authorization.contains(a -> a.uuid.equals(player.uuid()))) {
                    votekickRunner.accept(args, player);
                }
            });

            handler.<Player>register("cch", "<code>", "Пройти авторизацию.", (args, player) -> {
                AuthorizeEntry authorizeEntry = authorization.find(a -> a.uuid.equals(player.uuid()));
                if (authorizeEntry == null) {
                    player.sendMessage("[scarlet]Вам не требуется проходить авторизацию");
                    return;
                }

                if (!Strings.canParseInt(args[0]) || Strings.parseInt(args[0]) != authorizeEntry.code) {
                    player.kick("[scarlet]Не правильно введён код (подозрение что вы бот)", 3_600_000); // 1 час
                    return;
                }

                player.sendMessage("[accent]Вы успешно авторизовались.");
                authorization.remove(authorizeEntry);
            });
        }

        if (config.common.hubAddress != null) {
            handler.<Player>register("hub", "Выйти в лобби.", (args, player) -> {
                String address = config.common.hubAddress;
                int port = Vars.port;
                if (address.contains(":")) {
                    String[] parts = address.split(":");
                    address = parts[0];
                    port = Integer.parseInt(parts[1]);
                }

                Vars.net.pingHost(address, port, host -> {
                    Call.connect(player.con, host.address, host.port);
                    Log.info("&lb@&fi&lk has reconnected to the hub. &fi&lk[&lb@&fi&lk]", player.name, player.uuid());
                }, e -> {
                    player.sendMessage("[scarlet]Server currently is offline!");
                    Log.err(e);
                });
            });
        }
    }

    private boolean isError(String output) {
        try {
            String errorName = output.substring(0, output.indexOf(' ') - 1);
            Class.forName("org.mozilla.javascript." + errorName);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void bootstrapSchedulers() {

        if (tips.enabled) {
            tasks.add(Timer.schedule(() -> {
                if (tipsIndex >= tips.tips.size()) {
                    tipsIndex = 0;
                }

                Call.sendMessage(tips.tips.get(tipsIndex++));
            }, tips.tipsShowInterval / 1000f, tips.tipsShowInterval / 1000f)); // 2 минуты
        }

        if (config.dynamicDays.enabled) {
            tasks.add(Timer.schedule(() -> {
                Vars.state.rules.lighting = true;
                String parameter = getTimeParameter();
                if (!parameter.equals(oldparameter)) {
                    switch (parameter) {
                        case "morning" -> Call.sendMessage("[scarlet][[Server]:[] [white]На сервере наступает [yellow]утро[white]...");
                        case "day" -> Call.sendMessage("[scarlet][[Server]:[] [white]На сервере наступает [orange]день[white]...");
                        case "evening" -> Call.sendMessage("[scarlet][[Server]:[] [white]На сервере наступает [gray]вечер[white]...");
                        case "night" -> Call.sendMessage("[scarlet][[Server]:[] [white]На сервере наступает [blue]ночь[white]...");
                    }

                    oldparameter = parameter;
                    Vars.state.rules.ambientLight = config.dynamicDays.lightingParameters.get(parameter);
                }
            }, 0f, config.dynamicDays.updateInterval / 1000f));
        }

        if (config.updateDesc.enabled) {
            tasks.add(Timer.schedule(() -> Administration.Config.desc.set(formatDesc(getTimeParameter())),
                    0f, config.updateDesc.updateInterval / 1000f));
        }

        if (config.updateChat.enabled) {
            tasks.add(Timer.schedule(() -> Call.sendMessage(config.updateChat.chat),
                    0f, config.updateChat.updateInterval / 1000f));
        }

        if (config.autoGc.enabled) {
            tasks.add(Timer.schedule(System::gc, 0f, config.autoGc.updateInterval / 1000f));
        }
    }

    private String getTimeParameter() {
        LocalDateTime dateTime = LocalDateTime.now();
        for (var parameter : config.dynamicDays.timeParameters.entrySet()) {
            if (parameter.getValue().contains(dateTime.getHour())) {
                return parameter.getKey();
            }
        }
        throw new IllegalStateException("Incorrect time parameter!");
    }

    private String formatDesc(String status) {
        return config.updateDesc.desc.replace("%localtime%", LocalDateTime.now().format(formatter))
                .replace("%status%", localize(status));
    }

    private String localize(String status) {
        return switch (status) {
            case "morning" -> "утро";
            case "day" -> "день";
            case "evening" -> "вечер";
            case "night" -> "ночь";
            default -> throw new IllegalArgumentException("Incorrect status!");
        };
    }

    static class AuthorizeEntry {
        public final String uuid;
        public final int code;

        public long lastRememberTime = Time.millis();

        public AuthorizeEntry(String uuid, int code) {
            this.uuid = uuid;
            this.code = code;
        }
    }

    class BannerDisplayer extends Timer.Task {
        public final String uuid;

        public int idx;

        public BannerDisplayer(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public void run() {
            Player player = Groups.player.find(p -> p.uuid().equals(uuid));
            if (player == null) {
                return;
            }

            if (idx >= config.advertisingBanner.banners.size()) {
                idx = 0;
            }

            Config.Banner b = config.advertisingBanner.banners.get(idx++);
            if (b instanceof Config.WorldBanner w) {
                Call.label(player.con, w.text, w.timeLife, w.x * Vars.tilesize, w.y * Vars.tilesize);
            } else if (b instanceof Config.ScreenBanner s) {
                Call.infoPopup(player.con, s.text, s.timeLife, s.align, s.top, s.left, s.bottom, s.right);
            } else {
                throw new IllegalStateException();
            }

            Timer.schedule(this, b.timeLife);
        }
    }

    public record Tuple2<T1, T2>(T1 t1, T2 t2) {}
}
