import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimFly extends JavaPlugin implements Listener {

    private final Set<UUID> flyEnabled = new HashSet<>();
    private final String NO_FALL_META = "claimfly_noFall";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ClaimFly enabled.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (flyEnabled.contains(p.getUniqueId())) {
                p.setFlying(false);
                p.setAllowFlight(false);
                p.removeMetadata(NO_FALL_META, this);
            }
        }
        flyEnabled.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        if (!p.hasPermission("claimfly.use")) {
            p.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        UUID uuid = p.getUniqueId();

        if (flyEnabled.contains(uuid)) {
            flyEnabled.remove(uuid);
            p.setFlying(false);
            p.setAllowFlight(false);
            p.sendMessage("§cClaim fly disabled.");
        } else {
            flyEnabled.add(uuid);
            p.sendMessage("§aClaim fly enabled. You can fly in your claims or claims you are trusted in.");

            if (canFlyHere(p)) {
                p.setAllowFlight(true);
            }
        }

        return true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!flyEnabled.contains(p.getUniqueId())) return;

        if (canFlyHere(p)) {
            p.setAllowFlight(true);
        } else {
            if (p.getAllowFlight() || p.isFlying()) {
                p.setFlying(false);
                p.setAllowFlight(false);
                p.setFallDistance(0f);

                p.setMetadata(NO_FALL_META, new FixedMetadataValue(this, true));

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline()) {
                        p.removeMetadata(NO_FALL_META, this);
                    }
                }, 100L); // 5 seconds
            }
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && p.hasMetadata(NO_FALL_META)) {
            e.setCancelled(true);
            p.setFallDistance(0f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        flyEnabled.remove(p.getUniqueId());
        p.removeMetadata(NO_FALL_META, this);
    }

    private boolean canFlyHere(Player p) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(p.getLocation(), true, null);

        if (claim == null) return false;

        if (claim.getOwnerID().equals(p.getUniqueId())) {
            return true;
        }

        // trusted checks
        if (claim.allowBuild(p, Material.AIR) == null) return true;
        if (claim.allowContainers(p) == null) return true;
        if (claim.allowAccess(p) == null) return true;

        return false;
    }
}