package ac.grim.grimac.utils.latency;

import ac.grim.grimac.player.GrimPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompensatedFlying {
    ConcurrentHashMap<Integer, Boolean> lagCompensatedFlyingMap = new ConcurrentHashMap<>();
    boolean canPlayerFly;
    GrimPlayer player;

    public CompensatedFlying(GrimPlayer player) {
        this.player = player;
        this.canPlayerFly = player.bukkitPlayer.getAllowFlight();
    }

    public void setCanPlayerFly(boolean canFly) {
        lagCompensatedFlyingMap.put(player.lastTransactionSent.get(), canFly);
    }

    public boolean getCanPlayerFlyLagCompensated() {
        int lastTransactionReceived = player.lastTransactionReceived;

        boolean canFly = canPlayerFly;
        int bestKey = 0;

        Iterator<Map.Entry<Integer, Boolean>> iterator = lagCompensatedFlyingMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Boolean> flightStatus = iterator.next();

            if (flightStatus.getKey() > lastTransactionReceived) continue;

            if (flightStatus.getKey() < bestKey) {
                iterator.remove();
                continue;
            }

            bestKey = flightStatus.getKey();
            canFly = flightStatus.getValue();

            iterator.remove();
        }

        canPlayerFly = canFly;

        return canFly;
    }
}
