package com.trevorschoeny.openanywhere.checks;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.storage.StorageAttachment.PlayerStorageAttachment;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A carried attachment for the checks, as another mod would declare one (Pocket Ender
 * Chest's ender chest slot): one slot on every menu, hidden on the client, taking no
 * shift-clicks, so the other checks never meet it.
 */
public class CarriedSlot implements ModInitializer {

    static final Identifier ID = Identifier.fromNamespaceAndPath("openanywhere_checks", "pocket");
    static PlayerStorageAttachment<NonNullList<ItemStack>> POCKET;

    @Override
    public void onInitialize() {
        POCKET = StorageAttachment.playerAttached(ID.getNamespace(), ID.getPath(), 1).carried();
        ParityGroup.register(ID.toString(), SlotSpec.at("slot", new SlotGroupCategory(ID.getNamespace(), "pocket"))
                .storage(p -> POCKET.bind(p))
                .revealWhen(() -> false)
                .shiftClickIn(false)
                .collect(false));
    }
}
