package igentuman.bfr.client.gui.element;

import mekanism.api.text.ILangEntry;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiTabElementType;
import mekanism.client.gui.element.tab.TabType;
import mekanism.client.render.lib.ColorAtlas.ColorRegistryObject;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import igentuman.bfr.client.GeneratorsSpecialColors;
import igentuman.bfr.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import igentuman.bfr.common.GeneratorsLang;
import igentuman.bfr.common.MekanismGenerators;
import igentuman.bfr.common.network.to_server.PacketGeneratorsGuiButtonPress;
import igentuman.bfr.common.network.to_server.PacketGeneratorsGuiButtonPress.ClickedGeneratorsTileButton;
import igentuman.bfr.common.tile.fusion.TileEntityFusionReactorController;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class GuiFusionReactorTab extends GuiTabElementType<TileEntityFusionReactorController, FusionReactorTab> {

    public GuiFusionReactorTab(IGuiWrapper gui, TileEntityFusionReactorController tile, FusionReactorTab type) {
        super(gui, tile, type);
    }

    public enum FusionReactorTab implements TabType<TileEntityFusionReactorController> {
        HEAT(MekanismUtils.getResource(ResourceType.GUI, "heat.png"), GeneratorsLang.HEAT_TAB, 6, ClickedGeneratorsTileButton.TAB_HEAT, GeneratorsSpecialColors.TAB_MULTIBLOCK_HEAT),
        FUEL(MekanismGenerators.rl(ResourceType.GUI.getPrefix() + "fuel.png"), GeneratorsLang.FUEL_TAB, 34, ClickedGeneratorsTileButton.TAB_FUEL, GeneratorsSpecialColors.TAB_MULTIBLOCK_FUEL),
        STAT(MekanismUtils.getResource(ResourceType.GUI, "stats.png"), GeneratorsLang.STATS_TAB, 62, ClickedGeneratorsTileButton.TAB_STATS, SpecialColors.TAB_MULTIBLOCK_STATS);

        private final ClickedGeneratorsTileButton button;
        private final ColorRegistryObject colorRO;
        private final ILangEntry description;
        private final ResourceLocation path;
        private final int yPos;

        FusionReactorTab(ResourceLocation path, ILangEntry description, int y, ClickedGeneratorsTileButton button, ColorRegistryObject colorRO) {
            this.path = path;
            this.description = description;
            this.yPos = y;
            this.button = button;
            this.colorRO = colorRO;
        }

        @Override
        public ResourceLocation getResource() {
            return path;
        }

        @Override
        public void onClick(TileEntityFusionReactorController tile) {
            MekanismGenerators.packetHandler.sendToServer(new PacketGeneratorsGuiButtonPress(button, tile.getBlockPos()));
        }

        @Override
        public ITextComponent getDescription() {
            return description.translate();
        }

        @Override
        public int getYPos() {
            return yPos;
        }

        @Override
        public ColorRegistryObject getTabColor() {
            return colorRO;
        }
    }
}