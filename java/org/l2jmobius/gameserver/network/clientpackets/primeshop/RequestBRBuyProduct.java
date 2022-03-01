/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.network.clientpackets.primeshop;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.l2jmobius.Config;
import org.l2jmobius.commons.network.PacketReader;
import org.l2jmobius.commons.util.Chronos;
import org.l2jmobius.gameserver.data.xml.PrimeShopData;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.actor.request.PrimeShopRequest;
import org.l2jmobius.gameserver.model.holders.ItemHolder;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.primeshop.PrimeShopGroup;
import org.l2jmobius.gameserver.model.primeshop.PrimeShopItem;
import org.l2jmobius.gameserver.model.variables.AccountVariables;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.clientpackets.IClientIncomingPacket;
import org.l2jmobius.gameserver.network.serverpackets.primeshop.ExBRBuyProduct;
import org.l2jmobius.gameserver.network.serverpackets.primeshop.ExBRBuyProduct.ExBrProductReplyType;
import org.l2jmobius.gameserver.network.serverpackets.primeshop.ExBRGamePoint;
import org.l2jmobius.gameserver.util.Util;

/**
 * @author Gnacik, UnAfraid
 */
public class RequestBRBuyProduct implements IClientIncomingPacket
{
	private static final int HERO_COINS = 23805;
	
	private int _brId;
	private int _count;
	
	@Override
	public boolean read(GameClient client, PacketReader packet)
	{
		_brId = packet.readD();
		_count = packet.readD();
		return true;
	}
	
