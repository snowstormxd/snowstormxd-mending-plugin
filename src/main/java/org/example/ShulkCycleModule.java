package com.yourname.shulkcycle;

import com.zenith.plugin.PluginAPI;
import com.zenith.client.ProxyClient;
import com.zenith.world.BlockPos;
import com.zenith.inventory.Container;
import com.zenith.path.BaritoneController;
import com.zenith.item.ItemStack;
import com.zenith.inventory.InventoryView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core logic. Single worker thread implements a simple state machine.
 * NOTE: many method names below match the common example-plugin naming used in ZenithProxy examples.
 * If your Javadoc uses different names, search/replace the method (openContainerAt, quickMove, etc).
 */
public class ShulkCycleModule /* implements Module (if required by your template) */ {
    private final PluginAPI api;
    private final ProxyClient client;
    private final BaritoneController baritone;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private BlockPos chestPos;

    public ShulkCycleModule(PluginAPI api) {
        this.api = api;
        this.client = api.getProxyClient();                // example accessor - adjust if necessary
        this.baritone = client.getBaritone();              // example accessor - adjust if necessary
    }

    public String getStatus() {
        return running.get() ? "Running (chest " + chestPos + ")" : "Stopped";
    }

    public void startCycle(int x, int y, int z) {
        if (running.get()) return;
        this.chestPos = new BlockPos(x, y, z);
        running.set(true);

        worker = new Thread(() -> {
            api.getLogger().info("[ShulkerCycle] worker started");
            while (running.get()) {
                try {
                    // 1) Go to chest
                    if (!moveTo(chestPos)) {
                        api.getLogger().warning("Cannot path to chest; stopping.");
                        running.set(false);
                        break;
                    }

                    // 2) Open chest
                    Container chest = openContainerAt(chestPos);
                    if (chest == null) {
                        api.getLogger().warning("Could not open chest; stopping.");
                        running.set(false);
                        break;
                    }

                    // 3) Find and take one shulker box
                    int shulkerSlot = findShulkerInContainer(chest);
                    if (shulkerSlot == -1) {
                        api.getLogger().info("No shulkers left. Stopping cycle.");
                        running.set(false);
                        break;
                    }
                    quickMoveFromContainer(chest, shulkerSlot);

                    // 4) Find safe place nearby and place the shulker
                    BlockPos placePos = findSafePlaceNear(chestPos);
                    placeShulkerAt(placePos);

                    // 5) Open placed shulker and move items to hotbar
                    Container placed = openContainerAt(placePos);
                    if (placed == null) {
                        api.getLogger().warning("Could not open placed shulker; trying to pick it up and return.");
                        pickUpBlock(placePos);
                        depositShulkerBackToChest();
                        continue;
                    }
                    moveAllContainerToHotbar(placed);

                    // 6) Choose repair spot automatically (simple strategy below)
                    BlockPos xpSpot = chooseRepairSpot(chestPos, placePos);
                    if (xpSpot != null) moveTo(xpSpot);

                    // 7) Wait for mending
                    waitForMending(3 * 60 * 1000); // max 3 minutes by default

                    // 8) Return to placed shulker
                    moveTo(placePos);

                    // 9) Open and put hotbar back into shulker
                    placed = openContainerAt(placePos);
                    if (placed == null) {
                        api.getLogger().warning("Failed to open placed shulker to reinsert items.");
                    } else {
                        putHotbarBackIntoContainer(placed);
                    }

                    // 10) Pick up shulker, return to chest, deposit
                    pickUpBlock(placePos);
                    moveTo(chestPos);
                    depositShulkerBackToChest();

                    // small pause
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                } catch (Exception ex) {
                    api.getLogger().severe("Error in cycle: " + ex.getMessage());
                    ex.printStackTrace();
                    // safe stop on unhandled error
                    running.set(false);
                }
            }
            api.getLogger().info("[ShulkerCycle] worker ended");
        }, "ShulkerCycle-Worker");
        worker.start();
    }

