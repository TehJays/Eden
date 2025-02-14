package rip.diamond.practice.profile;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import rip.diamond.practice.Eden;
import rip.diamond.practice.Language;
import rip.diamond.practice.util.option.FalseOption;
import rip.diamond.practice.util.option.Option;
import rip.diamond.practice.util.option.TrueOption;

import java.util.List;

@Getter
@AllArgsConstructor
public enum ProfileSettings {

    TIME_CHANGER(
            Material.WATCH,
            Language.PROFILE_SETTINGS_TIME_CHANGER_NAME.toString(),
            Language.PROFILE_SETTINGS_TIME_CHANGER_LORE.toStringList(),
            "eden.settings.time-changer",
            ImmutableList.of(
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.time-changer").equals("normal"), Language.PROFILE_SETTINGS_TIME_CHANGER_NORMAL_SERVER_TIME.toString()) {
                        @Override
                        public void run(Player player) {
                            player.resetPlayerTime();
                        }

                        @Override
                        public String getValue() {
                            return "normal";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.time-changer").equals("day"), Language.PROFILE_SETTINGS_TIME_CHANGER_DAY.toString()) {
                        @Override
                        public void run(Player player) {
                            player.setPlayerTime(1000, false);
                        }

                        @Override
                        public String getValue() {
                            return "day";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.time-changer").equals("afternoon"), Language.PROFILE_SETTINGS_TIME_CHANGER_AFTERNOON.toString()) {
                        @Override
                        public void run(Player player) {
                            player.setPlayerTime(6000, false);
                        }

                        @Override
                        public String getValue() {
                            return "afternoon";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.time-changer").equals("night"), Language.PROFILE_SETTINGS_TIME_CHANGER_NIGHT.toString()) {
                        @Override
                        public void run(Player player) {
                            player.setPlayerTime(13000, false);
                        }

                        @Override
                        public String getValue() {
                            return "night";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.time-changer").equals("midnight"), Language.PROFILE_SETTINGS_TIME_CHANGER_MIDNIGHT.toString()) {
                        @Override
                        public void run(Player player) {
                            player.setPlayerTime(18000, false);
                        }

                        @Override
                        public String getValue() {
                            return "midnight";
                        }
                    }
            )
    ),
    ARENA_SELECTION(
            Material.GRASS,
            Language.PROFILE_SETTINGS_ARENA_SELECTION_NAME.toString(),
            Language.PROFILE_SETTINGS_ARENA_SELECTION_LORE.toStringList(),
            "eden.settings.arena-selection",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.arena-selection"))
    ),
    MATCH_SCOREBOARD(
            Material.ITEM_FRAME,
            Language.PROFILE_SETTINGS_MATCH_SCOREBOARD_NAME.toString(),
            Language.PROFILE_SETTINGS_MATCH_SCOREBOARD_LORE.toStringList(),
            "eden.settings.match-scoreboard",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.match-scoreboard"))
    ),
    ALLOW_DUEL_REQUEST(
            Material.DIAMOND_SWORD,
            Language.PROFILE_SETTINGS_ALLOW_DUEL_REQUEST_NAME.toString(),
            Language.PROFILE_SETTINGS_ALLOW_DUEL_REQUEST_LORE.toStringList(),
            "eden.settings.allow-duel-request",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.allow-duel-request"))
    ),
    ALLOW_PARTY_INVITE(
            Material.NETHER_STAR,
            Language.PROFILE_SETTINGS_ALLOW_PARTY_INVITE_NAME.toString(),
            Language.PROFILE_SETTINGS_ALLOW_PARTY_INVITE_LORE.toStringList(),
            "eden.settings.allow-party-invite",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.allow-party-invite"))
    ),
    SPECTATOR_VISIBILITY(
            Material.SKULL_ITEM,
            Language.PROFILE_SETTINGS_SPECTATOR_VISIBILITY_NAME.toString(),
            Language.PROFILE_SETTINGS_SPECTATOR_VISIBILITY_LORE.toStringList(),
            "eden.settings.spectator-visibility",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.spectator-visibility"))
    ),
    SPECTATOR_JOIN_LEAVE_MESSAGE(
            Material.FEATHER,
            Language.PROFILE_SETTINGS_SPECTATOR_JOIN_LEAVE_MESSAGE_NAME.toString(),
            Language.PROFILE_SETTINGS_SPECTATOR_JOIN_LEAVE_MESSAGE_LORE.toStringList(),
            "eden.settings.spectator-join-leave-message",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.spectator-join-leave-message"))
    ),
    EVENT_ANNOUNCEMENT(
            Material.EMERALD,
            Language.PROFILE_SETTINGS_EVENT_ANNOUNCEMENT_NAME.toString(),
            Language.PROFILE_SETTINGS_EVENT_ANNOUNCEMENT_LORE.toStringList(),
            "eden.settings.event-announcement",
            getBooleanDefaultOptions(Eden.INSTANCE.getConfigFile().getBoolean("profile.default-settings.event-announcement"))
    ),
    PING_RANGE(
            Material.STICK,
            Language.PROFILE_SETTINGS_PING_RANGE_NAME.toString(),
            Language.PROFILE_SETTINGS_PING_RANGE_LORE.toStringList(),
            "eden.settings.ping-range",
            ImmutableList.of(
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("infinite"), Language.PROFILE_SETTINGS_PING_RANGE_UNLIMITED.toString()) {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return Integer.MAX_VALUE + "";
                        }

                        @Override
                        public String toString() {
                            return "∞";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("300"), "300") {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return "300";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("200"), "200") {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return "200";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("150"), "150") {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return "150";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("100"), "100") {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return "100";
                        }
                    },
                    new Option(Eden.INSTANCE.getConfigFile().getString("profile.default-settings.ping-range").equals("50"), "50") {
                        @Override
                        public void run(Player player) {

                        }

                        @Override
                        public String getValue() {
                            return "50";
                        }
                    }
            )
    )
    ;

    private final Material icon;
    private final String name;
    private final List<String> description;
    private final String permission;
    private final List<Option> options;

    public Option getDefault() {
        return options.stream().filter(Option::isDefault_).findFirst().orElse(null);
    }

    public Option getOption(String name) {
        return getOptions().stream().filter(option -> option.getName().equals(name)).findFirst().orElse(null);
    }

    public Option getNextOption(Option option) {
        int index = findIndex(option);
        int nextIndex = index + 1 >= getOptions().size() ? 0 : index + 1;
        return getOptions().get(nextIndex);
    }

    public Option getLastOption(Option option) {
        int index = findIndex(option);
        int nextIndex = index - 1 < 0 ? getOptions().size() - 1 : index - 1;
        return getOptions().get(nextIndex);
    }

    public Option find(String value) {
        return getOptions().stream().filter(option -> option.getValue().equals(value)).findFirst().orElse(null);
    }

    private int findIndex(Option option) {
        return getOptions().indexOf(option);
    }

    private static List<Option> getBooleanDefaultOptions(boolean defaultValue) {
        return ImmutableList.of(
                new TrueOption(defaultValue),
                new FalseOption(!defaultValue)
        );
    }

}