	@Override
	public void run(GameClient client)
	{
		final PlayerInstance player = client.getPlayer();
		if (player == null)
		{
			return;
		}
		
		if (player.hasItemRequest() || player.hasRequest(PrimeShopRequest.class))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER_STATE));
			return;
		}
		
		player.addRequest(new PrimeShopRequest(player));
		
		final PrimeShopGroup item = PrimeShopData.getInstance().getItem(_brId);
		if (validatePlayer(item, _count, player))
		{
			
			boolean hasItems = true;
			// First loop to validate all items
			for (ItemHolder itemHolder : validatePaymentId(item))
			{
				final int paymentId = itemHolder.getId();
				final long price = itemHolder.getCount() * _count;
				if (paymentId < 0)
				{
					hasItems = false;
				}
				else if (paymentId > 0)
				{
					if (player.getInventory().getInventoryItemCount(paymentId, 0) < price)
					{
						hasItems = false;
					}
				}
				else
				{ // this is always 0
					if (player.getPrimePoints() < price)
					{
						hasItems = false;
					}
				}
			}
			
			if (!hasItems)
			{
				player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.LACK_OF_POINT));
				player.removeRequest(PrimeShopRequest.class);
				return;
			}
			
			// Second loop, only if all criteria has been met!
			// this should always be reached if player has all the coins needed for the purchase
			for (ItemHolder itemHolder : validatePaymentId(item))
			{
				final int paymentId = itemHolder.getId();
				final long price = itemHolder.getCount() * _count;
				if (paymentId > 0)
				{
					player.destroyItemByItemId("PrimeShop-" + item.getBrId(), paymentId, price, player, true);
				}
				else if (paymentId == 0)
				{
					player.setPrimePoints(player.getPrimePoints() - (int) price);
					if (Config.VIP_SYSTEM_PRIME_AFFECT)
					{
						player.updateVipPoints(price);
					}
				}
			}
			
			for (PrimeShopItem subItem : item.getItems())
			{
				player.addItem("PrimeShop", subItem.getId(), subItem.getCount() * _count, player, true);
			}
			if (item.isVipGift())
			{
				player.getAccountVariables().set(AccountVariables.VIP_ITEM_BOUGHT, Calendar.getInstance().getTimeInMillis());
			}
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.SUCCESS));
			player.sendPacket(new ExBRGamePoint(player));
		}
		
		player.removeRequest(PrimeShopRequest.class);
	}
	
	/**
	 * @param item
	 * @param count
	 * @param player
	 * @return
	 */
	private static boolean validatePlayer(PrimeShopGroup item, int count, PlayerInstance player)
	{
		final long currentTime = Chronos.currentTimeMillis() / 1000;
		if (item == null)
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_PRODUCT));
			Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " tried to buy invalid brId from Prime", Config.DEFAULT_PUNISH);
			return false;
		}
		else if ((count < 1) || (count > 99))
		{
			Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " tried to buy invalid itemcount [" + count + "] from Prime", Config.DEFAULT_PUNISH);
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER_STATE));
			return false;
		}
		else if ((item.getMinLevel() > 0) && (item.getMinLevel() > player.getLevel()))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER));
			return false;
		}
		else if ((item.getMaxLevel() > 0) && (item.getMaxLevel() < player.getLevel()))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER));
			return false;
		}
		else if ((item.getMinBirthday() > 0) && (item.getMinBirthday() > player.getBirthdays()))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER_STATE));
			return false;
		}
		else if ((item.getMaxBirthday() > 0) && (item.getMaxBirthday() < player.getBirthdays()))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVALID_USER_STATE));
			return false;
		}
		else if ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) & item.getDaysOfWeek()) == 0)
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.NOT_DAY_OF_WEEK));
			return false;
		}
		else if ((item.getStartSale() > 1) && (item.getStartSale() > currentTime))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.BEFORE_SALE_DATE));
			return false;
		}
		else if ((item.getEndSale() > 1) && (item.getEndSale() < currentTime))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.AFTER_SALE_DATE));
			return false;
		}
		
		if ((item.getVipTier() > player.getVipTier()) || (item.isVipGift() && !canReceiveGift(player, item)))
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.SOLD_OUT));
			return false;
		}
		
		final int weight = item.getWeight() * count;
		final long slots = item.getCount() * count;
		if (player.getInventory().validateWeight(weight))
		{
			if (!player.getInventory().validateCapacity(slots))
			{
				player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVENTROY_OVERFLOW));
				return false;
			}
		}
		else
		{
			player.sendPacket(new ExBRBuyProduct(ExBrProductReplyType.INVENTROY_OVERFLOW));
			return false;
		}
		
		return true;
	}
	
	/**
	 * Check if player can receive Gift from L2 Store
	 * @param player player in question
	 * @param item requested item.
	 * @return true if player can receive gift item.
	 */
	private static boolean canReceiveGift(PlayerInstance player, PrimeShopGroup item)
	{
		if (!Config.VIP_SYSTEM_ENABLED)
		{
			return false;
		}
		if (player.getVipTier() <= 0)
		{
			return false;
		}
		else if (item.getVipTier() != player.getVipTier())
		{
			player.sendMessage("This item is not for your vip tier!");
			return false;
		}
		else
		{
			long timeBought = player.getAccountVariables().getLong(AccountVariables.VIP_ITEM_BOUGHT, 0L);
			return timeBought <= 0;
		}
	}
	
	private static List<ItemHolder> validatePaymentId(PrimeShopGroup item)
	{
		
		List<ItemHolder> temp = new LinkedList<>();
		switch (item.getPaymentType())
		{
			case 0: // Prime points
			{
				if (item.getVipTier() > 0)
				{
					if (item.getPrice() > 0)
					{
						temp.add(new ItemHolder(Inventory.GOLD_COIN, item.getPrice()));
					}
					if (item.getSilverCoin() > 0)
					{
						temp.add(new ItemHolder(Inventory.SILVER_COIN, item.getSilverCoin()));
					}
				}
				else
				{
					temp.add(new ItemHolder(0, item.getPrice())); // prime points
				}
				return temp;
			}
			case 1: // Adenas
			{
				temp.add(new ItemHolder(Inventory.ADENA_ID, item.getPrice())); // Is this even used????
				return temp;
			}
			case 2: // Hero coins
			{
				temp.add(new ItemHolder(HERO_COINS, item.getPrice())); // Is this even used????
				return temp;
			}
		}
		temp.add(new ItemHolder(-1, -1));
		return temp;
	}
}
