package igentuman.bfr.client.gui;

import igentuman.bfr.client.gui.button.GuiReactorLogicButton;
import igentuman.bfr.client.gui.element.GuiLogicAdapterTab;
import igentuman.bfr.common.tile.reactor.TileEntityReactorLogicAdapter;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.button.GuiButtonDisableableImage;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiReactorLogicAdapter extends GuiMekanismTile<TileEntityReactorLogicAdapter> {

    private List<GuiReactorLogicButton> typeButtons = new ArrayList<>();
    private GuiButton coolingButton;
    private int buttonID = 0;

    public GuiReactorLogicAdapter(InventoryPlayer inventory, final TileEntityReactorLogicAdapter tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        ResourceLocation resource = getGuiLocation();
        addGuiElement(new GuiLogicAdapterTab(this, tileEntity, GuiLogicAdapterTab.LogicAdapterTab.INPUT, resource));
        addGuiElement(new GuiLogicAdapterTab(this, tileEntity, GuiLogicAdapterTab.LogicAdapterTab.OUTPUT, resource));
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(coolingButton = new GuiButtonDisableableImage(buttonID++, guiLeft + 23, guiTop + 19, 11, 11, 176, 11, -11, getGuiLocation()));
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) throws IOException {
        super.actionPerformed(guibutton);
        if (guibutton.id == coolingButton.id) {
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0)));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tileEntity.getName(), (xSize / 2) - (fontRenderer.getStringWidth(tileEntity.getName()) / 2), 6, 0x404040);
        renderScaledText(LangUtils.localize("gui.coolingMeasurements") + ": " + EnumColor.RED + LangUtils.transOnOff(tileEntity.activeCooled), 36, 20, 0x404040, 117);
        renderScaledText(LangUtils.localize("gui.redstoneOutputMode") + ": " + EnumColor.RED + tileEntity.logicType.getLocalizedName(), 23, 123, 0x404040, 130);
        String text = LangUtils.localize("gui.status") + ": " + EnumColor.RED + LangUtils.localize("gui." + (tileEntity.getRedstoneLevel() > 0 ? "outputting" : "idle"));
        fontRenderer.drawString(text, (xSize / 2) - (fontRenderer.getStringWidth(text) / 2), 136, 0x404040);
        int xAxis = mouseX - guiLeft;
        int yAxis = mouseY - guiTop;
        if (coolingButton.isMouseOver()) {
            displayTooltip(LangUtils.localize("gui.toggleCooling"), xAxis, yAxis);
        }
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected ResourceLocation getGuiLocation() {
        return MekanismUtils.getResource(ResourceType.GUI, "GuiReactorLogicAdapter.png");
    }
}