package igentuman.bfr.common.datagen.tag;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import igentuman.bfr.common.datagen.DataGenJsonConstants;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import mekanism.api.chemical.Chemical;
import mekanism.api.providers.*;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.block.Block;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITag.INamedTag;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeRegistryTagsProvider;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class BaseTagProvider implements IDataProvider {

    private final Map<TagType<?>, Map<INamedTag<?>, ITag.Builder>> supportedTagTypes = new Object2ObjectLinkedOpenHashMap<>();
    private final ExistingFileHelper existingFileHelper;
    private final DataGenerator gen;
    private final String modid;

    protected BaseTagProvider(DataGenerator gen, String modid, @Nullable ExistingFileHelper existingFileHelper) {
        this.gen = gen;
        this.modid = modid;
        this.existingFileHelper = existingFileHelper;
        addTagType(TagType.BLOCK);
        addTagType(TagType.ENTITY_TYPE);
        addTagType(TagType.TILE_ENTITY_TYPE);
    }

    @Nonnull
    @Override
    public String getName() {
        return "Tags: " + modid;
    }

    //Protected to allow for extensions to add their own supported types if they have one
    protected <TYPE extends IForgeRegistryEntry<TYPE>> void addTagType(TagType<TYPE> tagType) {
        supportedTagTypes.computeIfAbsent(tagType, type -> new Object2ObjectLinkedOpenHashMap<>());
    }

    protected abstract void registerTags();

    @Override
    public void run(@Nonnull DirectoryCache cache) {
        supportedTagTypes.values().forEach(Map::clear);
        registerTags();
        supportedTagTypes.forEach((tagType, tagTypeMap) -> act(cache, tagType, tagTypeMap));
    }

    private <TYPE extends IForgeRegistryEntry<TYPE>> void act(@Nonnull DirectoryCache cache, TagType<TYPE> tagType, Map<INamedTag<?>, ITag.Builder> tagTypeMap) {
        if (!tagTypeMap.isEmpty()) {
            //Create a dummy forge registry tags provider and pass all our collected data through to it
            new ForgeRegistryTagsProvider<TYPE>(gen, tagType.getRegistry(), modid, existingFileHelper) {
                @Override
                protected void addTags() {
                    //Add each tag builder to the wrapped provider's builder, but wrap the builder so that we
                    // make sure to first cleanup and remove excess/unused json components
                    // Note: We only override the methods used by the TagsProvider rather than proxying everything back to the original tag builder
                    tagTypeMap.forEach((tag, tagBuilder) -> builders.put(tag.getName(), new ITag.Builder() {
                        @Nonnull
                        @Override
                        public JsonObject serializeToJson() {
                            return cleanJsonTag(tagBuilder.serializeToJson());
                        }

                        @Nonnull
                        @Override
                        public <T> Stream<ITag.Proxy> getUnresolvedEntries(@Nonnull Function<ResourceLocation, ITag<T>> resourceTagFunction,
                              @Nonnull Function<ResourceLocation, T> resourceElementFunction) {
                            return tagBuilder.getUnresolvedEntries(resourceTagFunction, resourceElementFunction);
                        }
                    }));
                }

                @Nonnull
                @Override
                public String getName() {
                    return tagType.getName() + " Tags: " + modid;
                }
            }.run(cache);
        }
    }

    private JsonObject cleanJsonTag(JsonObject tagAsJson) {
        if (tagAsJson.has(DataGenJsonConstants.REPLACE)) {
            //Strip out the optional "replace" entry from the tag if it is the default value
            JsonPrimitive replace = tagAsJson.getAsJsonPrimitive(DataGenJsonConstants.REPLACE);
            if (replace.isBoolean() && !replace.getAsBoolean()) {
                tagAsJson.remove(DataGenJsonConstants.REPLACE);
            }
        }
        return tagAsJson;
    }

    //Protected to allow for extensions to add retrieve their own supported types if they have any
    protected <TYPE extends IForgeRegistryEntry<TYPE>> ForgeRegistryTagBuilder<TYPE> getBuilder(TagType<TYPE> tagType, INamedTag<TYPE> tag) {
        return new ForgeRegistryTagBuilder<>(supportedTagTypes.get(tagType).computeIfAbsent(tag, ignored -> ITag.Builder.tag()), modid);
    }

    protected ForgeRegistryTagBuilder<Item> getItemBuilder(INamedTag<Item> tag) {
        return getBuilder(TagType.ITEM, tag);
    }

    protected ForgeRegistryTagBuilder<Block> getBlockBuilder(INamedTag<Block> tag) {
        return getBuilder(TagType.BLOCK, tag);
    }

    protected ForgeRegistryTagBuilder<EntityType<?>> getEntityTypeBuilder(INamedTag<EntityType<?>> tag) {
        return getBuilder(TagType.ENTITY_TYPE, tag);
    }


    protected ForgeRegistryTagBuilder<TileEntityType<?>> getTileEntityTypeBuilder(INamedTag<TileEntityType<?>> tag) {
        return getBuilder(TagType.TILE_ENTITY_TYPE, tag);
    }

    protected void addToTag(INamedTag<Item> tag, IItemProvider... itemProviders) {
        ForgeRegistryTagBuilder<Item> tagBuilder = getItemBuilder(tag);
        for (IItemProvider itemProvider : itemProviders) {
            tagBuilder.add(itemProvider.asItem());
        }
    }

    protected void addToTag(INamedTag<Block> tag, IBlockProvider... blockProviders) {
        ForgeRegistryTagBuilder<Block> tagBuilder = getBlockBuilder(tag);
        for (IBlockProvider blockProvider : blockProviders) {
            tagBuilder.add(blockProvider.getBlock());
        }
    }

    protected void addToTags(INamedTag<Item> itemTag, INamedTag<Block> blockTag, IBlockProvider... blockProviders) {
        ForgeRegistryTagBuilder<Item> itemTagBuilder = getItemBuilder(itemTag);
        ForgeRegistryTagBuilder<Block> blockTagBuilder = getBlockBuilder(blockTag);
        for (IBlockProvider blockProvider : blockProviders) {
            itemTagBuilder.add(blockProvider.getItem());
            blockTagBuilder.add(blockProvider.getBlock());
        }
    }

    protected void addToTag(INamedTag<EntityType<?>> tag, IEntityTypeProvider... entityTypeProviders) {
        ForgeRegistryTagBuilder<EntityType<?>> tagBuilder = getEntityTypeBuilder(tag);
        for (IEntityTypeProvider entityTypeProvider : entityTypeProviders) {
            tagBuilder.add(entityTypeProvider.getEntityType());
        }
    }

    protected void addToTag(INamedTag<TileEntityType<?>> tag, TileEntityTypeRegistryObject<?>... tileEntityTypeRegistryObjects) {
        ForgeRegistryTagBuilder<TileEntityType<?>> tagBuilder = getTileEntityTypeBuilder(tag);
        for (TileEntityTypeRegistryObject<?> tileEntityTypeRO : tileEntityTypeRegistryObjects) {
            tagBuilder.add(tileEntityTypeRO.get());
        }
    }

    @SafeVarargs
    protected final <CHEMICAL extends Chemical<CHEMICAL>> void addToTag(ForgeRegistryTagBuilder<CHEMICAL> tagBuilder, IChemicalProvider<CHEMICAL>... providers) {
        for (IChemicalProvider<CHEMICAL> provider : providers) {
            tagBuilder.add(provider.getChemical());
        }
    }
}