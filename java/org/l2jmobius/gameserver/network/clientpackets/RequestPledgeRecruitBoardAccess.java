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
package org.l2jmobius.gameserver.network.clientpackets;

import org.l2jmobius.commons.network.PacketReader;
import org.l2jmobius.gameserver.instancemanager.ClanEntryManager;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanPrivilege;
import org.l2jmobius.gameserver.model.clan.entry.PledgeRecruitInfo;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * @author Sdw
 */
public class RequestPledgeRecruitBoardAccess implements IClientIncomingPacket
{
	private int _applyType;
	private int _karma;
	private String _information;
	private String _datailedInformation;
	private int _applicationType;
	private int _recruitingType;
	
	@Override
	public boolean read(GameClient client, PacketReader packet)
	{
		_applyType = packet.readD();
		_karma = packet.readD();
		_information = packet.readS();
		_datailedInformation = packet.readS();
		_applicationType = packet.readD(); // 0 - Allow, 1 - Public
		_recruitingType = packet.readD(); // 0 - Main clan
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
		
		final Clan clan = player.getClan();
		if (clan == null)
		{
			player.sendPacket(SystemMessageId.ONLY_THE_CLAN_LEADER_OR_SOMEONE_WITH_RANK_MANAGEMENT_AUTHORITY_MAY_REGISTER_THE_CLAN);
			return;
		}
		
		if (!player.hasClanPrivilege(ClanPrivilege.CL_MANAGE_RANKS))
		{
			player.sendPacket(SystemMessageId.ONLY_THE_CLAN_LEADER_OR_SOMEONE_WITH_RANK_MANAGEMENT_AUTHORITY_MAY_REGISTER_THE_CLAN);
			return;
		}
		
		final PledgeRecruitInfo pledgeRecruitInfo = new PledgeRecruitInfo(clan.getId(), _karma, _information, _datailedInformation, _applicationType, _recruitingType);
		
		switch (_applyType)
		{
			case 0: // remove
			{
				ClanEntryManager.getInstance().removeFromClanList(clan.getId());
				break;
			}
			case 1: // add
			{
				if (ClanEntryManager.getInstance().addToClanList(clan.getId(), pledgeRecruitInfo))
				{
					player.sendPacket(SystemMessageId.ENTRY_APPLICATION_COMPLETE_USE_ENTRY_APPLICATION_INFO_TO_CHECK_OR_CANCEL_YOUR_APPLICATION_APPLICATION_IS_AUTOMATICALLY_CANCELLED_AFTER_30_DAYS_IF_YOU_CANCEL_APPLICATION_YOU_CANNOT_APPLY_AGAIN_FOR_5_MINUTES);
				}
				else
				{
					final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_MAY_APPLY_FOR_ENTRY_AFTER_S1_MINUTE_S_DUE_TO_CANCELLING_YOUR_APPLICATION);
					sm.addLong(ClanEntryManager.getInstance().getClanLockTime(clan.getId()));
					player.sendPacket(sm);
				}
				break;
			}
			case 2: // update
			{
				if (ClanEntryManager.getInstance().updateClanList(clan.getId(), pledgeRecruitInfo))
				{
					player.sendPacket(SystemMessageId.ENTRY_APPLICATION_COMPLETE_USE_ENTRY_APPLICATION_INFO_TO_CHECK_OR_CANCEL_YOUR_APPLICATION_APPLICATION_IS_AUTOMATICALLY_CANCELLED_AFTER_30_DAYS_IF_YOU_CANCEL_APPLICATION_YOU_CANNOT_APPLY_AGAIN_FOR_5_MINUTES);
				}
				else
				{
					final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_MAY_APPLY_FOR_ENTRY_AFTER_S1_MINUTE_S_DUE_TO_CANCELLING_YOUR_APPLICATION);
					sm.addLong(ClanEntryManager.getInstance().getClanLockTime(clan.getId()));
					player.sendPacket(sm);
				}
				break;
			}
		}
	}
}
