package com.pirasalbe.utils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.MessageEntity.Type;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import com.pirasalbe.models.database.Request;
import com.pirasalbe.models.request.Source;

/**
 * Utility methods to manage requests
 *
 * @author pirasalbe
 *
 */
public class RequestUtils {

	private RequestUtils() {
		super();
	}

	public static List<Source> getNoRepeatSources(String noRepeat) {
		List<Source> noRepeatForSources = new ArrayList<>();

		if (noRepeat != null) {
			String[] sources = noRepeat.toUpperCase().split(",");
			for (String sourceString : sources) {
				Source source = Source.valueOf(sourceString.trim());
				noRepeatForSources.add(source);
			}
		}

		return noRepeatForSources;
	}

	public static String getNoRepeatSources(List<Source> sources) {
		String noRepeat = null;

		if (!sources.isEmpty()) {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < sources.size(); i++) {
				Source source = sources.get(i);
				builder.append(source);
				if (i < sources.size() - 1) {
					builder.append(",");
				}
			}
			noRepeat = builder.toString();
		}

		return noRepeat;
	}

	public static String getLink(String content, MessageEntity[] entities) {
		String link = null;

		if (entities != null) {
			for (MessageEntity entity : entities) {
				if (entity.type().equals(Type.text_link)) {
					link = entity.url();
				} else if (entity.type().equals(Type.url)) {
					link = content.substring(entity.offset(), entity.offset() + entity.length());
				}
			}
		}

		if (link != null) {
			// remove params
			link = link.split("\\?")[0];
		}

		return link;
	}

	/**
	 * Extract content from message
	 *
	 * @param text     Text content
	 * @param entities Entities
	 * @return
	 */
	public static String getContent(String text, MessageEntity[] entities) {
		StringBuilder contentBuilder = new StringBuilder(text);

		if (entities != null && entities.length > 0) {
			int offset = 0;

			// add all parts
			for (MessageEntity entity : entities) {
				// remove the entity part
				int begin = offset + entity.offset();
				int end = begin + entity.length();
				contentBuilder.delete(begin, end);

				// add formatted text
				String value = getEntityValue(text, entity);
				contentBuilder.insert(begin, value);

				// update offset
				offset += value.length() - (end - begin);
			}
		}

		return contentBuilder.toString();
	}

	private static String getEntityValue(String text, MessageEntity entity) {
		StringBuilder value = new StringBuilder();

		switch (entity.type()) {
		case bold:
			value.append("<b>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</b>");
			break;
		case code:
			value.append("<code>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</code>");
			break;
		case italic:
			value.append("<i>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</i>");
			break;
		case pre:
			value.append("<pre>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</pre>");
			break;
		case spoiler:
			value.append("<tg-spoiler>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</tg-spoiler>");
			break;
		case strikethrough:
			value.append("<s>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</s>");
			break;
		case text_link:
			value.append("<a href='").append(entity.url()).append("'>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</a>");
			break;
		case underline:
			value.append("<u>");
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			value.append("</u>");
			break;
		case bot_command:
		case cashtag:
		case hashtag:
		case url:
		case email:
		case mention:
		case phone_number:
		case text_mention:
		default:
			value.append(text.substring(entity.offset(), entity.offset() + entity.length()));
			break;
		}

		return value.toString();
	}

	public static String getTimeBetweenDates(LocalDateTime from, LocalDateTime to) {
		StringBuilder stringBuilder = new StringBuilder();

		// get numbers
		long days = DateUtils.getDays(from, to);
		long hours = DateUtils.getHours(from, to, days);
		long minutes = DateUtils.getMinutes(from, to, days, hours);

		// aggregate them
		if (days > 0) {
			stringBuilder.append(days).append(" day").append(StringUtils.getPlural(days));
		}

		// get hours
		if (days > 0 && hours > 0) {
			stringBuilder.append(minutes > 0 ? ", " : " and ");
		}

		if (hours > 0) {
			stringBuilder.append(hours).append(" hour").append(StringUtils.getPlural(hours));
		}

		// get minutes
		if (hours > 0 && minutes > 0 || days > 0 && minutes > 0) {
			stringBuilder.append(" and ");
		}

		if (minutes > 0 || stringBuilder.length() == 0) {
			stringBuilder.append(minutes).append(" minute").append(StringUtils.getPlural(minutes));
		}

		return stringBuilder.toString();
	}

	public static String getComeBackAgain(LocalDateTime requestTime, LocalDateTime nextValidRequest) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("Come back again in ");

		stringBuilder.append(getTimeBetweenDates(requestTime, nextValidRequest));

		stringBuilder.append(".");

		return stringBuilder.toString();
	}

	public static String getActionsLink(String username, Long messageId, Long groupId) {
		StringBuilder paramBuilder = new StringBuilder();
		paramBuilder.append("show_message=");
		paramBuilder.append(messageId);
		paramBuilder.append("_").append(TelegramConditionUtils.GROUP_CONDITION);
		paramBuilder.append(groupId);
		return TelegramUtils.getStartLink(username, paramBuilder.toString());
	}

	public static String getRequestInfo(TelegramBot bot, String groupName, Request request) {
		StringBuilder messageBuilder = new StringBuilder();

		messageBuilder.append(request.getContent());
		messageBuilder.append("\n\n[");

		messageBuilder.append("Request by ").append(getUser(bot, request)).append("(<code>").append(request.getUserId())
				.append("</code>)");
		messageBuilder.append(" in ").append("#").append(groupName.replace(' ', '_')).append(".");
		messageBuilder.append(" Status: <b>").append(request.getStatus().getDescription().toUpperCase()).append("</b>");

		messageBuilder.append("]");

		return messageBuilder.toString();
	}

	private static String getUser(TelegramBot bot, Request request) {
		GetChatMember getChatMember = new GetChatMember(request.getId().getGroupId(), request.getUserId());
		GetChatMemberResponse member = bot.execute(getChatMember);

		String user = null;
		if (member.isOk()) {
			user = TelegramUtils.tagUser(member.chatMember().user());
		} else {
			user = TelegramUtils.tagUser(request.getUserId());
		}

		return user.replace(".", "");
	}

}