    public void stop() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }

    // -------------------------
    // Helper methods (mostly small wrappers around client actions)
    // -------------------------

    private boolean moveTo(BlockPos pos) {
        // Block until arrival or timeout. Replace with the actual Baritone/Movement API.
        try {
            baritone.moveToBlocking(pos, 45_000); // example API (timeout 45s)
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Container openContainerAt(BlockPos pos) {
        // Right-click the block and wait for inventory to open.
        // Use client.interactBlock(pos, Hand.MAIN_HAND) + wait until client.getOpenContainer() appears.
        client.lookAt(pos);
        client.interactBlock(pos);
        Container c = client.waitForOpenContainer(3_000); // example helper; change if necessary
        return c;
    }

    private int findShulkerInContainer(Container chest) {
        for (int i = 0; i < chest.getSize(); i++) {
            ItemStack it = chest.getItem(i);
            if (it == null) continue;
            String id = it.getTypeId().toLowerCase();
            if (id.contains("shulker")) return i;
        }
        return -1;
    }

    private void quickMoveFromContainer(Container container, int slot) throws InterruptedException {
        container.quickMove(slot); // example; swaps to player inventory
        Thread.sleep(150);
    }

    private BlockPos findSafePlaceNear(BlockPos origin) {
        // naive: try positions around chest; return first air block. This is intentionally simple.
        for (int dx = 2; dx <= 4; dx++) {
            BlockPos tryPos = new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ());
            if (client.getWorld().isAirBlock(tryPos)) return tryPos;
        }
        // fallback: one block above chest
        return new BlockPos(origin.getX(), origin.getY() + 1, origin.getZ());
    }

    private void placeShulkerAt(BlockPos pos) throws InterruptedException {
        // Equip shulker from inventory (search for shulker)
        int invSlot = client.getInventory().findItemSlot("shulker_box");
        if (invSlot < 0) {
            api.getLogger().warning("No shulker in inventory to place.");
            return;
        }
        client.getInventory().setHeldSlot(invSlot);
        client.placeBlock(pos);
        Thread.sleep(200);
    }

    private void moveAllContainerToHotbar(Container container) throws InterruptedException {
        InventoryView inv = client.getInventory();
        for (int s = 0; s < container.getSize(); s++) {
            ItemStack it = container.getItem(s);
            if (it == null) continue;
            int freeHotbar = findFreeHotbarSlot();
            if (freeHotbar < 0) {
                api.getLogger().warning("Hotbar full - will stop moving items.");
                break;
            }
            container.quickMoveToHotbar(s, freeHotbar);
            Thread.sleep(120);
        }
    }

    private int findFreeHotbarSlot() {
        for (int s = 0; s < 9; s++) {
            if (client.getInventory().getItem(s) == null) return s;
        }
        return -1;
    }

    private BlockPos chooseRepairSpot(BlockPos chest, BlockPos placedShulker) {
        // Simple: pick a nearby spot 6 blocks away in X direction; if obstructed, use current player pos.
        BlockPos candidate = new BlockPos(chest.getX() + 6, chest.getY(), chest.getZ());
        if (client.getWorld().isWalkable(candidate)) return candidate;
        return client.getPlayerPosition();
    }

    private void waitForMending(long maxMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMillis && running.get()) {
            boolean allMended = true;
            for (int s = 0; s < 9; s++) {
                ItemStack it = client.getInventory().getItem(s);
                if (it == null) continue;
                if (it.hasDurability() && it.getDurability() > 0) {
                    allMended = false;
                    break;
                }
            }
            if (allMended) return;
            Thread.sleep(1000);
        }
    }

    private void putHotbarBackIntoContainer(Container container) throws InterruptedException {
        for (int s = 0; s < 9; s++) {
            ItemStack it = client.getInventory().getItem(s);
            if (it == null) continue;
            container.quickMoveFromHotbar(s);
            Thread.sleep(100);
        }
    }

    private void pickUpBlock(BlockPos pos) throws InterruptedException {
        // Break the shulker and pick up. Use safe break (sneak + break) to avoid items popping out if needed.
        client.breakBlock(pos);
        Thread.sleep(200);
    }

    private void depositShulkerBackToChest() throws InterruptedException {
        // find shulker in inventory then open chest and quickMove into first free slot
        int shSlot = client.getInventory().findItemSlot("shulker_box");
        if (shSlot < 0) {
            api.getLogger().warning("No shulker found in inventory to deposit.");
            return;
        }
        Container chest = openContainerAt(chestPos);
        if (chest == null) {
            api.getLogger().warning("Unable to open chest to deposit shulker.");
            return;
        }
        // quickMove shulker to chest
        client.getInventory().setHeldSlot(shSlot);
        chest.quickMoveFromPlayer(shSlot);
        Thread.sleep(200);
    }
}
