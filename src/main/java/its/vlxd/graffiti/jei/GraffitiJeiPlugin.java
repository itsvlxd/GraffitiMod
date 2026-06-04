package its.vlxd.graffiti.jei;

import its.vlxd.graffiti.GraffitiMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

@JeiPlugin
public class GraffitiJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.parse("graffiti:jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
                new ItemStack(GraffitiMod.GRAFFITI_TOOL.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.graffiti.description")
        );

        var factory = registration.getVanillaRecipeFactory();
        var recipe = factory.createShapedRecipeBuilder(
                        CraftingBookCategory.MISC,
                        List.of(new ItemStack(GraffitiMod.GRAFFITI_TOOL.get()))
                )
                .define('c', Ingredient.of(Items.COPPER_INGOT))
                .define('R', Ingredient.of(Items.RED_DYE))
                .define('G', Ingredient.of(Items.GREEN_DYE))
                .define('B', Ingredient.of(Items.BLUE_DYE))
                .define('i', Ingredient.of(Items.IRON_INGOT))
                .pattern(" c ")
                .pattern("RGB")
                .pattern("iii")
                .build();

        registration.addRecipes(
                RecipeTypes.CRAFTING,
                List.of(new RecipeHolder<>(ResourceLocation.parse("graffiti:crafting"), recipe))
        );
    }

    @Override
    public void registerIngredientAliases(IIngredientAliasRegistration registration) {
        registration.addAliases(
                VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(GraffitiMod.GRAFFITI_TOOL.get())),
                List.of("Graffiti Can", "Graffiti Spray", "Spray Can")
        );
    }
}
