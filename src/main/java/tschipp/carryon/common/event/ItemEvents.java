package tschipp.carryon.common.event;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.ClickEvent.Action;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;
import tschipp.carryon.common.handler.ForbiddenTileHandler;
import tschipp.carryon.common.handler.PickupHandler;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemTile;
import tschipp.carryon.network.client.CarrySlotPacket;

public class ItemEvents
{

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onBlockClick(PlayerInteractEvent.RightClickBlock event)
	{
		EntityPlayer player = event.getEntityPlayer();
		ItemStack stack = player.getHeldItemMainhand();
		if (!stack.isEmpty() && stack.getItem() == RegistrationHandler.itemTile && ItemTile.hasTileData(stack))
		{
			player.getEntityData().removeTag("carrySlot");
			event.setUseBlock(Result.DENY);
		}

	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onItemDropped(EntityJoinWorldEvent event)
	{
		Entity e = event.getEntity();
		World world = event.getWorld();
		if (e instanceof EntityItem)
		{
			EntityItem eitem = (EntityItem) e;
			ItemStack stack = eitem.getItem();
			Item item = stack.getItem();
			if (item == RegistrationHandler.itemTile && ItemTile.hasTileData(stack))
			{
				BlockPos pos = eitem.getPosition();
				BlockPos finalPos = pos;
				Block block = ItemTile.getBlock(stack);
				if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos) || !block.canPlaceBlockAt(world, pos))
				{
					for (EnumFacing facing : EnumFacing.VALUES)
					{
						BlockPos offsetPos = pos.offset(facing);
						if (world.getBlockState(offsetPos).getBlock().isReplaceable(world, offsetPos) && block.canPlaceBlockAt(world, offsetPos))
						{
							finalPos = offsetPos;
							break;
						}
					}
				}
				world.setBlockState(finalPos, ItemTile.getBlockState(stack));
				TileEntity tile = world.getTileEntity(finalPos);
				if (tile != null)
				{
					tile.readFromNBT(ItemTile.getTileData(stack));
					tile.setPos(finalPos);
				}
				ItemTile.clearTileData(stack);
				eitem.setItem(ItemStack.EMPTY);
			}
		}
	}

	@SubscribeEvent
	public void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) throws InstantiationException, IllegalAccessException
	{
		EntityPlayer player = event.getEntityPlayer();

		if (player instanceof EntityPlayerMP)
		{

			ItemStack main = player.getHeldItemMainhand();
			ItemStack off = player.getHeldItemOffhand();
			World world = event.getWorld();
			BlockPos pos = event.getPos();
			Block block = world.getBlockState(pos).getBlock();
			IBlockState state = world.getBlockState(pos);

			if (main.isEmpty() && off.isEmpty() && CarryOnKeybinds.isKeyPressed(player) && !ForbiddenTileHandler.isForbidden(block))
			{
				ItemStack stack = new ItemStack(RegistrationHandler.itemTile);

				TileEntity te = world.getTileEntity(pos);
				if (PickupHandler.canPlayerPickUpBlock(player, te, world, pos))
				{
					if (ItemTile.storeTileData(te, world, pos, state.getActualState(world, pos), stack))
					{
						IBlockState statee = world.getBlockState(pos);
						NBTTagCompound tag = new NBTTagCompound();
						tag = world.getTileEntity(pos) != null ? world.getTileEntity(pos).writeToNBT(tag) : new NBTTagCompound();

						try
						{
							CarryOn.network.sendTo(new CarrySlotPacket(player.inventory.currentItem), (EntityPlayerMP) player);
							world.removeTileEntity(pos);
							world.setBlockToAir(pos);
							player.setHeldItem(EnumHand.MAIN_HAND, stack);
							event.setUseBlock(Result.DENY);
							event.setCanceled(true);
						}
						catch (Exception e)
						{
							CarryOn.network.sendTo(new CarrySlotPacket(9), (EntityPlayerMP) player);
							world.setBlockState(pos, statee);
							if (!tag.hasNoTags())
								TileEntity.create(world, tag);

							player.sendMessage(new TextComponentString(TextFormatting.RED + "Error detected. Cannot pick up block."));
							TextComponentString s = new TextComponentString(TextFormatting.GOLD + "here");
							s.getStyle().setClickEvent(new ClickEvent(Action.OPEN_URL, "https://github.com/Tschipp/CarryOn/issues"));
							player.sendMessage(new TextComponentString(TextFormatting.RED + "Please report this error ").appendSibling(s));
						}

					}

				}

			}
		}
	}

}
