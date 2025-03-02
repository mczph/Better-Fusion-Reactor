package igentuman.bfr.common.tile.reactor;

import io.netty.buffer.ByteBuf;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.base.IActiveState;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import igentuman.bfr.common.BetterFusionReactor;
import mekanism.generators.common.item.ItemHohlraum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

public class TileEntityReactorController extends TileEntityReactorBlock implements IActiveState {

    public static final int MAX_WATER = 100 * Fluid.BUCKET_VOLUME;
    public static final int MAX_STEAM = MAX_WATER * 100;
    public static final int MAX_FUEL = Fluid.BUCKET_VOLUME;

    public FluidTank waterTank = new FluidTank(MAX_WATER);
    public FluidTank steamTank = new FluidTank(MAX_STEAM);

    public GasTank deuteriumTank = new GasTank(MAX_FUEL);
    public GasTank tritiumTank = new GasTank(MAX_FUEL);

    public GasTank fuelTank = new GasTank(MAX_FUEL);
    public BetterFusionReactor fusionReactor;
    public AxisAlignedBB box;
    public double clientTemp = 0;
    public boolean clientBurning = false;
    private SoundEvent soundEvent = new SoundEvent(new ResourceLocation(Mekanism.MODID, "tile.machine.fusionreactor"));
    @SideOnly(Side.CLIENT)
    private ISound activeSound;
    private int playSoundCooldown = 0;
    public Object radiation;

    public TileEntityReactorController() {
        super("ReactorController", 1000000000);
        inventory = NonNullList.withSize(1, ItemStack.EMPTY);
        if(Loader.isModLoaded("nuclearcraft")) {
            radiation = new nc.capability.radiation.source.RadiationSource(0D);
        } else {
            radiation = new Object();
        }
    }

    public void simulateRadiation()
    {
        if(!Loader.isModLoaded("nuclearcraft")) {
            return;
        }
        if(isBurning()) {
            ((nc.capability.radiation.source.RadiationSource) radiation).setRadiationLevel(0.001);
        } else {
            ((nc.capability.radiation.source.RadiationSource) radiation).setRadiationLevel(0);

        }
    }

    @Override
    public boolean isFrame() {
        return false;
    }

    public void radiateNeutrons(int neutrons) {
        //future impl
    }
    public BetterFusionReactor getReactor() {
        return fusionReactor;
    }

    public void setReactor(BetterFusionReactor reactor) {
        if (reactor != fusionReactor) {
            changed = true;
        }
        fusionReactor = reactor;
    }
    public void formMultiblock(boolean keepBurning) {
        if (getReactor() == null) {
            setReactor(new BetterFusionReactor(this));
        }
        getReactor().formMultiblock(keepBurning);
    }

    public double getPlasmaTemp() {
        if (getReactor() == null || !getReactor().isFormed()) {
            return 0;
        }
        return getReactor().getPlasmaTemp();
    }

