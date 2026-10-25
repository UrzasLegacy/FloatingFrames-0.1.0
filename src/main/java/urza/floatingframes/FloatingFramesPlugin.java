package urza.floatingframes;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class FloatingFramesPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(
                new FrameListener(new NamespacedKey(this, "floating")), this);
    }
}
