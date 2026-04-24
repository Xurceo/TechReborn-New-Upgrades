package trnewupgrades.client.events;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRContent;
import trnewupgrades.config.TRNUConfig;
import trnewupgrades.init.TRNUContent;

public class ToolTipAssistUtils {
    private static final ChatFormatting instructColour = ChatFormatting.BLUE;

	private static final ChatFormatting infoColour = ChatFormatting.GOLD;
	private static final ChatFormatting statColour = ChatFormatting.GOLD;

	private static final ChatFormatting posColour = ChatFormatting.GREEN;
	private static final ChatFormatting negColour = ChatFormatting.RED;

    public static List<Component> getUpgradeStats(TRNUContent.Upgrades upgradeType, int count, boolean shiftHeld) {
		List<Component> tips = new ArrayList<>();
		boolean shouldStackCalculate = count > 1;

		switch (upgradeType) {
			case OVERCLOCKERMK2 -> {
				tips.add(getStatStringUnit(I18n.get("trnewupgrades.tooltip.upgrade.speed_increase"), calculateSpeed(TRNUConfig.overclockermk2Speed * 100, count, 2, shiftHeld), "%", true));
				tips.add(getStatStringUnit(I18n.get("trnewupgrades.tooltip.upgrade.energy_increase"), calculateEnergyIncrease(TRNUConfig.overclockermk2Power + 1, count, 2, shiftHeld), "x", false));
			}
			case OVERCLOCKERMK3 -> {
				shouldStackCalculate = false; // Since the speed increase is already so high, it doesn't make sense to calculate for multiple upgrades
				tips.add(getStatStringUnit(I18n.get("trnewupgrades.tooltip.upgrade.speed_increase"), calculateSpeed(TRNUConfig.overclockermk3Speed * 100, count, 1, shiftHeld), "%", true));
				tips.add(getStatStringUnit(I18n.get("trnewupgrades.tooltip.upgrade.energy_increase"), calculateEnergyIncrease(TRNUConfig.overclockermk3Power + 1, count, 1, shiftHeld), "x", false));
			}
			case TRANSFORMERMK2 -> shouldStackCalculate = false;
			case TRANSFORMERINFINITE -> shouldStackCalculate = false;
			case STACK -> shouldStackCalculate = false;
		}
		// Add reminder that they can use shift to calculate the entire stack
		if (shouldStackCalculate && !shiftHeld) {
			tips.add(Component.literal(instructColour + I18n.get("trnewupgrades.tooltip.stack_info")));
		}

		return tips;
	}

    public static void addInfo(String inKey, List<Component> list) {
		addInfo(inKey, list, true);
	}

    public static void addInfo(String inKey, List<Component> list, boolean hidden) {
		String key = ("trnewupgrades.message.info." + inKey);

		if (I18n.exists(key)) {
			if (!hidden || Minecraft.getInstance().hasShiftDown()) {
				String info = I18n.get(key);
				List<MutableComponent> infoLines = Arrays.stream(info.split("\\r?\\n"))
					.map(infoLine -> Component.literal(infoColour + infoLine)).toList();
				list.addAll(1, infoLines);
			} else {
				list.add(Component.literal(instructColour + I18n.get("trnewupgrades.tooltip.more_info")));
			}
		}
	}

    private static double calculateEnergyIncrease(double value, int count, int maxCount, boolean shiftHeld) {
		double calculatedVal;

		if (shiftHeld) {
			calculatedVal = Math.pow(value, Math.min(count, maxCount));
		} else {
			calculatedVal = value;
		}

		return calculatedVal;
	}

	private static double calculateSpeed(double value, int count, int maxCount, boolean shiftHeld) {
		double calculatedVal;

		if (shiftHeld) {
			calculatedVal = Math.min(value * Math.min(count, maxCount), MachineBaseBlockEntity.SPEED_CAP * 100D);
		} else {
			calculatedVal = Math.min(value, MachineBaseBlockEntity.SPEED_CAP * 100D);
		}

		return calculatedVal;
	}

	private static Component getStatStringUnit(String text, double value, String unit, boolean isPositive) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US); // Always use dot
		NumberFormat formatter = new DecimalFormat("##.##", symbols); // Round to 2 decimal places
		return Component.literal(statColour + text + ": " + ((isPositive) ? posColour : negColour) + formatter.format(value) + unit);
	}
}