    public double getCaseTemp() {
        if (getReactor() == null || !getReactor().isFormed()) {
            return 0;
        }
        return getReactor().getCaseTemp();
    }

    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        if (Loader.isModLoaded("nuclearcraft") && capability == nc.capability.radiation.source.IRadiationSource.CAPABILITY_RADIATION_SOURCE) {
            return radiation != null;
        }
        return super.hasCapability(capability, side);
    }

    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (Loader.isModLoaded("nuclearcraft") && capability == nc.capability.radiation.source.IRadiationSource.CAPABILITY_RADIATION_SOURCE) {
            return (T) radiation;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            updateSound();
        }
        if (isFormed()) {
            getReactor().simulate();
            simulateRadiation();
            if (!world.isRemote && (getReactor().isBurning() != clientBurning || Math.abs(getReactor().getPlasmaTemp() - clientTemp) > 1000000)) {
                Mekanism.packetHandler.sendUpdatePacket(this);
                clientBurning = getReactor().isBurning();
                clientTemp = getReactor().getPlasmaTemp();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateSound() {
        if (!MekanismConfig.current().client.enableMachineSounds.val()) {
            return;
        }
        if (isBurning() && !isInvalid()) {
            if (--playSoundCooldown > 0) {
                return;
            }
            if (activeSound == null || !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(activeSound)) {
                activeSound = SoundHandler.startTileSound(soundEvent.getSoundName(), 1.0f, getPos());
                playSoundCooldown = 20;
            }
        } else if (activeSound != null) {
            SoundHandler.stopTileSound(getPos());
            activeSound = null;
            playSoundCooldown = 0;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world.isRemote) {
            updateSound();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        formMultiblock(true);
    }

    @Override
    public void onAdded() {
        super.onAdded();
        formMultiblock(false);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("formed", isFormed());
        if (isFormed()) {
            tag.setDouble("plasmaTemp", getReactor().getPlasmaTemp());
            tag.setDouble("caseTemp", getReactor().getCaseTemp());
            tag.setInteger("injectionRate", getReactor().getInjectionRate());
            tag.setFloat("targetReactivity", getReactor().getTargetReactivity());
            tag.setFloat("currentReactivity", getReactor().getCurrentReactivity());
            tag.setFloat("errorLevel", getReactor().getErrorLevel());
            tag.setFloat("adjustment", getReactor().getAdjustment());
            tag.setFloat("laserShootCountdown", getReactor().getLaserShootCountdown());
            tag.setBoolean("burning", getReactor().isBurning());
            if(Loader.isModLoaded("nuclearcraft")) {
                tag.setDouble("radiationLevel", ((nc.capability.radiation.source.RadiationSource) radiation).getRadiationLevel());
            }
        } else {
            tag.setDouble("plasmaTemp", 0);
            tag.setDouble("caseTemp", 0);
            tag.setInteger("injectionRate", 0);
            tag.setFloat("targetReactivity", 0);
            tag.setFloat("currentReactivity", 0);
            tag.setFloat("errorLevel", 0);
            tag.setFloat("adjustment", 0);
            tag.setInteger("laserShootCountdown", 0);
            tag.setBoolean("burning", false);
            if(Loader.isModLoaded("nuclearcraft")) {
                tag.setDouble("radiationLevel", 0);
            }
        }
        tag.setTag("fuelTank", fuelTank.write(new NBTTagCompound()));
        tag.setTag("deuteriumTank", deuteriumTank.write(new NBTTagCompound()));
        tag.setTag("tritiumTank", tritiumTank.write(new NBTTagCompound()));
        tag.setTag("waterTank", waterTank.writeToNBT(new NBTTagCompound()));
        tag.setTag("steamTank", steamTank.writeToNBT(new NBTTagCompound()));

        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boolean formed = tag.getBoolean("formed");
        if (formed) {
            setReactor(new BetterFusionReactor(this));
            getReactor().setPlasmaTemp(tag.getDouble("plasmaTemp"));
            getReactor().setCaseTemp(tag.getDouble("caseTemp"));
            getReactor().setInjectionRate(tag.getInteger("injectionRate"));
            getReactor().setTargetReactivity(tag.getFloat("targetReactivity"));
            getReactor().setCurrentReactivity(tag.getFloat("currentReactivity"));
            getReactor().setErrorLevel(tag.getFloat("errorLevel"));
            getReactor().setAdjustment(tag.getFloat("adjustment"));
            getReactor().setLaserShootCountdown(tag.getInteger("laserShootCountdown"));
            getReactor().setBurning(tag.getBoolean("burning"));
            if(Loader.isModLoaded("nuclearcraft")) {
              ((nc.capability.radiation.source.RadiationSource) radiation).setRadiationLevel(tag.getDouble("radiationLevel"));
            }
            getReactor().updateTemperatures();
        }
        fuelTank.read(tag.getCompoundTag("fuelTank"));
        deuteriumTank.read(tag.getCompoundTag("deuteriumTank"));
        tritiumTank.read(tag.getCompoundTag("tritiumTank"));
        waterTank.readFromNBT(tag.getCompoundTag("waterTank"));
        steamTank.readFromNBT(tag.getCompoundTag("steamTank"));
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(getReactor() != null && getReactor().isFormed());
        if (getReactor() != null) {
            data.add(getReactor().getPlasmaTemp());
            data.add(getReactor().getCaseTemp());
            data.add(getReactor().getInjectionRate());
            data.add(getReactor().isBurning());
            data.add(getReactor().getTargetReactivity());
            data.add(getReactor().getCurrentReactivity());
            data.add(getReactor().getErrorLevel());
            data.add(getReactor().getAdjustment());
            data.add(getReactor().getLaserShootCountdown());
            if(Loader.isModLoaded("nuclearcraft")) {
                data.add(((nc.capability.radiation.source.RadiationSource) radiation).getRadiationLevel());
            }
            data.add(fuelTank.getStored());
            data.add(deuteriumTank.getStored());
            data.add(tritiumTank.getStored());
            TileUtils.addTankData(data, waterTank);
            TileUtils.addTankData(data, steamTank);
        }
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            int type = dataStream.readInt();
            if(getReactor() == null) return;
            switch(type) {
                case 0:
                    getReactor().setInjectionRate(dataStream.readInt());
                    break;
                case 1:
                    getReactor().adjustReactivity(5);
                    break;
                case 2:
                    getReactor().adjustReactivity(-5);
            }
            return;
        }

        super.handlePacketData(dataStream);

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean formed = dataStream.readBoolean();
            if (formed) {
                if (getReactor() == null || !getReactor().formed) {
                    BlockPos corner = getPos().subtract(new Vec3i(2, 4, 2));
                    Mekanism.proxy.doMultiblockSparkle(this, corner, 5, 5, 6, tile -> tile instanceof TileEntityReactorBlock);
                }
                if (getReactor() == null) {
                    setReactor(new BetterFusionReactor(this));
                    MekanismUtils.updateBlock(world, getPos());
                }

                getReactor().formed = true;
                getReactor().setPlasmaTemp(dataStream.readDouble());
                getReactor().setCaseTemp(dataStream.readDouble());
                getReactor().setInjectionRate(dataStream.readInt());
                getReactor().setBurning(dataStream.readBoolean());
                getReactor().setTargetReactivity(dataStream.readFloat());
                getReactor().setCurrentReactivity(dataStream.readFloat());
                getReactor().setErrorLevel(dataStream.readFloat());
                getReactor().setAdjustment(dataStream.readFloat());
                getReactor().setLaserShootCountdown(dataStream.readInt());
                if(Loader.isModLoaded("nuclearcraft")) {
                    ((nc.capability.radiation.source.RadiationSource) radiation).setRadiationLevel(dataStream.readDouble());
                }
                fuelTank.setGas(new GasStack(MekanismFluids.FusionFuel, dataStream.readInt()));
                deuteriumTank.setGas(new GasStack(MekanismFluids.Deuterium, dataStream.readInt()));
                tritiumTank.setGas(new GasStack(MekanismFluids.Tritium, dataStream.readInt()));
                TileUtils.readTankData(dataStream, waterTank);
                TileUtils.readTankData(dataStream, steamTank);
            } else if (getReactor() != null) {
                setReactor(null);
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    public boolean isFormed() {
        return getReactor() != null && getReactor().isFormed();
    }

    public boolean isBurning() {
        return getActive() && getReactor().isBurning();
    }

    @Override
    public boolean getActive() {
        return isFormed();
    }

    @Override
    public void setActive(boolean active) {
        if (active == (getReactor() == null)) {
            setReactor(active ? new BetterFusionReactor(this) : null);
        }
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (box == null) {
            box = new AxisAlignedBB(getPos().getX() - 1, getPos().getY() - 3, getPos().getZ() - 1, getPos().getX() + 2, getPos().getY(), getPos().getZ() + 2);
        }
        return box;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return isFormed() ? new int[]{0} : InventoryUtils.EMPTY;
    }

    @Override
    public boolean isItemValidForSlot(int slot, @Nonnull ItemStack stack) {
        return stack.getItem() instanceof ItemHohlraum;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return false;
        }
        return super.isCapabilityDisabled(capability, side);
    }
}