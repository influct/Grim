package ac.grim.grimac.checks;

import ac.grim.grimac.GrimAC;
import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.events.FlagEvent;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import github.scarsz.configuralize.DynamicConfig;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Unmodifiable;

// Class from https://github.com/Tecnio/AntiCheatBase/blob/master/src/main/java/me/tecnio/anticheat/check/Check.java
@Getter
public class Check implements AbstractCheck {
    protected final GrimPlayer player;

    public double violations;
    private double decay;
    private double setbackVL;

    private String checkName;
    private String configName;
    private String alternativeName;
    private String description;

    private boolean experimental;

    @Setter
    @Getter(AccessLevel.NONE)
    private boolean isEnabled;

    @Unmodifiable
    @Getter(AccessLevel.NONE)
    private Set<String> disabledWorlds;

    @Override
    public boolean isExperimental() {
        return experimental;
    }

    public Check(final GrimPlayer player) {
        this.player = player;

        final Class<?> checkClass = this.getClass();

        if (checkClass.isAnnotationPresent(CheckData.class)) {
            final CheckData checkData = checkClass.getAnnotation(CheckData.class);
            this.checkName = checkData.name();
            this.configName = checkData.configName();
            // Fall back to check name
            if (this.configName.equals("DEFAULT")) this.configName = this.checkName;
            this.decay = checkData.decay();
            this.setbackVL = checkData.setback();
            this.alternativeName = checkData.alternativeName();
            this.experimental = checkData.experimental();
            this.description = checkData.description();
        }

        reload();
    }

    public boolean shouldModifyPackets() {
        return isEnabled && !player.disableGrim && !player.noModifyPacketPermission;
    }

    public final boolean flagAndAlert(String verbose) {
        if (flag()) {
            alert(verbose);
            return true;
        }
        return false;
    }

    public final boolean flagAndAlert() {
        return flagAndAlert("");
    }

    public final boolean flag() {
        if (player.disableGrim || (experimental && !GrimAPI.INSTANCE.getConfigManager().isExperimentalChecks()))
            return false; // Avoid calling event if disabled

        FlagEvent event = new FlagEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;


        player.punishmentManager.handleViolation(this);

        violations++;
        return true;
    }

    public final boolean flagWithSetback() {
        if (flag()) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final void reward() {
        violations = Math.max(0, violations - decay);
    }

    public void reload() {
        decay = getConfig().getDoubleElse(configName + ".decay", decay);
        setbackVL = getConfig().getDoubleElse(configName + ".setbackvl", setbackVL);

        if (setbackVL == -1) setbackVL = Double.MAX_VALUE;

        final Optional<Map<String, List<String>>> enabledWorldChecks = GrimAPI
                .INSTANCE.getConfigManager()
                .getConfig()
                .getOptionalMap("enabled-world-checks");

        if (!enabledWorldChecks.isPresent()) {
            GrimAPI.INSTANCE.getPlugin().getLogger().severe("'enabled-world-checks' not found in config!");
            disabledWorlds = Collections.emptySet();
        } else {
            disabledWorlds = enabledWorldChecks.get().entrySet().stream()
                    .filter(entry -> !entry.getValue().contains(getCheckName()) && !entry.getValue().contains(getConfigName()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            for (int i = 0; i < 10; i++) {
                System.err.println("aa " + disabledWorlds);
                System.err.println("bb " + enabledWorldChecks.get());
            }
        }
    }

    public boolean alert(String verbose) {
        return player.punishmentManager.handleAlert(player, verbose, this);
    }

    public DynamicConfig getConfig() {
        return GrimAPI.INSTANCE.getConfigManager().getConfig();
    }

    public boolean setbackIfAboveSetbackVL() {
        if (getViolations() > setbackVL) {
            return player.getSetbackTeleportUtil().executeViolationSetback();
        }
        return false;
    }

    public boolean isAboveSetbackVl() {
        return getViolations() > setbackVL;
    }

    public String formatOffset(double offset) {
        return offset > 0.001 ? String.format("%.5f", offset) : String.format("%.2E", offset);
    }

    public boolean isTransaction(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.PONG ||
                packetType == PacketType.Play.Client.WINDOW_CONFIRMATION;
    }

    public final boolean isEnabled() {
        return !disabledWorlds.contains(player.worldName()) && this.isEnabled;
    }
}

