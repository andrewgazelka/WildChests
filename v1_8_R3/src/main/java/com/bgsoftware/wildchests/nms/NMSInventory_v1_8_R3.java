package com.bgsoftware.wildchests.nms;

import com.bgsoftware.wildchests.api.objects.chests.Chest;
import com.bgsoftware.wildchests.api.objects.chests.StorageChest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;
import com.bgsoftware.wildchests.hooks.WildStackerHook;
import com.bgsoftware.wildchests.objects.chests.WChest;
import com.bgsoftware.wildchests.objects.chests.WStorageChest;
import com.bgsoftware.wildchests.objects.containers.TileEntityContainer;
import com.bgsoftware.wildchests.objects.inventory.WildItemStack;
import com.bgsoftware.wildchests.utils.ChestUtils;
import com.google.common.base.Predicate;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.Container;
import net.minecraft.server.v1_8_R3.ContainerChest;
import net.minecraft.server.v1_8_R3.ContainerHopper;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityItem;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.IUpdatePlayerListBox;
import net.minecraft.server.v1_8_R3.IWorldInventory;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagByte;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.PacketPlayOutOpenWindow;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntityChest;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftItem;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

@SuppressWarnings("unused")
public final class NMSInventory_v1_8_R3 implements NMSInventory {

    @Override
    public void updateTileEntity(Chest chest) {
        Location loc = chest.getLocation();
        World world = ((CraftWorld) loc.getWorld()).getHandle();
        BlockPosition blockPosition = new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Chunk chunk = world.getChunkAtWorldCoords(blockPosition);

        TileEntityWildChest tileEntityWildChest = new TileEntityWildChest(chest, world, blockPosition);

        chunk.tileEntities.put(blockPosition, tileEntityWildChest);
        world.capturedTileEntities.put(blockPosition, tileEntityWildChest);
        world.tileEntityList.add(tileEntityWildChest);
    }

    @Override
    public WildItemStack<?, ?> createItemStack(org.bukkit.inventory.ItemStack itemStack) {
        ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        return new WildItemStack<>(nmsItem, CraftItemStack.asCraftMirror(nmsItem));
    }

    @Override
    public com.bgsoftware.wildchests.objects.inventory.CraftWildInventory createInventory(Chest chest, int size, String title, int index) {
        WildInventory wildInventory = new WildInventory(size, title, chest, index);

        if(chest instanceof StorageChest)
            wildInventory.setItemFunction = (slot, itemStack) -> chest.setItem(slot, CraftItemStack.asCraftMirror(itemStack));

        return new CraftWildInventory(wildInventory);
    }

    @Override
    public void openPage(Player player, com.bgsoftware.wildchests.objects.inventory.CraftWildInventory inventory) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();

        String title = inventory.getTitle();

        Container container = createContainer(entityPlayer.inventory, entityPlayer, inventory);
        container.windowId = entityPlayer.nextContainerCounter();
        TileEntityWildChest tileEntityWildChest = getTileEntity(inventory.getOwner());

