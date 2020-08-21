package com.bgsoftware.wildchests.objects.chests;

import com.bgsoftware.wildchests.api.objects.data.InventoryData;
import com.bgsoftware.wildchests.database.Query;
import com.bgsoftware.wildchests.database.StatementHolder;
import com.bgsoftware.wildchests.api.objects.chests.RegularChest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;
import com.bgsoftware.wildchests.objects.inventory.CraftWildInventory;
import com.bgsoftware.wildchests.objects.inventory.WildItemStack;
import com.bgsoftware.wildchests.utils.SyncedArray;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

public class WRegularChest extends WChest implements RegularChest {

    protected SyncedArray<CraftWildInventory> inventories;

    public WRegularChest(UUID placer, Location location, ChestData chestData){
        super(placer, location, chestData);
        this.inventories = new SyncedArray<>(chestData.getDefaultPagesAmount());
        initContainer(chestData);
    }

    @Override
    public Inventory getPage(int page) {
        if(page < 0 || page >= getPagesAmount())
            return null;

        CraftWildInventory inventory = inventories.get(page);
        inventory.setTitle(getData().getTitle(page + 1).replace("{0}", getPagesAmount() + ""));

        return inventory;
    }

    @Override
    public Inventory[] getPages() {
        return inventories.stream().toArray(Inventory[]::new);
    }

    @Override
    public Inventory setPage(int page, int size, String title) {
        ChestData chestData = getData();
        checkCapacity(page + 1, chestData.getDefaultSize(), chestData.getDefaultTitle());
        CraftWildInventory inventory = plugin.getNMSInventory().createInventory(this, size, title, page);
        inventories.set(page, inventory);
        return inventory;
    }

    @Override
    public int getPagesAmount() {
        return inventories.length();
    }

    @Override
    public int getPageIndex(Inventory inventory) {
        for (int i = 0; i < getPagesAmount(); i++) {
            if (inventories.get(i).equals(inventory))
                return i;
        }

        return -1;
    }

    @Override
    public WildItemStack<?, ?>[] getWildContents() {
        return getNextFree().getWildContents();
    }

    @Override
    public WildItemStack<?, ?> getWildItem(int i) {
        Inventory firstPage = getPage(0);

        if(firstPage == null)
            return WildItemStack.AIR.cloneItemStack();

        int pageSize = firstPage.getSize();
        int page = i / pageSize;
        int slot = i % pageSize;

        CraftWildInventory actualPage = (CraftWildInventory) getPage(page);

        if(actualPage == null)
            return WildItemStack.AIR.cloneItemStack();

        return actualPage.getWildItem(slot);
    }

    @Override
    public void setItem(int i, WildItemStack<?, ?> itemStack) {
        Inventory firstPage = getPage(0);

        if(firstPage == null)
            return;

        int pageSize = firstPage.getSize();
        int page = i / pageSize;
        int slot = i % pageSize;

        CraftWildInventory actualPage = (CraftWildInventory) getPage(page);

        if(actualPage == null)
            return;

        actualPage.setItem(slot, itemStack);
    }

    @Override
    public void remove() {
        super.remove();
        Query.REGULAR_CHEST_DELETE.getStatementHolder()
                .setLocation(getLocation())
                .execute(true);
    }

    @Override
    public void executeInsertQuery(boolean async) {
        Query.REGULAR_CHEST_INSERT.getStatementHolder()
                .setLocation(location)
                .setString(placer.toString())
                .setString(getData().getName())
                .setString("")
                .execute(async);
    }

    @Override
    public void executeUpdateQuery(boolean async) {
        Query.REGULAR_CHEST_UPDATE.getStatementHolder()
                .setString(placer.toString())
                .setString(getData().getName())
                .setInventories(getPages())
                .setLocation(location)
                .execute(async);
    }

    @Override
    public StatementHolder getSelectQuery() {
        return Query.REGULAR_CHEST_SELECT.getStatementHolder().setLocation(location);
    }

    private CraftWildInventory getNextFree(){
        for(int i = 0; i < getPagesAmount(); i++){
            if(!inventories.get(i).isFull()) {
                return inventories.get(i);
            }
        }

        return inventories.get(0);
    }

    private void checkCapacity(int size, int inventorySize, String inventoryTitle){
        int oldSize = getPagesAmount();
        if(size > oldSize){
            inventories.increaseCapacity(size);
            for(int i = oldSize; i < size; i++)
                inventories.set(i, plugin.getNMSInventory().createInventory(this, inventorySize, inventoryTitle, i));
        }
    }

    private void initContainer(ChestData chestData){
        int size = chestData.getDefaultSize();
        Map<Integer, InventoryData> pagesData = chestData.getPagesData();

        for(int i = 0; i < getPagesAmount(); i++){
            String title = pagesData.containsKey(i + 1) ? pagesData.get(i + 1).getTitle() : chestData.getDefaultTitle();
            inventories.set(i, plugin.getNMSInventory().createInventory(this, size, title, i));
        }
    }

}
