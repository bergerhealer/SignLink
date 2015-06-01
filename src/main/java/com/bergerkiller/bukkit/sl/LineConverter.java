package com.bergerkiller.bukkit.sl;

import net.minecraft.server.v1_8_R3.IChatBaseComponent;

import com.bergerkiller.bukkit.common.conversion.ChatComponentConvertor;

public class LineConverter {
	public static IChatBaseComponent[] convert(String[] text){
		IChatBaseComponent[] chat = new IChatBaseComponent[text.length];
		for(int i = 0; i<text.length; i++){
			chat[i] = ChatComponentConvertor.convertTextToIChatBaseComponent(text[i]);
		}
		return chat;
	}
}
