package rip.diamond.practice.match;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EntityLightning;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityWeather;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.github.paperspigot.PaperSpigotConfig;
import rip.diamond.practice.Eden;
import rip.diamond.practice.Language;
import rip.diamond.practice.arenas.ArenaDetail;
import rip.diamond.practice.event.MatchEndEvent;
import rip.diamond.practice.event.MatchPlayerDeathEvent;
import rip.diamond.practice.event.MatchStartEvent;
import rip.diamond.practice.event.MatchStateChangeEvent;
import rip.diamond.practice.kits.Kit;
import rip.diamond.practice.match.task.*;
import rip.diamond.practice.match.team.Team;
import rip.diamond.practice.match.team.TeamColor;
import rip.diamond.practice.match.team.TeamPlayer;
import rip.diamond.practice.profile.PlayerProfile;
import rip.diamond.practice.profile.PlayerState;
import rip.diamond.practice.profile.ProfileSettings;
import rip.diamond.practice.profile.cooldown.Cooldown;
import rip.diamond.practice.profile.cooldown.CooldownType;
import rip.diamond.practice.profile.task.ProfileCooldownTask;
import rip.diamond.practice.queue.QueueType;
import rip.diamond.practice.util.*;
import rip.diamond.practice.util.exception.PracticeUnexpectedException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public abstract class Match {

    private final Eden plugin = Eden.INSTANCE;

    @Getter private static final Map<UUID, Match> matches = new HashMap<>();
    @Getter private static final Map<UUID, PostMatchInventory> postMatchInventories = new HashMap<>();

    private final UUID uuid = UUID.randomUUID();
    @Setter private boolean duel = true;
    @Setter private QueueType queueType = QueueType.UNRANKED;
    private final ArenaDetail arenaDetail;
    private final Kit kit;
    private final List<Team> teams;
    private MatchState state = MatchState.STARTING;
    private final List<UUID> spectators = new ArrayList<>();
    private final List<MatchEntity> entities = new ArrayList<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private final List<TaskTicker> tasks = new ArrayList<>();

    private final long startTimestamp = System.currentTimeMillis();

    public static void init() {
        new MatchClearItemTask();
        new MatchPostMatchInventoriesClearTask();
        new ProfileCooldownTask();

        new MatchMovementHandler();

        if (Eden.INSTANCE.getConfigFile().getBoolean("match.fix-hit-count-error")) {
            Bukkit.getWorlds().forEach(world -> {
                ((CraftWorld) world).getHandle().paperSpigotConfig.disablePlayerCrits = true;
            });

            try {
                PaperSpigotConfig.config.set("world-settings.default.game-mechanics.disable-player-crits", true);
                Field field = PaperSpigotConfig.class.getDeclaredField("CONFIG_FILE");
                field.setAccessible(true);
                File file = (File) field.get(null);
                PaperSpigotConfig.config.save(file);
            } catch (IllegalAccessException | IOException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    public Match(ArenaDetail arenaDetail, Kit kit, List<Team> teams) {
        this.arenaDetail = arenaDetail;
        this.kit = kit;
        this.teams = teams;

        matches.put(uuid, this);
    }

    /**
     * Start the match
     */
    public void start() {
        setupTeamSpawnLocation();

        //Arena setup logic

        //Check if the kit allows block building and breaking. If yes, we set the ArenaDetail to using to prevent player using the same arena
        if (kit.getGameRules().isBuild() || kit.getGameRules().isSpleef()) {
            if (Match.getMatches().values().stream().filter(match -> match != this).anyMatch(match -> (match.getKit().getGameRules().isBuild() || match.getKit().getGameRules().isSpleef()) && match.getArenaDetail() == arenaDetail)) {
                end(true, "其他戰鬥正在使用這個場地, 並且該戰鬥的職業需要使用方塊");
                return;
            }
            arenaDetail.setUsing(true);
        }

        //Setup player logic
        for (Player player : getMatchPlayers()) {
            PlayerProfile profile = PlayerProfile.get(player);
            profile.setMatch(this);
            profile.setPlayerState(PlayerState.IN_MATCH);

            PlayerUtil.reset(player);

            player.addPotionEffects(kit.getEffects());
            player.setMaximumNoDamageTicks(kit.getDamageTicks());

            if (kit.getGameRules().isReceiveKitLoadoutBook()) {
                for (ItemStack itemStack : profile.getKitData().get(kit.getName()).getKitItems(kit)) {
                    if (itemStack.getType().equals(Material.BOOK)) {
                        player.getInventory().setItem(8, itemStack);
                    } else {
                        player.getInventory().addItem(itemStack);
                    }
                }
            } else {
                kit.getKitLoadout().apply(this, player);
            }
            player.updateInventory();

            //Create the health display under NameTag, if needed
            if (kit.getGameRules().isShowHealth()) {
                plugin.getScoreboardHandler().getScoreboard(player).registerHealthObjective();
            }

            //Set up the knockback
            plugin.getSpigotAPI().getKnockback().applyKnockback(player, kit.getGameRules().getKnockbackName());
        }

        //Teleport players into their team spawn
        teams.forEach(team -> team.teleport(team.getSpawnLocation()));

        //Set team's color
        if (getMatchType() != MatchType.FFA) {
            for (int i = 0; i < teams.size(); i++) {
                Team team = teams.get(i);
                team.setTeamColor(TeamColor.values()[i]);
                team.getTeamPlayers().forEach(team::dye);
            }
        }

        MatchStartEvent event = new MatchStartEvent(this);
        event.call();

        new MatchNewRoundTask(this, null, false);
    }

    /**
     * @param profile A random profile from match players which is alive. This is used to create a score cooldown
     * @param scorer The TeamPlayer who scored the point
     */
    public void score(PlayerProfile profile, TeamPlayer entity, TeamPlayer scorer) {
        profile.getCooldowns().put(CooldownType.SCORE, new Cooldown(3));

        Team team = getTeam(scorer);
        team.handlePoint();
        if (state == MatchState.FIGHTING && team.getPoints() < kit.getGameRules().getMaximumPoints()) {
            if (kit.getGameRules().isOnlyLoserResetPositionWhenGetPoint()) {
                new MatchRespawnTask(this, entity);
                return;
            }
            new MatchFireworkTask(team.getTeamColor().getDyeColor().getColor(), this);
            new MatchNewRoundTask(this, scorer, true);
            return;
        }

        getOpponentTeam(team).getAliveTeamPlayers().forEach(teamTarget -> die(teamTarget.getPlayer(), false));
    }

    public void die(Player deadPlayer, boolean disconnected) {
        TeamPlayer teamPlayer = getTeamPlayer(deadPlayer);
        PlayerProfile profile = PlayerProfile.get(deadPlayer);
        Team team = getTeam(deadPlayer);

        if (!teamPlayer.isAlive()) {
            return;
        }

        teamPlayer.setAlive(false);
        teamPlayer.setDisconnected(disconnected);

        getMatchPlayers().forEach(VisibilityController::updateVisibility);

        //Setup Post-Match Inventory
        PostMatchInventory postMatchInventory = new PostMatchInventory(teamPlayer);
        postMatchInventories.put(teamPlayer.getUuid(), postMatchInventory);

        displayDeathMessage(teamPlayer, deadPlayer);

        //Play lightning effect and death animation
        MatchPlayerDeathEvent event = new MatchPlayerDeathEvent(this, deadPlayer);
        event.call();
        if (event.isPlayLightingEffect()) {
            EntityLightning lightning = new EntityLightning(((CraftPlayer)deadPlayer).getHandle().getWorld(), deadPlayer.getLocation().getX(), deadPlayer.getLocation().getY(), deadPlayer.getLocation().getZ(), true, false);
            for (Player player : getPlayersAndSpectators()) {
                ((CraftPlayer)player).getHandle().playerConnection.sendPacket(new PacketPlayOutSpawnEntityWeather(lightning));
                player.playSound(deadPlayer.getLocation(), Sound.AMBIENCE_THUNDER, 1.0F, 1.0F);
            }
        }
        if (event.isPlayDeathEffect()) {
            Util.playDeathAnimation(deadPlayer, getPlayersAndSpectators().stream().filter(player -> player != deadPlayer).collect(Collectors.toList()));
        }

        //Check if there's only one team survives. If yes, end the match
        if (canEnd()) {
            end();
        } else {
            PlayerUtil.spectator(deadPlayer);
            profile.setupItems();
        }
    }

    public void respawn(TeamPlayer teamPlayer) {
        Player player = teamPlayer.getPlayer();
        Team team = getTeam(player);
        team.getSpawnLocation().clone().add(0,0,0).getBlock().setType(Material.AIR);
        team.getSpawnLocation().clone().add(0,1,0).getBlock().setType(Material.AIR);
        Util.teleport(player, team.getSpawnLocation());
        player.setAllowFlight(false);
        player.setFlying(false);
        teamPlayer.setRespawning(false);
        getMatchPlayers().forEach(VisibilityController::updateVisibility);

        PlayerProfile profile = PlayerProfile.get(player);
        //So arrow will not be duplicated if GiveBackArrow is on
        profile.getCooldowns().get(CooldownType.ARROW).cancelCountdown();

        player.setExp(0);
        player.setLevel(0);

        teamPlayer.setProtectionUntil(System.currentTimeMillis() + (3 * 1000));
        teamPlayer.respawn(this);
        Language.MATCH_RESPAWN_MESSAGE.sendMessage(player);
    }

    public void displayDeathMessage(TeamPlayer teamPlayer, Player deadPlayer) {
        if (teamPlayer.isDisconnected()) {
            getPlayersAndSpectators().forEach(player -> Language.MATCH_DEATH_MESSAGE_DISCONNECT.sendMessage(player, getRelationColor(player, deadPlayer), teamPlayer.getUsername()));
        } else if (teamPlayer.getLastHitDamager() != null && teamPlayer.getLastHitDamager().getPlayer() != null) {
            getPlayersAndSpectators().forEach(player -> Language.MATCH_DEATH_MESSAGE_KILLED.sendMessage(player, getRelationColor(player, deadPlayer), teamPlayer.getUsername(), getRelationColor(player, teamPlayer.getLastHitDamager().getPlayer()), teamPlayer.getLastHitDamager().getUsername()));
        } else {
            getPlayersAndSpectators().forEach(player -> Language.MATCH_DEATH_MESSAGE_DEFAULT.sendMessage(player, getRelationColor(player, deadPlayer), teamPlayer.getUsername()));
        }
    }

    public void end() {
        end(false, null);
    }

    public void end(boolean forced, String reason) {
        if (state == MatchState.ENDING) {
            return;
        }

        Common.debug("正在結束 " + getClass().getSimpleName() + " 戰鬥 (" + teams.stream().map(team -> team.getLeader().getUsername()).collect(Collectors.joining(" vs ")) + ") (職業: " + kit.getName() + ") (地圖: " + arenaDetail.getArena().getName() + ") (UUID: " + uuid + ")");
        state = MatchState.ENDING;

        MatchEndEvent event = new MatchEndEvent(this, forced);
        event.call();

        if (forced) {
            broadcastMessage(Language.MATCH_FORCE_END_MESSAGE.toString(reason));
        } else {
            //Setup Post-Match Inventories
            for (TeamPlayer teamPlayer : getWinningPlayers()) {
                PostMatchInventory postMatchInventory = new PostMatchInventory(teamPlayer);
                postMatchInventories.put(teamPlayer.getUuid(), postMatchInventory);
            }

            for (Player player : getMatchPlayers()) {
                plugin.getScoreboardHandler().getScoreboard(player).unregisterHealthObjective();
            }

            if (Eden.INSTANCE.getConfigFile().getBoolean("match.title.end")) {
                displayMatchEndTitle();
            }
            displayMatchEndMessages();
            calculateMatchStats();
        }

        new MatchResetTask(this);
    }

    public void joinSpectate(Player player, Player target) {
        PlayerProfile profile = PlayerProfile.get(player);

        spectators.add(player.getUniqueId());

        getPlayersAndSpectators().forEach(other -> {
            //We do not want to send useless stuff to NPC. 'other' might be null because the NPC might be already destroyed because it is dead
            if (other != null && Util.isNPC(other)) {
                PlayerProfile otherProfile = PlayerProfile.get(other);
                if (otherProfile.getSettings().get(ProfileSettings.SPECTATOR_JOIN_LEAVE_MESSAGE).isEnabled()) {
                    Common.sendMessage(other, Language.MATCH_JOIN_SPECTATE.toString(player.getName()));
                }
            }
        });

        Util.teleport(player, target.getLocation());
        PlayerUtil.spectator(player);

        profile.setMatch(this);
        profile.setPlayerState(PlayerState.IN_SPECTATING);
        profile.setupItems();
    }

    public void leaveSpectate(Player player) {
        spectators.remove(player.getUniqueId());

        getPlayersAndSpectators().forEach(other -> {
            //We do not want to send useless stuff to NPC. 'other' might be null because the NPC might be already destroyed because it is dead
            if (other != null && Util.isNPC(other)) {
                PlayerProfile otherProfile = PlayerProfile.get(other);
                if (otherProfile.getSettings().get(ProfileSettings.SPECTATOR_JOIN_LEAVE_MESSAGE).isEnabled()) {
                    Common.sendMessage(other, Language.MATCH_LEAVE_SPECTATE.toString(player.getName()));
                }
            }
        });

        plugin.getLobbyManager().sendToSpawnAndReset(player);
    }

    public void addDroppedItem(Item item, String whoDropped) {
        if (whoDropped != null) {
            plugin.getEntityHider().setPlayerWhoDropped(item, whoDropped);
        }
        getEntities().add(new MatchEntity(item));
    }

    public void clearEntities(boolean forced) {
        Iterator<MatchEntity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            MatchEntity matchEntity = iterator.next();
            if (forced || System.currentTimeMillis() - matchEntity.getTimestamp() >= 10000) {
                matchEntity.getEntity().remove();
                iterator.remove();
            }
        }
    }

    public void setState(MatchState state) {
        this.state = state;

        getPlayersAndSpectators().forEach(VisibilityController::updateVisibility);

        MatchStateChangeEvent event = new MatchStateChangeEvent(this);
        Bukkit.getPluginManager().callEvent(event);
    }

    public boolean canEnd() {
        return teams.stream().filter(team -> !team.isEliminated()).count() <= 1;
    }

    public boolean isProtected(Location location, boolean isPlacing) {
        if (kit.getGameRules().isSpleef()) {
            return location.getBlock().getType() != Material.SNOW_BLOCK && location.getBlock().getType() != Material.SAND;
        }
        if (location.getBlockY() >= arenaDetail.getArena().getBuildMax() || location.getBlockY() <= arenaDetail.getArena().getYLimit()) {
            return true;
        }
        if (!arenaDetail.getCuboid().contains(location)) {
            return true;
        }
        if (kit.getGameRules().isBed()) {
            switch (location.getBlock().getType()) {
                case BED_BLOCK:
                case WOOD:
                case ENDER_STONE:
                    return false;
            }
        }
        if (kit.getGameRules().isBreakGoal() && location.getBlock().getType() == Material.BED_BLOCK) {
            return false;
        }
        if (kit.getGameRules().isPortalGoal()) {
            long count = Util.getBlocksAroundCenter(location, arenaDetail.getArena().getPortalProtectionRadius()).stream().filter(block -> block.getType() == Material.ENDER_PORTAL).count();
            if (count > 0) {
                return true;
            }
            if (location.getBlock().getType() == Material.STAINED_CLAY && (location.getBlock().getData() == 0 || location.getBlock().getData() == 11 || location.getBlock().getData() == 14)) {
                return false;
            }
        }
        if (!isPlacing) {
            return !getPlacedBlocks().contains(location);
        }
        return false;
    }

    public ChatColor getRelationColor(Player viewer, Player target) {
        if (viewer.equals(target)) {
            return ChatColor.GREEN;
        }

        Team team = getTeam(target);
        Team viewerTeam = getTeam(viewer);

        if (team == null || viewerTeam == null) {
            return ChatColor.YELLOW;
        }

        if (team.equals(viewerTeam)) {
            return ChatColor.GREEN;
        } else {
            return ChatColor.RED;
        }
    }

    /**
     * @return A list of players who are in the match, without spectators and disconnected players
     */
    public List<Player> getMatchPlayers() {
        List<Player> players = new ArrayList<>();
        teams.forEach(team -> players.addAll(team.getTeamPlayers().stream().filter(tP -> !tP.isDisconnected()).map(TeamPlayer::getPlayer).collect(Collectors.toList())));
        return players;
    }

    public List<Player> getSpectators() {
        List<Player> players = new ArrayList<>();
        spectators.forEach(spectatorUUID -> players.add(Bukkit.getPlayer(spectatorUUID)));
        return players;
    }

    public List<Player> getPlayersAndSpectators() {
        List<Player> players = new ArrayList<>(getMatchPlayers());
        players.addAll(getSpectators());
        return players;
    }

    public Team getTeam(TeamPlayer player) {
        for (Team team : teams) {
            if (team.getTeamPlayers().stream().filter(Objects::nonNull).collect(Collectors.toList()).contains(player)) {
                return team;
            }
        }
        return null;
    }

    public Team getTeam(Player player) {
        for (Team team : teams) {
            if (team.getTeamPlayers().stream().map(TeamPlayer::getPlayer).filter(Objects::nonNull).collect(Collectors.toList()).contains(player)) {
                return team;
            }
        }
        return null;
    }

    public TeamPlayer getTeamPlayer(Player player) {
        return getTeamPlayer(player.getUniqueId());
    }

    public TeamPlayer getTeamPlayer(UUID uuid) {
        for (Team team : teams) {
            for (TeamPlayer teamPlayer : team.getTeamPlayers()) {
                if (teamPlayer.getUuid().equals(uuid)) {
                    return teamPlayer;
                }
            }
        }
        return null;
    }

    public List<TeamPlayer> getTeamPlayers() {
        List<TeamPlayer> players = new ArrayList<>();
        teams.stream().map(Team::getTeamPlayers).forEach(players::addAll);
        return players.stream().filter(teamPlayer -> !teamPlayer.isDisconnected()).collect(Collectors.toList());
    }

    public int getMaximumBoxingHits() {
        if (!kit.getGameRules().isBoxing()) {
            throw new PracticeUnexpectedException("Kit type is not boxing");
        }
        Team team = getTeams().stream().max(Comparator.comparing(t -> t.getTeamPlayers().size())).orElse(null);
        if (team == null) {
            throw new PracticeUnexpectedException("Cannot find a suitable team to calculate the maximum allowed hits in boxing");
        }
        return team.getTeamPlayers().size() * 100;
    }

    public long getElapsedDuration() {
        return System.currentTimeMillis() - startTimestamp;
    }

    public void broadcastMessage(String... message) {
        getPlayersAndSpectators().forEach(player -> Common.sendMessage(player, message));
    }
    public void broadcastMessage(List<String> messages) {
        getPlayersAndSpectators().forEach(player -> Common.sendMessage(player, messages));
    }
    public void broadcastTitle(String... message) {
        for (String msg : message)
            getPlayersAndSpectators().forEach(player -> TitleSender.sendTitle(player, msg, PacketPlayOutTitle.EnumTitleAction.TITLE, 0, 21, 5));
    }
    public void broadcastSubTitle(String... message) {
        for (String msg : message)
            getPlayersAndSpectators().forEach(player -> TitleSender.sendTitle(player, msg, PacketPlayOutTitle.EnumTitleAction.SUBTITLE, 0, 21, 5));
    }
    public void broadcastTitle(Team team, String... message) {
        for (TeamPlayer teamPlayer : team.getTeamPlayers())
            if (teamPlayer.getPlayer() != null)
                for (String msg : message)
                    TitleSender.sendTitle(teamPlayer.getPlayer(), msg, PacketPlayOutTitle.EnumTitleAction.TITLE, 0, 21, 5);
    }
    public void broadcastSubTitle(Team team, String... message) {
        for (TeamPlayer teamPlayer : team.getTeamPlayers())
            if (teamPlayer.getPlayer() != null)
                for (String msg : message)
                    TitleSender.sendTitle(teamPlayer.getPlayer(), msg, PacketPlayOutTitle.EnumTitleAction.SUBTITLE, 0, 21, 5);
    }
    public void broadcastSound(Sound sound) {
        getPlayersAndSpectators().forEach(player -> player.playSound(player.getLocation(), sound, 10, 1));
    }
    public void broadcastSound(Team team, Sound sound) {
        team.getTeamPlayers().stream().map(TeamPlayer::getPlayer).filter(Objects::nonNull).forEach(player -> player.playSound(player.getLocation(), sound, 10, 1));
    }
    public void broadcastSpectatorsSound(Sound sound) {
        getSpectators().forEach(player -> player.playSound(player.getLocation(), sound, 10, 1));
    }

    public abstract void setupTeamSpawnLocation();

    public abstract void displayMatchEndMessages();

    public abstract void displayMatchEndTitle();

    public abstract void calculateMatchStats();

    public abstract MatchType getMatchType();

    public abstract Team getOpponentTeam(Team team);

    public abstract TeamPlayer getOpponent(TeamPlayer teamPlayer);
    public abstract List<TeamPlayer> getWinningPlayers();

    public abstract Team getWinningTeam();

    public abstract List<String> getMatchScoreboard(Player player);

    public abstract List<String> getSpectateScoreboard(Player player);
}
