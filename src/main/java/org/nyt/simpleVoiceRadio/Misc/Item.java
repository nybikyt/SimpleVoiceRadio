package org.nyt.simpleVoiceRadio.Misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.Utils.MiniMessageSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Item {
    private final SimpleVoiceRadio plugin;
    private final DisplayEntityManager displayEntityManager;

    public Item(SimpleVoiceRadio plugin, DisplayEntityManager displayEntityManager) {
        this.plugin = plugin;
        this.displayEntityManager = displayEntityManager;
    }

    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        displayEntityManager.setSkullByValue(plugin.getConfig().getString("radio.skull_skin", "ewogICJ0aW1lc3RhbXAiIDogMTc2ODczOTgxNzc0NiwKICAicHJvZmlsZUlkIiA6ICI2NmRmYzFmNTRlNTU0ZTZmODJjNTA5ZjM1NTJiYTkwZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJadWFyaWciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTM4ZmFhZWNlM2QyZmUyZTQzODRhODBhMDE3MGJhYzgyYjFmNTE0MDY2MmU2OTRjNjY2ZGQxZjcyMzNmZmI2ZSIKICAgIH0KICB9Cn0="), item);
        ItemMeta meta = item.getItemMeta();

        String displayName = plugin.getConfig().getString("radio.display-name", "Radio");
        meta.displayName(MiniMessageSerializer.parse(displayName).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = parseLore();
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        meta.getPersistentDataContainer().set(
                NamespacedKey.fromString("radio"),
                PersistentDataType.BOOLEAN,
                true
        );
        item.setItemMeta(meta);

        return item;
    }

    private List<Component> parseLore() {
        List<Component> lore = new ArrayList<>();

        if (plugin.getConfig().isList("radio.lore")) {
            plugin.getConfig().getStringList("radio.lore").stream()
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> MiniMessageSerializer.parse(line).decoration(TextDecoration.ITALIC, false))
                    .forEach(lore::add);
        } else if (plugin.getConfig().contains("radio.lore")) {
            String line = String.valueOf(plugin.getConfig().get("radio.lore")).trim();
            if (!line.isEmpty() && !line.equals("null")) lore.add(MiniMessageSerializer.parse(line).decoration(TextDecoration.ITALIC, false));
        }

        return lore;
    }

    private void setIngredient(ShapedRecipe recipe, char key, String materialName) {
        if (materialName == null || materialName.equalsIgnoreCase("AIR")) return;
        recipe.setIngredient(key, Material.valueOf(materialName.trim().toUpperCase()));
    }

    public void registerCraft() {
        if (!plugin.getConfig().getBoolean("radio.craft.enabled", true)) return;

        ItemStack item = getItem();
        NamespacedKey key = new NamespacedKey(plugin, "radio");
        ShapedRecipe recipe = new ShapedRecipe(key, item);

        try {
            List<String> recipeLines = plugin.getConfig().getStringList("radio.craft.recipe");
            String[] row1 = recipeLines.get(0).split(",");
            String[] row2 = recipeLines.get(1).split(",");
            String[] row3 = recipeLines.get(2).split(",");

            recipe.shape("012", "345", "678");

            setIngredient(recipe, '0', row1[0]);
            setIngredient(recipe, '1', row1[1]);
            setIngredient(recipe, '2', row1[2]);
            setIngredient(recipe, '3', row2[0]);
            setIngredient(recipe, '4', row2[1]);
            setIngredient(recipe, '5', row2[2]);
            setIngredient(recipe, '6', row3[0]);
            setIngredient(recipe, '7', row3[1]);
            setIngredient(recipe, '8', row3[2]);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.warn("Error while loading recipe: " + e.getMessage() + ". Using default recipe!");
            recipe.shape("CHC", "IJI", "RNR");
            recipe.setIngredient('C', Material.COPPER_INGOT);
            recipe.setIngredient('H', Material.CHISELED_COPPER);
            recipe.setIngredient('I', Material.IRON_INGOT);
            recipe.setIngredient('J', Material.JUKEBOX);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('R', Material.REDSTONE);
        }

        plugin.getServer().addRecipe(recipe);
    }

    public ItemStack[] getRecipeIngredients() {
        NamespacedKey key = new NamespacedKey(plugin, "radio");
        Recipe recipe = plugin.getServer().getRecipe(key);

        if (!(recipe instanceof ShapedRecipe shapedRecipe)) return new ItemStack[10];

        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(getItem());

        Map<Character, RecipeChoice> choiceMap = shapedRecipe.getChoiceMap();

        for (String row : shapedRecipe.getShape()) {
            for (char c : row.toCharArray()) {
                RecipeChoice choice = choiceMap.get(c);
                if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
                    ingredients.add(new ItemStack(materialChoice.getChoices().get(0)));
                } else {
                    ingredients.add(new ItemStack(Material.AIR));
                }
            }
        }

        return ingredients.toArray(new ItemStack[0]);
    }

    public void reloadCraft() {
        NamespacedKey key = new NamespacedKey(plugin, "radio");
        plugin.getServer().removeRecipe(key);
        registerCraft();
    }
}