        entityPlayer.playerConnection.sendPacket(new PacketPlayOutOpenWindow(container.windowId, tileEntityWildChest.getContainerName(), new ChatComponentText(title), inventory.getSize()));
        entityPlayer.activeContainer = container;
        entityPlayer.activeContainer.addSlotListener(entityPlayer);
    }

    @Override
    public void createDesignItem(com.bgsoftware.wildchests.objects.inventory.CraftWildInventory craftWildInventory, org.bukkit.inventory.ItemStack itemStack) {
        ItemStack designItem = CraftItemStack.asNMSCopy(itemStack == null || itemStack.getType() == Material.AIR ?
                new org.bukkit.inventory.ItemStack(Material.STAINED_GLASS_PANE, 1, (byte) 15) :
                itemStack.clone());

        designItem.count = 1;
        designItem.a("DesignItem", new NBTTagByte((byte) 1));

        WildInventory inventory = ((CraftWildInventory) craftWildInventory).getInventory();
        inventory.setItem(0, designItem, false);
        inventory.setItem(1, designItem, false);
        inventory.setItem(3, designItem, false);
        inventory.setItem(4, designItem, false);
    }

    private static Container createContainer(PlayerInventory playerInventory, EntityHuman entityHuman, com.bgsoftware.wildchests.objects.inventory.CraftWildInventory craftWildInventory){
        WildInventory inventory = ((CraftWildInventory) craftWildInventory).getInventory();
        return inventory.getSize() == 5 ? WildContainerHopper.of(playerInventory, entityHuman, inventory) :
                WildContainerChest.of(playerInventory, entityHuman, inventory);
    }

    private static TileEntityWildChest getTileEntity(Chest chest) {
        Location loc = chest.getLocation();
        World world = ((CraftWorld) loc.getWorld()).getHandle();
        BlockPosition blockPosition = new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return (TileEntityWildChest) world.getTileEntity(blockPosition);
    }

    private static class TileEntityWildChest extends TileEntityChest implements IWorldInventory, TileEntityContainer, IUpdatePlayerListBox {

        private final TileEntityChest tileEntityChest = new TileEntityChest();
        private final Chest chest;
        private final boolean isTrappedChest;

        private short currentCooldown = ChestUtils.DEFAULT_COOLDOWN;

        private AxisAlignedBB suctionItems = null;
        private boolean autoCraftMode = false;
        private boolean autoSellMode = false;

        private TileEntityWildChest(Chest chest, World world, BlockPosition blockPosition){
            this.chest = chest;
            this.world = world;
            updateTile(this, world, blockPosition);
            updateTile(tileEntityChest, world, blockPosition);
            isTrappedChest = world.getType(blockPosition).getBlock() == Blocks.TRAPPED_CHEST;
            ((WChest) chest).setTileEntityContainer(this);
            updateData();
        }

        @Override
        public void setItem(int i, ItemStack itemStack) {
            ((WChest) chest).setItem(i, new WildItemStack<>(itemStack, CraftItemStack.asCraftMirror(itemStack)));
        }

        @Override
        public ItemStack getItem(int i) {
            return (ItemStack) ((WChest) chest).getWildItem(i).getItemStack();
        }

        @Override
        public ItemStack[] getContents() {
            WildItemStack<?, ?>[] contents = ((WChest) chest).getWildContents();
            ItemStack[] newContents = new ItemStack[contents.length];

            for(int i = 0; i < contents.length; i++)
                newContents[i] = (ItemStack) contents[i].getItemStack();

            return newContents;
        }

        @Override
        public void b(NBTTagCompound nbttagcompound) {
            tileEntityChest.b(nbttagcompound);
        }

        @Override
        public String getContainerName() {
            return chest instanceof StorageChest ? "minecraft:hopper" : super.getContainerName();
        }

        @Override
        public Container createContainer(PlayerInventory playerinventory, EntityHuman entityHuman) {
            return NMSInventory_v1_8_R3.createContainer(playerinventory, entityHuman,
                    (com.bgsoftware.wildchests.objects.inventory.CraftWildInventory) chest.getPage(0));
        }

        @Override
        public IChatBaseComponent getScoreboardDisplayName() {
            return new ChatComponentText(chest.getPage(0).getTitle());
        }

        @Override
        public final void closeContainer(EntityHuman entityHuman) {
            CraftHumanEntity craftHumanEntity = entityHuman.getBukkitEntity();

            this.l = (int) this.transaction.stream().filter(human -> human.getGameMode() != GameMode.SPECTATOR).count();
            this.transaction.remove(craftHumanEntity);

            if (!craftHumanEntity.getHandle().isSpectator()) {
                int oldPower = Math.max(0, Math.min(15, this.l));
                this.l--;

                this.world.playBlockAction(this.position, this.getBlock(), 1, this.l);

                if (isTrappedChest) {
                    int newPower = Math.max(0, Math.min(15, this.l));
                    if (oldPower != newPower) {
                        CraftEventFactory.callRedstoneChange(this.world, this.position.getX(), this.position.getY(), this.position.getZ(), oldPower, newPower);
                    }
                }

                this.world.applyPhysics(this.position, this.getBlock());
                this.world.applyPhysics(this.position.down(), this.getBlock());
            }
        }

        @Override
        public final void startOpen(EntityHuman entityHuman) {
            CraftHumanEntity craftHumanEntity = entityHuman.getBukkitEntity();

            this.transaction.add(craftHumanEntity);

            if (!craftHumanEntity.getHandle().isSpectator()) {
                if (this.l < 0) {
                    this.l = 0;
                }

                int oldPower = Math.max(0, Math.min(15, this.l));
                this.l++;
                if (this.world == null) {
                    return;
                }

                this.world.playBlockAction(this.position, this.getBlock(), 1, this.l);

                if (isTrappedChest) {
                    int newPower = Math.max(0, Math.min(15, this.l));
                    if (oldPower != newPower) {
                        CraftEventFactory.callRedstoneChange(this.world, this.position.getX(), this.position.getY(), this.position.getZ(), oldPower, newPower);
                    }
                }

                this.world.applyPhysics(this.position, this.getBlock());
                this.world.applyPhysics(this.position.down(), this.getBlock());
            }
        }

        @Override
        public final void onOpen(CraftHumanEntity who) {

        }

        @Override
        public final void onClose(CraftHumanEntity who) {

        }

        @Override
        public void c() {
            super.c();

            ChestData chestData = chest.getData();

            {
                double x = position.getX() + world.random.nextFloat();
                double y = position.getY() + world.random.nextFloat();
                double z = position.getZ() + world.random.nextFloat();
                for(String particle : chestData.getChestParticles()) {
                    try {
                        ((WorldServer) world).sendParticles(null, EnumParticle.valueOf(particle),
                                false, x, y, z, 0, 0.0, 0.0, 0.0, 1.0);
                    }catch (Exception ignored){}
                }
            }

            if(--currentCooldown >= 0)
                return;

            Block currentBlock = world.getType(position).getBlock();

            if(currentBlock != Blocks.CHEST && currentBlock != Blocks.TRAPPED_CHEST){
                world.getChunkAtWorldCoords(position).tileEntities.remove(position);
                world.capturedTileEntities.remove(position);
                world.tileEntityList.remove(this);
                return;
            }

            currentCooldown = ChestUtils.DEFAULT_COOLDOWN;

            if(suctionItems != null) {
                for (Entity entity : world.a(EntityItem.class, suctionItems, (Predicate<? super EntityItem>) entity ->
                        entity != null && ChestUtils.SUCTION_PREDICATE.test((CraftItem) entity.getBukkitEntity(), chestData))) {
                    EntityItem entityItem = (EntityItem) entity;
                    org.bukkit.inventory.ItemStack itemStack = CraftItemStack.asCraftMirror(entityItem.getItemStack());
                    Item item = (Item) entityItem.getBukkitEntity();

                    if (WildStackerHook.isEnabled())
                        itemStack.setAmount(WildStackerHook.getItemAmount(item));

                    org.bukkit.inventory.ItemStack remainingItem = ChestUtils.getRemainingItem(chest.addItems(itemStack));

                    if (remainingItem == null) {
                        ((WorldServer) world).sendParticles(null, EnumParticle.CLOUD, false,
                                entityItem.locX, entityItem.locY, entityItem.locZ, 0, 0.0, 0.0, 0.0, 1.0);
                        entityItem.die();
                    } else if (WildStackerHook.isEnabled()) {
                        WildStackerHook.setRemainings(item, remainingItem.getAmount());
                    } else {
                        item.setItemStack(remainingItem);
                    }
                }
            }

            if(autoCraftMode){
                ChestUtils.tryCraftChest(chest);
            }

            if (autoSellMode){
                ChestUtils.trySellChest(chest);
            }
        }

        @Override
        public int getSize() {
            return chest.getPage(0).getSize() * chest.getPagesAmount();
        }

        @Override
        public int getViewingCount() {
            if (this.l < 0)
                this.l = 0;

            return l;
        }

        @Override
        public List<HumanEntity> getTransaction() {
            return transaction;
        }

        @Override
        public void setTransaction(List<HumanEntity> transaction) {
            this.transaction = transaction;
        }

        @Override
        public void openContainer(HumanEntity humanEntity) {
            startOpen(((CraftHumanEntity) humanEntity).getHandle());
        }

        @Override
        public void closeContainer(HumanEntity humanEntity) {
            closeContainer(((CraftHumanEntity) humanEntity).getHandle());
        }

        @Override
        public void updateData(){
            ChestData chestData = chest.getData();
            suctionItems = !chestData.isAutoSuction() ? null :  new AxisAlignedBB(
                    chestData.isAutoSuctionChunk() ? position.getX() >> 4 << 4 : position.getX() - chestData.getAutoSuctionRange(),
                    position.getY() - chestData.getAutoSuctionRange(),
                    chestData.isAutoSuctionChunk() ? position.getZ() >> 4 << 4 : position.getZ() - chestData.getAutoSuctionRange(),
                    chestData.isAutoSuctionChunk() ? (position.getX() >> 4 << 4) + 16 : position.getX() + chestData.getAutoSuctionRange(),
                    position.getY() + chestData.getAutoSuctionRange(),
                    chestData.isAutoSuctionChunk() ? (position.getZ() >> 4 << 4) + 16 : position.getZ() + chestData.getAutoSuctionRange()
            );
            autoCraftMode = chestData.isAutoCrafter();
            autoSellMode = chestData.isSellMode();
        }

        @Override
        public int[] getSlotsForFace(EnumDirection enumDirection) {
            return chest.getSlotsForFace();
        }

        @Override
        public boolean canPlaceItemThroughFace(int i, ItemStack itemStack, EnumDirection enumDirection) {
            return chest.canPlaceItemThroughFace(CraftItemStack.asCraftMirror(itemStack));
        }

        @Override
        public boolean canTakeItemThroughFace(int i, ItemStack itemStack, EnumDirection enumDirection) {
            return chest.canTakeItemThroughFace(i, CraftItemStack.asCraftMirror(itemStack));
        }

        @Override
        public ItemStack splitStack(int slot, int amount) {
            return slot != -2 || !(chest instanceof StorageChest) ? splitItem(slot, amount) :
                    (ItemStack) ((WStorageChest) chest).splitItem(amount).getItemStack();
        }

        @Override
        public void update() {
            super.update();
            if(chest instanceof StorageChest)
                ((StorageChest) chest).update();
        }

        private void updateTile(TileEntity tileEntity, World world, BlockPosition blockPosition){
            tileEntity.a(world);
            tileEntity.a(blockPosition);
        }

        private Block getBlock(){
            return world.getType(position).getBlock();
        }

        private ItemStack splitItem(int i, int amount){
            Inventory firstPage = chest.getPage(0);

            if(firstPage == null)
                return null;

            int pageSize = firstPage.getSize();
            int page = i / pageSize;
            int slot = i % pageSize;

            NMSInventory_v1_8_R3.CraftWildInventory actualPage = (NMSInventory_v1_8_R3.CraftWildInventory) chest.getPage(page);

            if(actualPage == null)
                return null;

            return actualPage.getInventory().splitStack(slot, amount);
        }

    }

    private static class WildInventory implements IInventory {

        private static final WildItemStack<ItemStack, CraftItemStack> AIR = new WildItemStack<>(null, CraftItemStack.asCraftMirror(null));

        private final WildItemStack<ItemStack, CraftItemStack>[] items;
        private final Chest chest;
        private final int index;

        private BiConsumer<Integer, ItemStack> setItemFunction = null;
        private int maxStack = 64;
        private int nonEmptyItems = 0;
        private String title;

        WildInventory(int size, String title, Chest chest, int index) {
            this.title = title == null ? "Chest" : title;
            //noinspection all
            this.items = new WildItemStack[size];
            Arrays.fill(this.items, AIR);
            this.chest = chest;
            this.index = index;
        }

        public int getSize() {
            return this.items.length;
        }

        public ItemStack getItem(int i) {
            return getWildItem(i).getItemStack();
        }

        public WildItemStack<ItemStack, CraftItemStack> getWildItem(int i) {
            return this.items[i] == null ? AIR : this.items[i];
        }

        public ItemStack splitStack(int slot, int amount) {
            return splitStack(slot, amount, true);
        }

        public ItemStack splitWithoutUpdate(int slot) {
            return splitStack(slot, 1, false);
        }

        private ItemStack splitStack(int slot, int amount, boolean update){
            ItemStack stack = this.getItem(slot);
            if (stack == null) {
                return null;
            } else {
                ItemStack result;
                if (stack.count <= amount) {
                    this.setItem(slot, null);
                    result = stack;
                } else {
                    result = CraftItemStack.copyNMSStack(stack, amount);
                    stack.count -= amount;
                }

                if(update)
                    this.update();

                return result;
            }
        }

        public void setItem(int i, ItemStack itemStack) {
            setItem(i, itemStack, true);
        }

        public void setItem(int i, ItemStack itemStack, boolean setItemFunction){
            setItem(i, new WildItemStack<>(itemStack, CraftItemStack.asCraftMirror(itemStack)), setItemFunction);
        }

        public void setItem(int i, WildItemStack<?, ?> wildItemStack, boolean setItemFunction){
            ItemStack itemstack = (ItemStack) wildItemStack.getItemStack();

            if(setItemFunction && this.setItemFunction != null){
                this.setItemFunction.accept(i, itemstack);
                return;
            }

            WildItemStack<ItemStack, CraftItemStack> original = getWildItem(i);
            this.items[i] = new WildItemStack<>(itemstack, CraftItemStack.asCraftMirror(itemstack));

            if(!ItemStack.matches(original.getItemStack(), itemstack)) {
                if (itemstack == null)
                    nonEmptyItems--;
                else
                    nonEmptyItems++;
            }

            if (itemstack != null && this.getMaxStackSize() > 0 && itemstack.count > this.getMaxStackSize()) {
                itemstack.count = this.getMaxStackSize();
            }
        }

        public int getMaxStackSize() {
            return this.maxStack;
        }

        public void setMaxStackSize(int size) {
            this.maxStack = size;
        }

        public void update() {
        }

        public boolean a(EntityHuman entityhuman) {
            return true;
        }

        public ItemStack[] getContents() {
            ItemStack[] contents = new ItemStack[this.items.length];
            for(int i = 0; i < contents.length; i++)
                contents[i] = getItem(i);
            return contents;
        }

        public void onOpen(CraftHumanEntity who) {
            if(index != 0 && !((WChest) chest).getTileEntityContainer().getTransaction().contains(who))
                throw new IllegalArgumentException("Opened directly page!");
        }

        public void onClose(CraftHumanEntity who) {
        }

        @Override
        public void startOpen(EntityHuman entityhuman) {
        }

        @Override
        public void closeContainer(EntityHuman entityhuman) {

        }

        public List<HumanEntity> getViewers() {
            return new ArrayList<>(((WChest) chest).getTileEntityContainer().getTransaction());
        }

        public org.bukkit.inventory.InventoryHolder getOwner() {
            return null;
        }

        public boolean b(int i, ItemStack itemstack) {
            return true;
        }

        public boolean isFull(){
            return nonEmptyItems == getSize();
        }

        @Override
        public int getProperty(int i) {
            return 0;
        }

        @Override
        public void b(int i, int i1) {

        }

        @Override
        public int g() {
            return 0;
        }

        @Override
        public void l() {

        }

        @Override
        public boolean hasCustomName() {
            return title != null;
        }

        @Override
        public String getName() {
            return getTitle();
        }

        @Override
        public IChatBaseComponent getScoreboardDisplayName() {
            return new ChatComponentText(getTitle());
        }

        void setTitle(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        @Override
        public String toString() {
            return "WildInventory{" +
                    "title='" + title + '\'' +
                    '}';
        }

    }

    private static class CraftWildInventory extends CraftInventory implements com.bgsoftware.wildchests.objects.inventory.CraftWildInventory {

        CraftWildInventory(IInventory inventory){
            super(inventory);
        }

        @Override
        public Chest getOwner() {
            return getInventory().chest;
        }


        @Override
        public WildItemStack<ItemStack, CraftItemStack> getWildItem(int slot) {
            return getInventory().getWildItem(slot);
        }

        @Override
        public void setItem(int i, WildItemStack<?, ?> itemStack) {
            getInventory().setItem(i, itemStack, true);
        }

        @Override
        public WildItemStack<?, ?>[] getWildContents() {
            return getInventory().items;
        }

        @Override
        public WildInventory getInventory() {
            return (WildInventory) super.getInventory();
        }

        @Override
        public void setTitle(String title) {
            getInventory().setTitle(title);
        }

        @Override
        public String getTitle() {
            return getInventory().getTitle();
        }

        @Override
        public boolean isFull() {
            return getInventory().isFull();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CraftWildInventory && getInventory() == ((CraftWildInventory) obj).getInventory();
        }
    }

    private static class WildContainerChest extends ContainerChest{

        private final PlayerInventory playerInventory;
        private final WildInventory inventory;
        private CraftInventoryView bukkitEntity;

        private WildContainerChest(PlayerInventory playerInventory, EntityHuman entityHuman, WildInventory inventory){
            super(playerInventory, inventory, entityHuman);
            this.playerInventory = playerInventory;
            this.inventory = inventory;
        }

        @Override
        public CraftInventoryView getBukkitView() {
            if(bukkitEntity == null) {
                NMSInventory_v1_8_R3.CraftWildInventory inventory = new NMSInventory_v1_8_R3.CraftWildInventory(this.inventory);
                bukkitEntity = new CraftInventoryView(playerInventory.player.getBukkitEntity(), inventory, this);
            }

            return bukkitEntity;
        }

        static Container of(PlayerInventory playerInventory, EntityHuman entityHuman, WildInventory inventory){
            return new WildContainerChest(playerInventory, entityHuman, inventory);
        }

    }

    private static class WildContainerHopper extends ContainerHopper {

        private final PlayerInventory playerInventory;
        private final WildInventory inventory;
        private CraftInventoryView bukkitEntity;

        private WildContainerHopper(PlayerInventory playerInventory, EntityHuman entityHuman, WildInventory inventory){
            super(playerInventory, inventory, entityHuman);
            this.playerInventory = playerInventory;
            this.inventory = inventory;
        }

        @Override
        public CraftInventoryView getBukkitView() {
            if(bukkitEntity == null) {
                NMSInventory_v1_8_R3.CraftWildInventory inventory = new NMSInventory_v1_8_R3.CraftWildInventory(this.inventory);
                bukkitEntity = new CraftInventoryView(playerInventory.player.getBukkitEntity(), inventory, this);
            }

            return bukkitEntity;
        }

        static WildContainerHopper of(PlayerInventory playerInventory, EntityHuman entityHuman, WildInventory inventory){
            return new WildContainerHopper(playerInventory, entityHuman, inventory);
        }

    }

}