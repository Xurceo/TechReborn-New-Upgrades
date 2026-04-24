package trnewupgrades.client.events;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import trnewupgrades.init.TRNUContent;
import trnewupgrades.item.UpgradeItem;

public class StackToolTipHandler implements ItemTooltipCallback {

	public static final Map<Item, Boolean> ITEM_ID = Maps.newHashMap();

    public static void setup() {
        ItemTooltipCallback.EVENT.register(new StackToolTipHandler());
    }


    public void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> lines) {
        Item item = stack.getItem();

        Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread())
			return;

        if (!ITEM_ID.computeIfAbsent(item, StackToolTipHandler::isTRNUItem))
			return;
        
        if (item instanceof UpgradeItem upgrade) {
			ToolTipAssistUtils.addInfo(item.getDescriptionId(), lines, false);
			lines.addAll(ToolTipAssistUtils.getUpgradeStats(TRNUContent.Upgrades.fromItem(upgrade), stack.getCount(), mc.hasShiftDown()));
		}
    }

    private static boolean isTRNUItem(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("trnewupgrades");
	}
}
