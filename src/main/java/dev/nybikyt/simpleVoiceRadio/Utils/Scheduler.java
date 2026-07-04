package dev.nybikyt.simpleVoiceRadio.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public final class Scheduler {

    private static final boolean FOLIA = detectFolia();

    private Scheduler() {
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void runAt(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAtLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static TaskHandle runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                    Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
            return scheduled::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    public static TaskHandle runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                    Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
            return scheduled::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }
}
