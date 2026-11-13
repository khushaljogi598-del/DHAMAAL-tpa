package org.dhamaal.tpaforms;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * /tpamenu     -> list of online players; tapping one sends a TPA request
 *                 and opens an ACCEPT / DENY form on the target's screen.
 * /playersmenu -> list of online players (display only).
 *
 * All teleport logic is left to the existing TPA plugin (EssentialsX):
 * this plugin only runs "tpa", "tpaccept" and "tpdeny" as the right player.
 */
public class DhamaalTpa extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("DhamaalTPA enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!isBedrock(player.getUniqueId())) {
            player.sendMessage("This menu is for Bedrock players. Use /tpa <player>.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("tpamenu")) {
            openTpaSelect(player);
        } else if (cmd.getName().equalsIgnoreCase("playersmenu")) {
            openPlayersList(player);
        }
        return true;
    }

    private boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    /** Online players except the viewer, sorted by name. Built fresh on every open. */
    private List<Player> others(Player viewer) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(viewer.getUniqueId()))
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .collect(Collectors.toList());
    }

    /** Adds a button with a head icon for Java players; Bedrock players have no Java skin, so no icon. */
    private void addPlayerButton(SimpleForm.Builder form, Player p) {
        if (isBedrock(p.getUniqueId())) {
            form.button(p.getName());
        } else {
            form.button(p.getName(), FormImage.Type.URL, "https://mc-heads.net/avatar/" + p.getName());
        }
    }

    // ---------------------------------------------------------------- TPA select

    private void openTpaSelect(Player requester) {
        List<Player> targets = others(requester);

        SimpleForm.Builder form = SimpleForm.builder()
                .title("TPA - SELECT PLAYER")
                .content(targets.isEmpty()
                        ? "No other players are online right now."
                        : "Tap the player you want to teleport to.");

        for (Player t : targets) {
            addPlayerButton(form, t);
        }

        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index < 0 || index >= targets.size()) {
                return;
            }
            Player target = targets.get(index);
            // Form callbacks are not on the main thread; Bukkit calls must be.
            Bukkit.getScheduler().runTask(this, () -> sendRequest(requester, target));
        });

        FloodgateApi.getInstance().sendForm(requester.getUniqueId(), form);
    }

    private void sendRequest(Player requester, Player target) {
        if (!requester.isOnline()) {
            return;
        }
        if (!target.isOnline()) {
            requester.sendMessage("That player just went offline.");
            return;
        }

        // Runs as the requester, exactly like typing /tpa <name>.
        requester.performCommand("tpa " + target.getName());

        // Only a Bedrock target gets the form. A Java target still gets the normal chat request.
        if (isBedrock(target.getUniqueId())) {
            openAcceptDeny(target, requester);
        }
    }

    // ---------------------------------------------------------------- Accept / Deny

    private void openAcceptDeny(Player target, Player requester) {
        ModalForm.Builder form = ModalForm.builder()
                .title("TELEPORT REQUEST")
                .content(requester.getName() + " wants to teleport to you.")
                .button1("ACCEPT")
                .button2("DENY");

        form.validResultHandler(response -> {
            boolean accepted = response.clickedButtonId() == 0;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!target.isOnline()) {
                    return;
                }
                // Runs as the target, exactly like typing /tpaccept or /tpdeny.
                target.performCommand(accepted ? "tpaccept" : "tpdeny");
            });
        });

        FloodgateApi.getInstance().sendForm(target.getUniqueId(), form);
    }

    // ---------------------------------------------------------------- Players list

    private void openPlayersList(Player viewer) {
        List<Player> online = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .collect(Collectors.toList());

        SimpleForm.Builder form = SimpleForm.builder()
                .title("DHAMAAL SMP - PLAYERS")
                .content("Players online: " + online.size());

        for (Player p : online) {
            addPlayerButton(form, p);
        }

        FloodgateApi.getInstance().sendForm(viewer.getUniqueId(), form);
    }
}
