package com.survivorserver.GlobalMarket.Command;

import java.util.ArrayList;
import java.util.List;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.survivorserver.GlobalMarket.HistoryHandler.MarketAction;
import com.survivorserver.GlobalMarket.LocaleHandler;
import com.survivorserver.GlobalMarket.Market;
import com.survivorserver.GlobalMarket.MarketStorage;

public class CreateCommand extends SubCommand {

	public CreateCommand(Market market, LocaleHandler locale) {
		super(market, locale);
	}

	@Override
	public String getCommand() {
		return "create";
	}
	
	@Override
	public String[] getAliases() {
		return new String[] {"list", "sell", "add"};
	}
	
	@Override
	public String getPermissionNode() {
		return "globalmarket.create";
	}
	
	@Override
	public String getHelp() {
		return locale.get("cmd.prefix") + locale.get("cmd.create_syntax") + " " + locale.get("cmd.create_descr");
	}
	
	@Override
	public boolean allowConsoleSender() {
		return false;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, String[] args) {
		String prefix = locale.get("cmd.prefix");
		MarketStorage storage = market.getStorage();
		Economy econ = market.getEcon();
		Player player = (Player) sender;
		if (player.getItemInHand() != null && player.getItemInHand().getType() != Material.AIR && args.length >= 2) {
			if (player.getGameMode() == GameMode.CREATIVE && !market.allowCreative(player)) {
				player.sendMessage(ChatColor.RED + locale.get("not_allowed_while_in_creative"));
				return true;
			}
			if (market.itemBlacklisted(player.getItemInHand())) {
				sender.sendMessage(ChatColor.RED + locale.get("item_is_blacklisted"));
				return true;
			}
			double price = 0;
			try {
				price = Double.parseDouble(args[1]);
			} catch(Exception e) {
				player.sendMessage(ChatColor.RED + locale.get("not_a_valid_number", args[1]));
				return true;
			}
			if (price < 0.01) {
				sender.sendMessage(prefix + locale.get("price_too_low"));
				return true;
			}
			double maxPrice = market.getMaxPrice(player);
			if (maxPrice > 0 && price > maxPrice) {
				sender.sendMessage(prefix + locale.get("price_too_high"));
				return true;
			}
			int max = market.maxListings(player);
			if (max > 0 && storage.getNumListingsFor(sender.getName(), player.getWorld().getName()) >= max) {
				sender.sendMessage(ChatColor.RED + locale.get("selling_too_many_items"));
				return true;
			}
			List<String> extraArgs = new ArrayList<String>();
			for (int i = 0; i < args.length; i++) {
				if (args[i].startsWith("-")) {
					extraArgs.add(args[i]);
				}
			}
			boolean infinite = false;
			if (extraArgs.contains("-inf") && sender.hasPermission("globalmarket.infinite")) {
				infinite = true;
			}
			int amount = 0;
			double fee = market.getCreationFee(player);
			if ((args.length == 3 && extraArgs.isEmpty()) || (args.length == 4 && !extraArgs.isEmpty())) {
				try {
					amount = Integer.parseInt(args[2]);
				} catch(Exception e) {
					player.sendMessage(ChatColor.RED + locale.get("not_a_valid_number", args[2]));
					return true;
				}
				if (amount <= 0) {
					player.sendMessage(ChatColor.RED + locale.get("not_a_valid_amount", args[2]));
					return true;
				}
				if (!infinite && player.getItemInHand().getAmount() < amount) {
					player.sendMessage(ChatColor.RED + locale.get("you_dont_have_x_of_this_item", amount));
					return true;
				}
			}
			ItemStack toList = player.getItemInHand().clone();
			if (fee > 0) {
				if (econ.has(sender.getName(), fee)) {
					econ.withdrawPlayer(sender.getName(), fee);
					if (market.enableHistory()) {
						market.getHistory().incrementSpent(sender.getName(), fee);
					}
					player.sendMessage(ChatColor.GREEN + locale.get("charged_fee", econ.format(fee)));
				} else {
					sender.sendMessage(ChatColor.RED + locale.get("you_cant_pay_this_fee"));
					return true;
				}
			}
			if (amount > 0) {
				if (player.getItemInHand().getAmount() == amount) {
					if (!infinite) {
						player.setItemInHand(new ItemStack(Material.AIR));
					}
				} else {
					if (!infinite) {
						player.getItemInHand().setAmount(player.getItemInHand().getAmount() - amount);
					}
				}
				toList.setAmount(amount);
			} else {
				player.setItemInHand(new ItemStack(Material.AIR));
			}
			String world = player.getWorld().getName();
			int tradeTime = market.getTradeTime(player);
			if (tradeTime > 0) {
				storage.queueListing(infinite ? market.getInfiniteSeller() : player.getName(), toList, price, world);
				sender.sendMessage(ChatColor.GREEN + locale.get("item_queued", tradeTime));
			} else {
				storage.createListing(infinite ? market.getInfiniteSeller() : player.getName(), toList, price, world);
				sender.sendMessage(ChatColor.GREEN + locale.get("item_listed"));
			}
			if (market.enableHistory()) {
				market.getHistory().storeHistory(player.getName(), "", MarketAction.LISTING_CREATED, toList, price);
			}
		} else {
			sender.sendMessage(prefix + locale.get("hold_an_item") + " " + locale.get("cmd.create_syntax"));
		}
		return true;
	}
}
