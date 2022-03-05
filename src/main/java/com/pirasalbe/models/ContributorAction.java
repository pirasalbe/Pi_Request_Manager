package com.pirasalbe.models;

public enum ContributorAction {
	CONFIRM("confirm"), DONE("mark as done"), PENDING("mark as pending"), PAUSE("mark as paused"), CANCEL("cancel"),
	REMOVE("remove");

	private String description;

	private ContributorAction(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
