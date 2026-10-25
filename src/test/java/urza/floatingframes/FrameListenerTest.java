package urza.floatingframes;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrameListenerTest {
    private final NamespacedKey key = new NamespacedKey("floatingframes", "floating");
    private FrameListener listener;
    private ItemFrame frame;
    private Player player;
    private PersistentDataContainer data;
    private AtomicBoolean visible;
    private AtomicReference<Byte> tag;

    @BeforeEach
    void setUp() {
        listener = new FrameListener(key);
        player = mock(Player.class);
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission("floatingframes.toggle")).thenReturn(true);
        prepareFrame(mock(ItemFrame.class));
    }

    private void prepareFrame(ItemFrame nextFrame) {
        frame = nextFrame;
        visible = new AtomicBoolean(true);
        tag = new AtomicReference<>();
        data = mock(PersistentDataContainer.class);
        when(frame.isVisible()).thenAnswer(i -> visible.get());
        doAnswer(i -> { visible.set(i.getArgument(0)); return null; }).when(frame).setVisible(anyBoolean());
        when(frame.getPersistentDataContainer()).thenReturn(data);
        when(data.get(key, PersistentDataType.BYTE)).thenAnswer(i -> tag.get());
        doAnswer(i -> { tag.set(i.getArgument(2)); return null; })
                .when(data).set(eq(key), eq(PersistentDataType.BYTE), any(Byte.class));
        doAnswer(i -> { tag.set(null); return null; }).when(data).remove(key);
    }

    private EntityDamageByEntityEvent punch() {
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(frame);
        when(event.getDamager()).thenReturn(player);
        when(event.getCause()).thenReturn(DamageCause.ENTITY_ATTACK);
        return event;
    }

    private HangingBreakByEntityEvent breakFrame() {
        var event = mock(HangingBreakByEntityEvent.class);
        when(event.getEntity()).thenReturn(frame);
        when(event.getRemover()).thenReturn(player);
        when(event.getCause()).thenReturn(RemoveCause.ENTITY);
        return event;
    }

    private HangingBreakEvent physics(RemoveCause cause) {
        return new HangingBreakEvent(frame, cause);
    }

    @Test void filledFrameHitIsCancelledAndTogglesBothWays() {
        var first = punch();
        listener.onFrameDamage(first);
        verify(first).setCancelled(true);
        assertFalse(visible.get());
        assertEquals((byte) 1, tag.get());
        var second = punch();
        listener.onFrameDamage(second);
        verify(second).setCancelled(true);
        assertTrue(visible.get());
        assertNull(tag.get());
        // Visibility changes never touch the displayed item or its rotation.
        verify(frame, never()).setRotation(any());
        verify(frame, never()).setItem(any());
    }

    @Test void emptyFrameBreakPathTogglesInsteadOfBreaking() {
        var event = breakFrame();
        listener.onFrameBreak(event);
        verify(event).setCancelled(true);
        assertFalse(visible.get());
    }

    @Test void glowFramesUseTheSameBehavior() {
        prepareFrame(mock(GlowItemFrame.class));
        listener.onFrameDamage(punch());
        assertFalse(visible.get());
        var event = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(event);
        assertTrue(event.isCancelled());
    }

    @Test void existingClaimDenialPreventsBothTogglePaths() {
        var damage = punch();
        when(damage.isCancelled()).thenReturn(true);
        listener.onFrameDamage(damage);
        var breaking = breakFrame();
        when(breaking.isCancelled()).thenReturn(true);
        listener.onFrameBreak(breaking);
        assertTrue(visible.get());
        assertNull(tag.get());
        verify(frame, never()).setVisible(anyBoolean());
    }

    @Test void standingPlayersKeepNormalInteractions() {
        when(player.isSneaking()).thenReturn(false);
        var damage = punch(); var breaking = breakFrame();
        listener.onFrameDamage(damage); listener.onFrameBreak(breaking);
        verify(damage, never()).setCancelled(anyBoolean());
        verify(breaking, never()).setCancelled(anyBoolean());
        assertTrue(visible.get());
    }

    @Test void permissionDeniedDoesNotToggle() {
        when(player.hasPermission("floatingframes.toggle")).thenReturn(false);
        listener.onFrameDamage(punch()); listener.onFrameBreak(breakFrame());
        assertTrue(visible.get());
        assertNull(tag.get());
    }

    @Test void onlyOwnedInvisibleFramesFloat() {
        var normal = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(normal); assertFalse(normal.isCancelled());
        visible.set(false);
        var foreign = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(foreign); assertFalse(foreign.isCancelled());
        tag.set((byte) 1);
        var owned = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(owned); assertTrue(owned.isCancelled());
        visible.set(true);
        var restored = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(restored); assertFalse(restored.isCancelled());
    }

    @Test void listenerRestartUsesPersistentEntityTag() {
        listener.onFrameDamage(punch());
        listener = new FrameListener(key);
        var event = physics(RemoveCause.PHYSICS);
        listener.onSupportLoss(event);
        assertTrue(event.isCancelled());
        listener.onFrameBreak(breakFrame());
        assertTrue(visible.get()); assertNull(tag.get());
    }

    @Test void otherRemovalCausesAreNotSuppressed() {
        listener.onFrameDamage(punch());
        for (RemoveCause cause : RemoveCause.values()) {
            if (cause == RemoveCause.PHYSICS) continue;
            var event = physics(cause);
            listener.onSupportLoss(event);
            assertFalse(event.isCancelled(), cause.name());
        }
    }

    @Test void projectilesAndPlayerExplosionsDoNotToggle() {
        var arrow = punch();
        when(arrow.getDamager()).thenReturn(mock(Arrow.class));
        when(arrow.getCause()).thenReturn(DamageCause.PROJECTILE);
        listener.onFrameDamage(arrow);
        var explosion = breakFrame();
        when(explosion.getCause()).thenReturn(RemoveCause.EXPLOSION);
        listener.onFrameBreak(explosion);
        assertTrue(visible.get());
        verify(explosion, never()).setCancelled(anyBoolean());
    }

    @Test void paintingsAreUnaffected() {
        var event = new HangingBreakEvent(mock(Painting.class), RemoveCause.PHYSICS);
        listener.onSupportLoss(event);
        assertFalse(event.isCancelled());
    }

    @Test void fixedInvulnerableAndForeignInvisibleFramesAreNotTakenOver() {
        when(frame.isFixed()).thenReturn(true);
        listener.onFrameBreak(breakFrame());
        assertTrue(visible.get());
        when(frame.isFixed()).thenReturn(false);
        when(frame.isInvulnerable()).thenReturn(true);
        listener.onFrameDamage(punch());
        assertTrue(visible.get());
        when(frame.isInvulnerable()).thenReturn(false);
        visible.set(false);
        listener.onFrameDamage(punch());
        assertFalse(visible.get()); assertNull(tag.get());
    }
}
