package urza.floatingframes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.persistence.PersistentDataType;

/** All mutations happen synchronously on the event's owning region thread. */
final class FrameListener implements Listener {
    private final NamespacedKey floatingKey;

    FrameListener(NamespacedKey floatingKey) {
        this.floatingKey = floatingKey;
    }

    // A hit that would pop the displayed item takes the damage-event path.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getCause() != DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof ItemFrame frame)
                || !(event.getDamager() instanceof Player player)
                || !canToggle(frame, player)) return;

        event.setCancelled(true);
        toggle(frame, player);
    }

    // A hit that would break the frame itself takes the hanging-break path.
    // Cancelling the damage path above prevents it from reaching this path too.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameBreak(HangingBreakByEntityEvent event) {
        if (event.isCancelled() || event.getCause() != RemoveCause.ENTITY
                || !(event.getEntity() instanceof ItemFrame frame)
                || !(event.getRemover() instanceof Player player)
                || !canToggle(frame, player)) return;

        event.setCancelled(true);
        toggle(frame, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSupportLoss(HangingBreakEvent event) {
        if (event.isCancelled() || event.getCause() != RemoveCause.PHYSICS
                || !(event.getEntity() instanceof ItemFrame frame)) return;

        // Do not change normal frames, paintings, or frames hidden by other plugins.
        if (!frame.isVisible() && isManaged(frame)) event.setCancelled(true);
    }

    private boolean canToggle(ItemFrame frame, Player player) {
        return player.isSneaking()
                && player.hasPermission("floatingframes.toggle")
                && !frame.isFixed()
                && !frame.isInvulnerable()
                && (frame.isVisible() || isManaged(frame));
    }

    private boolean isManaged(ItemFrame frame) {
        return Byte.valueOf((byte) 1).equals(frame.getPersistentDataContainer()
                .get(floatingKey, PersistentDataType.BYTE));
    }

    private void toggle(ItemFrame frame, Player player) {
        boolean makeVisible = !frame.isVisible();
        frame.setVisible(makeVisible);
        if (makeVisible) {
            frame.getPersistentDataContainer().remove(floatingKey);
        } else {
            frame.getPersistentDataContainer().set(floatingKey, PersistentDataType.BYTE, (byte) 1);
        }
        player.sendActionBar(Component.text(makeVisible
                ? "Frame visible. Support is required again."
                : "Frame invisible. You can remove its support block.", NamedTextColor.GREEN));
    }
}
